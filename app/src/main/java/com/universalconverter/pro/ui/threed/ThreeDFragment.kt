package com.universalconverter.pro.ui.threed

import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.*
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.universalconverter.pro.databinding.FragmentThreedBinding
import com.universalconverter.pro.engine.FileDetector
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class ThreeDFragment : Fragment() {
    private var _b: FragmentThreedBinding? = null
    private val b get() = _b!!
    private var renderer: ObjRenderer? = null
    private var currentUri: Uri? = null

    private val pickObj = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult; currentUri = uri; loadModel(uri)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentThreedBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGL(); setupControls()
    }

    private fun setupGL() {
        renderer = ObjRenderer()
        b.glSurface.setEGLContextClientVersion(2)
        b.glSurface.setRenderer(renderer)
        b.glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        var lx = 0f; var ly = 0f
        b.glSurface.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN  -> { lx=ev.x; ly=ev.y }
                MotionEvent.ACTION_MOVE  -> { renderer?.rotate((ev.x-lx)*0.4f, (ev.y-ly)*0.4f); b.glSurface.requestRender(); lx=ev.x; ly=ev.y }
            }; true
        }
        // Pinch zoom
        val scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                renderer?.zoom(d.scaleFactor); b.glSurface.requestRender(); return true
            }
        })
        b.glSurface.setOnTouchListener { v, ev -> scaleDetector.onTouchEvent(ev)
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { lx=ev.x; ly=ev.y }
                MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress) { renderer?.rotate((ev.x-lx)*0.4f,(ev.y-ly)*0.4f); b.glSurface.requestRender(); lx=ev.x; ly=ev.y }
            }; true
        }
    }

    private fun setupControls() {
        b.btnLoad.setOnClickListener { pickObj.launch("*/*") }
        b.btnReset.setOnClickListener { renderer?.reset(); b.glSurface.requestRender() }
        b.switchWireframe.setOnCheckedChangeListener { _, c -> renderer?.wireframe = c; b.glSurface.requestRender() }
        b.switchAutoRotate.setOnCheckedChangeListener { _, c -> renderer?.autoRotate = c; if(c) startAutoRotate() }
        b.btnExportObj.setOnClickListener { exportAs("obj") }
        b.btnExportStl.setOnClickListener { exportAs("stl") }

        b.seekLight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { renderer?.lightIntensity = p/100f; b.glSurface.requestRender() }
            override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        b.seekLight.progress = 80
    }

    private fun startAutoRotate() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (renderer?.autoRotate == true && _b != null) {
                    renderer?.rotate(1f, 0f); b.glSurface.requestRender()
                    handler.postDelayed(this, 16)
                }
            }
        }
        handler.post(runnable)
    }

    private fun loadModel(uri: Uri) {
        b.tvModelInfo.text = "Loading…"; b.progressModel.isVisible = true
        Thread {
            try {
                val vertices = mutableListOf<Float>(); val faces = mutableListOf<Int>()
                val fileName = requireContext().contentResolver.query(uri, null, null, null, null)?.use {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    it.moveToFirst(); if(idx>=0) it.getString(idx) else "model"
                } ?: "model"

                requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        when(parts.getOrNull(0)) {
                            "v"  -> { parts.getOrNull(1)?.toFloatOrNull()?.let { x -> parts.getOrNull(2)?.toFloatOrNull()?.let { y -> parts.getOrNull(3)?.toFloatOrNull()?.let { z -> vertices.add(x); vertices.add(y); vertices.add(z) } } } }
                            "f"  -> {
                                val v = parts.drop(1).mapNotNull { it.split("/")[0].toIntOrNull()?.minus(1) }
                                if (v.size >= 3) {
                                    faces.add(v[0]); faces.add(v[1]); faces.add(v[2])
                                    if (v.size >= 4) { faces.add(v[0]); faces.add(v[2]); faces.add(v[3]) }
                                }
                            }
                        }
                    }
                }

                val vCount = vertices.size / 3; val tCount = faces.size / 3
                renderer?.setMesh(vertices.toFloatArray(), faces.toIntArray())
                b.glSurface.requestRender()

                requireActivity().runOnUiThread {
                    b.progressModel.isVisible = false
                    b.tvModelInfo.text = "$fileName\n$vCount vertices · $tCount triangles"
                    b.cardExport.isVisible = true
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    b.progressModel.isVisible = false
                    Snackbar.make(b.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun exportAs(format: String) {
        val uri = currentUri ?: return
        Thread {
            try {
                val outDir = File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "UCEngine").also { it.mkdirs() }
                val outFile = File(outDir, "model_${System.currentTimeMillis()}.$format")
                val inStream = requireContext().contentResolver.openInputStream(uri)
                if (format == "stl") {
                    // Convert OBJ to STL
                    val vertices = mutableListOf<Float>(); val faces = mutableListOf<Int>()
                    BufferedReader(InputStreamReader(inStream)).forEachLine { line ->
                        val p = line.trim().split("\\s+".toRegex())
                        when(p.getOrNull(0)) {
                            "v" -> { p.getOrNull(1)?.toFloatOrNull()?.let{x->p.getOrNull(2)?.toFloatOrNull()?.let{y->p.getOrNull(3)?.toFloatOrNull()?.let{z->vertices.add(x);vertices.add(y);vertices.add(z)}}} }
                            "f" -> { val v=p.drop(1).mapNotNull{it.split("/")[0].toIntOrNull()?.minus(1)};if(v.size>=3){faces.add(v[0]);faces.add(v[1]);faces.add(v[2])} }
                        }
                    }
                    val sb = StringBuilder("solid model\n")
                    for (i in 0 until faces.size/3) {
                        val v0=faces[i*3]*3; val v1=faces[i*3+1]*3; val v2=faces[i*3+2]*3
                        if(v0+2<vertices.size&&v1+2<vertices.size&&v2+2<vertices.size) {
                            sb.append("facet normal 0 0 0\n outer loop\n")
                            sb.append("  vertex ${vertices[v0]} ${vertices[v0+1]} ${vertices[v0+2]}\n")
                            sb.append("  vertex ${vertices[v1]} ${vertices[v1+1]} ${vertices[v1+2]}\n")
                            sb.append("  vertex ${vertices[v2]} ${vertices[v2+1]} ${vertices[v2+2]}\n")
                            sb.append(" endloop\nendfacet\n")
                        }
                    }
                    sb.append("endsolid model")
                    outFile.writeText(sb.toString())
                } else {
                    outFile.outputStream().use { inStream?.copyTo(it) }
                }
                requireActivity().runOnUiThread {
                    Snackbar.make(b.root, "Exported: ${outFile.name} (${FileDetector.formatSize(outFile.length())})", Snackbar.LENGTH_LONG)
                        .setAction("Share") { shareFile(outFile) }.show()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread { Snackbar.make(b.root, "Export failed: ${e.message}", Snackbar.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun shareFile(file: File) = runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        startActivity(Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type="*/*"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share"))
    }

    override fun onPause()  { super.onPause();  b.glSurface.onPause() }
    override fun onResume() { super.onResume(); b.glSurface.onResume() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class ObjRenderer : GLSurfaceView.Renderer {
    private var vBuf: FloatBuffer? = null; private var vCount = 0
    var rotX = 20f; var rotY = -30f; private var scale = 1f
    var wireframe = false; var autoRotate = false; var lightIntensity = 0.8f
    private var prog = 0
    private val proj = FloatArray(16); private val view = FloatArray(16)
    private val model = FloatArray(16); private val mvp = FloatArray(16); private val tmp = FloatArray(16)

    private val vs = """
        attribute vec4 aPos; uniform mat4 uMVP; varying float vDepth;
        void main(){gl_Position=uMVP*aPos;vDepth=gl_Position.z*0.04+0.5;}""".trimIndent()
    private val fs = """
        precision mediump float; uniform float uLight; varying float vDepth;
        void main(){gl_FragColor=vec4(0.3,0.7,1.0,1.0)*vDepth*uLight;}""".trimIndent()

    fun setMesh(verts: FloatArray, faces: IntArray) {
        if (verts.isEmpty() || faces.isEmpty()) return
        val flat = FloatArray(faces.size * 3)
        for (i in faces.indices) {
            val vi = faces[i] * 3
            if (vi + 2 < verts.size) { flat[i*3]=verts[vi]; flat[i*3+1]=verts[vi+1]; flat[i*3+2]=verts[vi+2] }
        }
        var minX=Float.MAX_VALUE;var maxX=-Float.MAX_VALUE;var minY=Float.MAX_VALUE;var maxY=-Float.MAX_VALUE;var minZ=Float.MAX_VALUE;var maxZ=-Float.MAX_VALUE
        for (i in flat.indices step 3) { minX=minOf(minX,flat[i]);maxX=maxOf(maxX,flat[i]);minY=minOf(minY,flat[i+1]);maxY=maxOf(maxY,flat[i+1]);minZ=minOf(minZ,flat[i+2]);maxZ=maxOf(maxZ,flat[i+2]) }
        val sc = maxOf(maxX-minX,maxY-minY,maxZ-minZ).let{if(it==0f)1f else 2f/it}
        val cx=(minX+maxX)/2;val cy=(minY+maxY)/2;val cz=(minZ+maxZ)/2
        for (i in flat.indices step 3) { flat[i]=(flat[i]-cx)*sc;flat[i+1]=(flat[i+1]-cy)*sc;flat[i+2]=(flat[i+2]-cz)*sc }
        vBuf = ByteBuffer.allocateDirect(flat.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(flat);position(0)}
        vCount = faces.size
    }

    fun rotate(dx: Float, dy: Float) { rotY+=dx; rotX+=dy }
    fun zoom(f: Float) { scale = (scale * f).coerceIn(0.1f, 10f) }
    fun reset() { rotX=20f; rotY=-30f; scale=1f }

    override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) {
        GLES20.glClearColor(0.08f,0.08f,0.12f,1f); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val v=GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also{GLES20.glShaderSource(it,vs);GLES20.glCompileShader(it)}
        val f=GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also{GLES20.glShaderSource(it,fs);GLES20.glCompileShader(it)}
        prog=GLES20.glCreateProgram().also{GLES20.glAttachShader(it,v);GLES20.glAttachShader(it,f);GLES20.glLinkProgram(it)}
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0,0,w,h)
        Matrix.frustumM(proj,0,-(w.toFloat()/h),w.toFloat()/h,-1f,1f,2f,20f)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val buf=vBuf?:return
        Matrix.setLookAtM(view,0,0f,0f,5f,0f,0f,0f,0f,1f,0f)
        Matrix.setIdentityM(model,0); Matrix.scaleM(model,0,scale,scale,scale)
        Matrix.rotateM(model,0,rotX,1f,0f,0f); Matrix.rotateM(model,0,rotY,0f,1f,0f)
        Matrix.multiplyMM(tmp,0,view,0,model,0); Matrix.multiplyMM(mvp,0,proj,0,tmp,0)
        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog,"uMVP"),1,false,mvp,0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog,"uLight"),lightIntensity)
        val pos=GLES20.glGetAttribLocation(prog,"aPos")
        GLES20.glEnableVertexAttribArray(pos); GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,12,buf)
        if(wireframe) { for(i in 0 until vCount/3) GLES20.glDrawArrays(GLES20.GL_LINE_LOOP,i*3,3) }
        else GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vCount)
        GLES20.glDisableVertexAttribArray(pos)
    }
}
