package net.bananemdnsa.mchelden.state;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

public enum Phase {
    AUFBAU("buildup"),
    KRIEG("war"),
    FINAL_WAR("finalwar");

    private final String id;

    Phase(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable("mchelden.phase." + id);
    }

    @Nullable
    public Phase next() {
        return switch (this) {
            case AUFBAU -> KRIEG;
            case KRIEG -> FINAL_WAR;
            case FINAL_WAR -> null;
        };
    }

    /**
     * Loest eine eingetippte Kennung auf, oder {@code null}.
     *
     * <p><b>Ohne Rueckfall.</b> Frueher landete alles Unbekannte auf {@link #AUFBAU} — ein
     * vertipptes {@code phase set kreig} htte damit mitten in der Staffel die Phase
     * zurueckgeworfen, die Wand hochgezogen und die Border zurueckgesetzt, ohne ein Wort
     * dazu. Ein Command soll sich beschweren, nicht raten.
     */
    @Nullable
    public static Phase byId(String id) {
        for (Phase phase : values()) {
            if (phase.id.equals(id)) {
                return phase;
            }
        }
        return null;
    }

    /**
     * Loest eine <em>gespeicherte</em> Kennung auf. Unbekanntes faellt auf {@link #AUFBAU}.
     *
     * <p>Getrennt von {@link #byId}, weil es ein anderer Zweck ist: eine Speicherdatei darf
     * nicht ins Leere laufen, eine Tastatureingabe schon.
     *
     * <p>Die deutschen Kennungen der ersten Fassung werden mitgelesen. Sie stehen in
     * {@code mchelden_game.dat} jeder Welt, die vor der Umstellung angelegt wurde — ohne
     * das hier faenden solche Welten ihre Phase nicht mehr und stuenden nach dem Laden
     * still wieder im Aufbau. Kann weg, sobald die Staffel auf einer frischen Welt laeuft.
     */
    public static Phase bySavedId(String id) {
        Phase known = byId(id);
        if (known != null) {
            return known;
        }

        return switch (id) {
            case "aufbau" -> AUFBAU;
            case "krieg" -> KRIEG;
            default -> AUFBAU;
        };
    }
}
