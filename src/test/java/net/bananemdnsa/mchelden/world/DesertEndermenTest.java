package net.bananemdnsa.mchelden.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bananemdnsa.mchelden.VanillaData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Endermen sollen in der Wueste auch am Tag entstehen.
 *
 * <p><b>Warum es den Handler ueberhaupt braucht</b> steht nicht als Behauptung im
 * Kommentar, sondern wird hier gegen Mojangs Overworld nachgerechnet: deren
 * {@code monster_spawn_light_level} deckelt bei 7, die Oberflaeche steht am Tag auf 15.
 * Hebt Mojang das je an, ist der Handler ueberfluessig und der Test sagt es.
 *
 * <p><b>Die zweite Gefahr ist Auseinanderdriften.</b> Welches Biom und welcher Mob gemeint
 * sind, steht jetzt an drei Stellen: in den beiden Biome-Modifiern und im Handler. Wer
 * eines davon aendert, soll die anderen nicht vergessen.
 *
 * <p><b>Die dritte ist zu viel des Guten.</b> {@code SUCCEED} hebt die ganze Pruefung auf,
 * nicht nur den Lichttest. Der Handler muss die drei Faelle, in denen er nichts zu suchen
 * hat — anderer Mob, kein natuerlicher Spawn, Vanilla sagt ohnehin ja — abweisen, bevor er
 * ueberhaupt in die Welt schaut. Genau das laesst sich pruefen, indem gar keine Welt
 * mitgegeben wird: greift er doch darauf zu, fliegt der Test.
 */
class DesertEndermenTest {

    private static final String ENTFERNEN =
            "/data/mchelden/neoforge/biome_modifier/desert_no_hostiles.json";
    private static final String RUDEL =
            "/data/mchelden/neoforge/biome_modifier/desert_enderman_pack.json";

    private static final String OVERWORLD_IM_JAR = "data/minecraft/dimension_type/overworld.json";

    /** Himmelslicht an der Oberflaeche bei Tag. */
    private static final int TAGESLICHT = 15;

    private static JsonObject eigene(String pfad) throws IOException {
        try (InputStream in = DesertEndermenTest.class.getResourceAsStream(pfad)) {
            assertNotNull(in, "nicht im Klassenpfad: " + pfad);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    /** Ein Pruefereignis ohne Welt — wer sie anfasst, bekommt eine NPE. */
    private static SpawnPlacementCheck ereignis(EntityType<?> typ, MobSpawnType anlass,
            boolean vanillaSagtJa) {
        return new SpawnPlacementCheck(typ, null, anlass, BlockPos.ZERO,
                RandomSource.create(), vanillaSagtJa);
    }

    private static Result nachDemHandler(SpawnPlacementCheck ereignis) {
        DesertEndermen.onSpawnPlacement(ereignis);
        return ereignis.getResult();
    }

    /**
     * Der Grund fuer den Handler, aus Mojangs Zahlen: der Lichttest zieht gegen 15 und kann
     * ihn nicht gewinnen.
     */
    @Test
    void beiTageslichtSpawntVanillaNie() throws IOException {
        Optional<JsonObject> overworld = VanillaData.load(OVERWORLD_IM_JAR);
        Assumptions.assumeTrue(overworld.isPresent(), VanillaData.diagnose());

        JsonObject test = overworld.get().getAsJsonObject("monster_spawn_light_level");
        assertEquals("minecraft:uniform", test.get("type").getAsString());
        assertTrue(test.get("max_inclusive").getAsInt() < TAGESLICHT,
                "die Overworld laesst inzwischen von selbst bei Tageslicht spawnen: " + test);
    }

    @Test
    void derHandlerMeintDasselbeBiomWieDieModifier() throws IOException {
        String biom = Biomes.DESERT.location().toString();
        assertEquals(biom, eigene(ENTFERNEN).get("biomes").getAsString());
        assertEquals(biom, eigene(RUDEL).get("biomes").getAsString());
    }

    @Test
    void derHandlerMeintDenselbenMobWieDerRudelModifier() throws IOException {
        assertEquals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ENDERMAN).toString(),
                eigene(RUDEL).get("entity_types").getAsString());
    }

    /** Sagt Vanilla ohnehin ja, hat der Handler nichts zu tun — nachts also gar nichts. */
    @Test
    void woVanillaSchonJaSagtMischtErSichNichtEin() {
        assertEquals(Result.DEFAULT,
                nachDemHandler(ereignis(EntityType.ENDERMAN, MobSpawnType.NATURAL, true)));
    }

    /** Die Wueste bleibt bei Tag leer von allem anderen. */
    @Test
    void andereMobsBekommenNichtsGeschenkt() {
        assertEquals(Result.DEFAULT,
                nachDemHandler(ereignis(EntityType.ZOMBIE, MobSpawnType.NATURAL, false)));
        assertEquals(Result.DEFAULT,
                nachDemHandler(ereignis(EntityType.HUSK, MobSpawnType.NATURAL, false)));
    }

    /** Spawner und Spawn-Eier setzt jemand absichtlich; die gehen ihren eigenen Weg. */
    @Test
    void nurNatuerlicheSpawns() {
        for (MobSpawnType anlass : new MobSpawnType[] {
                MobSpawnType.SPAWNER, MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND,
                MobSpawnType.STRUCTURE}) {
            assertEquals(Result.DEFAULT,
                    nachDemHandler(ereignis(EntityType.ENDERMAN, anlass, false)),
                    "Anlass " + anlass);
        }
    }
}
