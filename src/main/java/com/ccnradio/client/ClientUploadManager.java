package com.ccnradio.client;

import com.ccnradio.Config;
import com.ccnradio.media.AudioMetadata;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.c2s.C2SRequestMediaList;
import com.ccnradio.network.packet.c2s.C2SUploadChunk;
import com.ccnradio.network.packet.c2s.C2SUploadFinish;
import com.ccnradio.network.packet.c2s.C2SUploadStart;
import com.ccnradio.network.packet.s2c.S2CUploadResult;
import com.ccnradio.network.packet.s2c.S2CUploadStart;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side upload state machine. The file is read on the IO pool, then sent in
 * 64 KiB chunks at two chunks per client tick.
 */
public final class ClientUploadManager {
    private static final int CHUNK_SIZE = 65536;
    private static final int CHUNKS_PER_TICK = 2;
    private static final long START_TIMEOUT_MS = 30_000L;

    private enum State {
        IDLE,
        READING,
        WAITING_START,
        UPLOADING,
        WAITING_FINISH
    }

    private static State state = State.IDLE;
    private static byte[] data;
    private static String originalName = "";
    private static String displayName = "";
    private static String format = "ogg";
    private static int durationSeconds;
    private static int nextChunk;
    private static int totalChunks;
    private static UUID sessionId;
    private static long timeoutAt;
    private static String statusKey = "";
    private static int progress;

    private ClientUploadManager() {
    }

    public static boolean isBusy() {
        return state != State.IDLE;
    }

    public static int progress() {
        return progress;
    }

    public static String statusKey() {
        return statusKey;
    }

    public static void start(Path path, String requestedName) {
        if (state != State.IDLE) {
            return;
        }
        state = State.READING;
        statusKey = "gui.ccnradio.reading";
        progress = 0;
        CompletableFuture.supplyAsync(() -> prepare(path, requestedName), Util.ioPool())
                .whenComplete((prepared, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (state != State.READING) {
                        return;
                    }
                    if (throwable != null || prepared == null) {
                        reset();
                        statusKey = "msg.ccnradio.upload_failed";
                        return;
                    }
                    if (prepared.errorKey() != null) {
                        reset();
                        statusKey = prepared.errorKey();
                        return;
                    }
                    data = prepared.data();
                    originalName = prepared.originalName();
                    displayName = prepared.displayName();
                    format = prepared.format();
                    durationSeconds = prepared.durationSeconds();
                    totalChunks = (data.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
                    nextChunk = 0;
                    state = State.WAITING_START;
                    timeoutAt = System.currentTimeMillis() + START_TIMEOUT_MS;
                    statusKey = "gui.ccnradio.waiting_server";
                    ModNetwork.sendToServer(new C2SUploadStart(originalName, displayName, format,
                            data.length, durationSeconds));
                }));
    }

    public static void tick() {
        if (state == State.IDLE || state == State.READING) {
            return;
        }
        if ((state == State.WAITING_START || state == State.WAITING_FINISH)
                && System.currentTimeMillis() > timeoutAt) {
            reset();
            statusKey = "msg.ccnradio.upload_failed";
            return;
        }
        if (state != State.UPLOADING) {
            return;
        }
        for (int i = 0; i < CHUNKS_PER_TICK && nextChunk < totalChunks; i++) {
            int start = nextChunk * CHUNK_SIZE;
            int end = Math.min(data.length, start + CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(data, start, end);
            ModNetwork.sendToServer(new C2SUploadChunk(sessionId, nextChunk, chunk));
            nextChunk++;
            progress = (int) ((long) nextChunk * 100L / totalChunks);
        }
        if (nextChunk >= totalChunks) {
            state = State.WAITING_FINISH;
            timeoutAt = System.currentTimeMillis() + 60_000L;
            statusKey = "gui.ccnradio.finalizing";
            ModNetwork.sendToServer(new C2SUploadFinish(sessionId));
        }
    }

    public static void onStartReply(S2CUploadStart message) {
        if (state != State.WAITING_START) {
            return;
        }
        if (!message.accepted() || message.sessionId() == null) {
            reset();
            statusKey = message.messageKey().isEmpty() ? "msg.ccnradio.upload_failed" : message.messageKey();
            return;
        }
        sessionId = message.sessionId();
        state = State.UPLOADING;
        statusKey = "gui.ccnradio.uploading";
        nextChunk = 0;
        progress = 0;
    }

    public static void onResult(S2CUploadResult message) {
        reset();
        statusKey = message.messageKey().isEmpty()
                ? (message.success() ? "msg.ccnradio.upload_ok" : "msg.ccnradio.upload_failed")
                : message.messageKey();
        ModNetwork.sendToServer(new C2SRequestMediaList());
    }

    public static void reset() {
        state = State.IDLE;
        data = null;
        originalName = "";
        displayName = "";
        format = "ogg";
        durationSeconds = 0;
        nextChunk = 0;
        totalChunks = 0;
        sessionId = null;
        timeoutAt = 0L;
        progress = 0;
    }

    public static void onLogout() {
        reset();
        statusKey = "";
    }

    private static PreparedFile prepare(Path path, String requestedName) {
        try {
            if (!Files.isRegularFile(path)) {
                return PreparedFile.error("msg.ccnradio.invalid_audio");
            }
            String fileName = path.getFileName().toString();
            if (fileName.length() > 128) {
                return PreparedFile.error("msg.ccnradio.upload_failed");
            }
            if (!AudioMetadata.isSupportedFileName(fileName)) {
                return PreparedFile.error("msg.ccnradio.invalid_format");
            }
            long size = Files.size(path);
            if (size <= 0 || size > Config.maxUploadBytes) {
                return PreparedFile.error("msg.ccnradio.file_too_large");
            }
            byte[] bytes = Files.readAllBytes(path);
            AudioMetadata.AudioInfo info = AudioMetadata.inspect(bytes, fileName).orElse(null);
            if (info == null) {
                return PreparedFile.error("msg.ccnradio.invalid_audio");
            }
            if (info.durationSeconds() <= 0 || info.durationSeconds() > Config.maxDurationSeconds) {
                return PreparedFile.error("msg.ccnradio.duration_too_long");
            }
            String name = sanitize(requestedName);
            if (name.isEmpty()) {
                name = AudioMetadata.stripExtension(fileName).trim();
            }
            if (name.isEmpty()) {
                name = "Audio";
            }
            if (name.length() > 64) {
                name = name.substring(0, 64);
            }
            return new PreparedFile(bytes, fileName, name, info.format(), info.durationSeconds(), null);
        } catch (IOException e) {
            return PreparedFile.error("msg.ccnradio.upload_failed");
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\p{Cntrl}]", "").trim();
    }

    private record PreparedFile(byte[] data, String originalName, String displayName, String format,
                                int durationSeconds, String errorKey) {
        private static PreparedFile error(String key) {
            return new PreparedFile(null, "", "", "ogg", 0, key);
        }
    }
}
