package net.bananemdnsa.mchelden.network;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Der Combat-Timer, nur bei Änderungen verschickt.
 *
 * <p>Der Client zählt selbst herunter. Jeden Tick zu synchronisieren wären bei zwanzig
 * Spielern vierhundert Pakete pro Sekunde für eine Zahl, die der Client ausrechnen kann.
 * Einmal pro Sekunde wird der Stand aber nachgezogen: der Client zaehlt in echter Zeit,
 * dieser Timer in Serverticks, und die fallen unter Last dahinter zurueck.
 *
 * @param remainingTicks verbleibende Ticks, 0 bedeutet ausserhalb des Kampfes
 * @param pearlsLeft verbleibende Enderperlen im Kontingent
 * @param cobwebsLeft verbleibende Spinnweben im Kontingent
 * @param hit steht ein Treffer dahinter? Nur dann leuchtet der Balken auf. Am Wert allein
 *            liesse sich das nicht ablesen — eine Korrektur schiebt ihn ebenfalls nach oben
 */
public record CombatSyncPayload(int remainingTicks, int pearlsLeft, int cobwebsLeft, boolean hit)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CombatSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "combat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CombatSyncPayload::remainingTicks,
                    ByteBufCodecs.VAR_INT, CombatSyncPayload::pearlsLeft,
                    ByteBufCodecs.VAR_INT, CombatSyncPayload::cobwebsLeft,
                    ByteBufCodecs.BOOL, CombatSyncPayload::hit,
                    CombatSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
