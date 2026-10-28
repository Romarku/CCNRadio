package com.ccnradio.client;

import com.ccnradio.client.screen.TapePlayerScreen;
import com.ccnradio.client.screen.TapeRecorderScreen;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.c2s.C2SRequestMediaList;
import com.ccnradio.network.packet.s2c.S2CBurnResult;
import com.ccnradio.network.packet.s2c.S2CDeleteResult;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadChunk;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadComplete;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadFailed;
import com.ccnradio.network.packet.s2c.S2CMediaDownloadStart;
import com.ccnradio.network.packet.s2c.S2CMediaList;
import com.ccnradio.network.packet.s2c.S2CPlaybackStart;
import com.ccnradio.network.packet.s2c.S2CPlaybackStop;
import com.ccnradio.network.packet.s2c.S2CUploadResult;
import com.ccnradio.network.packet.s2c.S2CUploadStart;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Single entry point for every client-bound packet. Keeping it separate from the
 * channel class ensures no client-only classes load on a dedicated server.
 */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handle(int type, Object message) {
        switch (type) {
            case ModNetwork.MEDIA_LIST -> handleMediaList((S2CMediaList) message);
            case ModNetwork.UPLOAD_START -> handleUploadStart((S2CUploadStart) message);
            case ModNetwork.UPLOAD_RESULT -> handleUploadResult((S2CUploadResult) message);
            case ModNetwork.DELETE_RESULT -> handleDeleteResult((S2CDeleteResult) message);
            case ModNetwork.BURN_RESULT -> handleBurnResult((S2CBurnResult) message);
            case ModNetwork.DOWNLOAD_START -> handleDownloadStart((S2CMediaDownloadStart) message);
            case ModNetwork.DOWNLOAD_CHUNK -> handleDownloadChunk((S2CMediaDownloadChunk) message);
            case ModNetwork.DOWNLOAD_COMPLETE -> handleDownloadComplete((S2CMediaDownloadComplete) message);
            case ModNetwork.DOWNLOAD_FAILED -> handleDownloadFailed((S2CMediaDownloadFailed) message);
            case ModNetwork.PLAYBACK_START -> handlePlaybackStart((S2CPlaybackStart) message);
            case ModNetwork.PLAYBACK_STOP -> handlePlaybackStop((S2CPlaybackStop) message);
            default -> {
            }
        }
    }

    public static void handleMediaList(S2CMediaList message) {
        ClientMediaList.set(message.entries(), message.canUpload(), message.denyKey());
    }

    public static void handleUploadStart(S2CUploadStart message) {
        ClientUploadManager.onStartReply(message);
    }

    public static void handleUploadResult(S2CUploadResult message) {
        ClientUploadManager.onResult(message);
        displayStatus(message.messageKey());
    }

    public static void handleDeleteResult(S2CDeleteResult message) {
        displayStatus(message.messageKey());
        if (message.success()) {
            ModNetwork.sendToServer(new C2SRequestMediaList());
        }
    }

    public static void handleBurnResult(S2CBurnResult message) {
        displayStatus(message.messageKey());
    }

    public static void handleDownloadStart(S2CMediaDownloadStart message) {
        ClientAudioDownloads.handleStart(message);
    }

    public static void handleDownloadChunk(S2CMediaDownloadChunk message) {
        ClientAudioDownloads.handleChunk(message);
    }

    public static void handleDownloadComplete(S2CMediaDownloadComplete message) {
        ClientAudioDownloads.handleComplete(message);
    }

    public static void handleDownloadFailed(S2CMediaDownloadFailed message) {
        ClientAudioDownloads.handleFailed(message);
    }

    public static void handlePlaybackStart(S2CPlaybackStart message) {
        ClientPlaybackController.handleStart(message);
    }

    public static void handlePlaybackStop(S2CPlaybackStop message) {
        ClientPlaybackController.handleStop(message);
    }

    public static void displayStatus(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TapeRecorderScreen screen) {
            screen.setStatus(key);
            return;
        }
        if (minecraft.screen instanceof TapePlayerScreen screen) {
            screen.setStatus(key);
            return;
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
