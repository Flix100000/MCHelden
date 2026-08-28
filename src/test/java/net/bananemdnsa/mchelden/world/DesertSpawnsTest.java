package net.bananemdnsa.mchelden.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bananemdnsa.mchelden.VanillaData;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * In der Wueste sollen nur noch Endermen spawnen, und zwar so oft wie im End.
 *
 * <p>Zwei Dinge daran sind leicht falsch zu machen und im Spiel schwer zu bemerken.
 *
 * <p><b>Erstens die Liste.</b> Sie muss genau die feindlichen Mobs der Wueste treffen —
 * nicht den Enderman, sonst ist die Wueste tot, und nicht Fledermaus, Kaninchen oder
 * Leuchttintenfisch, die bleiben sollen. Der Test leitet die erwartete Liste deswegen aus
 * Mojangs echter Wuestentabelle ab, statt sie abzuschreiben: kommt in einer neuen
 * Minecraft-Version ein Mob dazu, faellt hier auf, dass unsere Liste ihn nicht kennt.
 *
 * <p><b>Zweitens die Rudelgroesse.</b> "Gleiche Spawnrate wie im End" ist nicht nur das
 * Gewicht. Das End spawnt Endermen in Rudeln von genau vier, die Wueste in Rudeln von eins
 * bis vier — bei gleichem Gewicht kaemen also nur rund zwei Drittel so viele an.
 */
class DesertSpawnsTest {

    private static final String DESERT = "data/minecraft/worldgen/biome/desert.json";
    private static final String THE_END = "data/minecraft/worldgen/biome/the_end.json";

    private static final String ENTFERNEN =
            "/data/mchelden/neoforge/biome_modifier/desert_no_hostiles.json";
    private static final String RUDEL =
            "/data/mchelden/neoforge/biome_modifier/desert_enderman_pack.json";

    private static final String ENDERMAN = "minecraft:enderman";

    private static JsonObject eigene(String pfad) throws IOException {
        try (InputStream in = DesertSpawnsTest.class.getResourceAsStream(pfad)) {
            assertNotNull(in, "nicht im Klassenpfad: " + pfad);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static JsonObject vanilla(String pfad) throws IOException {
        Optional<JsonObject> datei = VanillaData.load(pfad);
        Assumptions.assumeTrue(datei.isPresent(), VanillaData.diagnose());
        return datei.get();
    }

    /** Die Monstereintraege eines Bioms, nach Entitaet aufgeschluesselt. */
    private static JsonObject monster(JsonObject biom, String typ) {
        for (JsonElement element : biom.getAsJsonObject("spawners").getAsJsonArray("monster")) {
            JsonObject eintrag = element.getAsJsonObject();
            if (typ.equals(eintrag.get("type").getAsString())) {
                return eintrag;
            }
        }
        return null;
    }

    private static Set<String> monsterarten(JsonObject biom) {
        Set<String> arten = new HashSet<>();
        for (JsonElement element : biom.getAsJsonObject("spawners").getAsJsonArray("monster")) {
            arten.add(element.getAsJsonObject().get("type").getAsString());
        }
        return arten;
    }

    /** Was unser Modifier aus der Wueste wirft, als Menge. */
    private static Set<String> zuEntfernen(JsonObject modifier) {
        Set<String> arten = new HashSet<>();
        JsonElement typen = modifier.get("entity_types");
        if (typen.isJsonArray()) {
            for (JsonElement element : typen.getAsJsonArray()) {
                arten.add(element.getAsString());
            }
        } else {
            arten.add(typen.getAsString());
        }
        return arten;
    }

    /**
     * Der Kern: entfernt werden muss genau das, was Mojang an Monstern in die Wueste setzt,
     * abzueglich des Endermans.
     */
    @Test
    void esVerschwindetGenauJedesMonsterAusserDemEnderman() throws IOException {
        Set<String> erwartet = monsterarten(vanilla(DESERT));
        erwartet.remove(ENDERMAN);

        assertEquals(erwartet, zuEntfernen(eigene(ENTFERNEN)));
    }

    /**
     * Der Enderman darf nicht in der Entfernungsliste stehen.
     *
     * <p>NeoForge wendet die Phase ADD vor REMOVE an. Wer den Enderman entfernt und mit
     * neuer Rudelgroesse wieder hinzufuegt, bekommt eine leere Wueste — das Hinzufuegen
     * passiert zuerst und wird danach mit weggeraeumt.
     */
    @Test
    void derEndermanStehtNichtInDerEntfernungsliste() throws IOException {
        assertFalse(zuEntfernen(eigene(ENTFERNEN)).contains(ENDERMAN));
    }

    /** Fledermaus, Kaninchen und Leuchttintenfisch sollen bleiben. */
    @Test
    void friedlicheMobsBleibenUnangetastet() throws IOException {
        Set<String> raus = zuEntfernen(eigene(ENTFERNEN));
        for (String harmlos : Set.of("minecraft:bat", "minecraft:rabbit", "minecraft:glow_squid")) {
            assertFalse(raus.contains(harmlos), harmlos + " soll bleiben");
        }
    }

    @Test
    void nurDasWuestenbiomIstBetroffen() throws IOException {
        assertEquals("minecraft:desert", eigene(ENTFERNEN).get("biomes").getAsString());
        assertEquals("minecraft:desert", eigene(RUDEL).get("biomes").getAsString());
    }

    /** Die Rudelgroesse wird auf die des Ends gesetzt, nicht auf eine ausgedachte Zahl. */
    @Test
    void dasRudelIstSoGrossWieImEnd() throws IOException {
        JsonObject imEnd = monster(vanilla(THE_END), ENDERMAN);
        assertNotNull(imEnd, "das End hat keinen Enderman mehr?");

        JsonObject unser = eigene(RUDEL);
        assertEquals(imEnd.get("minCount").getAsInt(), unser.get("min_count").getAsInt());
        assertEquals(imEnd.get("maxCount").getAsInt(), unser.get("max_count").getAsInt());
    }

    @Test
    void derRudelModifierMeintDenEnderman() throws IOException {
        assertEquals(ENDERMAN, eigene(RUDEL).get("entity_types").getAsString());
        assertEquals("mchelden:spawn_pack_size", eigene(RUDEL).get("type").getAsString());
    }

    /**
     * Das Gewicht muss nicht angefasst werden — aber nur, solange beide Biome dasselbe
     * nennen. Zieht Mojang eines der beiden nach, faellt es hier auf und der Modifier
     * braucht zusaetzlich ein Gewicht.
     */
    @Test
    void dieGewichteStimmenOhneZutunUeberein() throws IOException {
        assertEquals(monster(vanilla(THE_END), ENDERMAN).get("weight").getAsInt(),
                monster(vanilla(DESERT), ENDERMAN).get("weight").getAsInt());
    }

    /** Nach dem Entfernen ist der Enderman der einzige Monstereintrag — wie im End. */
    @Test
    void amEndeBleibtNurDerEndermanUebrig() throws IOException {
        Set<String> uebrig = monsterarten(vanilla(DESERT));
        uebrig.removeAll(zuEntfernen(eigene(ENTFERNEN)));

        assertEquals(Set.of(ENDERMAN), uebrig);
        assertTrue(monsterarten(vanilla(THE_END)).equals(uebrig),
                "das End hat eine andere Monsterliste: " + monsterarten(vanilla(THE_END)));
    }
}
