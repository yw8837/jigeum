package com.moa.babylanguage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.protobuf.CodedInputStream
import com.google.protobuf.InvalidProtocolBufferException
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtobufRuntimeTest {
    @Test fun bundledFaceModelInitializesAndProcessesFrameWithPatchedRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val image = BitmapImageBuilder(bitmap).build()
        val model = FaceLandmarker.createFromOptions(context, options)
        try {
            assertTrue(model.detect(image).faceLandmarks().isEmpty())
        } finally {
            model.close()
            image.close()
            bitmap.recycle()
        }
    }

    @Test fun deeplyNestedUnknownGroupsStopAtRecursionLimit() {
        // Owned 512-byte synthetic input: field1 start/end groups. A vulnerable
        // skip parser ignores its recursion budget and accepts all 256 levels.
        val depth = 256
        val payload = ByteArray(depth * 2) { if (it < depth) 0x0b.toByte() else 0x0c.toByte() }
        val parser = CodedInputStream.newInstance(payload)
        parser.setRecursionLimit(32)
        try {
            parser.skipMessage()
            fail("Nested unknown groups must respect the configured recursion limit")
        } catch (_: InvalidProtocolBufferException) {
            // A bounded parse failure is expected; StackOverflowError is not accepted.
        }
    }
}
