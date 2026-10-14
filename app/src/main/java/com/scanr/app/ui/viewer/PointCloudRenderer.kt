package com.scanr.app.ui.viewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: Float = 1f
)

class PointCloudRenderer : GLSurfaceView.Renderer {
    
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var pointSizeHandle: Int = 0
    
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    
    private var vertexBuffer: FloatBuffer? = null
    private var pointCount = 0
    
    private val points = mutableListOf<Point3D>()
    private var needsUpdate = false
    
    // Rotación automática
    var autoRotate = true
    var rotationAngle = 0f
    var rotationSpeed = 0.5f
    
    // Zoom y rotación manual
    var zoom = 3f
    var manualRotationX = 0f
    var manualRotationY = 0f
    
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform float uPointSize;
        attribute vec4 vPosition;
        attribute float vConfidence;
        varying float fConfidence;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            gl_PointSize = uPointSize;
            fConfidence = vConfidence;
        }
    """.trimIndent()
    
    private val fragmentShaderCode = """
        precision mediump float;
        varying float fConfidence;
        void main() {
            float dist = length(gl_PointCoord - vec2(0.5));
            if (dist > 0.5) discard;
            vec3 color = mix(vec3(0.0, 0.5, 1.0), vec3(0.0, 1.0, 0.5), fConfidence);
            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()
    
    fun addPoints(newPoints: List<Point3D>) {
        synchronized(points) {
            points.addAll(newPoints)
            needsUpdate = true
        }
    }
    
    fun clearPoints() {
        synchronized(points) {
            points.clear()
            needsUpdate = true
        }
    }
    
    fun getPointCount(): Int = points.size
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.05f, 0.05f, 0.1f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vConfidence")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 100f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        if (needsUpdate) {
            updateVertexBuffer()
            needsUpdate = false
        }
        
        if (pointCount == 0) return
        
        // Rotación automática
        if (autoRotate) {
            rotationAngle += rotationSpeed
        }
        
        // Configurar vista
        val eyeX = zoom * sin(Math.toRadians((rotationAngle + manualRotationY).toDouble())).toFloat()
        val eyeZ = zoom * cos(Math.toRadians((rotationAngle + manualRotationY).toDouble())).toFloat()
        val eyeY = zoom * 0.5f + manualRotationX * 0.02f
        
        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        
        GLES20.glUseProgram(program)
        
        vertexBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 16, buffer)
            
            buffer.position(3)
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glVertexAttribPointer(colorHandle, 1, GLES20.GL_FLOAT, false, 16, buffer)
            
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniform1f(pointSizeHandle, 6f)
            
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointCount)
            
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(colorHandle)
        }
    }
    
    private fun updateVertexBuffer() {
        synchronized(points) {
            pointCount = points.size
            if (pointCount == 0) return
            
            // Calcular centro para normalizar
            var cx = 0f; var cy = 0f; var cz = 0f
            points.forEach { cx += it.x; cy += it.y; cz += it.z }
            cx /= pointCount; cy /= pointCount; cz /= pointCount
            
            val buffer = ByteBuffer.allocateDirect(pointCount * 4 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            
            points.forEach { point ->
                buffer.put(point.x - cx)
                buffer.put(point.y - cy)
                buffer.put(point.z - cz)
                buffer.put(point.confidence)
            }
            
            buffer.position(0)
            vertexBuffer = buffer
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
