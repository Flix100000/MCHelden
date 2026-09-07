package net.bananemdnsa.mchelden.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 * Seelensand aus Sand und Quarz: vier Sand im Kreuz um einen Nether-Quarz, vier Seelensand
 * zurueck.
 *
 * <p>Drei Dinge an so einem Rezept fallen im Spiel nicht auf, wenn sie falsch sind. Erstens
 * die Ecken: schreibt man das Muster als volle Reihen, ist es ploetzlich ein Quadrat aus acht
 * Sand und verbraucht das Doppelte, ohne dass die Datei kaputt aussieht. Zweitens die
 * Freischaltung: ohne das Fortschritts-Rezept in {@code advancement/recipes} laesst sich das
 * Rezept zwar von Hand legen, taucht im Rezeptbuch aber nie auf — und niemand legt ein Rezept
 * von Hand, von dem er nichts weiss.
 *
 * <p>Drittens die Schreibweise der Zutaten. Sie hat sich zwischen den Versionen mehrfach
 * geaendert, und eine Zutat im falschen Format laedt nicht: das Rezept fehlt dann einfach,
 * ohne dass im Spiel etwas darauf hinweist. Erschwerend kommt hinzu, dass eine Stelle hier
 * zwei Sandsorten annimmt, was noch einmal anders geschrieben wird als eine einzelne Zutat.
 * Statt das Format abzuschreiben, wird es gegen Mojangs TNT-Rezept geprueft: dort steht
 * genau dieselbe Auswahl aus Sand und rotem Sand, in derselben Version.
 */
class SoulSandRecipeTest {

    private static final String REZEPT = "/data/mchelden/recipe/soul_sand.json";
    private static final String FREISCHALTUNG =
            "/data/mchelden/advancement/recipes/building_blocks/soul_sand.json";

    private static final String REZEPT_ID = "mchelden:soul_sand";

    private static final String TNT_IM_JAR = "data/minecraft/recipe/tnt.json";

    /** Der Quarz-aus-Sand-Loot kennt beide Sandsorten; das Rezept soll es genauso halten. */
    private static final Set<String> SANDSORTEN =
            Set.of("minecraft:sand", "minecraft:red_sand");

    private static JsonObject lade(String pfad) throws IOException {
        try (InputStream in = SoulSandRecipeTest.class.getResourceAsStream(pfad)) {
            assertNotNull(in, "nicht im Klassenpfad: " + pfad);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    /** Das Muster als Liste der drei Zeilen. */
    private static List<String> muster() throws IOException {
        JsonArray pattern = lade(REZEPT).getAsJsonArray("pattern");
        List<String> zeilen = new ArrayList<>();
        for (JsonElement zeile : pattern) {
            zeilen.add(zeile.getAsString());
        }
        return zeilen;
    }

    /** Der Rohwert, den ein Zeichen des Musters im Schluessel hat. */
    private static JsonElement schluessel(char zeichen) throws IOException {
        JsonObject key = lade(REZEPT).getAsJsonObject("key");
        JsonElement eintrag = key.get(String.valueOf(zeichen));
        assertNotNull(eintrag, "kein Schluessel fuer '" + zeichen + "' in " + key);
        return eintrag;
    }

    /** Loest ein Zeichen des Musters zu den Gegenstaenden auf, die dort passen. */
    private static Set<String> zutaten(char zeichen) throws IOException {
        JsonElement eintrag = schluessel(zeichen);
        Set<String> gegenstaende = new LinkedHashSet<>();
        if (eintrag.isJsonArray()) {
            for (JsonElement auswahl : eintrag.getAsJsonArray()) {
                gegenstaende.add(auswahl.getAsJsonObject().get("item").getAsString());
            }
        } else {
            gegenstaende.add(eintrag.getAsJsonObject().get("item").getAsString());
        }
        return gegenstaende;
    }

    /** Mojangs Sand-Auswahl aus dem TNT-Rezept: beide Sandsorten an einer Stelle. */
    private static JsonElement sandBeiMojang() throws IOException {
        Optional<JsonObject> tnt = VanillaData.load(TNT_IM_JAR);
        Assumptions.assumeTrue(tnt.isPresent(), VanillaData.diagnose());

        for (var eintrag : tnt.get().getAsJsonObject("key").entrySet()) {
            if (SANDSORTEN.equals(gegenstaende(eintrag.getValue()))) {
                return eintrag.getValue();
            }
        }
        throw new AssertionError("keine Sand-Auswahl in Mojangs TNT-Rezept: " + tnt.get());
    }

    /** Wie {@link #zutaten(char)}, aber fuer einen fremden Schluesseleintrag. */
    private static Set<String> gegenstaende(JsonElement eintrag) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement auswahl : eintrag.isJsonArray()
                ? eintrag.getAsJsonArray() : new JsonArray()) {
            ids.add(auswahl.getAsJsonObject().get("item").getAsString());
        }
        return ids;
    }

    @Test
    void esIstEinGeformtesRezept() throws IOException {
        assertEquals("minecraft:crafting_shaped", lade(REZEPT).get("type").getAsString());
    }

    @Test
    void inDerMitteLiegtQuarz() throws IOException {
        assertEquals(Set.of("minecraft:quartz"), zutaten(muster().get(1).charAt(1)));
    }

    @Test
    void obenUntenLinksUndRechtsLiegtSand() throws IOException {
        List<String> muster = muster();
        assertEquals(SANDSORTEN, zutaten(muster.get(0).charAt(1)), "oben");
        assertEquals(SANDSORTEN, zutaten(muster.get(2).charAt(1)), "unten");
        assertEquals(SANDSORTEN, zutaten(muster.get(1).charAt(0)), "links");
        assertEquals(SANDSORTEN, zutaten(muster.get(1).charAt(2)), "rechts");
    }

    /** Volle Reihen statt eines Kreuzes wuerden vier Sand mehr kosten. */
    @Test
    void dieEckenBleibenFrei() throws IOException {
        List<String> muster = muster();
        assertEquals(3, muster.size(), "drei Zeilen");
        for (String zeile : muster) {
            assertEquals(3, zeile.length(), "drei Spalten: " + zeile);
        }
        for (int zeile : new int[] {0, 2}) {
            for (int spalte : new int[] {0, 2}) {
                assertEquals(' ', muster.get(zeile).charAt(spalte),
                        "Ecke " + zeile + "/" + spalte + " ist belegt: " + muster);
            }
        }
    }

    @Test
    void esKommenVierSeelensandHeraus() throws IOException {
        JsonObject ergebnis = lade(REZEPT).getAsJsonObject("result");
        assertEquals("minecraft:soul_sand", ergebnis.get("id").getAsString());
        assertEquals(4, ergebnis.get("count").getAsInt());
    }

    /** Dieselbe Auswahl wie bei Mojang, also auch dieselbe Schreibweise. */
    @Test
    void derSandStehtDaWieBeiMojang() throws IOException {
        JsonElement mojang = sandBeiMojang();
        char sandzeichen = muster().get(0).charAt(1);
        assertEquals(mojang, schluessel(sandzeichen));
    }

    /** Ohne diese Datei steht das Rezept nie im Rezeptbuch. */
    @Test
    void dasRezeptWirdFreigeschaltet() throws IOException {
        JsonObject freischaltung = lade(FREISCHALTUNG);
        assertTrue(freischaltung.getAsJsonObject("rewards").getAsJsonArray("recipes")
                        .contains(JsonParser.parseString("\"" + REZEPT_ID + "\"")),
                "die Freischaltung gibt das Rezept nicht her: " + freischaltung);
    }

    /** Quarz ist die seltenere Haelfte — an ihr haengt die Freischaltung. */
    @Test
    void quarzImInventarSchaltetFrei() throws IOException {
        JsonObject kriterien = lade(FREISCHALTUNG).getAsJsonObject("criteria");
        assertTrue(kriterien.toString().contains("minecraft:quartz"),
                "kein Quarz unter den Kriterien: " + kriterien);
        assertEquals(REZEPT_ID, kriterien.getAsJsonObject("has_the_recipe")
                .getAsJsonObject("conditions").get("recipe").getAsString());
    }
}
