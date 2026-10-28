package com.ccnradio.registry;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.menu.TapePlayerMenu;
import com.ccnradio.menu.TapeRecorderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CCNRadioMod.MODID);

    public static final RegistryObject<MenuType<TapeRecorderMenu>> TAPE_RECORDER =
            MENUS.register("tape_recorder", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new TapeRecorderMenu(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<TapePlayerMenu>> TAPE_PLAYER =
            MENUS.register("tape_player", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new TapePlayerMenu(windowId, inventory, data.readBlockPos())));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
