package com.ccnradio.client;

import com.ccnradio.Config;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadChunk;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadComplete;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadFailed;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadStart;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives chunked media downloads and writes them into the client cache. Packets
 * arrive on the client main thread; each chunk is small (64 KiB).
 */
public final class ClientAudioDownloads {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private ClientAudioDownloads() {
    }

    public static boolean isDownloading(UUID mediaId) {
        return SESSIONS.containsKey(mediaId);
    }

    public static void handleStart(S2CMediaDownloadStart message) {
        if (message.totalBytes() <= 0 || message.totalBytes() > Config.maxUploadBytes
                || message.chunkSize() <= 0 || message.chunkSize() > 262144
                || message.totalChunks() <= 0) {
            fail(message.mediaId(), "msg.ccnradio.audio_download_failed");
            return;
        }
        close(message.mediaId(), true);
        Path part = AudioCache.partFor(message.mediaId());
        try {
            Files.createDirectories(part.getParent());
            OutputStream output = new BufferedOutputStream(Files.newOutputStream(part));
            SESSIONS.put(message.mediaId(), new Session(part, output, message.totalBytes(),
                    message.chunkSize(), message.totalChunks(), AudioCache.normalizeFormat(message.format())));
        } catch (IOException e) {
            fail(message.mediaId(), "msg.ccnradio.audio_download_failed");
        }
    }

    public static void handleChunk(S2CMediaDownloadChunk message) {
        Session session = SESSIONS.get(message.mediaId());
        if (session == null) {
            return;
        }
        if (message.index() != session.nextIndex || message.data() == null
                || message.data().length == 0
                || session.received + message.data().length > session.totalBytes) {
            fail(message.mediaId(), "msg.ccnradio.audio_download_failed");
            return;
        }
        try {
            session.output.write(message.data());
            session.received += message.data().length;
            session.nextIndex++;
        } catch (IOException e) {
            fail(message.mediaId(), "msg.ccnradio.audio_download_failed");
        }
    }

    public static void handleComplete(S2CMediaDownloadComplete message) {
        Session session = SESSIONS.remove(message.mediaId());
        if (session == null) {
            return;
        }
        try {
            session.output.close();
            if (session.received != session.totalBytes || session.nextIndex != session.totalChunks) {
                Files.deleteIfExists(session.part);
                failWithoutSession(message.mediaId(), "msg.ccnradio.audio_download_failed");
                return;
            }
            AudioCache.finishDownload(message.mediaId(), session.format, session.part);
            ClientPlaybackController.onMediaReady(message.mediaId());
        } catch (IOException e) {
            try {
                Files.deleteIfExists(session.part);
            } catch (IOException ignored) {
            }
            failWithoutSession(message.mediaId(), "msg.ccnradio.audio_download_failed");
        }
    }

    public static void handleFailed(S2CMediaDownloadFailed message) {
        close(message.mediaId(), true);
        ClientPlaybackController.onMediaFailed(message.mediaId());
        displayStatus(message.messageKey());
    }

    public static void reset() {
        for (UUID mediaId : SESSIONS.keySet()) {
            close(mediaId, true);
        }
        SESSIONS.clear();
    }

    private static void fail(UUID mediaId, String key) {
        close(mediaId, true);
        failWithoutSession(mediaId, key);
    }

    private static void failWithoutSession(UUID mediaId, String key) {
        ClientPlaybackController.onMediaFailed(mediaId);
        displayStatus(key);
    }

    private static void close(UUID mediaId, boolean deletePart) {
        Session session = SESSIONS.remove(mediaId);
        if (session == null) {
            return;
        }
        try {
            session.output.close();
        } catch (IOException ignored) {
        }
        if (deletePart) {
            try {
                Files.deleteIfExists(session.part);
            } catch (IOException ignored) {
            }
        }
    }

    private static void displayStatus(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private static final class Session {
        private final Path part;
        private final OutputStream output;
        private final int totalBytes;
        @SuppressWarnings("unused")
        private final int chunkSize;
        private final int totalChunks;
        private final String format;
        private int received;
        private int nextIndex;

        private Session(Path part, OutputStream output, int totalBytes, int chunkSize,
                        int totalChunks, String format) {
            this.part = part;
            this.output = output;
            this.totalBytes = totalBytes;
            this.chunkSize = chunkSize;
            this.totalChunks = totalChunks;
            this.format = format;
        }
    }
}
