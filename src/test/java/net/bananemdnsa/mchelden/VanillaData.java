package net.bananemdnsa.mchelden;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Zugriff auf Mojangs eigene Datendateien aus dem Ressourcen-Artefakt.
 *
 * <p>Wo eine Aenderung sich an Vanilla-Werten orientiert — dieselbe Chance wie in der
 * Ancient City, dieselbe Rudelgroesse wie im End — soll der Test gegen die echte Quelle
 * pruefen statt gegen abgeschriebene Zahlen. Aendert Mojang etwas, faellt es dann auf.
 *
 * <p>Nicht ueber den Klassenpfad: die Vanilla-Daten haengen dort nicht mit drin. Und nicht
 * relativ zum Arbeitsverzeichnis: ModDevGradle laesst die Tests in
 * {@code build/minecraft-junit} laufen, nicht im Projektstamm. Deswegen wird von dort aus
 * aufwaerts gesucht.
 */
public final class VanillaData {
    private static final Path ARTEFAKTE = Path.of("build", "moddev", "artifacts");

    private VanillaData() {
    }

    /**
     * Laedt eine Datei aus dem Ressourcen-Artefakt.
     *
     * @param pfadImJar etwa {@code data/minecraft/worldgen/biome/desert.json}
     * @return leer, wenn das Artefakt nicht da ist — dann gehoert der Test uebersprungen,
     *         nicht bestanden
     */
    public static Optional<JsonObject> load(String pfadImJar) throws IOException {
        Path verzeichnis = artefaktverzeichnis();
        if (verzeichnis == null) {
            return Optional.empty();
        }

        try (DirectoryStream<Path> jars = Files.newDirectoryStream(verzeichnis, "*client-extra*.jar")) {
            for (Path jar : jars) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    ZipEntry entry = zip.getEntry(pfadImJar);
                    if (entry != null) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            return Optional.of(JsonParser
                                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                                    .getAsJsonObject());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Sagt beim Ueberspringen, wonach vergeblich gesucht wurde. */
    public static String diagnose() throws IOException {
        Path verzeichnis = artefaktverzeichnis();
        StringBuilder sb = new StringBuilder("Vanilla-Artefakt nicht gefunden. Arbeitsverzeichnis: ")
                .append(Path.of("").toAbsolutePath())
                .append(" | Artefaktverzeichnis: ").append(verzeichnis);

        if (verzeichnis != null) {
            try (DirectoryStream<Path> alle = Files.newDirectoryStream(verzeichnis)) {
                sb.append(" | drin: ");
                for (Path datei : alle) {
                    sb.append(datei.getFileName()).append(' ');
                }
            }
        }
        return sb.toString();
    }

    /** Sucht vom Arbeitsverzeichnis aufwaerts, bis das Artefaktverzeichnis auftaucht. */
    private static Path artefaktverzeichnis() {
        for (Path ordner = Path.of("").toAbsolutePath(); ordner != null; ordner = ordner.getParent()) {
            Path kandidat = ordner.resolve(ARTEFAKTE);
            if (Files.isDirectory(kandidat)) {
                return kandidat;
            }
        }
        return null;
    }
}
