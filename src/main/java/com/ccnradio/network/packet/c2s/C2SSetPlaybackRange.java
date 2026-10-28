package com.ccnradio.network.packet.c2s;

import com.ccnradio.blockentity.TapePlayerBlockEntity;
import com.ccnradio.menu.TapePlayerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SSetPlaybackRange(BlockPos pos, int range) {
    public static void encode(C2SSetPlaybackRange message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeInt(message.range);
    }

    public static C2SSetPlaybackRange decode(FriendlyByteBuf buffer) {
        return new C2SSetPlaybackRange(buffer.readBlockPos(), buffer.readInt());
    }

    public static void handle(C2SSetPlaybackRange message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        if (!(player.containerMenu instanceof TapePlayerMenu menu) || !menu.getPos().equals(message.pos)) {
            return;
        }
        TapePlayerBlockEntity blockEntity = menu.getBlockEntity();
        if (blockEntity != null) {
            blockEntity.setRange(message.range);
        }
    }
}
