package com.moa.babylanguage;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.ResultReceiver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Standalone test-APK process: use only Android/Java, never target-APK Kotlin classes. */
public class ArtifactGrantReceiverActivity extends Activity {
    private ResultReceiver reply;
    private Uri uri;
    private byte[] first;
    private final Messenger requests = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message message) {
            if (message.what != 1) { super.handleMessage(message); return; }
            try {
                byte[] next = readUri();
                Bundle result = new Bundle();
                result.putBoolean("matches", Arrays.equals(first, next));
                if (reply != null) reply.send(2, result);
            } catch (Exception error) {
                sendError(2, error);
            } finally {
                finish();
            }
        }
    });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reply = getIntent().getParcelableExtra("reply");
        uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        try {
            first = readUri();
            Bundle result = new Bundle();
            result.putInt("uid", Process.myUid());
            result.putInt("bytes", first.length);
            result.putParcelable("requests", requests);
            if (reply != null) reply.send(1, result);
        } catch (Exception error) {
            sendError(1, error);
            finish();
        }
    }

    private byte[] readUri() throws IOException {
        if (uri == null) throw new IOException("Missing shared URI");
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Cannot open shared URI");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private void sendError(int code, Exception error) {
        Bundle result = new Bundle();
        result.putString("error", error.getClass().getName() + ": " + error.getMessage());
        if (reply != null) reply.send(code, result);
    }
}
