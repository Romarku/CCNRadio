package com.ccnradio.network.packet.c2s;

import com.ccnradio.server.MediaListService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SRequestMediaList() {
    public static void encode(C2SRequestMediaList message, FriendlyByteBuf buffer) {
    }

    public static C2SRequestMediaList decode(FriendlyByteBuf buffer) {
        return new C2SRequestMediaList();
    }

    public static void handle(C2SRequestMediaList message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            MediaListService.send(player);
        }
    }
}
