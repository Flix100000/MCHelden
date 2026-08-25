package net.bananemdnsa.mchelden.state;

import javax.annotation.Nullable;

public enum Phase {
    AUFBAU("aufbau", "Aufbau"),
    KRIEG("krieg", "Krieg"),
    FINAL_WAR("finalwar", "Final War");

    private final String id;
    private final String displayName;

    Phase(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
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
