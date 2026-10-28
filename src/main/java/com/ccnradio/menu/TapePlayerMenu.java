package com.ccnradio.menu;

import com.ccnradio.blockentity.TapePlayerBlockEntity;
import com.ccnradio.registry.ModItems;
import com.ccnradio.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class TapePlayerMenu extends AbstractContainerMenu {
    public static final int TAPE_SLOT = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = 37;

    private final BlockPos pos;
    private final Player menuPlayer;
    private final TapePlayerBlockEntity blockEntity;
    private final SimpleContainerData data;

    public TapePlayerMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.TAPE_PLAYER.get(), id);
        this.pos = pos;
        this.menuPlayer = inventory.player;
        BlockEntity be = inventory.player.level().getBlockEntity(pos);
        this.blockEntity = be instanceof TapePlayerBlockEntity tapePlayer ? tapePlayer : null;

        Container container = blockEntity != null ? blockEntity : new SimpleContainer(1);
        addSlot(new Slot(container, TAPE_SLOT, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BURNED_TAPE.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 154 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 212));
        }

        this.data = new SimpleContainerData(3);
        if (blockEntity != null) {
            data.set(0, blockEntity.getRange());
            data.set(1, blockEntity.isPlaying() ? 1 : 0);
            data.set(2, blockEntity.getPlayingDurationSeconds());
        }
        addDataSlots(data);
    }

    @Override
    public void broadcastChanges() {
        if (!menuPlayer.level().isClientSide && blockEntity != null) {
            data.set(0, blockEntity.getRange());
            data.set(1, blockEntity.isPlaying() ? 1 : 0);
            data.set(2, blockEntity.getPlayingDurationSeconds());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && (blockEntity == null || !blockEntity.isRemoved());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == TAPE_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BURNED_TAPE.get())) {
            if (!moveItemStackTo(stack, TAPE_SLOT, TAPE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getRange() {
        return data.get(0);
    }

    public boolean isPlaying() {
        return data.get(1) != 0;
    }

    public int getDurationSeconds() {
        return data.get(2);
    }

    public TapePlayerBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
