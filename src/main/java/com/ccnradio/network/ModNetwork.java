package com.ccnradio.network;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.network.packet.c2s.C2SBurnTape;
import com.ccnradio.network.packet.c2s.C2SDeleteMedia;
import com.ccnradio.network.packet.c2s.C2SPlayTape;
import com.ccnradio.network.packet.c2s.C2SRequestMediaDownload;
import com.ccnradio.network.packet.c2s.C2SRequestMediaList;
import com.ccnradio.network.packet.c2s.C2SSetPlaybackRange;
import com.ccnradio.network.packet.c2s.C2SStopTape;
import com.ccnradio.network.packet.c2s.C2SUploadChunk;
import com.ccnradio.network.packet.c2s.C2SUploadFinish;
import com.ccnradio.network.packet.c2s.C2SUploadStart;
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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Single network channel for uploads, downloads and playback synchronization.
 * Uploads and downloads are chunked because a custom payload cannot carry a 20 MB file.
 */
public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CCNRadioMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static boolean registered;
    private static int nextId;

    public static final int MEDIA_LIST = 0;
    public static final int UPLOAD_START = 1;
    public static final int UPLOAD_RESULT = 2;
    public static final int DELETE_RESULT = 3;
    public static final int BURN_RESULT = 4;
    public static final int DOWNLOAD_START = 5;
    public static final int DOWNLOAD_CHUNK = 6;
    public static final int DOWNLOAD_COMPLETE = 7;
    public static final int DOWNLOAD_FAILED = 8;
    public static final int PLAYBACK_START = 9;
    public static final int PLAYBACK_STOP = 10;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        serverMessage(C2SUploadStart.class, nextId++, C2SUploadStart::encode, C2SUploadStart::decode,
                C2SUploadStart::handle);
        serverMessage(C2SUploadChunk.class, nextId++, C2SUploadChunk::encode, C2SUploadChunk::decode,
                C2SUploadChunk::handle);
        serverMessage(C2SUploadFinish.class, nextId++, C2SUploadFinish::encode, C2SUploadFinish::decode,
                C2SUploadFinish::handle);
        serverMessage(C2SRequestMediaList.class, nextId++, C2SRequestMediaList::encode, C2SRequestMediaList::decode,
                C2SRequestMediaList::handle);
        serverMessage(C2SDeleteMedia.class, nextId++, C2SDeleteMedia::encode, C2SDeleteMedia::decode,
                C2SDeleteMedia::handle);
        serverMessage(C2SBurnTape.class, nextId++, C2SBurnTape::encode, C2SBurnTape::decode,
                C2SBurnTape::handle);
        serverMessage(C2SRequestMediaDownload.class, nextId++, C2SRequestMediaDownload::encode,
                C2SRequestMediaDownload::decode, C2SRequestMediaDownload::handle);
        serverMessage(C2SPlayTape.class, nextId++, C2SPlayTape::encode, C2SPlayTape::decode,
                C2SPlayTape::handle);
        serverMessage(C2SStopTape.class, nextId++, C2SStopTape::encode, C2SStopTape::decode,
                C2SStopTape::handle);
        serverMessage(C2SSetPlaybackRange.class, nextId++, C2SSetPlaybackRange::encode, C2SSetPlaybackRange::decode,
                C2SSetPlaybackRange::handle);

        clientMessage(S2CMediaList.class, nextId++, S2CMediaList::encode, S2CMediaList::decode,
                MEDIA_LIST);
        clientMessage(S2CUploadStart.class, nextId++, S2CUploadStart::encode, S2CUploadStart::decode,
                UPLOAD_START);
        clientMessage(S2CUploadResult.class, nextId++, S2CUploadResult::encode, S2CUploadResult::decode,
                UPLOAD_RESULT);
        clientMessage(S2CDeleteResult.class, nextId++, S2CDeleteResult::encode, S2CDeleteResult::decode,
                DELETE_RESULT);
        clientMessage(S2CBurnResult.class, nextId++, S2CBurnResult::encode, S2CBurnResult::decode,
                BURN_RESULT);
        clientMessage(S2CMediaDownloadStart.class, nextId++, S2CMediaDownloadStart::encode,
                S2CMediaDownloadStart::decode, DOWNLOAD_START);
        clientMessage(S2CMediaDownloadChunk.class, nextId++, S2CMediaDownloadChunk::encode,
                S2CMediaDownloadChunk::decode, DOWNLOAD_CHUNK);
        clientMessage(S2CMediaDownloadComplete.class, nextId++, S2CMediaDownloadComplete::encode,
                S2CMediaDownloadComplete::decode, DOWNLOAD_COMPLETE);
        clientMessage(S2CMediaDownloadFailed.class, nextId++, S2CMediaDownloadFailed::encode,
                S2CMediaDownloadFailed::decode, DOWNLOAD_FAILED);
        clientMessage(S2CPlaybackStart.class, nextId++, S2CPlaybackStart::encode, S2CPlaybackStart::decode,
                PLAYBACK_START);
        clientMessage(S2CPlaybackStop.class, nextId++, S2CPlaybackStop::encode, S2CPlaybackStop::decode,
                PLAYBACK_STOP);
    }

    private static <T> void serverMessage(Class<T> type, int id,
                                          BiConsumer<T, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, T> decoder,
                                          BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((message, context) -> {
                    handler.accept(message, context);
                    context.get().setPacketHandled(true);
                })
                .add();
    }

    private static <T> void clientMessage(Class<T> type, int id,
                                          BiConsumer<T, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, T> decoder,
                                          int clientType) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((message, context) -> {
                    ClientPacketDispatcher.dispatch(clientType, message);
                    context.get().setPacketHandled(true);
                })
                .add();
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
