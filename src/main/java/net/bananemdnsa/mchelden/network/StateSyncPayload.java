package net.bananemdnsa.mchelden.network;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Der eigene Spielerzustand, gebuendelt fuer die HUDs. Wird beim Join und nach jeder
 * Aenderung an genau den betroffenen Spieler geschickt.
 */
public record StateSyncPayload(
        int hearts,
        BountyView bounty,
        PlaytimeView playtime,
        String phaseId,
        boolean wallUp,
        ArenaView arena) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StateSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StateSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StateSyncPayload::hearts,
            BountyView.STREAM_CODEC, StateSyncPayload::bounty,
            PlaytimeView.STREAM_CODEC, StateSyncPayload::playtime,
            ByteBufCodecs.STRING_UTF8, StateSyncPayload::phaseId,
            ByteBufCodecs.BOOL, StateSyncPayload::wallUp,
            ArenaView.STREAM_CODEC, StateSyncPayload::arena,
            StateSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
