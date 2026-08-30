package com.lladlam.melox.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class MeiPoint(var x: Float, var y: Float, var r: Float = 1f, var g: Float = 1f, var b: Float = 1f, var ur: Float = 0f, var vr: Float = 0f, var up: Float = 1f, var vp: Float = 1f) {
    val u: FloatArray get() = floatArrayOf(up * cos(ur), up * sin(ur))
    val v: FloatArray get() = floatArrayOf(-vp * sin(vr), vp * cos(vr))
}

private fun generateMeiPoints(random: Random = Random.Default): Array<MeiPoint> = Array(25) { index ->
    val x = (index % 5) / 2f - 1f
    val y = (index / 5) / 2f - 1f
    val border = index % 5 == 0 || index % 5 == 4 || index / 5 == 0 || index / 5 == 4
    if (border) MeiPoint(x, y) else MeiPoint(x + random.nextFloat() * .42f - .21f, y + random.nextFloat() * .42f - .21f, up = .8f + random.nextFloat() * .4f, vp = .8f + random.nextFloat() * .4f, ur = random.nextFloat() * 1.8f - .9f, vr = random.nextFloat() * 1.8f - .9f)
}

private class MeiMesh(private val points: Array<MeiPoint>, private val n: Int = 18) {
    val vertices: FloatBuffer
    val indices: IntBuffer
    val indexCount: Int
    init {
        val grid = 4 * n
        val values = FloatArray(grid * grid * 7)
        var k = 0
        for (j in 0 until grid) for (i in 0 until grid) {
            val x = i.toFloat() / (grid - 1) * 2f - 1f
            val y = j.toFloat() / (grid - 1) * 2f - 1f
            val gx = (x + 1f) * 2f
            val gy = (y + 1f) * 2f
            val ix = gx.toInt().coerceIn(0, 3); val iy = gy.toInt().coerceIn(0, 3)
            val fx = gx - ix; val fy = gy - iy
            fun p(dx: Int, dy: Int) = points[(iy + dy).coerceIn(0, 4) * 5 + (ix + dx).coerceIn(0, 4)]
            val a = p(0, 0); val b = p(1, 0); val c = p(0, 1); val d = p(1, 1)
            fun blend(v: (MeiPoint) -> Float) = v(a) * (1 - fx) * (1 - fy) + v(b) * fx * (1 - fy) + v(c) * (1 - fx) * fy + v(d) * fx * fy
            values[k++] = blend { it.x }; values[k++] = blend { it.y }; values[k++] = blend { it.r }; values[k++] = blend { it.g }; values[k++] = blend { it.b }; values[k++] = i.toFloat() / (grid - 1); values[k++] = 1f - j.toFloat() / (grid - 1)
        }
        vertices = directFloat(values)
        val indexArray = IntArray((grid - 1) * (grid - 1) * 6); var q = 0
        for (j in 0 until grid - 1) for (i in 0 until grid - 1) { val t = j * grid + i; indexArray[q++] = t; indexArray[q++] = t + 1; indexArray[q++] = t + grid; indexArray[q++] = t + 1; indexArray[q++] = t + grid + 1; indexArray[q++] = t + grid }
        indexCount = indexArray.size; indices = ByteBuffer.allocateDirect(indexArray.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(indexArray); position(0) }
    }
}

private object MeiShader {
    const val vertex = """#version 300 es
in vec2 a_pos; in vec3 a_color; in vec2 a_uv; out vec3 v_color; out vec2 v_uv; uniform float u_aspect;
void main(){v_color=a_color;v_uv=a_uv;vec2 p=a_pos;if(u_aspect>1.0)p.y*=u_aspect;else p.x/=u_aspect;gl_Position=vec4(p,0.0,1.0);}"""
    const val fragment = """#version 300 es
precision highp float;in vec3 v_color;in vec2 v_uv;out vec4 outColor;uniform sampler2D u_texture;uniform float u_time;uniform float u_volume;
void main(){vec2 p=v_uv-vec2(.5);float s=sin(u_time*.22+u_volume),c=cos(u_time*.22+u_volume);p=mat2(c,-s,s,c)*p;vec2 uv=p+vec2(.5);vec4 x=texture(u_texture,uv);float v=smoothstep(.95,.18,distance(v_uv,vec2(.5)));outColor=vec4(x.rgb*v_color*(.65+.35*v),1.0);}"""
    const val quadVertex = """#version 300 es
in vec2 a_pos;in vec2 a_uv;out vec2 v_uv;void main(){v_uv=a_uv;gl_Position=vec4(a_pos,0.,1.);}"""
    const val quadFragment = """#version 300 es
precision mediump float;in vec2 v_uv;out vec4 outColor;uniform sampler2D u_texture;uniform float u_alpha;void main(){vec4 c=texture(u_texture,v_uv);outColor=vec4(c.rgb,c.a*u_alpha);}"""
}

internal class MeloXMeiMeshRenderer : GLSurfaceView.Renderer {
    private var program = 0; private var quadProgram = 0; private var texture = 0; private var fbo = 0; private var fboTexture = 0; private var mesh: MeiMesh? = null; private var bitmap: Bitmap? = null; private var started = 0L; private var width = 1; private var height = 1
    @Volatile var playing = true; @Volatile var volume = 0f
    fun setBitmap(value: Bitmap) {
        if (value.isRecycled || value === bitmap) return
        bitmap = value
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) { program = createProgram(MeiShader.vertex, MeiShader.fragment); quadProgram = createProgram(MeiShader.quadVertex, MeiShader.quadFragment); started = System.nanoTime(); GLES30.glDisable(GLES30.GL_DEPTH_TEST); GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA) }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w.coerceAtLeast(1); height = h.coerceAtLeast(1); GLES30.glViewport(0, 0, width, height); if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0); if (fboTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(fboTexture), 0); val fi = IntArray(1); GLES30.glGenFramebuffers(1, fi, 0); fbo = fi[0]; val ti = IntArray(1); GLES30.glGenTextures(1, ti, 0); fboTexture = ti[0]; GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexture); GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width / 2, height / 2, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR); GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo); GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTexture, 0); GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0) }
    override fun onDrawFrame(gl: GL10?) { bitmap?.let { b -> if (!b.isRecycled) { if (texture == 0) { val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0); texture = ids[0]; GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR) }; GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture); GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, b, 0); bitmap = null; mesh = MeiMesh(generateMeiPoints()) } }; GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo); GLES30.glViewport(0, 0, width / 2, height / 2); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); val m = mesh; if (m != null && program != 0 && texture != 0) { GLES30.glUseProgram(program); GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_time"), (System.nanoTime() - started) / 1e9f * if (playing) 1f else 0f); GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_volume"), volume.coerceIn(0f, 1f)); GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_aspect"), width.toFloat() / height); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture); GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "u_texture"), 0); drawMesh(m) }; GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); GLES30.glViewport(0, 0, width, height); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); if (m != null && quadProgram != 0) drawQuad() }
    private fun drawMesh(m: MeiMesh) { val p = GLES30.glGetAttribLocation(program, "a_pos"); val c = GLES30.glGetAttribLocation(program, "a_color"); val u = GLES30.glGetAttribLocation(program, "a_uv"); m.vertices.position(0); GLES30.glEnableVertexAttribArray(p); GLES30.glVertexAttribPointer(p, 2, GLES30.GL_FLOAT, false, 28, m.vertices); m.vertices.position(2); GLES30.glEnableVertexAttribArray(c); GLES30.glVertexAttribPointer(c, 3, GLES30.GL_FLOAT, false, 28, m.vertices); m.vertices.position(5); GLES30.glEnableVertexAttribArray(u); GLES30.glVertexAttribPointer(u, 2, GLES30.GL_FLOAT, false, 28, m.vertices); GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, m.indices); GLES30.glDisableVertexAttribArray(p); GLES30.glDisableVertexAttribArray(c); GLES30.glDisableVertexAttribArray(u) }
    private fun drawQuad() { val data = directFloat(floatArrayOf(-1f,-1f,0f,0f,1f,-1f,1f,0f,-1f,1f,0f,1f,1f,1f,1f,1f)); GLES30.glUseProgram(quadProgram); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexture); GLES30.glUniform1i(GLES30.glGetUniformLocation(quadProgram,"u_texture"),0); GLES30.glUniform1f(GLES30.glGetUniformLocation(quadProgram,"u_alpha"),1f); val p=GLES30.glGetAttribLocation(quadProgram,"a_pos"); val u=GLES30.glGetAttribLocation(quadProgram,"a_uv"); data.position(0); GLES30.glEnableVertexAttribArray(p); GLES30.glVertexAttribPointer(p,2,GLES30.GL_FLOAT,false,16,data); data.position(2); GLES30.glEnableVertexAttribArray(u); GLES30.glVertexAttribPointer(u,2,GLES30.GL_FLOAT,false,16,data); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4) }
    fun release() { if (texture != 0) GLES30.glDeleteTextures(1,intArrayOf(texture),0); if (fboTexture != 0) GLES30.glDeleteTextures(1,intArrayOf(fboTexture),0); if (fbo != 0) GLES30.glDeleteFramebuffers(1,intArrayOf(fbo),0); if (program != 0) GLES30.glDeleteProgram(program); if (quadProgram != 0) GLES30.glDeleteProgram(quadProgram); texture=0;fbo=0;fboTexture=0;program=0;quadProgram=0 }
    private fun createProgram(v: String, f: String): Int { fun compile(t:Int,s:String):Int { val x=GLES30.glCreateShader(t); GLES30.glShaderSource(x,s); GLES30.glCompileShader(x); val ok=IntArray(1); GLES30.glGetShaderiv(x,GLES30.GL_COMPILE_STATUS,ok,0); if(ok[0]==0){GLES30.glDeleteShader(x);return 0};return x }; val a=compile(GLES30.GL_VERTEX_SHADER,v); val b=compile(GLES30.GL_FRAGMENT_SHADER,f); if(a==0||b==0)return 0; val p=GLES30.glCreateProgram();GLES30.glAttachShader(p,a);GLES30.glAttachShader(p,b);GLES30.glLinkProgram(p);GLES30.glDeleteShader(a);GLES30.glDeleteShader(b);val ok=IntArray(1);GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);return if(ok[0]==1)p else 0 }
}

internal class MeloXMeiMeshBackgroundView(context: Context) : GLSurfaceView(context) { private val renderer = MeloXMeiMeshRenderer(); init { setEGLContextClientVersion(3); setEGLConfigChooser(8,8,8,8,0,0); setRenderer(renderer); renderMode=RENDERMODE_CONTINUOUSLY }; fun setBitmap(b:Bitmap)=queueEvent{renderer.setBitmap(b)}; fun setPlaying(v:Boolean){renderer.playing=v}; fun setVolume(v:Float){renderer.volume=v}; override fun onDetachedFromWindow(){queueEvent{renderer.release()};super.onDetachedFromWindow()} }

@Composable
internal fun MeloXMeiMeshBackdrop(artworkUrl: String?, isPlaying: Boolean, volume: Float, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(artworkUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(artworkUrl) {
        bitmap = withContext(Dispatchers.IO) {
            artworkUrl?.takeIf(String::isNotBlank)?.let { request ->
                (ImageLoader(context).execute(
                    ImageRequest.Builder(context).data(request).size(256, 256).build(),
                ) as? SuccessResult)?.image?.toBitmap()
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        MeloXBlurredArtworkBackdrop(artworkUrl)
        if (bitmap != null) AndroidView(factory={ MeloXMeiMeshBackgroundView(it) }, update={ view -> view.setBitmap(bitmap!!); view.setPlaying(isPlaying); view.setVolume(volume) }, modifier=Modifier.fillMaxSize())
    }
}

private fun directFloat(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(values);position(0)}
