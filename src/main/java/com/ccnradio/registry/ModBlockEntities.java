package com.ccnradio.registry;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.blockentity.TapePlayerBlockEntity;
import com.ccnradio.blockentity.TapeRecorderBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CCNRadioMod.MODID);

    public static final RegistryObject<BlockEntityType<TapeRecorderBlockEntity>> TAPE_RECORDER =
            BLOCK_ENTITIES.register("tape_recorder",
                    () -> BlockEntityType.Builder.of(TapeRecorderBlockEntity::new, ModBlocks.TAPE_RECORDER.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<TapePlayerBlockEntity>> TAPE_PLAYER =
            BLOCK_ENTITIES.register("tape_player",
                    () -> BlockEntityType.Builder.of(TapePlayerBlockEntity::new, ModBlocks.TAPE_PLAYER.get())
                            .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
