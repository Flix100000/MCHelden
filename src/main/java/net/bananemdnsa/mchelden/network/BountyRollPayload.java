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
 * Startet beim Empfaenger das Gluecksrad.
 *
 * <p>Die Kopf-Abfolge steht bewusst nicht drin: die baut der Client aus seiner Spielerliste,
 * sonst gingen bei zwanzig Spielern zwanzig UUIDs ueber die Leitung, damit jeder dasselbe
 * sieht, was ohnehin niemand vergleichen kann.
 *
 * <p>Ein leeres Ziel bedeutet: bei ungerader Spielerzahl leer ausgegangen. Der Lauf
 * findet trotzdem statt und haelt auf der Fragezeichen-Kachel.
 */
public record BountyRollPayload(Optional<UUID> targetId, String targetName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BountyRollPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "bounty_roll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BountyRollPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), BountyRollPayload::targetId,
                    ByteBufCodecs.STRING_UTF8, BountyRollPayload::targetName,
                    BountyRollPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
