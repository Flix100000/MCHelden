package net.bananemdnsa.mchelden;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Servereinstellungen der Mod.
 *
 * <p>Bewusst sehr klein gehalten. Was sich im Spiel per Befehl aendern laesst, gehoert in
 * den Spielzustand und nicht in eine Datei — sonst gibt es zwei Wahrheiten, die
 * auseinanderlaufen koennen. Hier steht nur, womit eine <em>frische</em> Welt beginnt: das
 * laesst sich vor dem ersten Start setzen, wo es noch keinen Befehl geben kann.
 */
public final class MCHeldenConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Weiteste Ausdehnung einer Minecraft-Welt. Weiter draussen gibt es nichts mehr. */
    private static final int WORLD_LIMIT = 30_000_000;

    public static final ModConfigSpec.IntValue CENTER_X;
    public static final ModConfigSpec.IntValue CENTER_Z;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                        "Wo die Arena liegt: Safezone, Trennwand und Weltborder haengen daran.",
                        "Gilt beim allerersten Start einer Welt und fuer '/helden center reset'.",
                        "Im laufenden Spiel verschiebt '/helden center <x> <z>' die Arena;",
                        "der Wert wird dann in der Welt gespeichert und nicht hier.")
                .push("arena");

        CENTER_X = BUILDER
                .comment("X-Koordinate der Arenamitte.")
                .defineInRange("center_x", 0, -WORLD_LIMIT, WORLD_LIMIT);
        CENTER_Z = BUILDER
                .comment("Z-Koordinate der Arenamitte.")
                .defineInRange("center_z", 0, -WORLD_LIMIT, WORLD_LIMIT);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private MCHeldenConfig() {
    }
}
