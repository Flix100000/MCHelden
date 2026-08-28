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
        // Englisch, anders als die Kommentare im Code: diese Zeilen landen in der
        // TOML-Datei und werden von Serverbetreibern gelesen, nicht von uns.
        BUILDER.comment(
                        "Where the arena sits: safe zone, dividing wall and world border all hang off it.",
                        "Applies on a world's very first start, and to '/helden center reset'.",
                        "While the game runs, '/helden center <x> <z>' moves the arena, and that",
                        "value is stored with the world rather than here.")
                .push("arena");

        CENTER_X = BUILDER
                .comment("X coordinate of the arena centre.")
                .defineInRange("center_x", 0, -WORLD_LIMIT, WORLD_LIMIT);
        CENTER_Z = BUILDER
                .comment("Z coordinate of the arena centre.")
                .defineInRange("center_z", 0, -WORLD_LIMIT, WORLD_LIMIT);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private MCHeldenConfig() {
    }
}
