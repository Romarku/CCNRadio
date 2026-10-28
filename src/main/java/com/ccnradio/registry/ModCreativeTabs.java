package com.ccnradio.registry;

import com.ccnradio.CCNRadioMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, CCNRadioMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ccnradio"))
                    .icon(() -> new ItemStack(ModItems.TAPE_RECORDER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TAPE_RECORDER.get());
                        output.accept(ModItems.TAPE_PLAYER.get());
                        output.accept(ModItems.BLANK_TAPE.get());
                        output.accept(ModItems.TAPE_PLAYER_CONFIGURATOR.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
