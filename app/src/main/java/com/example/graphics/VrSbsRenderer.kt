package com.example.graphics

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.example.model.VrSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * High-performance OpenGL ES 2.0 Renderer that duplicates captured screen content
 * into Side-by-Side (SBS) stereoscopic view with customizable IPD split, Scale,
 * Brown-Conrady Barrel Distortion (K1, K2, K3), Focal Length, Split Gap, and Lens Masking.
 */
class VrSbsRenderer(
    private val glSurfaceView: GLSurfaceView,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val tag = "VrSbsRenderer"

    private var program = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var updateSurface = false

    private val stMatrix = FloatArray(16)

    private var viewportWidth = 0
    private var viewportHeight = 0

    @Volatile
    var settings: VrSettings = VrSettings()

    // Full screen quad coordinates
    private val triangleVerticesData = floatArrayOf(
        // X, Y, Z, U, V
        -1.0f, -1.0f, 0f, 0f, 0f,
         1.0f, -1.0f, 0f, 1f, 0f,
        -1.0f,  1.0f, 0f, 0f, 1f,
         1.0f,  1.0f, 0f, 1f, 1f
    )

    private val triangleVertices: FloatBuffer = ByteBuffer.allocateDirect(
        triangleVerticesData.size * 4
    ).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(triangleVerticesData)
        position(0)
    }

    private val vertexShaderSource = """
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        uniform mat4 uSTMatrix;
        void main() {
            gl_Position = aPosition;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform float uScale;
        uniform float uAspectCorrection;
        uniform float uFocalLength;
        uniform float uBarrelK1;
        uniform float uBarrelK2;
        uniform float uBarrelK3;
        uniform float uIpdOffset;
        uniform float uYOffset;
        uniform int uMaskType;
        uniform float uBrightness;
        uniform int uShowGrid;
        uniform int uIsPaused;
        uniform int uChromaticAberration;

        void main() {
            vec2 uv = vTextureCoord;
            vec2 centered = (uv - 0.5) * 2.0;

            centered.x = (centered.x - uIpdOffset) / (uScale * uAspectCorrection);
            centered.y = (centered.y - uYOffset) / uScale;

            // Optical scale normalized by lens focal length (42mm reference)
            float focalScale = 42.0 / max(uFocalLength, 20.0);
            vec2 distCoord = centered * focalScale;
            float r2 = dot(distCoord, distCoord);
            float r4 = r2 * r2;
            float r6 = r4 * r2;

            // Brown-Conrady Polynomial Lens Distortion Correction
            float distFactor = 1.0;
            if (uBarrelK1 > 0.0001 || uBarrelK2 > 0.0001 || uBarrelK3 > 0.0001) {
                distFactor = 1.0 + (uBarrelK1 * r2) + (uBarrelK2 * r4) + (uBarrelK3 * r6);
            }

            vec2 warped = centered * distFactor;
            vec2 sampleCoord = (warped * 0.5) + 0.5;

            // Outside bounds -> black void
            if (sampleCoord.x < 0.0 || sampleCoord.x > 1.0 || sampleCoord.y < 0.0 || sampleCoord.y > 1.0) {
                if (uShowGrid == 1) {
                    float r = length((uv - 0.5) * 2.0);
                    if (abs(r - 0.95) < 0.015) {
                        gl_FragColor = vec4(0.0, 0.85, 1.0, 1.0);
                        return;
                    }
                }
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }

            vec4 color;
            if (uChromaticAberration == 1) {
                // Radial chromatic dispersion compensation (Red and Blue channels shifted slightly)
                vec2 sampleCoordR = ((centered * (distFactor * 0.995)) * 0.5) + 0.5;
                vec2 sampleCoordB = ((centered * (distFactor * 1.005)) * 0.5) + 0.5;
                float rCh = texture2D(sTexture, clamp(sampleCoordR, 0.0, 1.0)).r;
                float gCh = texture2D(sTexture, sampleCoord).g;
                float bCh = texture2D(sTexture, clamp(sampleCoordB, 0.0, 1.0)).b;
                color = vec4(rCh, gCh, bCh, 1.0);
            } else {
                color = texture2D(sTexture, sampleCoord);
            }

            // Pause visual feedback: gentle cyan tint indicator
            if (uIsPaused == 1) {
                color.rgb = mix(color.rgb, vec3(0.0, 0.8, 1.0), 0.15);
            }

            // Alignment grid & crosshairs for calibration
            if (uShowGrid == 1) {
                if (abs(sampleCoord.x - 0.5) < 0.003 || abs(sampleCoord.y - 0.5) < 0.003) {
                    color = mix(color, vec4(0.0, 1.0, 0.8, 1.0), 0.85);
                }
                vec2 grid = fract(sampleCoord * 8.0);
                if (grid.x < 0.03 || grid.y < 0.03) {
                    color = mix(color, vec4(0.2, 0.6, 0.9, 1.0), 0.45);
                }
            }

            // Lens Masking
            if (uMaskType == 1) {
                // Circular VR lens mask with smooth anti-aliased edge
                float dist = length((uv - 0.5) * 2.0);
                if (dist > 0.98) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                } else if (dist > 0.86) {
                    float edge = smoothstep(0.98, 0.86, dist);
                    color.rgb *= edge;
                }
            } else if (uMaskType == 2) {
                // Soft cinema edge
                float edgeX = smoothstep(0.0, 0.04, sampleCoord.x) * smoothstep(1.0, 0.96, sampleCoord.x);
                float edgeY = smoothstep(0.0, 0.04, sampleCoord.y) * smoothstep(1.0, 0.96, sampleCoord.y);
                color.rgb *= (edgeX * edgeY);
            }

            color.rgb *= uBrightness;
            gl_FragColor = color;
        }
    """.trimIndent()

    // Shader locations
    private var aPositionHandle = 0
    private var aTextureCoordHandle = 0
    private var uSTMatrixHandle = 0
    private var uScaleHandle = 0
    private var uAspectCorrectionHandle = 0
    private var uFocalLengthHandle = 0
    private var uBarrelK1Handle = 0
    private var uBarrelK2Handle = 0
    private var uBarrelK3Handle = 0
    private var uIpdOffsetHandle = 0
    private var uYOffsetHandle = 0
    private var uMaskTypeHandle = 0
    private var uBrightnessHandle = 0
    private var uShowGridHandle = 0
    private var uIsPausedHandle = 0
    private var uChromaticAberrationHandle = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(vertexShaderSource, fragmentShaderSource)
        if (program == 0) {
            Log.e(tag, "Failed to create OpenGL ES program")
            return
        }

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uScaleHandle = GLES20.glGetUniformLocation(program, "uScale")
        uAspectCorrectionHandle = GLES20.glGetUniformLocation(program, "uAspectCorrection")
        uFocalLengthHandle = GLES20.glGetUniformLocation(program, "uFocalLength")
        uBarrelK1Handle = GLES20.glGetUniformLocation(program, "uBarrelK1")
        uBarrelK2Handle = GLES20.glGetUniformLocation(program, "uBarrelK2")
        uBarrelK3Handle = GLES20.glGetUniformLocation(program, "uBarrelK3")
        uIpdOffsetHandle = GLES20.glGetUniformLocation(program, "uIpdOffset")
        uYOffsetHandle = GLES20.glGetUniformLocation(program, "uYOffset")
        uMaskTypeHandle = GLES20.glGetUniformLocation(program, "uMaskType")
        uBrightnessHandle = GLES20.glGetUniformLocation(program, "uBrightness")
        uShowGridHandle = GLES20.glGetUniformLocation(program, "uShowGrid")
        uIsPausedHandle = GLES20.glGetUniformLocation(program, "uIsPaused")
        uChromaticAberrationHandle = GLES20.glGetUniformLocation(program, "uChromaticAberration")

        // Create OES external texture for SurfaceTexture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@VrSbsRenderer)
        }

        Matrix.setIdentityM(stMatrix, 0)

        surfaceTexture?.let {
            onSurfaceTextureReady(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val currentSettings = settings

        synchronized(this) {
            // Only update texture when NOT paused to freeze frame cleanly
            if (updateSurface && !currentSettings.isPaused) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)
                updateSurface = false
            } else if (updateSurface && currentSettings.isPaused) {
                updateSurface = false
            }
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        if (program == 0 || viewportWidth <= 0 || viewportHeight <= 0) return

        GLES20.glUseProgram(program)

        // Setup vertex buffers
        triangleVertices.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, triangleVertices
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        triangleVertices.position(3)
        GLES20.glVertexAttribPointer(
            aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, triangleVertices
        )
        GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

        GLES20.glUniform1f(uScaleHandle, currentSettings.scale)
        GLES20.glUniform1f(uAspectCorrectionHandle, currentSettings.aspectCorrection)
        GLES20.glUniform1f(uFocalLengthHandle, currentSettings.focalLength)
        GLES20.glUniform1f(uBarrelK1Handle, currentSettings.barrelK1)
        GLES20.glUniform1f(uBarrelK2Handle, currentSettings.barrelK2)
        GLES20.glUniform1f(uBarrelK3Handle, currentSettings.barrelK3)
        GLES20.glUniform1f(uYOffsetHandle, currentSettings.yOffset)
        GLES20.glUniform1i(uMaskTypeHandle, currentSettings.maskType)
        GLES20.glUniform1f(uBrightnessHandle, currentSettings.brightness)
        GLES20.glUniform1i(uShowGridHandle, if (currentSettings.showCalibrationGrid) 1 else 0)
        GLES20.glUniform1i(uIsPausedHandle, if (currentSettings.isPaused) 1 else 0)
        GLES20.glUniform1i(uChromaticAberrationHandle, if (currentSettings.chromaticAberration) 1 else 0)

        // Calculate split gap between eyes
        val gapPixels = (viewportWidth * (currentSettings.splitGapWidth * 0.5f)).toInt().coerceAtLeast(0)
        val eyeWidth = (viewportWidth - gapPixels) / 2

        // --- Render Left Eye ---
        GLES20.glViewport(0, 0, eyeWidth, viewportHeight)
        val leftIpdOffset = -(currentSettings.ipdOffset + currentSettings.parallax3D)
        GLES20.glUniform1f(uIpdOffsetHandle, leftIpdOffset)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // --- Render Right Eye ---
        val rightEyeX = eyeWidth + gapPixels
        GLES20.glViewport(rightEyeX, 0, eyeWidth, viewportHeight)
        val rightIpdOffset = +(currentSettings.ipdOffset + currentSettings.parallax3D)
        GLES20.glUniform1f(uIpdOffsetHandle, rightIpdOffset)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            updateSurface = true
        }
        glSurfaceView.requestRender()
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(tag, "Could not compile shader $shaderType: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) return 0

        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, pixelShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(tag, "Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }
}
