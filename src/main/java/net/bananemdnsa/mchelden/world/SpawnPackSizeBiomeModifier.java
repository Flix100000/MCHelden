package net.bananemdnsa.mchelden.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.bananemdnsa.mchelden.registry.MCHeldenBiomeModifiers;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Setzt die Rudelgroesse eines Mobs in bestimmten Biomen neu.
 *
 * <p>NeoForge bringt {@code add_spawns} und {@code remove_spawns} mit, aber nichts, was
 * einen vorhandenen Eintrag <em>aendert</em>. Und beides zu kombinieren geht nicht: das
 * Hinzufuegen laeuft in Phase {@link Phase#ADD}, das Entfernen in {@link Phase#REMOVE} —
 * und REMOVE kommt danach. Wer einen Mob entfernt und mit neuer Rudelgroesse wieder
 * hinzufuegt, bekommt ihn gar nicht: das Hinzufuegen passiert zuerst und wird anschliessend
 * mit weggeraeumt.
 *
 * <p>Deswegen diese Klasse, und deswegen laeuft sie in {@link Phase#MODIFY}: da stehen die
 * Entfernungen schon fest, und was uebrig ist, laesst sich anfassen.
 *
 * <p>Das Gewicht bleibt, wie es war. Geaendert wird nur, wie viele Tiere ein geglueckter
 * Spawnversuch auf einmal setzt — und genau daran haengt, wie viele am Ende herumlaufen.
 */
public record SpawnPackSizeBiomeModifier(HolderSet<Biome> biomes,
                                         HolderSet<EntityType<?>> entityTypes,
                                         int minCount,
                                         int maxCount) implements BiomeModifier {

    public static final MapCodec<SpawnPackSizeBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(
                            Biome.LIST_CODEC.fieldOf("biomes")
                                    .forGetter(SpawnPackSizeBiomeModifier::biomes),
                            RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE)
                                    .fieldOf("entity_types")
                                    .forGetter(SpawnPackSizeBiomeModifier::entityTypes),
                            ExtraCodecs.POSITIVE_INT.fieldOf("min_count")
                                    .forGetter(SpawnPackSizeBiomeModifier::minCount),
                            ExtraCodecs.POSITIVE_INT.fieldOf("max_count")
                                    .forGetter(SpawnPackSizeBiomeModifier::maxCount))
                    .apply(builder, SpawnPackSizeBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || !this.biomes.contains(biome)) {
            return;
        }

        var spawns = builder.getMobSpawnSettings();

        // Ueber alle Kategorien, nicht nur ueber die Monster: welche Kategorie ein Mob hat,
        // steht am Mob und nicht hier, und eine Annahme darueber waere genau die Art Fehler,
        // die erst auffaellt, wenn jemand die Klasse fuer etwas anderes benutzt.
        for (MobCategory category : MobCategory.values()) {
            spawns.getSpawner(category).replaceAll(this::resized);
        }
    }

    private MobSpawnSettings.SpawnerData resized(MobSpawnSettings.SpawnerData data) {
        if (!this.entityTypes.contains(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(data.type))) {
            return data;
        }
        return new MobSpawnSettings.SpawnerData(data.type, data.getWeight(), this.minCount, this.maxCount);
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return MCHeldenBiomeModifiers.SPAWN_PACK_SIZE.get();
    }
}
