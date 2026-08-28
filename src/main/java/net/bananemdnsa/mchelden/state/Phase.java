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
     * Loest eine Kennung auf. Unbekanntes faellt auf {@link #AUFBAU} zurueck.
     *
     * <p>Die deutschen Kennungen der ersten Fassung werden mitgelesen. Sie stehen in
     * {@code mchelden_game.dat} jeder Welt, die vor der Umstellung angelegt wurde — ohne
     * das hier faenden solche Welten ihre Phase nicht mehr und stuenden nach dem Laden
     * still wieder im Aufbau. Kann weg, sobald die Staffel auf einer frischen Welt laeuft.
     */
    public static Phase byId(String id) {
        for (Phase phase : values()) {
            if (phase.id.equals(id)) {
                return phase;
            }
        }

        return switch (id) {
            case "aufbau" -> AUFBAU;
            case "krieg" -> KRIEG;
            default -> AUFBAU;
        };
    }
}
