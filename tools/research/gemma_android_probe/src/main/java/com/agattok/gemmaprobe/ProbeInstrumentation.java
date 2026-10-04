package com.agattok.gemmaprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.PowerManager;
import android.os.SystemClock;
import com.google.ai.edge.litertlm.*;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Self-targeted debug research runner. Parent supervises process timeout/native fatal crashes. */
public final class ProbeInstrumentation extends Instrumentation {
    private Bundle args;
    private static final String MODEL_SHA = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments == null ? new Bundle() : arguments; start(); }

    @Override public void onStart() {
        String runId = args.getString("run_id", "");
        String status = "failed", stage = "validation";
        JSONObject result = new JSONObject();
        Engine engine = null;
        Conversation conversation = null;
        Thread cancelThread = null;
        MemorySampler memory = new MemorySampler();
        AtomicLong cancelRequest = new AtomicLong(-1), cancelReturn = new AtomicLong(-1);
        AtomicReference<Throwable> cancelError = new AtomicReference<>();
        AtomicReference<Throwable> inferenceError = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        StringBuilder response = new StringBuilder();
        File results = null;
        boolean cleanup = true;
        long wallStart = now(), inferenceStart = -1, inferenceEnd = -1;
        try {
            put(result, "schema_version", 1);
            put(result, "run_id", runId);
            put(result, "quality_validated", false);
            put(result, "semantic_validation", "not_performed");
            put(result, "device_model", Build.MODEL);
            put(result, "device_api", Build.VERSION.SDK_INT);
            put(result, "device_abi", Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]);
            put(result, "is_physical_device_verified", false); // Parent determines physical-device evidence.
            ActivityManager.MemoryInfo deviceMemory = new ActivityManager.MemoryInfo();
            ((ActivityManager) getTargetContext().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(deviceMemory);
            put(result, "device_total_ram_bytes", deviceMemory.totalMem);
            put(result, "thermal_before", thermal());
            memory.start();
            ProbeInput.require(!Files.isSymbolicLink(getTargetContext().getFilesDir().toPath()));
            File files = getTargetContext().getFilesDir().getCanonicalFile();
            ProbeInput.require(runId.matches("[a-zA-Z0-9_-]{1,40}"));
            results = ProbeInput.child(files, "results");
            ProbeInput.require(results.isDirectory() || results.mkdir());
            ProbeInput.require(!ProbeInput.child(results, runId + ".json").exists()
                && !ProbeInput.child(results, runId + "-response.txt").exists()
                && !ProbeInput.child(results, runId + ".json.tmp").exists());
            ProbeInput input = ProbeInput.read(args, files);
            put(result, "mode", input.mode);
            put(result, "backend", input.backend);
            put(result, "vision_backend", input.visionBackend);
            put(result, "audio_backend", "cpu");
            put(result, "context_tokens", input.contextTokens);
            put(result, "output_tokens_limit", input.outputTokens);
            put(result, "duration_ms", input.durationMs);
            put(result, "frame_count", input.frames.size());
            put(result, "media_order", input.mediaOrder);
            put(result, "video_only", input.videoOnly);
            put(result, "cancel_after_ms", input.cancelAfterMs);
            put(result, "input_validation_ms", millisSince(wallStart));
            stage = "model_verification";
            long verifyStart = now();
            try {
                ProbeInput.require(input.model.isFile() && input.model.length() == 2588147712L);
                ProbeInput.require(MODEL_SHA.equals(sha(input.model)));
                put(result, "model_sha256", MODEL_SHA);
                put(result, "model_bytes", input.model.length());
            } finally { put(result, "verify_model_ms", millisSince(verifyStart)); }

            // Only now touch SDK native state. Benchmark flag is read when the engine is constructed.
            stage = "native_setup";
            Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
            ExperimentalFlags.INSTANCE.setEnableBenchmark(true);
            ExperimentalFlags.INSTANCE.setEnableConversationConstrainedDecoding(false);
            long capabilitiesStart = now();
            try (Capabilities caps = new Capabilities(input.model.getAbsolutePath())) {
                SupportedModalities supported = caps.inputModalities();
                JSONObject modal = new JSONObject();
                put(modal, "text", supported.getText()); put(modal, "vision", supported.getVision());
                put(modal, "audio", supported.getAudio()); put(modal, "video", supported.getVideo());
                put(result, "capabilities", modal);
                put(result, "supports_thinking", caps.supportsThinking());
                put(result, "supports_function_calling", caps.supportsFunctionCalling());
                put(result, "max_vision_token_budget", caps.maxVisionTokenBudget());
            } finally { put(result, "capabilities_ms", millisSince(capabilitiesStart)); }
            stage = "engine_initialization";
            long loadStart = now();
            try {
                ProbeInput.require(!Files.isSymbolicLink(getTargetContext().getCacheDir().toPath()));
                engine = new Engine(new EngineConfig(input.model.getAbsolutePath(), backend(input.backend),
                    input.mode.equals("av") ? backend(input.visionBackend) : null,
                    input.mode.equals("av") && !input.videoOnly ? new Backend.CPU() : null,
                    input.contextTokens, input.mode.equals("av") ? Math.max(1, input.frames.size()) : null,
                    getTargetContext().getCacheDir().getCanonicalPath()));
                engine.initialize();
            } finally { put(result, "load_ms", millisSince(loadStart)); }
            stage = "conversation_creation";
            long conversationStart = now();
            conversation = engine.createConversation(new ConversationConfig(null, Collections.emptyList(),
                Collections.emptyList(), new SamplerConfig(1, 1.0, 0.0, 17), false,
                null, Collections.singletonMap("enable_thinking", Boolean.FALSE),
                null, false, input.outputTokens, null, false));
            put(result, "conversation_create_ms", millisSince(conversationStart));
            List<Content> contents = contents(input);
            AtomicLong firstText = new AtomicLong(-1), firstCallback = new AtomicLong(-1), terminal = new AtomicLong(-1);
            final Conversation active = conversation;
            stage = "inference";
            inferenceStart = now();
            if (input.cancelAfterMs > 0) {
                cancelThread = new Thread(() -> {
                    try {
                        if (!done.await(input.cancelAfterMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                                && done.getCount() != 0) {
                            cancelRequest.set(now());
                            active.cancelProcess();
                            cancelReturn.set(now());
                        }
                    } catch (Throwable failure) { cancelError.set(failure); }
                }, "probe-cancel");
                cancelThread.start();
            }
            active.sendMessageAsync(Message.Companion.user(Contents.Companion.of(contents)), new MessageCallback() {
                @Override public void onMessage(Message message) {
                    firstCallback.compareAndSet(-1, now());
                    synchronized (response) {
                        for (Content content : message.getContents().getContents()) {
                            if (content instanceof Content.Text) {
                                String value = ((Content.Text) content).getText();
                                if (!value.isEmpty()) firstText.compareAndSet(-1, now());
                                response.append(value);
                            }
                        }
                    }
                }
                @Override public void onDone() {
                    long callbackTime = now();
                    firstCallback.compareAndSet(-1, callbackTime);
                    terminal.set(callbackTime);
                    done.countDown();
                }
                @Override public void onError(Throwable failure) {
                    long callbackTime = now();
                    firstCallback.compareAndSet(-1, callbackTime);
                    inferenceError.set(failure);
                    terminal.set(callbackTime);
                    done.countDown();
                }
            });
            done.await(); // Native termination required before cleanup. Parent owns bounded timeout.
            inferenceEnd = terminal.get();
            if (cancelThread != null) cancelThread.join();
            put(result, "first_callback_ms", firstCallback.get() < 0 ? JSONObject.NULL : (firstCallback.get() - inferenceStart) / 1e6);
            put(result, "first_token_ms", firstText.get() < 0 ? JSONObject.NULL : (firstText.get() - inferenceStart) / 1e6);
            put(result, "first_token_metric", "first_nonempty_text_callback_not_individual_token");
            try {
                BenchmarkInfo benchmark = active.getBenchmarkInfo();
                JSONObject metrics = new JSONObject();
                put(metrics, "init_seconds", benchmark.getInitTimeInSecond());
                put(metrics, "first_token_seconds", benchmark.getTimeToFirstTokenInSecond());
                put(metrics, "prefill_tokens", benchmark.getLastPrefillTokenCount());
                put(metrics, "decode_tokens", benchmark.getLastDecodeTokenCount());
                put(metrics, "prefill_tokens_per_second", benchmark.getLastPrefillTokensPerSecond());
                put(metrics, "decode_tokens_per_second", benchmark.getLastDecodeTokensPerSecond());
                put(result, "sdk_benchmark", metrics);
            } catch (Throwable unavailable) { put(result, "sdk_benchmark_unavailable", true); }
            Throwable failure = inferenceError.get();
            boolean acknowledgedCancel = cancelRequest.get() >= inferenceStart
                && cancelReturn.get() >= cancelRequest.get()
                && inferenceEnd >= cancelRequest.get()
                && failure instanceof CancellationException && cancelError.get() == null;
            if (acknowledgedCancel) status = "cancelled";
            else if (failure != null) throw failure;
            else if (cancelRequest.get() >= 0) { put(result, "error_kind", "cancel_not_acknowledged"); }
            else if (response.length() == 0) { put(result, "error_kind", "empty_response"); }
            else status = "completed";
        } catch (Throwable failure) {
            status = "failed";
            put(result, "error_kind", failure.getClass().getSimpleName());
            put(result, "error_stage", stage);
            // No exception messages, stack traces, prompts or generated text sent to status/logcat.
        } finally {
            done.countDown();
            if (cancelThread != null && cancelThread.isAlive()) {
                try { cancelThread.join(); } catch (InterruptedException interrupted) { cleanup = false; Thread.currentThread().interrupt(); }
            }
            long cleanupStart = now();
            if (conversation != null) {
                try { conversation.close(); } catch (Throwable failure) { cleanup = false; put(result, "conversation_close_error", failure.getClass().getSimpleName()); }
            }
            if (engine != null) {
                try { engine.close(); } catch (Throwable failure) { cleanup = false; put(result, "engine_close_error", failure.getClass().getSimpleName()); }
            }
            put(result, "cleanup_ms", millisSince(cleanupStart));
            put(result, "cleanup_complete", cleanup);
            if (!cleanup || cancelError.get() != null) status = "failed";
            put(result, "cancel_requested", cancelRequest.get() >= 0);
            put(result, "cancel_request_ms", cancelRequest.get() < 0 || inferenceStart < 0 ? JSONObject.NULL : (cancelRequest.get() - inferenceStart) / 1e6);
            put(result, "cancel_call_ms", cancelReturn.get() < 0 ? JSONObject.NULL : (cancelReturn.get() - cancelRequest.get()) / 1e6);
            put(result, "cancel_terminal_ms", cancelRequest.get() < 0 || inferenceEnd < 0 ? JSONObject.NULL : (inferenceEnd - cancelRequest.get()) / 1e6);
            if (cancelError.get() != null) put(result, "cancel_error_kind", cancelError.get().getClass().getSimpleName());
            put(result, "inference_ms", inferenceStart < 0 ? 0 : ((inferenceEnd < 0 ? now() : inferenceEnd) - inferenceStart) / 1e6);
            put(result, "thermal_after", thermal());
            memory.stopAndRecord(result);
            put(result, "total_ms", millisSince(wallStart));
            put(result, "response_bytes", 0);
            try {
                if (results != null && runId.matches("[a-zA-Z0-9_-]{1,40}")) {
                    if (inferenceStart >= 0) {
                        File responseFile = ProbeInput.child(results, runId + "-response.txt");
                        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
                        writeNew(responseFile, bytes);
                        put(result, "response_bytes", bytes.length);
                        put(result, "response_sha256", sha(responseFile));
                        put(result, "response_file", "results/" + responseFile.getName());
                    }
                    put(result, "status", status);
                    File target = ProbeInput.child(results, runId + ".json");
                    File temporary = ProbeInput.child(results, runId + ".json.tmp");
                    writeNew(temporary, result.toString(2).getBytes(StandardCharsets.UTF_8));
                    ProbeInput.require(!target.exists() && temporary.renameTo(target));
                }
            } catch (Throwable outputFailure) { status = "failed"; }
            Bundle terminalStatus = new Bundle();
            terminalStatus.putString("run_id", runId.matches("[a-zA-Z0-9_-]{1,40}") ? runId : "invalid");
            terminalStatus.putString("status", status);
            finish(status.equals("failed") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, terminalStatus);
        }
    }

    private static List<Content> contents(ProbeInput in) {
        List<Content> frames = new ArrayList<>(), result = new ArrayList<>();
        if (in.mode.equals("av")) {
            for (int i = 0; i < in.frames.size(); i++) {
                Content image = new Content.ImageFile(in.frames.get(i).getAbsolutePath());
                Content text = new Content.Text("Frame at " + in.offsets.get(i) + " ms");
                if (in.mediaOrder.equals("legacy")) { frames.add(text); frames.add(image); }
                else { frames.add(image); frames.add(text); }
            }
        }
        if (in.mediaOrder.equals("recommended")) result.addAll(frames);
        result.add(new Content.Text(in.prompt));
        if (in.mode.equals("av") && in.audio != null) result.add(new Content.AudioFile(in.audio.getAbsolutePath()));
        if (in.mediaOrder.equals("legacy")) result.addAll(frames);
        return result;
    }
    private static Backend backend(String value) { return value.equals("gpu") ? new Backend.GPU() : new Backend.CPU(); }
    private static long now() { return SystemClock.elapsedRealtimeNanos(); }
    private static double millisSince(long start) { return (now() - start) / 1e6; }
    private Object thermal() {
        if (Build.VERSION.SDK_INT < 29) return JSONObject.NULL;
        try { return ((PowerManager) getTargetContext().getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus(); }
        catch (RuntimeException unavailable) { return JSONObject.NULL; }
    }
    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (org.json.JSONException ignored) { /* Optional NaN SDK metrics are omitted. */ }
    }
    private static String sha(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int count; (count = stream.read(buffer)) != -1;) hash.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }
    private static void writeNew(File target, byte[] bytes) throws Exception {
        ProbeInput.require(target.createNewFile());
        try (FileOutputStream stream = new FileOutputStream(target)) { stream.write(bytes); stream.getFD().sync(); }
    }

    private static final class MemorySampler {
        volatile boolean running;
        Thread thread;
        long peakPss, peakNativePss, peakRss, vmHwm = -1, startPss, endPss;
        int samples;
        synchronized void sample() {
            try {
                Debug.MemoryInfo info = new Debug.MemoryInfo();
                Debug.getMemoryInfo(info);
                long pss = info.getTotalPss();
                if (samples == 0) startPss = pss;
                endPss = pss; peakPss = Math.max(peakPss, pss); peakNativePss = Math.max(peakNativePss, info.nativePss); samples++;
                for (String line : Files.readAllLines(new File("/proc/self/status").toPath(), StandardCharsets.US_ASCII)) {
                    if (line.startsWith("VmRSS:")) peakRss = Math.max(peakRss, metric(line));
                    if (line.startsWith("VmHWM:")) vmHwm = Math.max(vmHwm, metric(line));
                }
            } catch (Exception unavailable) { /* Explicit sample counts/null HWM describe availability. */ }
        }
        private long metric(String line) { return Long.parseLong(line.trim().split("\\s+")[1]); }
        void start() {
            running = true; sample();
            thread = new Thread(() -> {
                while (running) {
                    try { Thread.sleep(250); } catch (InterruptedException ended) { return; }
                    if (running) sample();
                }
            }, "probe-memory");
            thread.start();
        }
        void stopAndRecord(JSONObject result) {
            running = false;
            if (thread != null) { thread.interrupt(); try { thread.join(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } }
            sample();
            put(result, "memory_metric", "Debug.MemoryInfo_PSS_and_proc_self_RSS_sampled_250ms_entire_run");
            put(result, "memory_samples", samples);
            put(result, "peak_pss_kb", samples == 0 ? JSONObject.NULL : peakPss);
            put(result, "start_pss_kb", samples == 0 ? JSONObject.NULL : startPss);
            put(result, "end_pss_kb", samples == 0 ? JSONObject.NULL : endPss);
            put(result, "peak_native_pss_kb_sampled", samples == 0 ? JSONObject.NULL : peakNativePss);
            put(result, "peak_self_rss_kb_sampled", peakRss == 0 ? JSONObject.NULL : peakRss);
            put(result, "vm_hwm_kb_process_lifetime", vmHwm < 0 ? JSONObject.NULL : vmHwm);
            put(result, "native_heap_allocated_bytes_after_cleanup", Debug.getNativeHeapAllocatedSize());
        }
    }
}
