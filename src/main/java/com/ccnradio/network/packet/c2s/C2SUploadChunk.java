package com.ccnradio.network.packet.c2s;

import com.ccnradio.media.UploadManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record C2SUploadChunk(UUID sessionId, int index, byte[] data) {
    public static void encode(C2SUploadChunk message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.sessionId);
        buffer.writeInt(message.index);
        buffer.writeByteArray(message.data);
    }

    public static C2SUploadChunk decode(FriendlyByteBuf buffer) {
        return new C2SUploadChunk(buffer.readUUID(), buffer.readInt(), buffer.readByteArray(262144));
    }

    public static void handle(C2SUploadChunk message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            UploadManager.handleChunk(player, message);
        }
    }
}
