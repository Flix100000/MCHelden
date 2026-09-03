package net.bananemdnsa.mchelden.network;

import java.util.Optional;
import java.util.UUID;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Der Duell-Timer, nur bei Änderungen verschickt — wie der Combat-Timer.
 *
 * <p>Die Gegner-UUID haengt mit drin, weil der Glow an ihr haengt: der Client laesst genau
 * diesen einen Spieler fuer sich leuchten. Dass sonst niemand etwas sieht, liegt daran,
 * dass bei niemandem sonst etwas ankommt — das Paket geht nur an die beiden Duellanten.
 *
 * @param remainingTicks verbleibende Ticks, 0 bedeutet: kein Duell
 * @param opponent der Duellgegner, leer ausserhalb eines Duells
 * @param hit steht ein Treffer oder ein Duellbeginn dahinter? Nur dann leuchtet der Balken
 *            auf — die Korrektur einmal pro Sekunde schiebt den Wert ebenfalls nach oben
 */
public record DuelSyncPayload(int remainingTicks, Optional<UUID> opponent, boolean hit)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DuelSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "duel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DuelSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelSyncPayload::remainingTicks,
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), DuelSyncPayload::opponent,
                    ByteBufCodecs.BOOL, DuelSyncPayload::hit,
                    DuelSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
