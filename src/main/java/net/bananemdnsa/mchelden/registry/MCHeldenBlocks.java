package net.bananemdnsa.mchelden.registry;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.grave.GraveBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MCHeldenBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MCHelden.MODID);

    /**
     * Das Grab.
     *
     * <p>Bewusst ohne zugehöriges Item: es soll nur durch Sterben entstehen, nicht craftbar
     * oder aufsammelbar sein.
     *
     * <p>Abbaubar wie Erde, aber explosionsfest und kolbenfest. Das Abbauen ist harmlos, weil
     * dabei alles herausfällt — es ist nur ein schnellerer Weg an dieselbe Beute. Explosionen
     * und Kolben bleiben ausgeschlossen, damit ein Creeper nicht fremden Nachlass über die
     * halbe Landschaft verteilt.
     */
    public static final DeferredBlock<GraveBlock> GRAVE = BLOCKS.register("grave",
            () -> new GraveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(0.5F, 1200.0F)
                    .sound(SoundType.DEEPSLATE)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    private MCHeldenBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
