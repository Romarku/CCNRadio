package com.ccnradio.network.packet.c2s;

import com.ccnradio.media.UploadManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record C2SUploadFinish(UUID sessionId) {
    public static void encode(C2SUploadFinish message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.sessionId);
    }

    public static C2SUploadFinish decode(FriendlyByteBuf buffer) {
        return new C2SUploadFinish(buffer.readUUID());
    }

    public static void handle(C2SUploadFinish message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            UploadManager.handleFinish(player, message);
        }
    }
}
