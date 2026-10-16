package com.example.graphics

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
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
 * Brown-Conrady Barrel Distortion (K1, K2, K3), Focal Length, Split Gap, Lens Masking,
 * 2-Second Center Dot Dwell-Click Ring, Augmented Reality (AR) Camera Passthrough,
 * and Apple Vision Pro Hand Tracking HUD.
 */
class VrSbsRenderer(
    private val glSurfaceView: GLSurfaceView,
    private val onCameraSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val tag = "VrSbsRenderer"

    private var program = 0
    private var appTextureId = 0
    private var cameraTextureId = 0
    private var bitmapTextureId = 0

    private var appSurfaceTexture: SurfaceTexture? = null
    private var cameraSurfaceTexture: SurfaceTexture? = null

    private var updateAppSurface = false
    private var updateCameraSurface = false
    private var hasValidAppOesFrame = false
    private var hasValidCameraOesFrame = false
    private var uploadedBitmapVersion = -1L
    private var hasValidBitmapTexture = false

    private val stMatrix = FloatArray(16)
    private val camMatrix = FloatArray(16)

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
        varying vec2 vRawUv;
        uniform mat4 uSTMatrix;
        void main() {
            gl_Position = aPosition;
            vRawUv = aTextureCoord.xy;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        varying vec2 vRawUv;
        uniform sampler2D uAppBitmapTex;
        uniform int uHasBitmapTex;
        uniform samplerExternalOES sTexture;
        uniform int uHasAppOesTex;
        uniform samplerExternalOES sCameraTexture;
        uniform int uHasCameraTex;
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
        uniform int uShowCursor;
        uniform float uCursorX;
        uniform float uCursorY;
        uniform int uCursorClicking;
        uniform float uDwellProgress;
        uniform int uArPassthrough;
        uniform float uArAppOpacity;
        uniform int uShowCameraPip;
        uniform int uCinemaMode;
        uniform float uCinemaCurvature;
        uniform int uCinemaAmbientGlow;
        uniform float uEyeSign;
        uniform float uEyeAspect;
        uniform int uHeadTrackingEnabled;
        uniform int uHeadTrackingMode;
        uniform float uHeadYaw;
        uniform float uHeadPitch;
        uniform float uHeadRoll;

        vec4 sampleAppColor(vec2 coord) {
            vec2 c = clamp(coord, 0.0, 1.0);
            if (uHasBitmapTex == 1) {
                return texture2D(uAppBitmapTex, c);
            } else if (uHasAppOesTex == 1) {
                return texture2D(sTexture, vec2(c.x, 1.0 - c.y));
            } else {
                // Fallback spatial grid canvas while waiting for first frame
                vec2 g = fract(c * 12.0);
                float line = (g.x < 0.04 || g.y < 0.04) ? 0.25 : 0.08;
                return vec4(0.04, line * 0.8, line * 1.2, 1.0);
            }
        }

        void main() {
            // Anti-feedback signature block at left-edge vertical center (safe from nav/status bars & rounded corners)
            if (vRawUv.x < 0.025 && abs(vRawUv.y - 0.5) < 0.025) {
                gl_FragColor = vec4(0.051, 0.949, 0.741, 1.0);
                return;
            }

            // Normalized eye coordinate where (0,0) is Top-Left and (1,1) is Bottom-Right
            vec2 baseUv = vec2(vRawUv.x, 1.0 - vRawUv.y);
            vec2 rawCentered = (baseUv - 0.5) * 2.0;

            // Normalize vertical coordinate by eye viewport aspect ratio (eyeWidth / viewportHeight)
            // so the screen in each eye is ALWAYS wide (widescreen theater aspect) and NEVER a tall/narrow rectangle
            float safeEyeAspect = clamp(uEyeAspect, 0.18, 2.5);
            float verticalAspectScale = (safeEyeAspect < 0.88)
                ? (1.0 / (safeEyeAspect * 0.78))
                : 1.16;
            vec2 aspectCentered = vec2(rawCentered.x, rawCentered.y * verticalAspectScale);

            // Lens Masking check (aspect-corrected so lens is never a tall skinny oval)
            float lensMaskFactor = 1.0;
            if (uMaskType == 1) {
                vec2 maskCoord = vec2(rawCentered.x * 0.90, aspectCentered.y * 0.78);
                float dist = length(maskCoord);
                if (dist > 0.98) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                } else if (dist > 0.86) {
                    lensMaskFactor = smoothstep(0.98, 0.86, dist);
                }
            }

            // Optical scale normalized by lens focal length (42mm reference)
            float focalScale = 42.0 / max(uFocalLength, 20.0);

            // Calculate AR Rear Camera Passthrough UV
            vec2 camCentered = rawCentered * focalScale * 0.92;
            float camR2 = dot(camCentered, camCentered);
            float camDist = 1.0 + (uBarrelK1 * 0.5 * camR2);
            vec2 camUv = ((rawCentered * camDist) * 0.5) + 0.5;

            vec4 arCameraColor = vec4(0.05, 0.12, 0.16, 1.0);
            if (uArPassthrough == 1 && uHasCameraTex == 1) {
                arCameraColor = texture2D(sCameraTexture, clamp(camUv, 0.0, 1.0));
            }

            // Wide App Window coordinates: fills full horizontal eye width, compact widescreen vertical height
            float horizWidthFactor = clamp(uScale * max(uAspectCorrection, 1.12), 0.72, 1.35);
            float vertHeightFactor = clamp(uScale, 0.65, 1.25);

            // 3DoF Head Tracking orientation transformation
            vec2 trackedAspect = aspectCentered;
            if (uHeadTrackingEnabled == 1) {
                float cosR = cos(uHeadRoll);
                float sinR = sin(uHeadRoll);
                trackedAspect = vec2(
                    aspectCentered.x * cosR - aspectCentered.y * sinR,
                    aspectCentered.x * sinR + aspectCentered.y * cosR
                );
            }

            float headX = 0.0;
            float headY = 0.0;
            if (uHeadTrackingEnabled == 1 && uHeadTrackingMode != 1) {
                // In World-Locked Screen (0) or Panoramic Cinema Canvas (2), offset viewport position by head Yaw & Pitch
                headX = uHeadYaw * 1.30;
                headY = uHeadPitch * 1.20;
            }

            vec2 centered;
            centered.x = (trackedAspect.x - uIpdOffset + headX) / horizWidthFactor;
            centered.y = (trackedAspect.y - uYOffset + headY) / vertHeightFactor;

            vec2 distCoord = centered * focalScale * 0.65;
            float r2 = dot(distCoord, distCoord);
            float r4 = r2 * r2;
            float r6 = r4 * r2;

            // Brown-Conrady Polynomial Lens Distortion Correction
            float distFactor = 1.0;
            if (uBarrelK1 > 0.0001 || uBarrelK2 > 0.0001 || uBarrelK3 > 0.0001) {
                distFactor = 1.0 + (uBarrelK1 * 0.55 * r2) + (uBarrelK2 * 0.55 * r4) + (uBarrelK3 * 0.55 * r6);
            }

            vec2 warped = centered * distFactor;

            // Curved Cinema Screen Display Mode: Concave cylindrical theater projection + 3D depth arc
            // Keeps full horizontal width without pinching the sides inward
            if (uCinemaMode == 1 && uCinemaCurvature > 0.005) {
                float curve = clamp(uCinemaCurvature, 0.0, 0.85);
                float cx = warped.x;
                float cy = warped.y;
                float horizArc = cx * cx;
                // Center of concave cinema screen recedes in 3D perspective; left/right wings wrap closer
                float perspectiveDepth = 1.0 + (curve * 0.22 * (1.0 - horizArc));
                warped.y = cy * perspectiveDepth;
                // Stereoscopic 3D concave parallax per eye (preserves full wide horizontal span)
                warped.x = cx + (uEyeSign * curve * 0.014 * (horizArc - 0.35));
            }

            vec2 sampleCoord = (warped * 0.5) + 0.5;

            bool isOutsideAppWindow = (sampleCoord.x < 0.0 || sampleCoord.x > 1.0 || sampleCoord.y < 0.0 || sampleCoord.y > 1.0);

            if (isOutsideAppWindow) {
                if (uArPassthrough == 1) {
                    vec4 bg = arCameraColor;
                    bg.rgb *= (uBrightness * lensMaskFactor);
                    gl_FragColor = bg;
                    return;
                }
                if (uShowGrid == 1) {
                    float r = length(rawCentered);
                    if (abs(r - 0.95) < 0.015) {
                        gl_FragColor = vec4(0.0, 0.85, 1.0, 1.0);
                        return;
                    }
                }
                // Dynamic Theater Ambilight Glow around the Curved Cinema Screen
                if (uCinemaMode == 1 && uCinemaAmbientGlow == 1) {
                    float dxOut = max(0.0, max(-sampleCoord.x, sampleCoord.x - 1.0));
                    float dyOut = max(0.0, max(-sampleCoord.y, sampleCoord.y - 1.0));
                    float outDist = length(vec2(dxOut, dyOut));
                    if (outDist < 0.16) {
                        float glowFalloff = smoothstep(0.16, 0.0, outDist);
                        vec4 edgeSample = sampleAppColor(clamp(sampleCoord, 0.02, 0.98));
                        vec3 theaterTint = mix(edgeSample.rgb, vec3(0.05, 0.55, 0.85), 0.35);
                        vec3 glowRgb = theaterTint * (glowFalloff * glowFalloff * 0.45 * uBrightness * lensMaskFactor);
                        gl_FragColor = vec4(glowRgb, 1.0);
                        return;
                    }
                }
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }

            vec4 color;
            if (uChromaticAberration == 1) {
                vec2 sampleCoordR = ((warped * 0.995) * 0.5) + 0.5;
                vec2 sampleCoordB = ((warped * 1.005) * 0.5) + 0.5;
                float rCh = sampleAppColor(sampleCoordR).r;
                float gCh = sampleAppColor(sampleCoord).g;
                float bCh = sampleAppColor(sampleCoordB).b;
                color = vec4(rCh, gCh, bCh, 1.0);
            } else {
                color = sampleAppColor(sampleCoord);
            }

            // Subtle cylindrical theater surface shading in Curved Cinema Mode
            if (uCinemaMode == 1 && uCinemaCurvature > 0.005) {
                float cxNorm = (sampleCoord.x - 0.5) * 2.0;
                float sheen = 1.0 + (uCinemaCurvature * 0.06 * (1.0 - cxNorm * cxNorm));
                color.rgb *= sheen;
            }

            // Subtle glowing cyan spatial window border around the app inside each VR lens
            float borderDistX = min(sampleCoord.x, 1.0 - sampleCoord.x);
            float borderDistY = min(sampleCoord.y, 1.0 - sampleCoord.y);
            float minBorder = min(borderDistX, borderDistY);
            if (minBorder < 0.008) {
                color.rgb = mix(color.rgb, vec3(0.0, 0.90, 1.0), 0.65);
            } else if (uArPassthrough == 1) {
                color.rgb = mix(arCameraColor.rgb, color.rgb, uArAppOpacity);
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

            // Stereoscopic 3D VR Cursor Reticle + Trackpad Click Ripple Effect
            if (uShowCursor == 1) {
                vec2 cursorDelta = sampleCoord - vec2(uCursorX, uCursorY);
                cursorDelta.x *= 1.15;
                float cursorDist = length(cursorDelta);
                float outerRadius = (uCursorClicking == 1) ? 0.032 : 0.020;
                float innerRadius = (uCursorClicking == 1) ? 0.012 : 0.006;

                // Trackpad Click Burst Halo when user clicks on the trackpad
                if (uCursorClicking == 1 && cursorDist < 0.048 && cursorDist > 0.032) {
                    float rippleAlpha = smoothstep(0.048, 0.032, cursorDist);
                    color.rgb = mix(color.rgb, vec3(0.0, 1.0, 0.46), rippleAlpha * 0.75);
                }

                // High-contrast dark halo for visibility on bright apps
                if (cursorDist < outerRadius + 0.004 && cursorDist > outerRadius - 0.005) {
                    color.rgb = mix(color.rgb, vec3(0.0, 0.0, 0.0), 0.85);
                }
                // Neon outer reticle ring (flashes bright green when trackpad is clicked)
                if (cursorDist < outerRadius && cursorDist > outerRadius - 0.0045) {
                    color.rgb = (uCursorClicking == 1) ? vec3(0.0, 1.0, 0.46) : vec3(0.0, 0.90, 1.0);
                }
                // Precision center dot (flashes crisp white + neon green on trackpad click)
                if (cursorDist < innerRadius) {
                    color.rgb = (uCursorClicking == 1) ? vec3(1.0, 1.0, 1.0) : vec3(0.0, 1.0, 0.46);
                }
            }

            // Soft cinema vignette if maskType == 2
            if (uMaskType == 2) {
                float edgeX = smoothstep(0.0, 0.04, sampleCoord.x) * smoothstep(1.0, 0.96, sampleCoord.x);
                float edgeY = smoothstep(0.0, 0.04, sampleCoord.y) * smoothstep(1.0, 0.96, sampleCoord.y);
                color.rgb *= (edgeX * edgeY);
            }

            color.rgb *= (uBrightness * lensMaskFactor);
            gl_FragColor = vec4(color.rgb, 1.0);
        }
    """.trimIndent()

    // Shader locations
    private var aPositionHandle = 0
    private var aTextureCoordHandle = 0
    private var uSTMatrixHandle = 0
    private var uAppBitmapTexHandle = 0
    private var uHasBitmapTexHandle = 0
    private var sTextureHandle = 0
    private var uHasAppOesTexHandle = 0
    private var sCameraTextureHandle = 0
    private var uHasCameraTexHandle = 0
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
    private var uShowCursorHandle = 0
    private var uCursorXHandle = 0
    private var uCursorYHandle = 0
    private var uCursorClickingHandle = 0
    private var uDwellProgressHandle = 0
    private var uArPassthroughHandle = 0
    private var uArAppOpacityHandle = 0
    private var uShowCameraPipHandle = 0
    private var uCinemaModeHandle = 0
    private var uCinemaCurvatureHandle = 0
    private var uCinemaAmbientGlowHandle = 0
    private var uEyeSignHandle = 0
    private var uEyeAspectHandle = 0
    private var uHeadTrackingEnabledHandle = 0
    private var uHeadTrackingModeHandle = 0
    private var uHeadYawHandle = 0
    private var uHeadPitchHandle = 0
    private var uHeadRollHandle = 0

    @Volatile
    var headYaw: Float = 0f
    @Volatile
    var headPitch: Float = 0f
    @Volatile
    var headRoll: Float = 0f

    fun updateHeadPose(yaw: Float, pitch: Float, roll: Float) {
        headYaw = yaw
        headPitch = pitch
        headRoll = roll
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(vertexShaderSource, fragmentShaderSource)
        if (program == 0) {
            Log.e(tag, "Failed to create OpenGL ES program")
            return
        }

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uAppBitmapTexHandle = GLES20.glGetUniformLocation(program, "uAppBitmapTex")
        uHasBitmapTexHandle = GLES20.glGetUniformLocation(program, "uHasBitmapTex")
        sTextureHandle = GLES20.glGetUniformLocation(program, "sTexture")
        uHasAppOesTexHandle = GLES20.glGetUniformLocation(program, "uHasAppOesTex")
        sCameraTextureHandle = GLES20.glGetUniformLocation(program, "sCameraTexture")
        uHasCameraTexHandle = GLES20.glGetUniformLocation(program, "uHasCameraTex")
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
        uShowCursorHandle = GLES20.glGetUniformLocation(program, "uShowCursor")
        uCursorXHandle = GLES20.glGetUniformLocation(program, "uCursorX")
        uCursorYHandle = GLES20.glGetUniformLocation(program, "uCursorY")
        uCursorClickingHandle = GLES20.glGetUniformLocation(program, "uCursorClicking")
        uDwellProgressHandle = GLES20.glGetUniformLocation(program, "uDwellProgress")
        uArPassthroughHandle = GLES20.glGetUniformLocation(program, "uArPassthrough")
        uArAppOpacityHandle = GLES20.glGetUniformLocation(program, "uArAppOpacity")
        uShowCameraPipHandle = GLES20.glGetUniformLocation(program, "uShowCameraPip")
        uCinemaModeHandle = GLES20.glGetUniformLocation(program, "uCinemaMode")
        uCinemaCurvatureHandle = GLES20.glGetUniformLocation(program, "uCinemaCurvature")
        uCinemaAmbientGlowHandle = GLES20.glGetUniformLocation(program, "uCinemaAmbientGlow")
        uEyeSignHandle = GLES20.glGetUniformLocation(program, "uEyeSign")
        uEyeAspectHandle = GLES20.glGetUniformLocation(program, "uEyeAspect")
        uHeadTrackingEnabledHandle = GLES20.glGetUniformLocation(program, "uHeadTrackingEnabled")
        uHeadTrackingModeHandle = GLES20.glGetUniformLocation(program, "uHeadTrackingMode")
        uHeadYawHandle = GLES20.glGetUniformLocation(program, "uHeadYaw")
        uHeadPitchHandle = GLES20.glGetUniformLocation(program, "uHeadPitch")
        uHeadRollHandle = GLES20.glGetUniformLocation(program, "uHeadRoll")

        // Create 3 textures: [0] = App OES, [1] = Rear Camera OES, [2] = Clean App Bitmap 2D Texture
        val textures = IntArray(3)
        GLES20.glGenTextures(3, textures, 0)
        appTextureId = textures[0]
        cameraTextureId = textures[1]
        bitmapTextureId = textures[2]

        for (texId in intArrayOf(appTextureId, cameraTextureId)) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        uploadedBitmapVersion = -1L
        hasValidBitmapTexture = false
        hasValidAppOesFrame = false
        hasValidCameraOesFrame = false

        appSurfaceTexture = SurfaceTexture(appTextureId).apply {
            setOnFrameAvailableListener(this@VrSbsRenderer)
        }

        cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener {
                synchronized(this@VrSbsRenderer) {
                    updateCameraSurface = true
                }
                glSurfaceView.requestRender()
            }
        }

        Matrix.setIdentityM(stMatrix, 0)
        Matrix.setIdentityM(camMatrix, 0)

        appSurfaceTexture?.let {
            onSurfaceTextureReady(it)
        }
        cameraSurfaceTexture?.let {
            onCameraSurfaceTextureReady?.invoke(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val currentSettings = settings

        synchronized(this) {
            if (updateAppSurface && !currentSettings.isPaused) {
                try {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    appSurfaceTexture?.updateTexImage()
                    appSurfaceTexture?.getTransformMatrix(stMatrix)
                    hasValidAppOesFrame = true
                } catch (_: Exception) {}
                updateAppSurface = false
            } else if (updateAppSurface && currentSettings.isPaused) {
                updateAppSurface = false
            }

            if (updateCameraSurface) {
                try {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                    cameraSurfaceTexture?.updateTexImage()
                    cameraSurfaceTexture?.getTransformMatrix(camMatrix)
                    hasValidCameraOesFrame = true
                } catch (_: Exception) {}
                updateCameraSurface = false
            }
        }

        // Upload latest clean Bitmap frame from VrFrameHolder if updated
        val currentHolderVersion = VrFrameHolder.frameVersion
        val latestBmp = VrFrameHolder.latestBitmap
        if (!currentSettings.isPaused &&
            latestBmp != null &&
            !latestBmp.isRecycled &&
            currentHolderVersion != uploadedBitmapVersion
        ) {
            try {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTextureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, latestBmp, 0)
                uploadedBitmapVersion = currentHolderVersion
                hasValidBitmapTexture = true
            } catch (e: Exception) {
                Log.w(tag, "Bitmap texture upload error: ${e.message}")
            }
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        if (program == 0 || viewportWidth <= 0 || viewportHeight <= 0) return

        GLES20.glUseProgram(program)

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

        // Bind App OES Texture to unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, appTextureId)
        GLES20.glUniform1i(sTextureHandle, 0)
        GLES20.glUniform1i(uHasAppOesTexHandle, if (hasValidAppOesFrame) 1 else 0)

        // Bind Camera AR OES Texture to unit 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            if (hasValidCameraOesFrame) cameraTextureId else appTextureId
        )
        GLES20.glUniform1i(sCameraTextureHandle, 1)
        GLES20.glUniform1i(uHasCameraTexHandle, if (hasValidCameraOesFrame) 1 else 0)

        // Bind Clean App 2D Bitmap Texture to unit 2
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTextureId)
        GLES20.glUniform1i(uAppBitmapTexHandle, 2)
        GLES20.glUniform1i(uHasBitmapTexHandle, if (hasValidBitmapTexture) 1 else 0)

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
        val headEnabled = currentSettings.headTrackingEnabled
        val effectiveYaw = if (headYaw != 0f) headYaw else currentSettings.headYaw
        val effectivePitch = if (headPitch != 0f) headPitch else currentSettings.headPitch
        val effectiveRoll = if (headRoll != 0f) headRoll else currentSettings.headRoll

        val isGazeAimMode = headEnabled && currentSettings.headTrackingMode == VrSettings.HEAD_TRACKING_GAZE_AIM
        val finalCursorX = if (isGazeAimMode) {
            (0.5f + effectiveYaw * 0.95f).coerceIn(0.05f, 0.95f)
        } else {
            currentSettings.cursorX
        }
        val finalCursorY = if (isGazeAimMode) {
            (0.5f - effectivePitch * 0.95f).coerceIn(0.05f, 0.95f)
        } else {
            currentSettings.cursorY
        }

        GLES20.glUniform1i(
            uShowCursorHandle,
            if (currentSettings.gearVrTouchpadEnabled || currentSettings.handTrackingEnabled || currentSettings.dwellClickEnabled || isGazeAimMode) 1 else 0
        )
        GLES20.glUniform1f(uCursorXHandle, finalCursorX)
        GLES20.glUniform1f(uCursorYHandle, finalCursorY)
        GLES20.glUniform1i(uCursorClickingHandle, if (currentSettings.isCursorClicking) 1 else 0)
        GLES20.glUniform1f(uDwellProgressHandle, currentSettings.dwellProgress)
        GLES20.glUniform1i(uArPassthroughHandle, if (currentSettings.arPassthroughEnabled) 1 else 0)
        GLES20.glUniform1f(uArAppOpacityHandle, currentSettings.arAppOpacity)
        GLES20.glUniform1i(
            uShowCameraPipHandle,
            if (currentSettings.handTrackingEnabled && currentSettings.showCameraRadarPip) 1 else 0
        )
        GLES20.glUniform1i(uCinemaModeHandle, if (currentSettings.cinemaScreenEnabled) 1 else 0)
        GLES20.glUniform1f(uCinemaCurvatureHandle, currentSettings.cinemaCurvature)
        GLES20.glUniform1i(uCinemaAmbientGlowHandle, if (currentSettings.cinemaAmbientGlow) 1 else 0)

        // Head tracking 3DoF uniforms
        GLES20.glUniform1i(uHeadTrackingEnabledHandle, if (headEnabled) 1 else 0)
        GLES20.glUniform1i(uHeadTrackingModeHandle, currentSettings.headTrackingMode)
        GLES20.glUniform1f(uHeadYawHandle, effectiveYaw)
        GLES20.glUniform1f(uHeadPitchHandle, effectivePitch)
        GLES20.glUniform1f(uHeadRollHandle, effectiveRoll)

        // Calculate split gap between eyes
        val gapPixels = (viewportWidth * (currentSettings.splitGapWidth * 0.5f)).toInt().coerceAtLeast(0)
        val eyeWidth = (viewportWidth - gapPixels) / 2
        val eyeAspect = if (viewportHeight > 0) eyeWidth.toFloat() / viewportHeight.toFloat() else 1.0f
        GLES20.glUniform1f(uEyeAspectHandle, eyeAspect)

        // --- Render Left Eye ---
        GLES20.glViewport(0, 0, eyeWidth, viewportHeight)
        val leftIpdOffset = -(currentSettings.ipdOffset + currentSettings.parallax3D)
        GLES20.glUniform1f(uIpdOffsetHandle, leftIpdOffset)
        GLES20.glUniform1f(uEyeSignHandle, -1.0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // --- Render Right Eye ---
        val rightEyeX = eyeWidth + gapPixels
        GLES20.glViewport(rightEyeX, 0, eyeWidth, viewportHeight)
        val rightIpdOffset = +(currentSettings.ipdOffset + currentSettings.parallax3D)
        GLES20.glUniform1f(uIpdOffsetHandle, rightIpdOffset)
        GLES20.glUniform1f(uEyeSignHandle, +1.0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            updateAppSurface = true
        }
        glSurfaceView.requestRender()
    }

    fun release() {
        appSurfaceTexture?.setOnFrameAvailableListener(null)
        appSurfaceTexture?.release()
        appSurfaceTexture = null

        cameraSurfaceTexture?.setOnFrameAvailableListener(null)
        cameraSurfaceTexture?.release()
        cameraSurfaceTexture = null
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
