package net.bananemdnsa.mchelden.state;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/**
 * Auf welcher Haelfte der Welt ein Spieler zuhause ist.
 *
 * <p>Die Trennwand steht bei X = 0. Westen ist alles Negative, Osten alles Positive — das
 * ist keine Geschmacksfrage, sondern folgt Minecrafts Achsen, und beim Debuggen ist es
 * dieselbe Richtung wie im F3-Bildschirm.
 */
public enum Side {
    WEST("west", -1),
    EAST("east", 1);

    private final String id;
    /** Vorzeichen der X-Koordinaten dieser Seite. */
    private final int sign;

    Side(String id, int sign) {
        this.id = id;
        this.sign = sign;
    }

    public String getId() {
        return id;
    }

    public int getSign() {
        return sign;
    }

    public Component getDisplayName() {
        return Component.translatable("mchelden.side." + id);
    }

    /** Zu welcher Seite eine X-Koordinate gehoert. Genau null zaehlt als Osten. */
    public static Side of(double x) {
        return x < 0 ? WEST : EAST;
    }

    /** Liegt diese Koordinate auf meiner Seite? */
    public boolean contains(double x) {
        return of(x) == this;
    }

    @Nullable
    public static Side byId(String id) {
        for (Side side : values()) {
            if (side.id.equals(id)) {
                return side;
            }
        }
        return null;
    }
}
