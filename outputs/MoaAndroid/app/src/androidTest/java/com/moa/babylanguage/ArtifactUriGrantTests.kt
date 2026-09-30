package com.moa.babylanguage

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ArtifactUriGrantTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun separateTestApkUidReopensOriginalGrantedUriAfterSourceAndCacheClear() {
        val id = "uri-grant-${UUID.randomUUID()}"
        val store = ObservationStore(compose.activity, "$id.db", "$id-legacy")
        val record = Observation(System.currentTimeMillis(), "옹알이", "놀이 중", "", "같이 놀아 줄래?")
        val first = AtomicReference<Bundle>()
        val second = AtomicReference<Bundle>()
        val initialRead = CountDownLatch(1)
        val laterRead = CountDownLatch(1)
        val reply = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(code: Int, data: Bundle) {
                if (code == 1) { first.set(data); initialRead.countDown() }
                if (code == 2) { second.set(data); laterRead.countDown() }
            }
        }
        var generated: File? = null
        var retained: File? = null
        try {
            runBlocking {
                store.save(record)
                val generation = MemoryArtifacts.currentGeneration()
                val file = MemoryArtifacts.card(compose.activity, record, generation)
                generated = file
                retained = File(compose.activity.cacheDir, "exports/dispatched/${file.name}")
                MemoryArtifacts.share(compose.activity, file, "image/png", generation, ArtifactShareLauncher { context, chooser ->
                    val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                    val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
                    val receiver = Intent(send).setComponent(ComponentName(testPackage, ArtifactGrantReceiverActivity::class.java.name))
                    receiver.putExtra("reply", reply)
                    context.startActivity(receiver)
                })
            }
            assertTrue("Separate receiver must consume Android's URI grant", initialRead.await(8, TimeUnit.SECONDS))
            assertNull(first.get().getString("error"))
            assertTrue(first.get().getInt("bytes") > 0)
            assertNotEquals("A same-UID provider read would not prove grant consumption", Process.myUid(), first.get().getInt("uid"))
            runBlocking { store.deleteAll(); MemoryArtifacts.clear(compose.activity) }
            assertTrue(runBlocking { store.snapshot().observations.isEmpty() })
            first.get().getParcelable<Messenger>("requests")!!.send(Message.obtain().apply { what = 1 })
            assertTrue("Recipient must reopen its original grant after deletion", laterRead.await(8, TimeUnit.SECONDS))
            assertNull(second.get().getString("error"))
            assertTrue(second.get().getBoolean("matches"))
        } finally {
            // Finish a waiting receiver even when an earlier assertion fails.
            first.get()?.getParcelable<Messenger>("requests")?.let { runCatching { it.send(Message.obtain().apply { what = 1 }) } }
            runBlocking { store.close() }
            compose.activity.deleteDatabase("$id.db")
            compose.activity.getSharedPreferences("$id-legacy", 0).edit().clear().commit()
            generated?.delete(); retained?.delete()
        }
    }
}
