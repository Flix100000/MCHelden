package net.bananemdnsa.mchelden.network;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Startschuss fuer den Zerfall der Safezone-Kuppel.
 *
 * <p>Drei Stufen statt eines Schalters, weil der Vorgang drei Momente hat: das Aufziehen
 * im Countdown, den Bruch, und den Abbruch, wenn ein Op den Phasenwechsel zuruecknimmt.
 * Ohne den Abbruch bliebe eine gluehende Kuppel stehen, die nie zerbricht.
 *
 * <p>Die Dauer steht nicht drin — die kennt der Client aus {@code SafeZoneShatter}, damit
 * Scherben und Zeitrechnung nicht auseinanderlaufen koennen. Dieselbe Ueberlegung wie beim
 * {@link WallDropPayload}.
 */
public record SafeZoneShatterPayload(Stage stage) implements CustomPacketPayload {

    /** Aufziehen, Bruch, Abbruch. */
    public enum Stage {
        ARM,
        BREAK,
        CANCEL
    }

    public static final CustomPacketPayload.Type<SafeZoneShatterPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "safezone_shatter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafeZoneShatterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(SafeZoneShatterPayload::byIndex, Stage::ordinal),
                    SafeZoneShatterPayload::stage,
                    SafeZoneShatterPayload::new);

    /**
     * Ein unbekannter Wert wird zum Abbruch, nicht zum Bruch.
     *
     * <p>Kommt ein Paket kaputt an, ist eine stehende Kuppel der harmlosere Fehler als
     * eine, die grundlos zerspringt.
     */
    private static Stage byIndex(int index) {
        Stage[] all = Stage.values();
        return index >= 0 && index < all.length ? all[index] : Stage.CANCEL;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
