package com.ccnradio.item;

import com.ccnradio.media.MediaEntry;
import com.ccnradio.util.TapeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A tape that points at exactly one server-side media file. The stack size is one so
 * two tapes with different NBT never merge accidentally.
 */
public final class BurnedTapeItem extends Item {
    public BurnedTapeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!TapeData.isBurnedTape(stack)) {
            tooltip.add(Component.translatable("tooltip.ccnradio.burned_tape_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int duration = TapeData.getDurationSeconds(stack);
        String uploader = TapeData.getUploaderName(stack);
        tooltip.add(Component.translatable("tooltip.ccnradio.burned_tape").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ccnradio.tape_duration", TapeData.formatDuration(duration))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!uploader.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.ccnradio.tape_uploader", uploader)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        if (TapeData.isBurnedTape(stack)) {
            String name = TapeData.getDisplayName(stack);
            if (!name.isEmpty()) {
                return Component.literal(name);
            }
        }
        return super.getName(stack);
    }

    public static ItemStack create(MediaEntry entry) {
        return TapeData.createBurnedTape(entry);
    }
}
