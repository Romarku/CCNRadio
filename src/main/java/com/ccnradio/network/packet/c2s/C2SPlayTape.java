package com.ccnradio.network.packet.c2s;

import com.ccnradio.blockentity.TapePlayerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SPlayTape(BlockPos pos) {
    public static void encode(C2SPlayTape message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
    }

    public static C2SPlayTape decode(FriendlyByteBuf buffer) {
        return new C2SPlayTape(buffer.readBlockPos());
    }

    public static void handle(C2SPlayTape message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        if (player.distanceToSqr(message.pos.getX() + 0.5D, message.pos.getY() + 0.5D,
                message.pos.getZ() + 0.5D) > 64.0D) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(message.pos);
        if (blockEntity instanceof TapePlayerBlockEntity tapePlayer) {
            tapePlayer.startPlayback(player);
        }
    }
}
