package com.ccnradio.client.screen;

import com.ccnradio.client.ClientMediaList;
import com.ccnradio.client.ClientUploadManager;
import com.ccnradio.media.AudioMetadata;
import com.ccnradio.media.MediaEntry;
import com.ccnradio.menu.TapeRecorderMenu;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.c2s.C2SBurnTape;
import com.ccnradio.network.packet.c2s.C2SDeleteMedia;
import com.ccnradio.network.packet.c2s.C2SRequestMediaList;
import com.ccnradio.util.TapeData;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TapeRecorderScreen extends AbstractContainerScreen<TapeRecorderMenu> {
    private static final int LIST_X = 8;
    private static final int LIST_Y = 40;
    private static final int LIST_WIDTH = 160;
    private static final int LIST_HEIGHT = 56;
    private static final int ROW_HEIGHT = 14;
    private static final int VISIBLE_ROWS = 4;

    private final List<MediaEntry> entries = new ArrayList<>();
    private UUID selected;
    private int scroll;
    private int lastListVersion = -1;
    private String status = "";
    private String lastUploadStatus = "";
    private EditBox nameBox;
    private EditBox pathBox;
    private Button uploadButton;
    private Button browseButton;
    private Button burnButton;
    private UUID deleteConfirmId;
    private long deleteConfirmAt;
    private boolean uploading;

    public TapeRecorderScreen(TapeRecorderMenu menu, Inventory inventory, Component title) {
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

        nameBox = new EditBox(font, leftPos + 8, topPos + 108, 94, 16,
                Component.translatable("gui.ccnradio.name_hint"));
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.translatable("gui.ccnradio.name_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);

        browseButton = addRenderableWidget(Button.builder(Component.translatable("gui.ccnradio.browse"),
                        button -> openFileBrowser(false))
                .bounds(leftPos + 106, topPos + 108, 62, 16)
                .build());

        pathBox = new EditBox(font, leftPos + 8, topPos + 126, 94, 16,
                Component.translatable("gui.ccnradio.path_hint"));
        pathBox.setMaxLength(260);
        pathBox.setHint(Component.translatable("gui.ccnradio.path_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(pathBox);

        uploadButton = addRenderableWidget(Button.builder(Component.translatable("gui.ccnradio.upload_short"),
                        button -> onUpload())
                .bounds(leftPos + 106, topPos + 126, 30, 16)
                .build());
        burnButton = addRenderableWidget(Button.builder(Component.translatable("gui.ccnradio.burn_short"),
                        button -> onBurn())
                .bounds(leftPos + 138, topPos + 126, 30, 16)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ccnradio.refresh_short"),
                        button -> ModNetwork.sendToServer(new C2SRequestMediaList()))
                .bounds(leftPos + 118, topPos + 6, 50, 12)
                .build());

        ModNetwork.sendToServer(new C2SRequestMediaList());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (lastListVersion != ClientMediaList.version()) {
            entries.clear();
            entries.addAll(ClientMediaList.entries());
            lastListVersion = ClientMediaList.version();
            if (selected != null && entries.stream().noneMatch(entry -> entry.getId().equals(selected))) {
                selected = null;
            }
            scroll = Mth.clamp(scroll, 0, maxScroll());
        }

        String uploadStatus = ClientUploadManager.statusKey();
        if (!uploadStatus.isEmpty() && !uploadStatus.equals(lastUploadStatus)) {
            status = uploadStatus;
            lastUploadStatus = uploadStatus;
        }

        uploading = ClientUploadManager.isBusy();
        if (!ClientMediaList.canUpload() && status.isEmpty() && !ClientMediaList.denyKey().isEmpty()) {
            status = ClientMediaList.denyKey();
        }
        if (uploadButton != null) {
            uploadButton.active = ClientMediaList.canUpload() && !uploading;
        }
        if (browseButton != null) {
            browseButton.active = !uploading;
        }
        if (burnButton != null) {
            burnButton.active = selected != null && !uploading;
        }
        if (nameBox != null) {
            nameBox.tick();
        }
        if (pathBox != null) {
            pathBox.tick();
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

        drawSlot(graphics, x + 26, y + 20);
        drawSlot(graphics, x + 134, y + 20);
        graphics.drawString(font, "→", x + 78, y + 24, 0x404040, false);

        graphics.fill(x + LIST_X, y + LIST_Y, x + LIST_X + LIST_WIDTH, y + LIST_Y + LIST_HEIGHT, 0xFF8B8B8B);
        graphics.fill(x + LIST_X, y + LIST_Y, x + LIST_X + LIST_WIDTH, y + LIST_Y + 1, 0xFF373737);
        graphics.fill(x + LIST_X, y + LIST_Y + LIST_HEIGHT - 1, x + LIST_X + LIST_WIDTH,
                y + LIST_Y + LIST_HEIGHT, 0xFFFFFFFF);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, x + 8 + column * 18, y + 154 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, x + 8 + column * 18, y + 212);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderList(graphics, mouseX, mouseY);
        renderStatus(graphics);
        if (uploading) {
            int progress = ClientUploadManager.progress();
            int barX = leftPos + 8;
            int barY = topPos + 96;
            graphics.fill(barX, barY, barX + 160, barY + 2, 0xFF373737);
            graphics.fill(barX, barY, barX + 160 * progress / 100, barY + 2, 0xFF3C8C3C);
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = leftPos + LIST_X;
        int listY = topPos + LIST_Y;
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.ccnradio.empty"),
                    listX + LIST_WIDTH / 2, listY + 24, 0x404040);
            return;
        }

        int end = Math.min(entries.size(), scroll + VISIBLE_ROWS);
        for (int index = scroll; index < end; index++) {
            MediaEntry entry = entries.get(index);
            int rowY = listY + (index - scroll) * ROW_HEIGHT;
            boolean isSelected = entry.getId().equals(selected);
            boolean hovered = mouseX >= listX && mouseX < listX + LIST_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (isSelected) {
                graphics.fill(listX, rowY, listX + LIST_WIDTH, rowY + ROW_HEIGHT - 1, 0xFF5A7A9A);
            } else if (hovered) {
                graphics.fill(listX, rowY, listX + LIST_WIDTH, rowY + ROW_HEIGHT - 1, 0xFFB0C4DE);
            }
            int color = isSelected ? 0xFFFFFFFF : 0x404040;
            String duration = TapeData.formatDuration(entry.getDurationSeconds());
            int durationX = listX + LIST_WIDTH - 18 - font.width(duration);
            graphics.drawString(font, font.plainSubstrByWidth(entry.getDisplayName(), durationX - listX - 4),
                    listX + 3, rowY + 3, color, false);
            graphics.drawString(font, duration, durationX, rowY + 3, color, false);
            if (isOwn(entry)) {
                graphics.drawString(font, "x", listX + LIST_WIDTH - 10, rowY + 3, 0xFFAA0000, false);
            }
        }

        int hoveredRow = rowAt(mouseX, mouseY);
        if (hoveredRow >= 0 && hoveredRow < entries.size()) {
            MediaEntry entry = entries.get(hoveredRow);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(entry.getDisplayName()).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("gui.ccnradio.by", entry.getUploaderName())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.ccnradio.duration",
                    TapeData.formatDuration(entry.getDurationSeconds())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.ccnradio.size", formatSize(entry.getSize()))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.ccnradio.format", entry.getFormat().toUpperCase())
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (isOwn(entry)) {
                tooltip.add(Component.translatable("gui.ccnradio.delete_hint").withStyle(ChatFormatting.RED));
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        if (status.isEmpty()) {
            return;
        }
        Component message = Component.translatable(status);
        if (uploading) {
            message = message.copy().append(" " + ClientUploadManager.progress() + "%");
        }
        int color = isError(status) ? 0xAA0000 : 0x404040;
        graphics.drawString(font, font.plainSubstrByWidth(message.getString(), 160),
                leftPos + 8, topPos + 98, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = leftPos + LIST_X;
        int listY = topPos + LIST_Y;
        if (mouseX >= listX && mouseX < listX + LIST_WIDTH
                && mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0 && row < entries.size()) {
                MediaEntry entry = entries.get(row);
                if (isOwn(entry) && mouseX >= listX + LIST_WIDTH - 14) {
                    confirmDelete(entry);
                    return true;
                }
                selected = entry.getId();
                deleteConfirmId = null;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listX = leftPos + LIST_X;
        int listY = topPos + LIST_Y;
        if (mouseX >= listX && mouseX < listX + LIST_WIDTH
                && mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int rowAt(double mouseX, double mouseY) {
        int listX = leftPos + LIST_X;
        int listY = topPos + LIST_Y;
        if (mouseX < listX || mouseX >= listX + LIST_WIDTH
                || mouseY < listY || mouseY >= listY + LIST_HEIGHT) {
            return -1;
        }
        return scroll + (int) ((mouseY - listY) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - VISIBLE_ROWS);
    }

    private void onUpload() {
        if (!ClientMediaList.canUpload()) {
            setStatus(ClientMediaList.denyKey().isEmpty() ? "msg.ccnradio.no_permission" : ClientMediaList.denyKey());
            return;
        }
        if (ClientUploadManager.isBusy()) {
            return;
        }
        String raw = pathBox == null ? "" : pathBox.getValue().trim();
        if (raw.isEmpty()) {
            // Nothing picked yet: open the browser and upload as soon as a file is chosen.
            openFileBrowser(true);
            return;
        }
        Path path = parsePath(raw);
        if (path == null) {
            setStatus("msg.ccnradio.invalid_audio");
            return;
        }
        startUpload(path);
    }

    private void openFileBrowser(boolean uploadWhenPicked) {
        if (ClientUploadManager.isBusy()) {
            return;
        }
        UploadFilePicker.pick(path -> {
            if (path == null || minecraft == null || minecraft.screen != this) {
                return;
            }
            if (pathBox != null) {
                pathBox.setValue(path.toString());
            }
            if (nameBox != null && nameBox.getValue().trim().isEmpty()) {
                String base = AudioMetadata.stripExtension(path.getFileName().toString()).trim();
                if (base.length() > 48) {
                    base = base.substring(0, 48);
                }
                nameBox.setValue(base);
            }
            if (uploadWhenPicked) {
                startUpload(path);
            } else {
                setStatus("gui.ccnradio.file_selected");
            }
        }, key -> {
            if (minecraft != null && minecraft.screen == this) {
                setStatus(key);
            }
        });
    }

    private void startUpload(Path path) {
        if (path == null || !ClientMediaList.canUpload() || ClientUploadManager.isBusy()) {
            return;
        }
        ClientUploadManager.start(path, nameBox == null ? "" : nameBox.getValue());
    }

    private static Path parsePath(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Paths.get(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void onBurn() {
        if (selected == null) {
            setStatus("gui.ccnradio.no_selection");
            return;
        }
        ModNetwork.sendToServer(new C2SBurnTape(selected));
        setStatus("gui.ccnradio.burning");
    }

    private void confirmDelete(MediaEntry entry) {
        long now = Util.getMillis();
        if (entry.getId().equals(deleteConfirmId) && now - deleteConfirmAt < 3000L) {
            ModNetwork.sendToServer(new C2SDeleteMedia(entry.getId()));
            deleteConfirmId = null;
            setStatus("gui.ccnradio.deleting");
        } else {
            deleteConfirmId = entry.getId();
            deleteConfirmAt = now;
            setStatus("gui.ccnradio.delete_confirm");
        }
    }

    private boolean isOwn(MediaEntry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && entry.getUploader().equals(minecraft.player.getUUID());
    }

    private static boolean isError(String key) {
        return key.contains("failed") || key.contains("missing") || key.contains("too_")
                || key.contains("invalid") || key.contains("no_") || key.contains("admin_only");
    }

    private static String formatSize(long size) {
        if (size < 1024L) {
            return size + " B";
        }
        if (size < 1024L * 1024L) {
            return String.format("%.1f KiB", size / 1024.0D);
        }
        return String.format("%.1f MiB", size / (1024.0D * 1024.0D));
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
}
