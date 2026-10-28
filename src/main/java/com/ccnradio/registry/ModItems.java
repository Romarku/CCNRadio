package com.ccnradio.registry;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.item.BlankTapeItem;
import com.ccnradio.item.BurnedTapeItem;
import com.ccnradio.item.TapePlayerConfiguratorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CCNRadioMod.MODID);

    public static final RegistryObject<Item> TAPE_RECORDER = ITEMS.register("tape_recorder",
            () -> new BlockItem(ModBlocks.TAPE_RECORDER.get(), new Item.Properties()));

    public static final RegistryObject<Item> TAPE_PLAYER = ITEMS.register("tape_player",
            () -> new BlockItem(ModBlocks.TAPE_PLAYER.get(), new Item.Properties()));

    public static final RegistryObject<Item> BLANK_TAPE = ITEMS.register("blank_tape",
            () -> new BlankTapeItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> BURNED_TAPE = ITEMS.register("burned_tape",
            () -> new BurnedTapeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TAPE_PLAYER_CONFIGURATOR = ITEMS.register("tape_player_configurator",
            () -> new TapePlayerConfiguratorItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
