package net.bananemdnsa.mchelden.network;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bittet den Client, seine eigenen Zahlen zu Trennwand und Safezone auszugeben.
 *
 * <p>Was gezeichnet wird, weiss nur der Client: ob die Flaeche dort ueberhaupt als
 * vorhanden gilt, wie weit die Kamera weg ist, welche Deckkraft daraus folgt. Bleibt der
 * Bildschirm leer, ist ohne diese Zahlen jede Erklaerung geraten — und Etappe 4 hat
 * gezeigt, dass drei Vermutungen weniger klaeren als eine Messung.
 *
 * <p>Ohne Inhalt: die Frage selbst ist die ganze Nachricht.
 */
public record RenderDebugPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenderDebugPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "render_debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenderDebugPayload> STREAM_CODEC =
            StreamCodec.unit(new RenderDebugPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
