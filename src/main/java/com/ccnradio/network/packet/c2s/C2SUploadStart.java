package com.ccnradio.network.packet.c2s;

import com.ccnradio.media.UploadManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SUploadStart(String originalName, String displayName, String format, int size, int durationSeconds) {
    public static void encode(C2SUploadStart message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.originalName, 128);
        buffer.writeUtf(message.displayName, 64);
        buffer.writeUtf(message.format, 8);
        buffer.writeInt(message.size);
        buffer.writeInt(message.durationSeconds);
    }

    public static C2SUploadStart decode(FriendlyByteBuf buffer) {
        return new C2SUploadStart(buffer.readUtf(128), buffer.readUtf(64), buffer.readUtf(8),
                buffer.readInt(), buffer.readInt());
    }

    public static void handle(C2SUploadStart message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            UploadManager.handleStart(player, message);
        }
    }
}
