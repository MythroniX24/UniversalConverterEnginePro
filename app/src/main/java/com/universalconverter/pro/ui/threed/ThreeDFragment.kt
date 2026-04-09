package com.universalconverter.pro.ui.threed

import android.content.Context
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.universalconverter.pro.databinding.FragmentThreedBinding
import com.universalconverter.pro.engine.FileDetector
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES20
import android.opengl.GLU
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ThreeDFragment : Fragment() {

    private var _binding: FragmentThreedBinding? = null
    private val binding get() = _binding!!

    private var objRenderer: ObjRenderer? = null

    private val pickObj = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        loadObjFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThreedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPickObj.setOnClickListener { pickObj.launch("*/*") }
        binding.btnResetCamera.setOnClickListener {
            objRenderer?.resetCamera()
            binding.glSurfaceView.requestRender()
        }

        setupGlSurface()
        setupTouchRotation()
    }

    private fun setupGlSurface() {
        binding.glSurfaceView.setEGLContextClientVersion(2)
        objRenderer = ObjRenderer()
        binding.glSurfaceView.setRenderer(objRenderer)
        binding.glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private fun setupTouchRotation() {
        var lastX = 0f
        var lastY = 0f
        binding.glSurfaceView.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    objRenderer?.rotate(dx * 0.5f, dy * 0.5f)
                    binding.glSurfaceView.requestRender()
                    lastX = event.x; lastY = event.y
                }
            }
            true
        }
    }

    private fun loadObjFile(uri: Uri) {
        binding.tvModelInfo.text = "Loading…"
        binding.progressBar3d.isVisible = true

        Thread {
            try {
                val vertices = mutableListOf<Float>()
                val normals  = mutableListOf<Float>()
                val faces    = mutableListOf<Int>()
                var vertCount = 0

                requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        when {
                            parts[0] == "v" && parts.size >= 4 -> {
                                vertices.add(parts[1].toFloatOrNull() ?: 0f)
                                vertices.add(parts[2].toFloatOrNull() ?: 0f)
                                vertices.add(parts[3].toFloatOrNull() ?: 0f)
                                vertCount++
                            }
                            parts[0] == "vn" && parts.size >= 4 -> {
                                normals.add(parts[1].toFloatOrNull() ?: 0f)
                                normals.add(parts[2].toFloatOrNull() ?: 0f)
                                normals.add(parts[3].toFloatOrNull() ?: 0f)
                            }
                            parts[0] == "f" && parts.size >= 4 -> {
                                // Triangulate face
                                val v0 = (parts[1].split("/")[0].toIntOrNull() ?: 1) - 1
                                val v1 = (parts[2].split("/")[0].toIntOrNull() ?: 1) - 1
                                val v2 = (parts[3].split("/")[0].toIntOrNull() ?: 1) - 1
                                faces.add(v0); faces.add(v1); faces.add(v2)
                                if (parts.size >= 5) {
                                    val v3 = (parts[4].split("/")[0].toIntOrNull() ?: 1) - 1
                                    faces.add(v0); faces.add(v2); faces.add(v3)
                                }
                            }
                        }
                    }
                }

                val fileName = requireContext().contentResolver
                    .query(uri, null, null, null, null)?.use {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        it.moveToFirst()
                        if (idx >= 0) it.getString(idx) else "model.obj"
                    } ?: "model.obj"

                objRenderer?.setMesh(vertices.toFloatArray(), faces.toIntArray())
                binding.glSurfaceView.requestRender()

                requireActivity().runOnUiThread {
                    binding.progressBar3d.isVisible = false
                    binding.tvModelInfo.text =
                        "$fileName\n$vertCount vertices · ${faces.size / 3} triangles"
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    binding.progressBar3d.isVisible = false
                    Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        binding.glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceView.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── OpenGL ES 2.0 Renderer ───────────────────────────────────────────────────
class ObjRenderer : GLSurfaceView.Renderer {

    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0
    private var rotX = 20f
    private var rotY = -30f

    private var program = 0
    private val projMatrix  = FloatArray(16)
    private val viewMatrix  = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix   = FloatArray(16)
    private val tempMatrix  = FloatArray(16)

    private val vertexShader = """
        attribute vec4 aPosition;
        uniform mat4 uMVPMatrix;
        varying float vDepth;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vDepth = gl_Position.z * 0.05 + 0.5;
        }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        varying float vDepth;
        void main() {
            gl_FragColor = vec4(0.2, 0.6, 1.0, 1.0) * vDepth;
        }
    """.trimIndent()

    fun setMesh(vertices: FloatArray, faces: IntArray) {
        if (vertices.isEmpty() || faces.isEmpty()) return
        // Build indexed triangle list into flat buffer
        val floats = FloatArray(faces.size * 3)
        for (i in faces.indices) {
            val vi = faces[i] * 3
            if (vi + 2 < vertices.size) {
                floats[i * 3]     = vertices[vi]
                floats[i * 3 + 1] = vertices[vi + 1]
                floats[i * 3 + 2] = vertices[vi + 2]
            }
        }

        // Normalize to unit cube
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in floats.indices step 3) {
            minX = minOf(minX, floats[i]);     maxX = maxOf(maxX, floats[i])
            minY = minOf(minY, floats[i+1]);   maxY = maxOf(maxY, floats[i+1])
            minZ = minOf(minZ, floats[i+2]);   maxZ = maxOf(maxZ, floats[i+2])
        }
        val scale = maxOf(maxX-minX, maxY-minY, maxZ-minZ).let { if (it == 0f) 1f else 2f / it }
        val cx = (minX+maxX)/2; val cy = (minY+maxY)/2; val cz = (minZ+maxZ)/2
        for (i in floats.indices step 3) {
            floats[i]   = (floats[i]   - cx) * scale
            floats[i+1] = (floats[i+1] - cy) * scale
            floats[i+2] = (floats[i+2] - cz) * scale
        }

        vertexBuffer = ByteBuffer.allocateDirect(floats.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(floats); position(0) }
        vertexCount = faces.size
    }

    fun rotate(dx: Float, dy: Float) { rotY += dx; rotX += dy }
    fun resetCamera() { rotX = 20f; rotY = -30f }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = buildProgram(vertexShader, fragmentShader)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.frustumM(projMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val buf = vertexBuffer ?: return

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)

        GLES20.glUseProgram(program)
        val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, src: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
        }
    }
}
