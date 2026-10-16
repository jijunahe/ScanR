"""Turn a folder of guided photos into a mesh Blender can open."""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

import numpy as np


class ReconstructionError(RuntimeError):
    pass


def colmap_command(colmap: str, *args: str) -> list[str]:
    if colmap.lower().endswith(".bat"):
        return ["cmd", "/c", colmap, *args]
    return [colmap, *args]


def find_colmap() -> str | None:
    env = os.environ.get("COLMAP_PATH", "").strip()
    if env and Path(env).exists():
        return env
    found = shutil.which("colmap")
    if found:
        return found
    candidates = [
        r"C:\Program Files\COLMAP\COLMAP.bat",
        r"C:\Program Files\COLMAP\bin\colmap.exe",
        "/usr/bin/colmap",
        "/usr/local/bin/colmap",
    ]
    for candidate in candidates:
        if Path(candidate).exists():
            return candidate
    return None


def reconstruct(job_dir: Path, log) -> dict:
    colmap = find_colmap()
    if not colmap:
        raise ReconstructionError(
            "COLMAP no está instalado. Instálalo y vuelve a abrir el servidor. "
            "En Windows, descarga el ZIP desde github.com/colmap/colmap/releases "
            "y define la variable COLMAP_PATH con la ruta de COLMAP.bat."
        )

    images = job_dir / "images"
    database = job_dir / "database.db"
    sparse = job_dir / "sparse"
    sparse.mkdir(exist_ok=True)
    work = job_dir / "work"
    work.mkdir(exist_ok=True)

    params = _average_camera_params(job_dir / "meta.json")
    log("Buscando puntos en común entre las fotos…")
    _extract_features(colmap, database, images, params, log)
    log("Emparejando fotos…")
    _run_first_ok(
        [
            colmap_command(colmap, "exhaustive_matcher", "--database_path", str(database), "--SiftMatching.use_gpu", "0"),
            colmap_command(colmap, "exhaustive_matcher", "--database_path", str(database)),
        ],
        log,
    )
    log("Calculando la posición de cada foto…")
    _run(colmap_command(colmap, "mapper", "--database_path", str(database), "--image_path", str(images), "--output_path", str(sparse)), log)

    model = _largest_model(sparse)
    if model is None:
        raise ReconstructionError(
            "Las fotos no se pudieron unir. Acércate, da la vuelta más despacio "
            "y pon el objeto sobre una superficie con dibujo, como un periódico."
        )

    txt_dir = work / "txt"
    txt_dir.mkdir(exist_ok=True)
    _run(
        colmap_command(colmap, "model_converter", "--input_path", str(model), "--output_path", str(txt_dir), "--output_type", "TXT"),
        log,
    )
    _run(
        colmap_command(colmap, "model_converter", "--input_path", str(model), "--output_path", str(work / "nube.ply"), "--output_type", "PLY"),
        log,
    )

    mesh_ply = work / "modelo.ply"
    textured = None
    if os.environ.get("SCANR_USE_GPU", "0") == "1":
        textured = _try_dense(colmap, images, model, job_dir / "dense", mesh_ply, log)
    if not mesh_ply.exists():
        log("Creando la superficie (sin tarjeta NVIDIA)…")
        _run_first_ok(
            [
                colmap_command(colmap, "delaunay_mesher", "--input_path", str(model), "--output_path", str(mesh_ply), "--input_type", "sparse"),
                colmap_command(colmap, "delaunay_mesher", "--input_path", str(model), "--output_path", str(mesh_ply)),
                colmap_command(colmap, "delaunay_mesher", "--input_path", str(txt_dir), "--output_path", str(mesh_ply)),
            ],
            log,
        )

    import trimesh

    mesh = trimesh.load(mesh_ply, force="mesh")
    if mesh.is_empty or len(getattr(mesh, "faces", [])) == 0:
        raise ReconstructionError(
            "Se reconoció la escena, pero no salió una superficie. "
            "Haz otra vuelta con más fotos y el objeto más quieto."
        )

    points_path = txt_dir / "points3D.txt"
    if points_path.exists():
        xyz, rgb = _parse_points(points_path)
        if len(xyz) > 0:
            _transfer_color(mesh, xyz, rgb)

    aligned = False
    centers = _colmap_centers(txt_dir / "images.txt")
    arcore = _arcore_centers(job_dir / "meta.json")
    pairs = [(centers[name], arcore[name]) for name in centers.keys() & arcore.keys()]
    if len(pairs) >= 3:
        src = np.array([a for a, _ in pairs], dtype=np.float64)
        dst = np.array([b for _, b in pairs], dtype=np.float64)
        scale, rotation, translation = umeyama(src, dst)
        if np.isfinite(scale) and scale > 0:
            mesh.vertices = (scale * (rotation @ mesh.vertices.T)).T + translation
            aligned = True
            log(f"Escala ajustada al tamaño real (factor {scale:.3f}).")

    obj_path = work / "modelo.obj"
    mesh.export(obj_path)
    colored_ply = work / "modelo-color.ply"
    mesh.export(colored_ply)

    archive = job_dir / "modelo.zip"
    if archive.exists():
        archive.unlink()
    shutil.make_archive(str(job_dir / "modelo"), "zip", work, ".")

    return {
        "zip": archive,
        "images": _registered_count(txt_dir / "images.txt"),
        "faces": int(len(mesh.faces)),
        "aligned": aligned,
        "textured": textured is not None,
    }


def umeyama(src: np.ndarray, dst: np.ndarray) -> tuple[float, np.ndarray, np.ndarray]:
    """Similarity that maps src points onto dst points."""
    count = src.shape[0]
    mu_src = src.mean(axis=0)
    mu_dst = dst.mean(axis=0)
    src_c = src - mu_src
    dst_c = dst - mu_dst
    covariance = dst_c.T @ src_c / count
    u_mat, singular, vt_mat = np.linalg.svd(covariance)
    sign = np.eye(3)
    if np.linalg.det(u_mat) * np.linalg.det(vt_mat) < 0:
        sign[2, 2] = -1
    rotation = u_mat @ sign @ vt_mat
    variance = float((src_c ** 2).sum() / count)
    scale = float(np.trace(np.diag(singular) @ sign) / variance) if variance > 0 else 1.0
    translation = mu_dst - scale * rotation @ mu_src
    return scale, rotation, translation


def _extract_features(colmap: str, database: Path, images: Path, params: str | None, log) -> None:
    base = colmap_command(colmap, "feature_extractor", "--database_path", str(database), "--image_path", str(images))
    variants = []
    if params:
        variants.append(base + [
            "--ImageReader.single_camera", "1",
            "--ImageReader.camera_model", "PINHOLE",
            "--ImageReader.camera_params", params,
            "--SiftExtraction.use_gpu", "0",
            "--SiftExtraction.max_image_size", "1600",
        ])
    variants.append(base + ["--ImageReader.single_camera", "1", "--SiftExtraction.use_gpu", "0"])
    variants.append(base + ["--ImageReader.single_camera", "1"])
    _run_first_ok(variants, log)


def _try_dense(colmap: str, images: Path, model: Path, dense: Path, mesh_ply: Path, log) -> Path | None:
    log("Reconstrucción detallada con la tarjeta gráfica…")
    dense.mkdir(exist_ok=True)
    _run(colmap_command(
        colmap, "image_undistorter",
        "--image_path", str(images),
        "--input_path", str(model),
        "--output_path", str(dense),
        "--output_type", "COLMAP",
        "--max_image_size", "1600",
    ), log)
    _run(colmap_command(
        colmap, "patch_match_stereo",
        "--workspace_path", str(dense),
        "--workspace_format", "COLMAP",
        "--PatchMatchStereo.geom_consistency", "true",
    ), log)
    fused = dense / "fused.ply"
    _run(colmap_command(
        colmap, "stereo_fusion",
        "--workspace_path", str(dense),
        "--workspace_format", "COLMAP",
        "--input_type", "geometric",
        "--output_path", str(fused),
    ), log)
    poisson = dense / "meshed-poisson.ply"
    _run(colmap_command(colmap, "poisson_mesher", "--input_path", str(fused), "--output_path", str(poisson)), log)
    shutil.copyfile(poisson, mesh_ply)
    textured = dense / "textured"
    try:
        _run(colmap_command(
            colmap, "mesh_texturer",
            "--workspace_path", str(dense),
            "--input_path", str(poisson),
            "--output_path", str(textured),
        ), log)
        return textured
    except ReconstructionError as error:
        log(f"Sin textura: {error}")
        return mesh_ply


def _run_first_ok(commands: list[list[str]], log) -> None:
    last: Exception | None = None
    for command in commands:
        try:
            _run(command, log)
            return
        except ReconstructionError as error:
            last = error
            log(str(error))
    if last:
        raise last
    raise ReconstructionError("No hubo comando para ejecutar.")


def _run(command: list[str], log) -> None:
    log("$ " + " ".join(command))
    completed = subprocess.run(command, capture_output=True, text=True)
    tail = "\n".join(part.strip() for part in (completed.stdout, completed.stderr) if part and part.strip())
    if tail:
        log(tail[-2500:])
    if completed.returncode != 0:
        step = command[1] if len(command) > 1 else command[0]
        raise ReconstructionError(f"COLMAP falló en {step} (código {completed.returncode}).")


def _largest_model(sparse: Path) -> Path | None:
    models = [path for path in sparse.iterdir() if path.is_dir()]
    if not models:
        return None
    def weight(path: Path) -> int:
        images = path / "images.bin"
        return images.stat().st_size if images.exists() else 0
    return max(models, key=weight)


def _average_camera_params(meta_path: Path) -> str | None:
    meta = _read_meta(meta_path)
    rows = meta.get("images") or []
    if not rows:
        return None
    fx = sum(float(row["fx"]) for row in rows) / len(rows)
    fy = sum(float(row["fy"]) for row in rows) / len(rows)
    cx = sum(float(row["cx"]) for row in rows) / len(rows)
    cy = sum(float(row["cy"]) for row in rows) / len(rows)
    return f"{fx:.4f},{fy:.4f},{cx:.4f},{cy:.4f}"


def _read_meta(path: Path) -> dict:
    import json
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def _arcore_centers(path: Path) -> dict[str, np.ndarray]:
    centers = {}
    for row in _read_meta(path).get("images") or []:
        centers[row["file"]] = np.array([float(row["tx"]), float(row["ty"]), float(row["tz"])], dtype=np.float64)
    return centers


def _colmap_centers(path: Path) -> dict[str, np.ndarray]:
    centers = {}
    pending = None
    if not path.exists():
        return centers
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if pending is None and len(parts) >= 10 and parts[0].isdigit():
            pending = parts
            continue
        if pending is None:
            continue
        qw, qx, qy, qz = (float(value) for value in pending[1:5])
        translation = np.array([float(value) for value in pending[5:8]], dtype=np.float64)
        rotation = _quaternion_to_matrix(qw, qx, qy, qz)
        center = -rotation.T @ translation
        centers[" ".join(pending[9:])] = center
        pending = None
    return centers


def _quaternion_to_matrix(qw: float, qx: float, qy: float, qz: float) -> np.ndarray:
    return np.array([
        [1 - 2 * qy * qy - 2 * qz * qz, 2 * qx * qy - 2 * qz * qw, 2 * qx * qz + 2 * qy * qw],
        [2 * qx * qy + 2 * qz * qw, 1 - 2 * qx * qx - 2 * qz * qz, 2 * qy * qz - 2 * qx * qw],
        [2 * qx * qz - 2 * qy * qw, 2 * qy * qz + 2 * qx * qw, 1 - 2 * qx * qx - 2 * qy * qy],
    ], dtype=np.float64)


def _parse_points(path: Path) -> tuple[np.ndarray, np.ndarray]:
    xyz = []
    rgb = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) < 8:
            continue
        xyz.append([float(parts[1]), float(parts[2]), float(parts[3])])
        rgb.append([int(float(parts[4])), int(float(parts[5])), int(float(parts[6]))])
    if not xyz:
        return np.zeros((0, 3)), np.zeros((0, 3), dtype=np.uint8)
    return np.array(xyz, dtype=np.float64), np.array(rgb, dtype=np.uint8)


def _transfer_color(mesh, xyz: np.ndarray, rgb: np.ndarray) -> None:
    if len(xyz) > 6000:
        index = np.linspace(0, len(xyz) - 1, 6000).astype(int)
        xyz = xyz[index]
        rgb = rgb[index]
    vertices = np.asarray(mesh.vertices, dtype=np.float64)
    colors = np.zeros((len(vertices), 4), dtype=np.uint8)
    colors[:, 3] = 255
    chunk = 300
    for start in range(0, len(vertices), chunk):
        block = vertices[start:start + chunk]
        distance = ((block[:, None, :] - xyz[None, :, :]) ** 2).sum(axis=2)
        nearest = distance.argmin(axis=1)
        colors[start:start + chunk, :3] = rgb[nearest]
    mesh.visual.vertex_colors = colors


def _registered_count(path: Path) -> int:
    if not path.exists():
        return 0
    count = 0
    pending = False
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if not pending and len(parts) >= 10 and parts[0].isdigit():
            count += 1
            pending = True
        else:
            pending = False
    return count
