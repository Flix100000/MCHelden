package net.bananemdnsa.mchelden.network;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Löst die Herzverlust-Animation aus. Getrennt vom Zustands-Sync, weil der Zustand
 * schon beim Tod stimmt, die Animation aber erst beim Respawn laufen soll — auf dem
 * Todesbildschirm sähe sie niemand.
 */
public record HeartLostPayload(int remaining) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HeartLostPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "heart_lost"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HeartLostPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HeartLostPayload::remaining,
            HeartLostPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
