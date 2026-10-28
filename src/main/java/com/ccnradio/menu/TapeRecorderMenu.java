package com.ccnradio.menu;

import com.ccnradio.blockentity.TapeRecorderBlockEntity;
import com.ccnradio.media.MediaEntry;
import com.ccnradio.media.MediaStorage;
import com.ccnradio.registry.ModItems;
import com.ccnradio.registry.ModMenus;
import com.ccnradio.util.TapeData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

public final class TapeRecorderMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    private static final int PLAYER_INVENTORY_START = 2;
    private static final int PLAYER_INVENTORY_END = 38;

    private final BlockPos pos;
    private final Container container;

    public TapeRecorderMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.TAPE_RECORDER.get(), id);
        this.pos = pos;
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        this.container = blockEntity instanceof TapeRecorderBlockEntity recorder ? recorder : new SimpleContainer(2);

        addSlot(new Slot(container, INPUT_SLOT, 26, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BLANK_TAPE.get());
            }
        });
        addSlot(new Slot(container, OUTPUT_SLOT, 134, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == OUTPUT_SLOT || index == INPUT_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BLANK_TAPE.get())) {
            if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
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

    public Container getContainer() {
        return container;
    }

    /**
     * Server-side burn operation. Returns a translation key describing the outcome.
     */
    public static String tryBurn(ServerPlayer player, UUID mediaId) {
        if (!(player.containerMenu instanceof TapeRecorderMenu menu)) {
            return "msg.ccnradio.burn_failed";
        }
        MediaEntry entry = MediaStorage.get(mediaId);
        if (entry == null) {
            return "msg.ccnradio.media_missing";
        }
        ItemStack input = menu.container.getItem(INPUT_SLOT);
        if (input.isEmpty() || !input.is(ModItems.BLANK_TAPE.get())) {
            return "msg.ccnradio.burn_no_tape";
        }
        ItemStack output = menu.container.getItem(OUTPUT_SLOT);
        if (!output.isEmpty()) {
            return "msg.ccnradio.burn_output_full";
        }

        ItemStack tape = TapeData.createBurnedTape(entry);
        input.shrink(1);
        menu.container.setItem(INPUT_SLOT, input.isEmpty() ? ItemStack.EMPTY : input);
        menu.container.setItem(OUTPUT_SLOT, tape);
        menu.broadcastChanges();
        return "msg.ccnradio.burn_ok";
    }
}
