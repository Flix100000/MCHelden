package net.bananemdnsa.mchelden.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.world.SpawnPackSizeBiomeModifier;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class MCHeldenBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MCHelden.MODID);

    /**
     * Aendert die Rudelgroesse eines vorhandenen Spawneintrags.
     *
     * <p>Die Luecke, die NeoForge offen laesst: seine eigenen Modifier koennen Spawns nur
     * hinzufuegen oder entfernen. Warum sich beides nicht zu einer Aenderung kombinieren
     * laesst, steht bei {@link SpawnPackSizeBiomeModifier}.
     */
    public static final Supplier<MapCodec<SpawnPackSizeBiomeModifier>> SPAWN_PACK_SIZE =
            BIOME_MODIFIERS.register("spawn_pack_size", () -> SpawnPackSizeBiomeModifier.CODEC);

    private MCHeldenBiomeModifiers() {
    }

    public static void register(IEventBus modEventBus) {
        BIOME_MODIFIERS.register(modEventBus);
    }
}
