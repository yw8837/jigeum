package com.moa.babylanguage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.ResultReceiver

/** Test-APK receiver. It reopens the original granted URI only when the parent requests it. */
class ArtifactGrantReceiverActivity : Activity() {
    private var reply: ResultReceiver? = null
    private var uri: Uri? = null
    private var first: ByteArray? = null
    private val requests = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            try {
                val next = contentResolver.openInputStream(uri!!)!!.use { it.readBytes() }
                reply?.send(2, Bundle().apply { putBoolean("matches", first!!.contentEquals(next)) })
            } catch (error: Exception) {
                reply?.send(2, Bundle().apply { putString("error", error.javaClass.name + ": " + error.message) })
            } finally { finish() }
        }
    })
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reply = intent.getParcelableExtra("reply")
        uri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        try {
            first = contentResolver.openInputStream(uri!!)!!.use { it.readBytes() }
            reply?.send(1, Bundle().apply {
                putInt("uid", Process.myUid())
                putInt("bytes", first!!.size)
                putParcelable("requests", requests)
            })
        } catch (error: Exception) {
            reply?.send(1, Bundle().apply { putString("error", error.javaClass.name + ": " + error.message) })
            finish()
        }
    }
}
