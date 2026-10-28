package com.ccnradio.media;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.Config;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.c2s.C2SUploadChunk;
import com.ccnradio.network.packet.c2s.C2SUploadFinish;
import com.ccnradio.network.packet.c2s.C2SUploadStart;
import com.ccnradio.network.packet.s2c.S2CUploadResult;
import com.ccnradio.network.packet.s2c.S2CUploadStart;
import com.ccnradio.server.MediaListService;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunked upload receiver. Clients send a start packet, then sequential 64 KiB chunks,
 * then a finish packet. The server never trusts the client's filename or duration: it
 * re-inspects the completed file before moving it into the media library.
 */
public final class UploadManager {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000L;

    private UploadManager() {
    }

    public static synchronized void handleStart(ServerPlayer player, C2SUploadStart message) {
        if (!UploadSettings.canUpload(player)) {
            ModNetwork.sendToPlayer(player, new S2CUploadStart(false, "msg.ccnradio.no_permission", null));
            return;
        }
        if (MediaStorage.getTmpDir() == null) {
            ModNetwork.sendToPlayer(player, new S2CUploadStart(false, "msg.ccnradio.upload_failed", null));
            return;
        }
        if (message.size() <= 0 || message.size() > Config.maxUploadBytes) {
            ModNetwork.sendToPlayer(player, new S2CUploadStart(false, "msg.ccnradio.file_too_large", null));
            return;
        }
        if (message.durationSeconds() <= 0 || message.durationSeconds() > Config.maxDurationSeconds) {
            ModNetwork.sendToPlayer(player, new S2CUploadStart(false, "msg.ccnradio.duration_too_long", null));
            return;
        }
        if (!AudioMetadata.isSupportedFileName(message.originalName())) {
            ModNetwork.sendToPlayer(player, new S2CUploadStart(false, "msg.ccnradio.invalid_format", null));
            return;
        }

        cancel(player.getUUID());

        UUID sessionId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Path tempFile = MediaStorage.getTmpDir().resolve(sessionId + ".part");
        try {
            OutputStream output = Files.newOutputStream(tempFile);
            Session session = new Session(sessionId, mediaId, player.getUUID(), tempFile, output,
                    sanitizeName(message.displayName(), message.originalName()), message.originalName(),
                    message.size(), message.durationSeconds(), System.currentTimeMillis());
            SESSIONS.put(sessionId, session);
            ModNetwork.sendToPlayer(player, new S2CUploadStart(true, "", sessionId));
        } catch (IOException e) {
            CCNRadioMod.LOGGER.error("Unable to create CCNR upload session for {}", player.getGameProfile().getName(), e);
            ModNetwork.sendToPlayer(player, new S2CUploadStart(false, "msg.ccnradio.upload_failed", null));
        }
    }

    public static synchronized void handleChunk(ServerPlayer player, C2SUploadChunk message) {
        Session session = SESSIONS.get(message.sessionId());
        if (session == null || !session.playerId.equals(player.getUUID())) {
            return;
        }
        if (message.index() != session.nextIndex) {
            fail(player, session, "msg.ccnradio.upload_failed");
            return;
        }
        byte[] data = message.data();
        if (data == null || data.length == 0 || data.length > 262144
                || session.received + data.length > session.expectedSize) {
            fail(player, session, "msg.ccnradio.upload_failed");
            return;
        }
        try {
            session.output.write(data);
            session.received += data.length;
            session.nextIndex++;
        } catch (IOException e) {
            CCNRadioMod.LOGGER.error("CCNR upload write failed for {}", player.getGameProfile().getName(), e);
            fail(player, session, "msg.ccnradio.upload_failed");
        }
    }

    public static synchronized void handleFinish(ServerPlayer player, C2SUploadFinish message) {
        Session session = SESSIONS.get(message.sessionId());
        if (session == null || !session.playerId.equals(player.getUUID())) {
            return;
        }
        closeQuietly(session.output);
        if (session.received != session.expectedSize) {
            fail(player, session, "msg.ccnradio.upload_failed");
            return;
        }

        try {
            AudioMetadata.AudioInfo info = AudioMetadata.inspect(session.tempFile).orElse(null);
            if (info == null) {
                fail(player, session, "msg.ccnradio.invalid_audio");
                return;
            }
            if (info.durationSeconds() > Config.maxDurationSeconds) {
                fail(player, session, "msg.ccnradio.duration_too_long");
                return;
            }
            if (info.size() > Config.maxUploadBytes) {
                fail(player, session, "msg.ccnradio.file_too_large");
                return;
            }

            MediaEntry entry = new MediaEntry(session.mediaId, session.playerId,
                    player.getGameProfile().getName(), session.displayName, info.durationSeconds(),
                    info.size(), info.format(), System.currentTimeMillis());
            MediaStorage.add(entry, session.tempFile);
            SESSIONS.remove(session.sessionId);
            ModNetwork.sendToPlayer(player, new S2CUploadResult(true, "msg.ccnradio.upload_ok", entry.getId()));
            MediaListService.broadcastToOpenRecorders(player.server);
        } catch (Exception e) {
            CCNRadioMod.LOGGER.error("CCNR upload finalization failed for {}", player.getGameProfile().getName(), e);
            fail(player, session, "msg.ccnradio.upload_failed");
        }
    }

    public static synchronized void cancel(UUID playerId) {
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (session.playerId.equals(playerId)) {
                closeQuietly(session.output);
                deleteQuietly(session.tempFile);
                iterator.remove();
            }
        }
    }

    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (now - session.startedAt > SESSION_TIMEOUT_MS) {
                closeQuietly(session.output);
                deleteQuietly(session.tempFile);
                iterator.remove();
            }
        }
    }

    public static synchronized void shutdown() {
        for (Session session : SESSIONS.values()) {
            closeQuietly(session.output);
            deleteQuietly(session.tempFile);
        }
        SESSIONS.clear();
    }

    private static void fail(ServerPlayer player, Session session, String key) {
        closeQuietly(session.output);
        deleteQuietly(session.tempFile);
        SESSIONS.remove(session.sessionId);
        ModNetwork.sendToPlayer(player, new S2CUploadResult(false, key, null));
    }

    private static void closeQuietly(OutputStream output) {
        try {
            output.close();
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String sanitizeName(String requested, String originalName) {
        String name = requested == null ? "" : requested;
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = AudioMetadata.stripExtension(originalName == null ? "Audio" : originalName).trim();
        }
        if (name.isEmpty()) {
            name = "Audio";
        }
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    private static final class Session {
        private final UUID sessionId;
        private final UUID mediaId;
        private final UUID playerId;
        private final Path tempFile;
        private final OutputStream output;
        private final String displayName;
        private final String originalName;
        private final long expectedSize;
        @SuppressWarnings("unused")
        private final int declaredDurationSeconds;
        private final long startedAt;
        private long received;
        private int nextIndex;

        private Session(UUID sessionId, UUID mediaId, UUID playerId, Path tempFile, OutputStream output,
                        String displayName, String originalName, long expectedSize, int declaredDurationSeconds,
                        long startedAt) {
            this.sessionId = sessionId;
            this.mediaId = mediaId;
            this.playerId = playerId;
            this.tempFile = tempFile;
            this.output = output;
            this.displayName = displayName;
            this.originalName = originalName;
            this.expectedSize = expectedSize;
            this.declaredDurationSeconds = declaredDurationSeconds;
            this.startedAt = startedAt;
        }
    }
}
