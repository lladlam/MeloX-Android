package com.lladlam.melox.ui.player

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
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

private data class MeiPoint(
    var x: Float,
    var y: Float,
    var ur: Float = 0f,
    var vr: Float = 0f,
    var up: Float = .5f,
    var vp: Float = .5f,
) {
    val u: FloatArray get() = floatArrayOf(up * cos(ur), up * sin(ur))
    val v: FloatArray get() = floatArrayOf(-vp * sin(vr), vp * cos(vr))
}

private fun generateMeiPoints(random: Random = Random.Default): Array<MeiPoint> = Array(25) { index ->
    val column = index % 5
    val row = index / 5
    val x = column / 2f - 1f
    val y = row / 2f - 1f
    val border = column == 0 || column == 4 || row == 0 || row == 4
    if (border) {
        MeiPoint(x, y)
    } else {
        MeiPoint(
            x = x + random.nextFloat() * .42f - .21f,
            y = y + random.nextFloat() * .42f - .21f,
            up = .4f + random.nextFloat() * .2f,
            vp = .4f + random.nextFloat() * .2f,
            ur = random.nextFloat() * 1.8f - .9f,
            vr = random.nextFloat() * 1.8f - .9f,
        )
    }
}

private fun hermite0(t: Float) = 2f * t * t * t - 3f * t * t + 1f
private fun hermite1(t: Float) = t * t * t - 2f * t * t + t
private fun hermite2(t: Float) = -2f * t * t * t + 3f * t * t
private fun hermite3(t: Float) = t * t * t - t * t

private class MeiMesh(points: Array<MeiPoint>, subdivision: Int = 20) {
    val vertices: FloatBuffer
    val indices: IntBuffer
    val indexCount: Int

    init {
        val grid = 4 * subdivision + 1
        val values = FloatArray(grid * grid * 4)
        var output = 0
        for (row in 0 until grid) {
            for (column in 0 until grid) {
                val patchX = minOf(column / subdivision, 3)
                val patchY = minOf(row / subdivision, 3)
                val tx = if (column == grid - 1) 1f else (column % subdivision).toFloat() / subdivision
                val ty = if (row == grid - 1) 1f else (row % subdivision).toFloat() / subdivision
                val a = points[patchY * 5 + patchX]
                val b = points[patchY * 5 + patchX + 1]
                val c = points[(patchY + 1) * 5 + patchX]
                val d = points[(patchY + 1) * 5 + patchX + 1]

                fun interpolate(axis: Int): Float {
                    fun value(point: MeiPoint) = if (axis == 0) point.x else point.y
                    fun u(point: MeiPoint) = point.u[axis]
                    fun v(point: MeiPoint) = point.v[axis]
                    val top = hermite0(tx) * value(a) + hermite1(tx) * u(a) +
                        hermite2(tx) * value(b) + hermite3(tx) * u(b)
                    val bottom = hermite0(tx) * value(c) + hermite1(tx) * u(c) +
                        hermite2(tx) * value(d) + hermite3(tx) * u(d)
                    val topV = hermite0(tx) * v(a) + hermite2(tx) * v(b)
                    val bottomV = hermite0(tx) * v(c) + hermite2(tx) * v(d)
                    return hermite0(ty) * top + hermite1(ty) * topV +
                        hermite2(ty) * bottom + hermite3(ty) * bottomV
                }

                values[output++] = interpolate(0)
                values[output++] = interpolate(1)
                values[output++] = column.toFloat() / (grid - 1)
                values[output++] = 1f - row.toFloat() / (grid - 1)
            }
        }
        vertices = directFloat(values)

        val indexArray = IntArray((grid - 1) * (grid - 1) * 6)
        var index = 0
        for (row in 0 until grid - 1) {
            for (column in 0 until grid - 1) {
                val topLeft = row * grid + column
                indexArray[index++] = topLeft
                indexArray[index++] = topLeft + 1
                indexArray[index++] = topLeft + grid
                indexArray[index++] = topLeft + 1
                indexArray[index++] = topLeft + grid + 1
                indexArray[index++] = topLeft + grid
            }
        }
        indexCount = indexArray.size
        indices = ByteBuffer.allocateDirect(indexArray.size * Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply { put(indexArray); position(0) }
    }
}

private object MeiShader {
    const val vertex = """#version 300 es
precision highp float;
in vec2 a_pos;
in vec2 a_uv;
out vec2 v_uv;
uniform float u_aspect;
uniform float u_time;
uniform float u_volume;
void main() {
    v_uv = a_uv;
    vec2 p = a_pos;
    float edge = 16.0 * a_uv.x * (1.0 - a_uv.x) * a_uv.y * (1.0 - a_uv.y);
    float motion = (0.018 + u_volume * 0.025) * edge;
    p += motion * vec2(
        sin(u_time * 0.73 + a_uv.y * 8.0) + sin(u_time * 0.31 + a_uv.x * 5.0),
        cos(u_time * 0.61 + a_uv.x * 7.0) + cos(u_time * 0.27 + a_uv.y * 6.0)
    );
    if (u_aspect > 1.0) p.y *= u_aspect; else p.x /= u_aspect;
    gl_Position = vec4(p, 0.0, 1.0);
}"""

    const val fragment = """#version 300 es
precision highp float;
in vec2 v_uv;
out vec4 outColor;
uniform sampler2D u_texture;
uniform float u_time;
uniform float u_volume;
float noise(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(.06711056, .00583715)))); }
void main() {
    vec2 flow = vec2(
        sin(v_uv.y * 10.0 + u_time * .42) + sin(v_uv.x * 6.0 - u_time * .23),
        cos(v_uv.x * 9.0 - u_time * .37) + cos(v_uv.y * 7.0 + u_time * .19)
    );
    vec2 uv = v_uv + flow * (.014 + u_volume * .018);
    vec3 color = texture(u_texture, uv).rgb;
    float vignette = smoothstep(.92, .24, distance(v_uv, vec2(.5)));
    color *= .58 + .42 * vignette;
    color += vec3(noise(gl_FragCoord.xy) / 255.0 - .5 / 255.0);
    outColor = vec4(color, 1.0);
}"""

    const val quadVertex = """#version 300 es
in vec2 a_pos;
in vec2 a_uv;
out vec2 v_uv;
void main() { v_uv = a_uv; gl_Position = vec4(a_pos, 0.0, 1.0); }
"""

    const val quadFragment = """#version 300 es
precision mediump float;
in vec2 v_uv;
out vec4 outColor;
uniform sampler2D u_texture;
void main() { outColor = texture(u_texture, v_uv); }
"""
}

internal class MeloXMeiMeshRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var quadProgram = 0
    private var texture = 0
    private var fbo = 0
    private var fboTexture = 0
    private var fboWidth = 1
    private var fboHeight = 1
    private var mesh: MeiMesh? = null
    private var bitmap: Bitmap? = null
    private var width = 1
    private var height = 1
    private var accumulatedTime = 0f
    private var lastFrame = System.nanoTime()
    private val quad = directFloat(floatArrayOf(
        -1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
    ))

    @Volatile var playing = true
    @Volatile var volume = 0f

    fun setBitmap(value: Bitmap) {
        if (!value.isRecycled && value !== bitmap) bitmap = value
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(MeiShader.vertex, MeiShader.fragment)
        quadProgram = createProgram(MeiShader.quadVertex, MeiShader.quadFragment)
        texture = 0
        fbo = 0
        fboTexture = 0
        lastFrame = System.nanoTime()
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        rebuildFbo()
    }

    private fun rebuildFbo() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (fboTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(fboTexture), 0)
        fboWidth = maxOf(1, (width * .75f).toInt())
        fboHeight = maxOf(1, (height * .75f).toInt())
        fbo = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
        fboTexture = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, fboWidth, fboHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTexture, 0)
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(fboTexture), 0)
            fbo = 0
            fboTexture = 0
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        uploadPendingBitmap()
        val now = System.nanoTime()
        if (playing) accumulatedTime += (now - lastFrame).coerceAtMost(100_000_000L) / 1e9f
        lastFrame = now

        val currentMesh = mesh
        if (currentMesh == null || program == 0 || texture == 0) {
            clearDefaultFramebuffer()
            return
        }

        if (fbo != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glViewport(0, 0, fboWidth, fboHeight)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            drawMesh(currentMesh, fboWidth.toFloat() / fboHeight)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, width, height)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            drawQuad()
        } else {
            clearDefaultFramebuffer()
            drawMesh(currentMesh, width.toFloat() / height)
        }
    }

    private fun clearDefaultFramebuffer() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun uploadPendingBitmap() {
        val pending = bitmap ?: return
        if (pending.isRecycled) {
            bitmap = null
            return
        }
        val processed = processMeiAlbumTexture(pending)
        if (texture == 0) texture = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_MIRRORED_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_MIRRORED_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, processed, 0)
        processed.recycle()
        bitmap = null
        mesh = MeiMesh(generateMeiPoints())
    }

    private fun drawMesh(value: MeiMesh, aspect: Float) {
        GLES30.glUseProgram(program)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_time"), accumulatedTime * .25f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_volume"), volume.coerceIn(0f, 1f))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "u_aspect"), aspect)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "u_texture"), 0)
        val position = GLES30.glGetAttribLocation(program, "a_pos")
        val uv = GLES30.glGetAttribLocation(program, "a_uv")
        value.vertices.position(0)
        GLES30.glEnableVertexAttribArray(position)
        GLES30.glVertexAttribPointer(position, 2, GLES30.GL_FLOAT, false, 16, value.vertices)
        value.vertices.position(2)
        GLES30.glEnableVertexAttribArray(uv)
        GLES30.glVertexAttribPointer(uv, 2, GLES30.GL_FLOAT, false, 16, value.vertices)
        value.indices.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, value.indexCount, GLES30.GL_UNSIGNED_INT, value.indices)
        GLES30.glDisableVertexAttribArray(position)
        GLES30.glDisableVertexAttribArray(uv)
    }

    private fun drawQuad() {
        GLES30.glUseProgram(quadProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(quadProgram, "u_texture"), 0)
        val position = GLES30.glGetAttribLocation(quadProgram, "a_pos")
        val uv = GLES30.glGetAttribLocation(quadProgram, "a_uv")
        quad.position(0)
        GLES30.glEnableVertexAttribArray(position)
        GLES30.glVertexAttribPointer(position, 2, GLES30.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES30.glEnableVertexAttribArray(uv)
        GLES30.glVertexAttribPointer(uv, 2, GLES30.GL_FLOAT, false, 16, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(position)
        GLES30.glDisableVertexAttribArray(uv)
    }

    fun release() {
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        if (fboTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(fboTexture), 0)
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (program != 0) GLES30.glDeleteProgram(program)
        if (quadProgram != 0) GLES30.glDeleteProgram(quadProgram)
        texture = 0
        fbo = 0
        fboTexture = 0
        program = 0
        quadProgram = 0
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                GLES30.glDeleteShader(shader)
                return 0
            }
            return shader
        }
        val vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertex == 0 || fragment == 0) return 0
        val result = GLES30.glCreateProgram()
        GLES30.glAttachShader(result, vertex)
        GLES30.glAttachShader(result, fragment)
        GLES30.glLinkProgram(result)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES30.glGetProgramiv(result, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) GLES30.glDeleteProgram(result)
        return if (status[0] == 1) result else 0
    }
}

private fun processMeiAlbumTexture(source: Bitmap): Bitmap {
    if (source.isRecycled) return Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    var image = Bitmap.createScaledBitmap(source, 32, 32, true)
    val pixels = IntArray(32 * 32)
    image.getPixels(pixels, 0, 32, 0, 0, 32, 32)
    pixels.indices.forEach { index ->
        var red = Color.red(pixels[index]).toFloat()
        var green = Color.green(pixels[index]).toFloat()
        var blue = Color.blue(pixels[index]).toFloat()
        val alpha = Color.alpha(pixels[index])
        red = (red - 128f) * .4f + 128f
        green = (green - 128f) * .4f + 128f
        blue = (blue - 128f) * .4f + 128f
        val gray = red * .3f + green * .59f + blue * .11f
        red = ((gray * -2f + red * 3f) - 128f) * 1.7f + 128f
        green = ((gray * -2f + green * 3f) - 128f) * 1.7f + 128f
        blue = ((gray * -2f + blue * 3f) - 128f) * 1.7f + 128f
        pixels[index] = Color.argb(
            alpha,
            (red * .75f).toInt().coerceIn(0, 255),
            (green * .75f).toInt().coerceIn(0, 255),
            (blue * .75f).toInt().coerceIn(0, 255),
        )
    }
    image.recycle()
    image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, 32, 0, 0, 32, 32)
    }
    repeat(4) { image = blurMeiTexture(image, 2) }
    return image
}

private fun blurMeiTexture(source: Bitmap, radius: Int): Bitmap {
    val width = source.width
    val height = source.height
    val input = IntArray(width * height)
    val output = IntArray(width * height)
    source.getPixels(input, 0, width, 0, 0, width, height)
    val divisor = (radius * 2 + 1) * (radius * 2 + 1)
    for (y in 0 until height) for (x in 0 until width) {
        var alpha = 0; var red = 0; var green = 0; var blue = 0
        for (dy in -radius..radius) for (dx in -radius..radius) {
            val pixel = input[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)]
            alpha += Color.alpha(pixel); red += Color.red(pixel)
            green += Color.green(pixel); blue += Color.blue(pixel)
        }
        output[y * width + x] = Color.argb(alpha / divisor, red / divisor, green / divisor, blue / divisor)
    }
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(output, 0, width, 0, 0, width, height)
    source.recycle()
    return result
}

internal class MeloXMeiMeshBackgroundView(context: Context) : GLSurfaceView(context) {
    private val renderer = MeloXMeiMeshRenderer()
    private var lastBitmap: Bitmap? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        setBackgroundColor(Color.TRANSPARENT)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setBitmap(value: Bitmap) {
        if (value === lastBitmap || value.isRecycled) return
        lastBitmap = value
        queueEvent { renderer.setBitmap(value) }
    }

    fun setPlaying(value: Boolean) {
        renderer.playing = value
        renderMode = if (value) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        if (!value) requestRender()
    }

    fun setVolume(value: Float) {
        renderer.volume = value
        if (renderMode == RENDERMODE_WHEN_DIRTY) requestRender()
    }

    override fun onDetachedFromWindow() {
        queueEvent { renderer.release() }
        super.onDetachedFromWindow()
    }
}

@Composable
internal fun MeloXMeiMeshBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    volume: Float,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(artworkUrl) { mutableStateOf<Bitmap?>(null) }
    val supportsGles = remember(context) {
        context.getSystemService(ActivityManager::class.java)
            ?.deviceConfigurationInfo?.reqGlEsVersion?.let { it >= 0x30000 } == true
    }

    LaunchedEffect(artworkUrl) {
        bitmap = withContext(Dispatchers.IO) {
            artworkUrl?.takeIf(String::isNotBlank)?.let { request ->
                (ImageLoader(context).execute(
                    ImageRequest.Builder(context)
                        .data(request)
                        .size(256, 256)
                        .allowHardware(false)
                        .build(),
                ) as? SuccessResult)?.image?.toBitmap()
            }
        }
    }

    Box(modifier.fillMaxSize().clipToBounds()) {
        // The fallback remains behind the SurfaceView during buffer resize and GL recovery.
        MeloXBlurredArtworkBackdrop(artworkUrl)
        if (supportsGles && bitmap != null && !MeloXSettingsRuntime.reduceMotion) {
            val currentBitmap = bitmap ?: return@Box
            AndroidView(
                factory = { MeloXMeiMeshBackgroundView(it) },
                update = { view ->
                    view.setBitmap(currentBitmap)
                    view.setPlaying(isPlaying)
                    view.setVolume(volume)
                },
                modifier = Modifier.fillMaxSize().clipToBounds(),
            )
        }
    }
}

private fun directFloat(values: FloatArray): FloatBuffer = ByteBuffer
    .allocateDirect(values.size * Float.SIZE_BYTES)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .apply { put(values); position(0) }
