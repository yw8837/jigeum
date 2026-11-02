@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.moa.babylanguage

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.media3.common.MediaItem
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Reproduces the library failure without requiring a hardware HDR encoder on the emulator. */
class CaptionHdrCompatibilityTest {
    @Test fun plainCaptionFailsInLegacyHdrShaderAndDrawsInSdrShader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val display = GlUtil.getDefaultEglDisplay()
        val egl = GlUtil.createEglContext(EGL14.EGL_NO_CONTEXT, display, 3, GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888)
        val surface = GlUtil.createFocusedPlaceholderEglSurface(egl, display)
        val input = GlUtil.createTexture(720, 960, false)
        val output = GlUtil.createTexture(720, 960, false)
        val fbo = GlUtil.createFboForTexture(output)
        try {
            GlUtil.focusFramebufferUsingCurrentContext(fbo, 720, 960)
            for (hdr in listOf(true, false)) {
                val bitmap = TranslatedMedia.caption(Observation(1, "옹알이", "0:00–0:02", "", "옹알옹알, 내 목소리를 들어봐요."), true)
                assertFalse(bitmap.hasGainmap())
                val shader = OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bitmap)))
                    .toGlShaderProgram(context, hdr) as BaseGlShaderProgram
                try {
                    shader.configure(720, 960)
                    if (hdr) {
                        try { shader.drawFrame(input, 0); fail("SDR bitmap must hit the legacy HDR gainmap check") }
                        catch (expected: IllegalArgumentException) {
                            assertTrue(expected.stackTrace.any { it.className.endsWith("OverlayShaderProgram") && it.methodName == "drawFrame" })
                        }
                    } else { shader.drawFrame(input, 0); GlUtil.checkGlError() }
                } finally { shader.release(); bitmap.recycle() }
            }
        } finally {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GlUtil.deleteTexture(input); GlUtil.deleteTexture(output)
            GlUtil.destroyEglSurface(display, surface); GlUtil.destroyEglContext(display, egl)
        }
    }

    @Test fun actualExportCompositionToneMapsOnlyCaptionedVideoAndInfersSourceTracks() {
        val movie = EditedMediaItem.Builder(MediaItem.fromUri("file:///synthetic.mp4")).build()
        assertEquals(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            TranslatedMedia.videoComposition(movie, captioned = true).hdrMode)
        assertEquals(Composition.HDR_MODE_KEEP_HDR,
            TranslatedMedia.videoComposition(movie, captioned = false).hdrMode)
    }
}
