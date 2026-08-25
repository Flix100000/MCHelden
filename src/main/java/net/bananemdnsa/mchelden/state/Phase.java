package net.bananemdnsa.mchelden.state;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

public enum Phase {
    AUFBAU("aufbau"),
    KRIEG("krieg"),
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

    public static Phase byId(String id) {
        for (Phase phase : values()) {
            if (phase.id.equals(id)) {
                return phase;
            }
        }
        return AUFBAU;
    }
}
