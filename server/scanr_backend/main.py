"""ScanR backend. Run it on the computer that will build the 3D model."""

from __future__ import annotations

import json
import queue
import shutil
import threading
import traceback
import uuid
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse

from scanr_backend.reconstruct import ReconstructionError, find_colmap, reconstruct

DATA = Path(__file__).resolve().parent.parent / "data"
DATA.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="ScanR")
jobs: dict[str, dict] = {}
pending: queue.Queue[str] = queue.Queue()
lock = threading.Lock()


def _write(job_id: str, **fields) -> None:
    with lock:
        job = jobs[job_id]
        job.update(fields)
        (DATA / job_id / "status.json").write_text(
            json.dumps({key: value for key, value in job.items() if key != "result"}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def _log(job_id: str, line: str) -> None:
    text = line.strip()
    if not text:
        return
    with lock:
        job = jobs[job_id]
        job["log"] = (job.get("log", "") + "\n" + text)[-6000:]
        job["message"] = text.splitlines()[-1][:240]
    log_path = DATA / job_id / "job.log"
    with log_path.open("a", encoding="utf-8") as handle:
        handle.write(text + "\n")


def _worker() -> None:
    while True:
        job_id = pending.get()
        try:
            _write(job_id, status="running", message="Preparando las fotos…")
            result = reconstruct(DATA / job_id, lambda line: _log(job_id, line))
            faces = result["faces"]
            used = result["images"]
            note = f"Modelo listo: {faces} caras, {used} fotos usadas."
            if result["aligned"]:
                note += " Tamaño aproximado al real."
            _write(job_id, status="done", message=note, result={
                "faces": faces,
                "images": used,
                "aligned": result["aligned"],
            })
        except Exception as error:
            detail = str(error).strip() or error.__class__.__name__
            _log(job_id, traceback.format_exc())
            _write(job_id, status="error", message=detail[:500])
        finally:
            pending.task_done()


@app.on_event("startup")
def start_worker() -> None:
    threading.Thread(target=_worker, daemon=True).start()


@app.get("/api/health")
def health() -> dict:
    colmap = find_colmap()
    return {
        "ok": True,
        "colmap": colmap is not None,
        "colmap_path": colmap,
        "gpu": False,
    }


@app.post("/api/jobs")
async def create_job(
    meta: str = Form(...),
    images: list[UploadFile] = File(...),
) -> dict:
    if len(images) < 6:
        raise HTTPException(status_code=400, detail="Hacen falta al menos 6 fotos.")
    try:
        payload = json.loads(meta)
    except json.JSONDecodeError as error:
        raise HTTPException(status_code=400, detail="El archivo de datos de las fotos no es válido.") from error

    job_id = uuid.uuid4().hex[:12]
    folder = DATA / job_id
    image_dir = folder / "images"
    image_dir.mkdir(parents=True)
    saved = []
    for upload in images:
        name = Path(upload.filename or "foto.jpg").name
        target = image_dir / name
        with target.open("wb") as handle:
            shutil.copyfileobj(upload.file, handle)
        saved.append(name)
    (folder / "meta.json").write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    with lock:
        jobs[job_id] = {
            "id": job_id,
            "status": "queued",
            "message": f"{len(saved)} fotos en cola.",
            "log": "",
        }
    pending.put(job_id)
    return {"id": job_id, "status": "queued", "images": len(saved)}


@app.get("/api/jobs/{job_id}")
def job_status(job_id: str) -> dict:
    with lock:
        job = jobs.get(job_id)
    if job is None:
        status_path = DATA / job_id / "status.json"
        if not status_path.exists():
            raise HTTPException(status_code=404, detail="No existe ese trabajo.")
        job = json.loads(status_path.read_text(encoding="utf-8"))
    return {
        "id": job_id,
        "status": job.get("status"),
        "message": job.get("message", ""),
    }


@app.get("/api/jobs/{job_id}/model")
def download_model(job_id: str):
    archive = DATA / job_id / "modelo.zip"
    if not archive.exists():
        raise HTTPException(status_code=404, detail="El modelo todavía no está listo.")
    return FileResponse(archive, media_type="application/zip", filename=f"scanr-{job_id}.zip")
