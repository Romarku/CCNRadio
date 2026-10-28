package com.ccnradio.network.packet.c2s;

import com.ccnradio.menu.TapeRecorderMenu;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.s2c.S2CBurnResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record C2SBurnTape(UUID mediaId) {
    public static void encode(C2SBurnTape message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.mediaId);
    }

    public static C2SBurnTape decode(FriendlyByteBuf buffer) {
        return new C2SBurnTape(buffer.readUUID());
    }

    public static void handle(C2SBurnTape message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        String resultKey = TapeRecorderMenu.tryBurn(player, message.mediaId);
        boolean success = "msg.ccnradio.burn_ok".equals(resultKey);
        ModNetwork.sendToPlayer(player, new S2CBurnResult(success, resultKey));
    }
}
