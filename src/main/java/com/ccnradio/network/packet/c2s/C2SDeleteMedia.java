package com.ccnradio.network.packet.c2s;

import com.ccnradio.media.MediaStorage;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.s2c.S2CDeleteResult;
import com.ccnradio.server.MediaListService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record C2SDeleteMedia(UUID mediaId) {
    public static void encode(C2SDeleteMedia message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.mediaId);
    }

    public static C2SDeleteMedia decode(FriendlyByteBuf buffer) {
        return new C2SDeleteMedia(buffer.readUUID());
    }

    public static void handle(C2SDeleteMedia message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        boolean deleted = MediaStorage.delete(message.mediaId, player.getUUID(), player.hasPermissions(2));
        ModNetwork.sendToPlayer(player, new S2CDeleteResult(deleted,
                deleted ? "msg.ccnradio.delete_ok" : "msg.ccnradio.delete_failed"));
        if (deleted) {
            MediaListService.broadcastToOpenRecorders(player.server);
        }
    }
}
