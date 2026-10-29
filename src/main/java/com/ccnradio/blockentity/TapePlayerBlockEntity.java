package com.ccnradio.blockentity;

import com.ccnradio.Config;
import com.ccnradio.media.MediaEntry;
import com.ccnradio.media.MediaStorage;
import com.ccnradio.menu.TapePlayerMenu;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.s2c.S2CPlaybackStart;
import com.ccnradio.network.packet.s2c.S2CPlaybackStop;
import com.ccnradio.registry.ModBlockEntities;
import com.ccnradio.registry.ModItems;
import com.ccnradio.util.TapeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative tape player state. The playback clock lives here so players
 * entering or leaving the range can be synchronized to the current position.
 */
public final class TapePlayerBlockEntity extends BlockEntity implements Container, MenuProvider {
    /**
     * Ticks between announcing a tape and the start of its playback clock. Clients use this
     * window to download the file; without it, short clips often finished downloading after
     * the tape had already ended and were silently skipped.
     */
    private static final int START_DELAY_TICKS = 20;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    private int range = Config.defaultPlaybackRange;
    private boolean playing;
    private long startGameTime;
    private UUID playingMediaId;
    private String playingFormat = "ogg";
    private int playingDurationSeconds;

    public TapePlayerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TAPE_PLAYER.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!playing) {
            return;
        }
        MediaEntry entry = MediaStorage.get(playingMediaId);
        if (entry == null || !Files.isRegularFile(MediaStorage.pathFor(entry))
                || !TapeData.isBurnedTape(getTape())) {
            stopPlayback(null);
            return;
        }
        long elapsedTicks = level.getGameTime() - startGameTime;
        if (playingDurationSeconds > 0 && elapsedTicks >= playingDurationSeconds * 20L) {
            stopPlayback(null);
            return;
        }
        // Re-sync roughly once per second. Clients that just entered the range pick the
        // tape up at the correct offset; clients that are already playing ignore it.
        if (level.getGameTime() % 20L == 0L) {
            broadcastSync(level, pos);
        }
    }

    public void startPlayback(ServerPlayer actor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (playing) {
            message(actor, "msg.ccnradio.already_playing");
            return;
        }
        if (!TapeData.isBurnedTape(getTape())) {
            message(actor, "msg.ccnradio.no_tape");
            return;
        }
        MediaEntry entry = MediaStorage.get(TapeData.getMediaId(getTape()));
        if (entry == null || !Files.isRegularFile(MediaStorage.pathFor(entry))) {
            message(actor, "msg.ccnradio.media_missing");
            return;
        }
        playing = true;
        startGameTime = serverLevel.getGameTime() + START_DELAY_TICKS;
        playingMediaId = entry.getId();
        playingFormat = entry.getFormat();
        playingDurationSeconds = entry.getDurationSeconds();
        setChanged();
        broadcastSync(serverLevel, worldPosition);
        message(actor, "msg.ccnradio.started");
    }

    public void stopPlayback(@Nullable ServerPlayer actor) {
        if (!playing) {
            message(actor, "msg.ccnradio.not_playing");
            return;
        }
        playing = false;
        playingMediaId = null;
        playingDurationSeconds = 0;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            broadcastStop(serverLevel);
        }
        message(actor, "msg.ccnradio.stopped");
    }

    public void setRange(int requestedRange) {
        int clamped = Mth.clamp(requestedRange, Config.minPlaybackRange, Config.maxPlaybackRange);
        if (clamped == range) {
            return;
        }
        range = clamped;
        setChanged();
        if (playing && level instanceof ServerLevel serverLevel) {
            // Clients outside the new range must be told to stop, then everyone inside
            // the new range is synchronized again.
            broadcastStop(serverLevel);
            broadcastSync(serverLevel, worldPosition);
        }
    }

    private void broadcastSync(ServerLevel level, BlockPos pos) {
        if (!playing || playingMediaId == null) {
            return;
        }
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double rangeSqr = (double) range * (double) range;
        S2CPlaybackStart packet = new S2CPlaybackStart(worldPosition, playingMediaId, playingFormat,
                startGameTime, range, playingDurationSeconds);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(centerX, centerY, centerZ) <= rangeSqr) {
                ModNetwork.sendToPlayer(player, packet);
            }
        }
    }

    private void broadcastStop(ServerLevel level) {
        S2CPlaybackStop packet = new S2CPlaybackStop(worldPosition);
        for (ServerPlayer player : level.players()) {
            ModNetwork.sendToPlayer(player, packet);
        }
    }

    private void message(@Nullable ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    // ------------------------------------------------------------------
    // Container
    // ------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? items.get(0) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            if (playing && (getTape().isEmpty() || !Objects.equals(TapeData.getMediaId(getTape()), playingMediaId))) {
                stopPlayback(null);
            }
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) {
            if (playing) {
                stopPlayback(null);
            }
            setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        ItemStack normalized = stack.copy();
        if (normalized.getCount() > getMaxStackSize()) {
            normalized.setCount(getMaxStackSize());
        }
        items.set(0, normalized);
        if (playing && (normalized.isEmpty()
                || !Objects.equals(TapeData.getMediaId(normalized), playingMediaId))) {
            stopPlayback(null);
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.is(ModItems.BURNED_TAPE.get());
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        if (playing) {
            stopPlayback(null);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Tape", getTape().save(new CompoundTag()));
        tag.putInt("Range", range);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.set(0, ItemStack.of(tag.getCompound("Tape")));
        if (tag.contains("Range")) {
            range = Mth.clamp(tag.getInt("Range"), Config.minPlaybackRange, Config.maxPlaybackRange);
        }
        playing = false;
        playingMediaId = null;
    }

    @Override
    public void setRemoved() {
        if (playing && level instanceof ServerLevel serverLevel) {
            playing = false;
            playingMediaId = null;
            broadcastStop(serverLevel);
        }
        super.setRemoved();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ccnradio.tape_player");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TapePlayerMenu(id, inventory, worldPosition);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public ItemStack getTape() {
        return items.get(0);
    }

    public int getRange() {
        return range;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getPlayingDurationSeconds() {
        return playingDurationSeconds;
    }

    public UUID getPlayingMediaId() {
        return playingMediaId;
    }

    public String getPlayingFormat() {
        return playingFormat;
    }
}
