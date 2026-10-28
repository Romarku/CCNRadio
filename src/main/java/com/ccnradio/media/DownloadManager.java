package com.ccnradio.media;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadChunk;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadComplete;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadFailed;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadStart;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttled server-to-client media transfer. Two chunks per player per tick keeps a
 * 20 MB tape at roughly eight seconds without flooding the connection.
 */
public final class DownloadManager {
    private static final int CHUNK_SIZE = 65536;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int MAX_PENDING = 4;

    private static final Map<UUID, Deque<DownloadTask>> QUEUES = new ConcurrentHashMap<>();

    private DownloadManager() {
    }

    public static synchronized void enqueue(ServerPlayer player, UUID mediaId) {
        MediaEntry entry = MediaStorage.get(mediaId);
        if (entry == null) {
            ModNetwork.sendToPlayer(player, new S2CMediaDownloadFailed(mediaId, "msg.ccnradio.media_missing"));
            return;
        }
        Deque<DownloadTask> queue = QUEUES.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
        for (DownloadTask task : queue) {
            if (task.mediaId.equals(mediaId)) {
                return;
            }
        }
        if (queue.size() >= MAX_PENDING) {
            ModNetwork.sendToPlayer(player, new S2CMediaDownloadFailed(mediaId, "msg.ccnradio.audio_download_failed"));
            return;
        }
        Path path = MediaStorage.pathFor(entry);
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            ModNetwork.sendToPlayer(player, new S2CMediaDownloadFailed(mediaId, "msg.ccnradio.media_missing"));
            return;
        }
        if (size <= 0 || size > Integer.MAX_VALUE) {
            ModNetwork.sendToPlayer(player, new S2CMediaDownloadFailed(mediaId, "msg.ccnradio.media_missing"));
            return;
        }
        queue.addLast(new DownloadTask(player, entry, path, size));
    }

    public static synchronized void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Deque<DownloadTask>>> iterator = QUEUES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Deque<DownloadTask>> entry = iterator.next();
            Deque<DownloadTask> queue = entry.getValue();
            if (queue.isEmpty()) {
                iterator.remove();
                continue;
            }
            DownloadTask task = queue.peekFirst();
            if (task.player.hasDisconnected()) {
                task.close();
                queue.clear();
                iterator.remove();
                continue;
            }
            if (!task.started) {
                task.start();
            }
            if (task.started) {
                for (int i = 0; i < CHUNKS_PER_TICK && task.started; i++) {
                    task.pump();
                }
            }
            if (task.finished || task.failed) {
                task.close();
                queue.removeFirst();
            }
        }
    }

    public static synchronized void cancel(UUID playerId) {
        Deque<DownloadTask> queue = QUEUES.remove(playerId);
        if (queue != null) {
            for (DownloadTask task : queue) {
                task.close();
            }
        }
    }

    public static synchronized void shutdown() {
        for (Deque<DownloadTask> queue : QUEUES.values()) {
            for (DownloadTask task : queue) {
                task.close();
            }
        }
        QUEUES.clear();
    }

    private static final class DownloadTask {
        private final ServerPlayer player;
        private final UUID mediaId;
        private final String format;
        private final Path path;
        private final long totalBytes;
        private final int totalChunks;
        private InputStream input;
        private int nextChunk;
        private boolean started;
        private boolean finished;
        private boolean failed;

        private DownloadTask(ServerPlayer player, MediaEntry entry, Path path, long totalBytes) {
            this.player = player;
            this.mediaId = entry.getId();
            this.format = entry.getFormat();
            this.path = path;
            this.totalBytes = totalBytes;
            this.totalChunks = (int) ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE);
        }

        private void start() {
            try {
                input = Files.newInputStream(path);
                started = true;
                ModNetwork.sendToPlayer(player, new S2CMediaDownloadStart(mediaId, format,
                        (int) totalBytes, CHUNK_SIZE, totalChunks));
            } catch (IOException e) {
                CCNRadioMod.LOGGER.error("Unable to open CCNR media for download: {}", path, e);
                failed = true;
                ModNetwork.sendToPlayer(player, new S2CMediaDownloadFailed(mediaId, "msg.ccnradio.audio_download_failed"));
            }
        }

        private void pump() {
            if (finished || failed) {
                return;
            }
            try {
                byte[] buffer = new byte[(int) Math.min(CHUNK_SIZE, Math.max(1, totalBytes - (long) nextChunk * CHUNK_SIZE))];
                int read = 0;
                while (read < buffer.length) {
                    int result = input.read(buffer, read, buffer.length - read);
                    if (result < 0) {
                        break;
                    }
                    read += result;
                }
                if (read <= 0) {
                    finished = true;
                    ModNetwork.sendToPlayer(player, new S2CMediaDownloadComplete(mediaId));
                    return;
                }
                byte[] data = read == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, read);
                ModNetwork.sendToPlayer(player, new S2CMediaDownloadChunk(mediaId, nextChunk, data));
                nextChunk++;
                if (nextChunk >= totalChunks) {
                    finished = true;
                    ModNetwork.sendToPlayer(player, new S2CMediaDownloadComplete(mediaId));
                }
            } catch (IOException e) {
                CCNRadioMod.LOGGER.error("Unable to read CCNR media for download: {}", path, e);
                failed = true;
                ModNetwork.sendToPlayer(player, new S2CMediaDownloadFailed(mediaId, "msg.ccnradio.audio_download_failed"));
            }
        }

        private void close() {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
                input = null;
            }
            started = false;
        }
    }
}
