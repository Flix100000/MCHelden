package net.bananemdnsa.mchelden.registry;

import java.util.function.Supplier;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.grave.GraveBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MCHeldenBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MCHelden.MODID);

    public static final Supplier<BlockEntityType<GraveBlockEntity>> GRAVE =
            BLOCK_ENTITIES.register("grave", () -> BlockEntityType.Builder
                    .of(GraveBlockEntity::new, MCHeldenBlocks.GRAVE.get())
                    .build(null));

    private MCHeldenBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
