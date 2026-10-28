package com.ccnradio.client.screen;

import com.ccnradio.Config;
import com.ccnradio.menu.TapePlayerMenu;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.c2s.C2SSetPlaybackRange;
import com.ccnradio.util.TapeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class TapePlayerScreen extends AbstractContainerScreen<TapePlayerMenu> {
    private RangeSlider rangeSlider;
    private String status = "";
    private int lastServerRange = -1;

    public TapePlayerScreen(TapePlayerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 236;
        this.titleLabelX = 8;
        this.titleLabelY = 8;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 144;
    }

    @Override
    protected void init() {
        super.init();
        int initialRange = Mth.clamp(menu.getRange(), Config.minPlaybackRange, Config.maxPlaybackRange);
        rangeSlider = addRenderableWidget(new RangeSlider(leftPos + 8, topPos + 48, 160, 20, initialRange));
        lastServerRange = initialRange;
        addRenderableWidget(Button.builder(Component.translatable("gui.ccnradio.apply"), button -> {
                    ModNetwork.sendToServer(new C2SSetPlaybackRange(menu.getPos(), rangeSlider.getRange()));
                    setStatus("msg.ccnradio.range_set");
                })
                .bounds(leftPos + 128, topPos + 72, 40, 18)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int serverRange = menu.getRange();
        if (rangeSlider != null && serverRange >= Config.minPlaybackRange && serverRange != lastServerRange) {
            lastServerRange = serverRange;
            rangeSlider.setRange(serverRange);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x, y, x + imageWidth, y + 1, 0xFF000000);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF000000);
        graphics.fill(x, y, x + 1, y + imageHeight, 0xFF000000);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF000000);

        graphics.fill(x + 79, y + 19, x + 97, y + 37, 0xFF373737);
        graphics.fill(x + 80, y + 20, x + 96, y + 36, 0xFF8B8B8B);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, x + 8 + column * 18, y + 154 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, x + 8 + column * 18, y + 212);
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack tape = menu.getSlot(TapePlayerMenu.TAPE_SLOT).getItem();
        if (TapeData.isBurnedTape(tape)) {
            String name = TapeData.getDisplayName(tape);
            if (name.isEmpty()) {
                name = Component.translatable("item.ccnradio.burned_tape").getString();
            }
            graphics.drawString(font, font.plainSubstrByWidth(name, 160), leftPos + 8, topPos + 100,
                    0x404040, false);
            graphics.drawString(font, Component.translatable("gui.ccnradio.duration",
                    TapeData.formatDuration(TapeData.getDurationSeconds(tape))).getString(), leftPos + 8,
                    topPos + 112, 0x404040, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.ccnradio.tape_empty"),
                    leftPos + 8, topPos + 100, 0x777777, false);
        }

        Component state = menu.isPlaying()
                ? Component.translatable("gui.ccnradio.playing")
                : Component.translatable("gui.ccnradio.stopped");
        graphics.drawString(font, state.getString(), leftPos + 8, topPos + 124, 0x404040, false);
        if (!status.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(status).getString(), 160),
                    leftPos + 8, topPos + 134, 0x404040, false);
        }
    }

    public void setStatus(String key) {
        this.status = key == null ? "" : key;
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }

    private static final class RangeSlider extends AbstractSliderButton {
        private final int min;
        private final int max;

        private RangeSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Component.empty(),
                    (initial - Config.minPlaybackRange)
                            / (double) Math.max(1, Config.maxPlaybackRange - Config.minPlaybackRange));
            this.min = Config.minPlaybackRange;
            this.max = Config.maxPlaybackRange;
            updateMessage();
        }

        private int getRange() {
            return Mth.clamp(min + (int) Math.round(value * (max - min)), min, max);
        }

        private void setRange(int range) {
            this.value = Mth.clamp((range - min) / (double) Math.max(1, max - min), 0.0D, 1.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.ccnradio.range", getRange()));
        }

        @Override
        protected void applyValue() {
            updateMessage();
        }
    }
}
