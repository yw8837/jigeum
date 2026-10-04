package com.agattok.gemmaprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.CRC32;

/** All validation runs before any SDK/native initialization. No caller-selected absolute paths. */
final class ProbeInput {
    String runId, mode, backend, visionBackend, mediaOrder, prompt;
    int contextTokens, outputTokens, cancelAfterMs, durationMs;
    boolean videoOnly;
    File model, audio;
    final List<File> frames = new ArrayList<>();
    final List<Integer> offsets = new ArrayList<>();

    static ProbeInput read(Bundle args, File files) throws Exception {
        ProbeInput in = new ProbeInput();
        in.runId = args.getString("run_id", "");
        require(in.runId.matches("[a-zA-Z0-9_-]{1,40}"));
        in.mode = choice(args, "mode", null, "text", "av");
        in.backend = choice(args, "backend", null, "cpu", "gpu");
        in.visionBackend = choice(args, "vision_backend", "gpu", "cpu", "gpu");
        in.mediaOrder = choice(args, "media_order", "legacy", "legacy", "recommended");
        in.videoOnly = choice(args, "video_only", "false", "true", "false").equals("true");
        require(!in.videoOnly || in.mode.equals("av"));
        in.contextTokens = integerArg(args, "context_tokens", "4096", 4096, 16384);
        require(in.contextTokens == 4096 || in.contextTokens == 8192 || in.contextTokens == 16384);
        in.outputTokens = integerArg(args, "output_tokens", "700", 1, 700);
        in.cancelAfterMs = integerArg(args, "cancel_after_ms", "0", 0, 10000);
        in.model = child(files, "model.litertlm");
        File input = child(files, "input");
        require(input.isDirectory());
        File bundle = child(input, "bundle.json");
        require(bundle.isFile() && bundle.length() > 0 && bundle.length() <= 100000);
        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundle.toPath()), StandardCharsets.UTF_8));
        in.durationMs = exactInt(json.get("duration_ms"), 1000, 20000);
        Object prompt = json.get("prompt");
        require(prompt instanceof String);
        in.prompt = (String) prompt;
        require(!in.prompt.trim().isEmpty() && in.prompt.length() <= 12000);
        Object audio = json.get("audio");
        if (audio == JSONObject.NULL) {
            require(in.videoOnly);
        } else {
            require(!in.videoOnly && audio instanceof String && audio.equals("audio.wav"));
            in.audio = child(input, "audio.wav");
            validateWav(in.audio, in.durationMs);
        }
        JSONArray frames = json.getJSONArray("frames");
        require(frames.length() <= 40 && (in.mode.equals("text") || frames.length() > 0));
        int previous = -1;
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < frames.length(); i++) {
            JSONObject frame = frames.getJSONObject(i);
            int offset = exactInt(frame.get("offset_ms"), 0, in.durationMs - 1);
            require(offset > previous);
            previous = offset;
            Object name = frame.get("path");
            require(name instanceof String && ((String) name).matches("frame-[0-9]{3}\\.png") && seen.add((String) name));
            File image = child(input, (String) name);
            validatePng(image);
            in.frames.add(image);
            in.offsets.add(offset);
        }
        return in;
    }

    static File child(File parent, String name) throws IOException {
        require(name.matches("[a-zA-Z0-9_.-]+") && !name.equals(".") && !name.equals(".."));
        File child = new File(parent, name);
        require(!Files.isSymbolicLink(parent.toPath()) && !Files.isSymbolicLink(child.toPath()));
        require(child.getCanonicalFile().equals(child.getAbsoluteFile()));
        require(child.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile()));
        return child;
    }

    static int exactInt(Object value, int min, int max) {
        require(value instanceof Integer || value instanceof Long);
        long n = ((Number) value).longValue();
        require(n >= min && n <= max);
        return (int) n;
    }

    private static int integerArg(Bundle args, String key, String fallback, int min, int max) {
        String value = args.getString(key, fallback);
        require(value != null && value.matches("[0-9]{1,5}"));
        return exactInt(Long.parseLong(value), min, max);
    }

    private static String choice(Bundle args, String key, String fallback, String a, String b) {
        String value = args.getString(key, fallback);
        require(a.equals(value) || b.equals(value));
        return value;
    }

    static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("invalid_probe_input");
    }

    private static void validatePng(File image) throws IOException {
        require(image.isFile() && image.length() >= 33 && image.length() <= 2 * 1024 * 1024);
        try (RandomAccessFile f = new RandomAccessFile(image, "r")) {
            require(f.readLong() == 0x89504e470d0a1a0aL);
            boolean header = false, data = false, end = false;
            byte[] buffer = new byte[8192];
            while (f.getFilePointer() < f.length()) {
                require(f.length() - f.getFilePointer() >= 12 && !end);
                long length = Integer.toUnsignedLong(f.readInt());
                int type = f.readInt();
                require(length <= f.length() - f.getFilePointer() - 4);
                if (!header) {
                    require(type == 0x49484452 && length == 13);
                    header = true;
                } else require(type != 0x49484452);
                if (type == 0x49444154) data = true;
                if (type == 0x49454e44) { require(data && length == 0); end = true; }
                CRC32 crc = new CRC32();
                crc.update((type >>> 24) & 255); crc.update((type >>> 16) & 255);
                crc.update((type >>> 8) & 255); crc.update(type & 255);
                for (long remaining = length; remaining > 0;) {
                    int count = (int) Math.min(buffer.length, remaining);
                    f.readFully(buffer, 0, count); crc.update(buffer, 0, count); remaining -= count;
                }
                require(crc.getValue() == Integer.toUnsignedLong(f.readInt()));
            }
            require(header && data && end);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), opts);
        require(opts.outWidth > 0 && opts.outHeight > 0 && opts.outWidth <= 512 && opts.outHeight <= 512
            && "image/png".equals(opts.outMimeType));
        Bitmap decoded = BitmapFactory.decodeFile(image.getAbsolutePath());
        require(decoded != null);
        decoded.recycle();
    }

    private static long u32(RandomAccessFile f) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));
    }
    private static int u16(RandomAccessFile f) throws IOException { return Short.toUnsignedInt(Short.reverseBytes(f.readShort())); }

    private static void validateWav(File audio, int durationMs) throws IOException {
        require(audio.isFile() && audio.length() >= 44 && audio.length() <= 1024 * 1024);
        try (RandomAccessFile f = new RandomAccessFile(audio, "r")) {
            require(f.readInt() == 0x52494646 && u32(f) + 8 == f.length() && f.readInt() == 0x57415645);
            boolean fmt = false, data = false;
            while (f.getFilePointer() < f.length()) {
                require(f.length() - f.getFilePointer() >= 8);
                int tag = f.readInt();
                long size = u32(f), start = f.getFilePointer(), next = start + size + (size & 1);
                require(next <= f.length());
                if (tag == 0x666d7420) {
                    require(!fmt && !data && (size == 16 || size == 18));
                    require(u16(f) == 1 && u16(f) == 1 && u32(f) == 16000 && u32(f) == 32000
                        && u16(f) == 2 && u16(f) == 16);
                    if (size == 18) require(u16(f) == 0);
                    fmt = true;
                } else if (tag == 0x64617461) {
                    require(fmt && !data && size == (long) durationMs * 32);
                    data = true;
                }
                f.seek(next);
            }
            require(fmt && data && f.getFilePointer() == f.length());
        }
    }
}
