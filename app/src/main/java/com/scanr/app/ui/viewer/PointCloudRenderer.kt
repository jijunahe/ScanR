package com.scanr.app.ui.viewer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import com.scanr.app.data.PointCloud
import com.scanr.app.data.Vector3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class PointCloudGLSurfaceView(context: Context) : GLSurfaceView(context) {
    
    private val renderer: PointCloudRenderer
    private var previousX = 0f
    private var previousY = 0f
    
    init {
        setEGLContextClientVersion(2)
        renderer = PointCloudRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
    
    fun setPointCloud(pointCloud: PointCloud) {
        renderer.setPointCloud(pointCloud)
        requestRender()
    }
    
    fun setBackgroundColor(r: Float, g: Float, b: Float) {
        renderer.setBackgroundColor(r, g, b)
        requestRender()
    }
    
    fun setPointSize(size: Float) {
        renderer.pointSize = size
        requestRender()
    }
    
    fun setPointColor(r: Float, g: Float, b: Float) {
        renderer.setPointColor(r, g, b)
        requestRender()
    }
    
    fun resetView() {
        renderer.resetView()
        requestRender()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = x - previousX
                val dy = y - previousY
                
                if (event.pointerCount == 1) {
                    renderer.rotateY(dx * 0.5f)
                    renderer.rotateX(dy * 0.5f)
                } else if (event.pointerCount == 2) {
                    renderer.zoom(dy * 0.01f)
                }
                
                requestRender()
            }
        }
        
        previousX = x
        previousY = y
        return true
    }
}

class PointCloudRenderer : GLSurfaceView.Renderer {
    
    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var pointCount = 0
    
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    
    private var program = 0
    
    private var rotationX = 0f
    private var rotationY = 0f
    private var zoom = 3f
    
    private var bgR = 0.1f
    private var bgG = 0.1f
    private var bgB = 0.15f
    
    private var pointR = 0.3f
    private var pointG = 0.7f
    private var pointB = 1.0f
    
    var pointSize = 4f
    
    private var centerX = 0f
    private var centerY = 0f
    private var centerZ = 0f
    
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform float uPointSize;
        attribute vec4 vPosition;
        attribute vec3 vNormal;
        varying vec3 fNormal;
        varying float fDepth;
        
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            gl_PointSize = uPointSize;
            fNormal = vNormal;
            fDepth = gl_Position.z / gl_Position.w;
        }
    """.trimIndent()
    
    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec3 uColor;
        uniform vec3 uLightDir;
        varying vec3 fNormal;
        varying float fDepth;
        
        void main() {
            vec3 normal = normalize(fNormal);
            float diffuse = max(dot(normal, uLightDir), 0.3);
            float depthFade = 1.0 - fDepth * 0.3;
            vec3 color = uColor * diffuse * depthFade;
            
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord);
            if (dist > 0.5) discard;
            
            float alpha = 1.0 - smoothstep(0.3, 0.5, dist);
            gl_FragColor = vec4(color, alpha);
        }
    """.trimIndent()
    
    fun setPointCloud(pointCloud: PointCloud) {
        if (pointCloud.isEmpty) {
            pointCount = 0
            return
        }
        
        val center = pointCloud.boundingBoxCenter
        centerX = center.x
        centerY = center.y
        centerZ = center.z
        
        val size = pointCloud.boundingBoxSize
        zoom = maxOf(size.x, size.y, size.z) * 2f + 1f
        
        val positions = pointCloud.toPositionArray()
        val normals = pointCloud.toNormalArray()
        
        vertexBuffer = ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(positions)
                position(0)
            }
        
        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(normals)
                position(0)
            }
        
        pointCount = pointCloud.size
    }
    
    fun setBackgroundColor(r: Float, g: Float, b: Float) {
        bgR = r
        bgG = g
        bgB = b
    }
    
    fun setPointColor(r: Float, g: Float, b: Float) {
        pointR = r
        pointG = g
        pointB = b
    }
    
    fun rotateX(angle: Float) {
        rotationX += angle
        rotationX = rotationX.coerceIn(-90f, 90f)
    }
    
    fun rotateY(angle: Float) {
        rotationY += angle
    }
    
    fun zoom(delta: Float) {
        zoom += delta
        zoom = zoom.coerceIn(0.5f, 20f)
    }
    
    fun resetView() {
        rotationX = 0f
        rotationY = 0f
        zoom = 3f
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(bgR, bgG, bgB, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        val ratio = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 100f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(bgR, bgG, bgB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        if (pointCount == 0 || vertexBuffer == null) return
        
        val eyeX = zoom * sin(Math.toRadians(rotationY.toDouble())).toFloat() * cos(Math.toRadians(rotationX.toDouble())).toFloat()
        val eyeY = zoom * sin(Math.toRadians(rotationX.toDouble())).toFloat()
        val eyeZ = zoom * cos(Math.toRadians(rotationY.toDouble())).toFloat() * cos(Math.toRadians(rotationX.toDouble())).toFloat()
        
        Matrix.setLookAtM(viewMatrix, 0,
            eyeX + centerX, eyeY + centerY, eyeZ + centerZ,
            centerX, centerY, centerZ,
            0f, 1f, 0f
        )
        
        Matrix.setIdentityM(modelMatrix, 0)
        
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
        
        GLES20.glUseProgram(program)
        
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
        
        val normalHandle = GLES20.glGetAttribLocation(program, "vNormal")
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 12, normalBuffer)
        
        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        val pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
        GLES20.glUniform1f(pointSizeHandle, pointSize)
        
        val colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        GLES20.glUniform3f(colorHandle, pointR, pointG, pointB)
        
        val lightDirHandle = GLES20.glGetUniformLocation(program, "uLightDir")
        GLES20.glUniform3f(lightDirHandle, 0.5f, 1f, 0.5f)
        
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointCount)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
