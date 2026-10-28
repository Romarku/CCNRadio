package com.ccnradio.client;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.client.audio.AudioPlayback;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.c2s.C2SRequestMediaDownload;
import com.ccnradio.network.packet.s2c.S2CPlaybackStart;
import com.ccnradio.network.packet.s2c.S2CPlaybackStop;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks every tape player the client should currently hear. Server packets carry the
 * authoritative playback start time, so a player who leaves and re-enters the range
 * resumes at the correct position instead of restarting the tape.
 */
public final class ClientPlaybackController {
    private static final Map<BlockPos, PlaybackSession> SESSIONS = new HashMap<>();
    private static ResourceKey<Level> lastDimension;

    private ClientPlaybackController() {
    }

    public static void handleStart(S2CPlaybackStart message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        PlaybackSession session = SESSIONS.get(message.pos());
        if (session == null) {
            session = new PlaybackSession(message.pos());
            SESSIONS.put(message.pos(), session);
        }
        session.update(message);
        session.ensureAudio();
    }

    public static void handleStop(S2CPlaybackStop message) {
        PlaybackSession session = SESSIONS.remove(message.pos());
        if (session != null) {
            session.stopAudio();
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stopAll();
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            stopAll();
        }
        lastDimension = dimension;

        List<BlockPos> toRemove = new ArrayList<>();
        for (PlaybackSession session : SESSIONS.values()) {
            session.tick();
            if (session.finished()) {
                toRemove.add(session.pos);
            }
        }
        for (BlockPos pos : toRemove) {
            PlaybackSession session = SESSIONS.remove(pos);
            if (session != null) {
                session.stopAudio();
            }
        }
    }

    public static void onMediaReady(UUID mediaId) {
        for (PlaybackSession session : SESSIONS.values()) {
            if (mediaId.equals(session.mediaId)) {
                session.downloadRequested = false;
                session.audioFailed = false;
                session.ensureAudio();
            }
        }
    }

    public static void onMediaFailed(UUID mediaId) {
        for (PlaybackSession session : SESSIONS.values()) {
            if (mediaId.equals(session.mediaId)) {
                session.downloadRequested = false;
                session.audioFailed = true;
            }
        }
    }

    public static void stopAll() {
        for (PlaybackSession session : SESSIONS.values()) {
            session.stopAudio();
        }
        SESSIONS.clear();
        lastDimension = null;
    }

    private static final class PlaybackSession {
        private final BlockPos pos;
        private UUID mediaId;
        private String format = "ogg";
        private long startGameTime;
        private int range = 16;
        private int durationSeconds;
        private AudioPlayback.AudioHandle audio;
        private boolean downloadRequested;
        private boolean audioFailed;
        private boolean requestedOnce;

        private PlaybackSession(BlockPos pos) {
            this.pos = pos;
        }

        private void update(S2CPlaybackStart message) {
            boolean changedMedia = !message.mediaId().equals(mediaId);
            boolean changedClock = message.startGameTime() != startGameTime;
            if (changedMedia || changedClock) {
                stopAudio();
                downloadRequested = false;
                audioFailed = false;
                requestedOnce = false;
            }
            this.mediaId = message.mediaId();
            this.format = message.format();
            this.startGameTime = message.startGameTime();
            this.range = message.range();
            this.durationSeconds = message.durationSeconds();
        }

        private void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || mediaId == null) {
                return;
            }
            double distance = Math.sqrt(minecraft.player.distanceToSqr(
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            if (distance > range) {
                stopAudio();
                return;
            }
            if (elapsedSeconds(minecraft) >= durationSeconds + 1.0D) {
                stopAudio();
                return;
            }
            if (audio == null && !audioFailed) {
                ensureAudio();
            }
            if (audio != null) {
                updateGain(minecraft, distance);
            }
        }

        private void ensureAudio() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || mediaId == null || audio != null) {
                return;
            }
            double distance = Math.sqrt(minecraft.player.distanceToSqr(
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            if (distance > range) {
                return;
            }
            double offset = elapsedSeconds(minecraft);
            if (offset < 0.0D || offset >= durationSeconds) {
                return;
            }
            Path file = AudioCache.find(mediaId);
            if (file == null) {
                if (!downloadRequested && !requestedOnce) {
                    downloadRequested = true;
                    requestedOnce = true;
                    ModNetwork.sendToServer(new C2SRequestMediaDownload(mediaId));
                }
                return;
            }
            try {
                audio = AudioPlayback.play(file, format, offset);
                updateGain(minecraft, distance);
            } catch (Exception e) {
                audioFailed = true;
                CCNRadioMod.LOGGER.warn("Unable to play CCNR tape {}", mediaId, e);
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable("msg.ccnradio.playback_failed"), true);
                }
            }
        }

        private double elapsedSeconds(Minecraft minecraft) {
            if (minecraft.level == null) {
                return 0.0D;
            }
            long elapsedTicks = minecraft.level.getGameTime() - startGameTime;
            return Math.max(0L, elapsedTicks) / 20.0D;
        }

        private void updateGain(Minecraft minecraft, double distance) {
            float attenuation = (float) Math.max(0.0D, Math.min(1.0D, 1.0D - distance / Math.max(1, range)));
            float records = minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
            float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
            audio.setVolume(attenuation * records * master);
            float pan = (float) Math.max(-1.0D, Math.min(1.0D,
                    (minecraft.player.getX() - (pos.getX() + 0.5D)) / Math.max(1, range)));
            audio.setPan(pan);
        }

        private boolean finished() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level != null && elapsedSeconds(minecraft) >= durationSeconds + 1.0D;
        }

        private void stopAudio() {
            if (audio != null) {
                audio.stop();
                audio = null;
            }
        }
    }
}
