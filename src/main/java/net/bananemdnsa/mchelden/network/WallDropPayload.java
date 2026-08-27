package net.bananemdnsa.mchelden.network;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Startet oder stoppt das Aufbrechen der Trennwand.
 *
 * <p>Die Wand faellt nicht weg, sie bricht: von 0,0 nach aussen, hinter der Partikelwelle
 * her. Ohne dieses Paket wuesste der Client nur, dass sie irgendwann nicht mehr da ist —
 * und wuerde sie im letzten Moment einfach ausblenden.
 *
 * <p>{@code dropping = false} bricht den Vorgang ab. Das wird gebraucht, wenn ein Op den
 * Phasenwechsel mitten im Countdown zuruecknimmt: ohne den Abbruch bliebe eine Luecke in
 * der Wand stehen, die sich nie wieder schliesst.
 *
 * <p>Die Geschwindigkeit steht nicht drin — die kennt der Client aus {@code DividerWall},
 * damit Bruchkante und Funken nicht auseinanderlaufen koennen.
 */
public record WallDropPayload(boolean dropping) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WallDropPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "wall_drop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WallDropPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, WallDropPayload::dropping,
                    WallDropPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
