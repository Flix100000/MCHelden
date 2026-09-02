package net.bananemdnsa.mchelden.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bananemdnsa.mchelden.VanillaData;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Mending soll sich angeln lassen. In Vanilla geht das theoretisch — ueber den Schatzfund,
 * ein Buch unter sechs Eintraegen, zufaellig verzaubert — und praktisch nicht: das sind
 * Promille, und ein Event dauert Wochen, nicht Jahre.
 *
 * <p>Zwei Dinge daran sind leicht falsch zu machen.
 *
 * <p><b>Erstens die Zieltabelle.</b> Der naheliegende Anker waere die Schatztabelle. Sie
 * waere wirkungslos: {@code LootTable#getRandomItems} ruft {@code CommonHooks.modifyLoot}
 * genau einmal, geschachtelte Tabellen laufen ueber {@code getRandomItemsRaw}, und
 * {@code LootContext#setQueriedLootTableId} merkt sich nur die erste ID. Der Modifier muss
 * an {@code minecraft:gameplay/fishing} haengen.
 *
 * <p><b>Zweitens das offene Wasser.</b> Weil der Wurf damit nicht mehr am Schatzfund haengt,
 * traegt er dessen Bedingung selbst. Sie ist nicht abgeschrieben, sondern wird gegen Mojangs
 * echten Schatzeintrag geprueft — aendert Mojang sie, faellt es auf.
 */
class FishingMendingInjectionTest {

    private static final String EINGESPEIST =
            "/data/mchelden/loot_table/inject/fishing_mending.json";
    private static final String MODIFIER =
            "/data/mchelden/loot_modifiers/fishing_mending.json";
    private static final String REGISTRIERUNG =
            "/data/neoforge/loot_modifiers/global_loot_modifiers.json";

    private static final String ANGELN_IM_JAR = "data/minecraft/loot_table/gameplay/fishing.json";

    private static final String ANGELTABELLE = "minecraft:gameplay/fishing";
    private static final String SCHATZTABELLE = "minecraft:gameplay/fishing/treasure";

    /** Ein Buch pro rund vierhundert Zuegen — mit Koeder III etwa eine Stunde. */
    private static final double CHANCE = 0.0025;

    private static JsonObject lade(String pfad) throws IOException {
        try (InputStream in = FishingMendingInjectionTest.class.getResourceAsStream(pfad)) {
            assertNotNull(in, "nicht im Klassenpfad: " + pfad);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static JsonObject einzigerPool() throws IOException {
        JsonArray pools = lade(EINGESPEIST).getAsJsonArray("pools");
        assertEquals(1, pools.size(), "genau ein Pool");
        return pools.get(0).getAsJsonObject();
    }

    /** Die Bedingung eines Pools an ihrem {@code condition}-Typ. */
    private static JsonObject bedingung(JsonObject pool, String typ) {
        for (JsonElement element : pool.getAsJsonArray("conditions")) {
            JsonObject term = element.getAsJsonObject();
            if (typ.equals(term.get("condition").getAsString())) {
                return term;
            }
        }
        throw new AssertionError("keine Bedingung " + typ + " in " + pool);
    }

    /** Mojangs Bedingung am Schatzeintrag der Angeltabelle. */
    private static JsonObject offenesWasserBeiMojang() throws IOException {
        Optional<JsonObject> angeln = VanillaData.load(ANGELN_IM_JAR);
        Assumptions.assumeTrue(angeln.isPresent(), VanillaData.diagnose());

        for (JsonElement poolElement : angeln.get().getAsJsonArray("pools")) {
            for (JsonElement entryElement : poolElement.getAsJsonObject().getAsJsonArray("entries")) {
                JsonObject entry = entryElement.getAsJsonObject();
                if (entry.has("value") && SCHATZTABELLE.equals(entry.get("value").getAsString())) {
                    JsonArray conditions = entry.getAsJsonArray("conditions");
                    assertEquals(1, conditions.size(), "Mojangs Schatzeintrag hat genau eine Bedingung");
                    return conditions.get(0).getAsJsonObject();
                }
            }
        }
        throw new AssertionError("kein Schatzeintrag in Mojangs Angeltabelle");
    }

    @Test
    void derModifierHaengtAnDerAngeltabelle() throws IOException {
        JsonArray conditions = lade(MODIFIER).getAsJsonArray("conditions");
        assertEquals(1, conditions.size());

        JsonObject term = conditions.get(0).getAsJsonObject();
        assertEquals("neoforge:loot_table_id", term.get("condition").getAsString());
        assertEquals(ANGELTABELLE, term.get("loot_table_id").getAsString(),
                "an der Schatztabelle waere der Modifier wirkungslos");
    }

    @Test
    void derModifierZeigtAufDieEingespeisteTabelle() throws IOException {
        JsonObject modifier = lade(MODIFIER);
        assertEquals("neoforge:add_table", modifier.get("type").getAsString());
        assertEquals("mchelden:inject/fishing_mending", modifier.get("table").getAsString());
    }

    /** Ohne Eintrag in dieser Datei laedt NeoForge den Modifier gar nicht erst. */
    @Test
    void derModifierIstBeiNeoForgeAngemeldet() throws IOException {
        JsonObject registrierung = lade(REGISTRIERUNG);
        assertTrue(registrierung.getAsJsonArray("entries").contains(
                        JsonParser.parseString("\"mchelden:fishing_mending\"")),
                "nicht angemeldet: " + registrierung);
    }

    /**
     * Der Angelhaken steht nur im Parametersatz {@code minecraft:fishing} zur Verfuegung.
     * Mit einem anderen Typ verweigert Minecraft die Tabelle beim Laden.
     */
    @Test
    void dieEingespeisteTabelleIstEineAngeltabelle() throws IOException {
        assertEquals("minecraft:fishing", lade(EINGESPEIST).get("type").getAsString());
    }

    @Test
    void esWirdGenauEinmalGezogen() throws IOException {
        assertEquals(1.0, einzigerPool().get("rolls").getAsDouble(), 1.0e-9);
    }

    @Test
    void dieChanceIstEinBuchProVierhundertZuege() throws IOException {
        JsonObject zufall = bedingung(einzigerPool(), "minecraft:random_chance");
        assertEquals(CHANCE, zufall.get("chance").getAsDouble(), 1.0e-9);
    }

    @Test
    void nurImOffenenWasser() throws IOException {
        assertEquals(offenesWasserBeiMojang(),
                bedingung(einzigerPool(), "minecraft:entity_properties"),
                "Bedingung weicht von Mojangs Schatzeintrag ab");
    }

    @Test
    void esFaelltEinBuchMitGenauMending() throws IOException {
        JsonArray entries = einzigerPool().getAsJsonArray("entries");
        assertEquals(1, entries.size());

        JsonObject entry = entries.get(0).getAsJsonObject();
        assertEquals("minecraft:item", entry.get("type").getAsString());
        assertEquals("minecraft:enchanted_book", entry.get("name").getAsString());

        JsonArray functions = entry.getAsJsonArray("functions");
        assertEquals(1, functions.size());

        JsonObject verzaubern = functions.get(0).getAsJsonObject();
        assertEquals("minecraft:set_enchantments", verzaubern.get("function").getAsString());

        JsonObject enchantments = verzaubern.getAsJsonObject("enchantments");
        assertEquals(1, enchantments.size(), "genau Mending, sonst nichts");
        assertEquals(1, enchantments.get("minecraft:mending").getAsInt());
    }
}
