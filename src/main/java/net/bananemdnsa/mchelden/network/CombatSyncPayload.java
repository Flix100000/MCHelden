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
 *
 * @param remainingTicks verbleibende Ticks, 0 bedeutet ausserhalb des Kampfes
 * @param pearlsLeft verbleibende Enderperlen im Kontingent
 * @param cobwebsLeft verbleibende Spinnweben im Kontingent
 */
public record CombatSyncPayload(int remainingTicks, int pearlsLeft, int cobwebsLeft)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CombatSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "combat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CombatSyncPayload::remainingTicks,
                    ByteBufCodecs.VAR_INT, CombatSyncPayload::pearlsLeft,
                    ByteBufCodecs.VAR_INT, CombatSyncPayload::cobwebsLeft,
                    CombatSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
