package net.bananemdnsa.mchelden.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bananemdnsa.mchelden.VanillaData;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Die beiden Ancient-City-Trims sollen im Schiffswrack mit derselben Chance liegen wie in
 * ihrer Heimat. "Dieselbe Chance" ist keine Zahl, die man einmal abschreibt: sie ergibt
 * sich aus Gewicht und Gesamtgewicht des Pools, und ein zusaetzlicher Eintrag verschiebt
 * beides. Beim Bearbeiten der Tabelle rechnet das niemand nach.
 *
 * <p>Die Chancen stehen deswegen hier als Bruch, und zusaetzlich wird direkt gegen Mojangs
 * eigene Tabelle verglichen — aendern die die Gewichte, faellt es auf.
 */
class ShipwreckTrimInjectionTest {

    private static final String WARD = "minecraft:ward_armor_trim_smithing_template";
    private static final String SILENCE = "minecraft:silence_armor_trim_smithing_template";

    private static final String EINGESPEIST =
            "/data/mchelden/loot_table/inject/shipwreck_ancient_trim.json";
    private static final String MODIFIER =
            "/data/mchelden/loot_modifiers/shipwreck_ancient_trim.json";
    private static final String REGISTRIERUNG =
            "/data/neoforge/loot_modifiers/global_loot_modifiers.json";

    private static final String ANCIENT_CITY_IM_JAR =
            "data/minecraft/loot_table/chests/ancient_city.json";

    private static final Set<String> WRACKTRUHEN = Set.of(
            "minecraft:chests/shipwreck_supply",
            "minecraft:chests/shipwreck_treasure",
            "minecraft:chests/shipwreck_map");

    private static JsonObject lade(String pfad) throws IOException {
        try (InputStream in = ShipwreckTrimInjectionTest.class.getResourceAsStream(pfad)) {
            assertNotNull(in, "nicht im Klassenpfad: " + pfad);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static JsonObject ancientCity() throws IOException {
        Optional<JsonObject> stadt = VanillaData.load(ANCIENT_CITY_IM_JAR);
        Assumptions.assumeTrue(stadt.isPresent(), VanillaData.diagnose());
        return stadt.get();
    }

    /**
     * Die Chance pro Truhe, dass dieses Item faellt.
     *
     * <p>Ein fehlendes {@code weight} bedeutet 1 — so steht Silence in Vanilla drin.
     */
    private static double chance(JsonObject tabelle, String item) {
        for (JsonElement poolElement : tabelle.getAsJsonArray("pools")) {
            JsonObject pool = poolElement.getAsJsonObject();

            int gesamt = 0;
            int treffer = 0;
            for (JsonElement entryElement : pool.getAsJsonArray("entries")) {
                JsonObject entry = entryElement.getAsJsonObject();
                int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                gesamt += weight;
                if (entry.has("name") && item.equals(entry.get("name").getAsString())) {
                    treffer = weight;
                }
            }

            if (treffer > 0) {
                assertEquals(1.0, pool.get("rolls").getAsDouble(), 1.0e-9,
                        "Die Rechnung gilt nur bei genau einem Zug pro Truhe");
                return treffer / (double) gesamt;
            }
        }
        return 0.0;
    }

    private static Set<String> zieltabellen(JsonObject modifier) {
        Set<String> ids = new HashSet<>();
        sammle(modifier.getAsJsonArray("conditions"), ids);
        return ids;
    }

    /** Die Bedingungen sind geschachtelt: ein {@code any_of} mit den einzelnen Tabellen darin. */
    private static void sammle(JsonArray terms, Set<String> ids) {
        for (JsonElement element : terms) {
            JsonObject term = element.getAsJsonObject();
            if (term.has("loot_table_id")) {
                ids.add(term.get("loot_table_id").getAsString());
            }
            if (term.has("terms")) {
                sammle(term.getAsJsonArray("terms"), ids);
            }
        }
    }

    @Test
    void silenceLiegtMitEinsZuAchtzigImWrack() throws IOException {
        assertEquals(1.0 / 80.0, chance(lade(EINGESPEIST), SILENCE), 1.0e-9);
    }

    @Test
    void wardLiegtMitVierZuAchtzigImWrack() throws IOException {
        assertEquals(4.0 / 80.0, chance(lade(EINGESPEIST), WARD), 1.0e-9);
    }

    /** Ein Zug, ein Pool — sonst faellt im Wrack mehr als in der Stadt. */
    @Test
    void esWirdGenauEinmalGezogen() throws IOException {
        JsonArray pools = lade(EINGESPEIST).getAsJsonArray("pools");
        assertEquals(1, pools.size());
        assertEquals(1.0, pools.get(0).getAsJsonObject().get("rolls").getAsDouble(), 1.0e-9);
    }

    /** Der Rest des Gewichts muss auf Leer entfallen, sonst liegt in jeder Truhe ein Trim. */
    @Test
    void inDenAllermeistenTruhenLiegtNichts() throws IOException {
        JsonObject tabelle = lade(EINGESPEIST);
        double zusammen = chance(tabelle, SILENCE) + chance(tabelle, WARD);
        assertEquals(0.0625, zusammen, 1.0e-9, "Trimchance zusammen");
    }

    @Test
    void alleDreiWracktruhenSindEingetragen() throws IOException {
        assertEquals(WRACKTRUHEN, zieltabellen(lade(MODIFIER)));
    }

    @Test
    void derModifierZeigtAufDieEingespeisteTabelle() throws IOException {
        JsonObject modifier = lade(MODIFIER);
        assertEquals("neoforge:add_table", modifier.get("type").getAsString());
        assertEquals("mchelden:inject/shipwreck_ancient_trim", modifier.get("table").getAsString());
    }

    /** Ohne Eintrag in dieser Datei laedt NeoForge den Modifier gar nicht erst. */
    @Test
    void derModifierIstBeiNeoForgeAngemeldet() throws IOException {
        JsonObject registrierung = lade(REGISTRIERUNG);
        assertTrue(registrierung.getAsJsonArray("entries").contains(
                        JsonParser.parseString("\"mchelden:shipwreck_ancient_trim\"")),
                "nicht angemeldet: " + registrierung);
    }

    @Test
    void silenceFaelltSoOftWieBeiMojang() throws IOException {
        assertEquals(chance(ancientCity(), SILENCE), chance(lade(EINGESPEIST), SILENCE), 1.0e-9);
    }

    @Test
    void wardFaelltSoOftWieBeiMojang() throws IOException {
        assertEquals(chance(ancientCity(), WARD), chance(lade(EINGESPEIST), WARD), 1.0e-9);
    }
}
