package com.ccnradio.command;

import com.ccnradio.media.UploadSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class CCNRadioCommands {
    private CCNRadioCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ccnradio")
                .then(Commands.literal("adminuploadonly")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> setMode(context, UploadSettings.Mode.ADMIN_ONLY)))
                .then(Commands.literal("anyonecanupload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> setMode(context, UploadSettings.Mode.ANYONE)))
                .then(Commands.literal("status")
                        .executes(context -> {
                            UploadSettings.Mode mode = UploadSettings.getMode();
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    mode == UploadSettings.Mode.ADMIN_ONLY
                                            ? "command.ccnradio.status_admin"
                                            : "command.ccnradio.status_anyone"), false);
                            return 1;
                        })));
    }

    private static int setMode(CommandContext<CommandSourceStack> context, UploadSettings.Mode mode) {
        UploadSettings.setMode(mode);
        context.getSource().sendSuccess(() -> Component.translatable(
                mode == UploadSettings.Mode.ADMIN_ONLY
                        ? "command.ccnradio.mode_admin"
                        : "command.ccnradio.mode_anyone"), true);
        return 1;
    }
}
