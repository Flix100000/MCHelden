package net.bananemdnsa.mchelden.network;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Loest die Ansage beim Ausscheiden aus.
 *
 * <p>Frueher waren das zwei Vanilla-Titelpakete. Die kann der Client nur vierfach
 * vergroessert zeichnen, womit die Zeile aus dem Bild lief — deswegen gehen jetzt nur die
 * beiden Namen hinueber, und der Client baut und bemisst die Ansage selbst.
 *
 * <p>Ein leerer Killer bedeutet: niemand hat den Treffer gesetzt. Dann faellt die zweite
 * Zeile weg, statt ins Leere zu zeigen.
 */
public record EliminationPayload(String victim, String killer) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EliminationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCHelden.MODID, "elimination"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EliminationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, EliminationPayload::victim,
                    ByteBufCodecs.STRING_UTF8, EliminationPayload::killer,
                    EliminationPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
