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
 * Sand soll Quarz geben wie Kies Feuerstein — nur doppelt so oft.
 *
 * <p>"Doppelt so oft" ist keine einzelne Zahl. Kies hat eine Staffel ueber die
 * Glueck-Stufen, und bei Stufe III steht er bereits auf hundert Prozent: dort ist nichts
 * mehr zu verdoppeln. Wer die Staffel von Hand abschreibt, uebersieht entweder die
 * Deckelung oder den krummen Wert bei Stufe I.
 *
 * <p>Die erwartete Staffel wird deswegen aus Mojangs echter Kiestabelle abgeleitet statt
 * abgeschrieben. Aendert Mojang die Feuerstein-Chance, faellt es hier auf.
 */
class SandQuartzInjectionTest {

    private static final String EINGESPEIST =
            "/data/mchelden/loot_table/inject/sand_quartz.json";
    private static final String MODIFIER =
            "/data/mchelden/loot_modifiers/sand_quartz.json";
    private static final String REGISTRIERUNG =
            "/data/neoforge/loot_modifiers/global_loot_modifiers.json";

    private static final String KIES_IM_JAR = "data/minecraft/loot_table/blocks/gravel.json";

    private static final Set<String> SANDTABELLEN = Set.of(
            "minecraft:blocks/sand",
            "minecraft:blocks/red_sand");

    private static JsonObject lade(String pfad) throws IOException {
        try (InputStream in = SandQuartzInjectionTest.class.getResourceAsStream(pfad)) {
            assertNotNull(in, "nicht im Klassenpfad: " + pfad);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static JsonArray bedingungen() throws IOException {
        JsonArray pools = lade(EINGESPEIST).getAsJsonArray("pools");
        assertEquals(1, pools.size(), "genau ein Pool");
        return pools.get(0).getAsJsonObject().getAsJsonArray("conditions");
    }

    /** Sucht eine Bedingung an ihrem Typ, auch in einem umgekehrten oder verknuepften Term. */
    private static JsonObject bedingung(JsonArray terme, String typ) {
        for (JsonElement element : terme) {
            JsonObject treffer = suche(element.getAsJsonObject(), typ);
            if (treffer != null) {
                return treffer;
            }
        }
        throw new AssertionError("keine Bedingung " + typ + " in " + terme);
    }

    private static JsonObject suche(JsonObject term, String typ) {
        if (typ.equals(term.get("condition").getAsString())) {
            return term;
        }
        if (term.has("term")) {
            return suche(term.getAsJsonObject("term"), typ);
        }
        if (term.has("terms")) {
            for (JsonElement element : term.getAsJsonArray("terms")) {
                JsonObject treffer = suche(element.getAsJsonObject(), typ);
                if (treffer != null) {
                    return treffer;
                }
            }
        }
        return null;
    }

    /** Unsere Staffel ueber die Glueck-Stufen. */
    private static JsonArray unsereStaffel() throws IOException {
        return bedingung(bedingungen(), "minecraft:table_bonus").getAsJsonArray("chances");
    }

    /** Mojangs Feuerstein-Staffel aus der Kiestabelle. */
    private static double[] feuersteinBeiMojang() throws IOException {
        Optional<JsonObject> kies = VanillaData.load(KIES_IM_JAR);
        Assumptions.assumeTrue(kies.isPresent(), VanillaData.diagnose());

        JsonObject staffel = staffelBeiFeuerstein(kies.get());
        assertNotNull(staffel, "keine Feuerstein-Staffel in Mojangs Kiestabelle");

        JsonArray chances = staffel.getAsJsonArray("chances");
        double[] werte = new double[chances.size()];
        for (int i = 0; i < werte.length; i++) {
            werte[i] = chances.get(i).getAsDouble();
        }
        return werte;
    }

    /** Steigt durch die verschachtelten Alternativen der Kiestabelle bis zum Feuerstein hinab. */
    private static JsonObject staffelBeiFeuerstein(JsonElement knoten) {
        if (knoten.isJsonArray()) {
            for (JsonElement element : knoten.getAsJsonArray()) {
                JsonObject treffer = staffelBeiFeuerstein(element);
                if (treffer != null) {
                    return treffer;
                }
            }
            return null;
        }

        JsonObject objekt = knoten.getAsJsonObject();
        if (objekt.has("name") && "minecraft:flint".equals(objekt.get("name").getAsString())) {
            return bedingung(objekt.getAsJsonArray("conditions"), "minecraft:table_bonus");
        }
        for (String feld : new String[] {"pools", "entries", "children"}) {
            if (objekt.has(feld)) {
                JsonObject treffer = staffelBeiFeuerstein(objekt.getAsJsonArray(feld));
                if (treffer != null) {
                    return treffer;
                }
            }
        }
        return null;
    }

    private static Set<String> zieltabellen(JsonObject modifier) {
        Set<String> ids = new HashSet<>();
        sammle(modifier.getAsJsonArray("conditions"), ids);
        return ids;
    }

    /** Die Bedingungen sind geschachtelt: ein any_of mit den einzelnen Tabellen darin. */
    private static void sammle(JsonArray terme, Set<String> ids) {
        for (JsonElement element : terme) {
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
    void beideSandsortenSindEingetragen() throws IOException {
        assertEquals(SANDTABELLEN, zieltabellen(lade(MODIFIER)));
    }

    @Test
    void derModifierZeigtAufDieEingespeisteTabelle() throws IOException {
        JsonObject modifier = lade(MODIFIER);
        assertEquals("neoforge:add_table", modifier.get("type").getAsString());
        assertEquals("mchelden:inject/sand_quartz", modifier.get("table").getAsString());
    }

    /** Ohne Eintrag in dieser Datei laedt NeoForge den Modifier gar nicht erst. */
    @Test
    void derModifierIstBeiNeoForgeAngemeldet() throws IOException {
        JsonObject registrierung = lade(REGISTRIERUNG);
        assertTrue(registrierung.getAsJsonArray("entries").contains(
                        JsonParser.parseString("\"mchelden:sand_quartz\"")),
                "nicht angemeldet: " + registrierung);
    }

    @Test
    void esWirdGenauEinmalGezogen() throws IOException {
        JsonArray pools = lade(EINGESPEIST).getAsJsonArray("pools");
        assertEquals(1, pools.size());
        assertEquals(1.0, pools.get(0).getAsJsonObject().get("rolls").getAsDouble(), 1.0e-9);
    }

    @Test
    void esFaelltQuarz() throws IOException {
        JsonArray entries = lade(EINGESPEIST).getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries");
        assertEquals(1, entries.size());

        JsonObject entry = entries.get(0).getAsJsonObject();
        assertEquals("minecraft:item", entry.get("type").getAsString());
        assertEquals("minecraft:quartz", entry.get("name").getAsString());
    }

    /** Eine feste Chance wuerde Glueck ignorieren — die Staffel ist der ganze Punkt. */
    @Test
    void dieChanceHaengtAmGlueck() throws IOException {
        JsonObject staffel = bedingung(bedingungen(), "minecraft:table_bonus");
        assertEquals("minecraft:fortune", staffel.get("enchantment").getAsString());
    }

    @Test
    void jedeStufeIstDasDoppelteVonKies() throws IOException {
        double[] kies = feuersteinBeiMojang();
        JsonArray unsere = unsereStaffel();

        assertEquals(kies.length, unsere.size(), "gleich viele Glueck-Stufen wie Kies");
        for (int stufe = 0; stufe < kies.length; stufe++) {
            assertEquals(Math.min(1.0, kies[stufe] * 2.0), unsere.get(stufe).getAsDouble(), 1.0e-7,
                    "Glueck " + stufe);
        }
    }

    /** Bei Glueck III steht schon Kies auf hundert Prozent — mehr als immer geht nicht. */
    @Test
    void beiGlueckDreiIstGedeckelt() throws IOException {
        JsonArray unsere = unsereStaffel();
        assertEquals(1.0, unsere.get(unsere.size() - 1).getAsDouble(), 1.0e-9);
    }

    /** Wie beim Kies: mit Behutsamkeit kommt der Block, sonst nichts. */
    @Test
    void behutsamkeitSchaltetDenQuarzAb() throws IOException {
        JsonObject umgekehrt = bedingung(bedingungen(), "minecraft:inverted");
        assertEquals("minecraft:match_tool",
                umgekehrt.getAsJsonObject("term").get("condition").getAsString());
        assertTrue(umgekehrt.toString().contains("minecraft:silk_touch"),
                "die umgekehrte Bedingung meint nicht Behutsamkeit: " + umgekehrt);
    }

    /** Gesprengter Sand verbrennt; sein Quarz darf nicht liegen bleiben. */
    @Test
    void gesprengterSandGibtKeinenQuarz() throws IOException {
        bedingung(bedingungen(), "minecraft:survives_explosion");
    }
}
