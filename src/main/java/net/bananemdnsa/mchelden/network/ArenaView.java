package net.bananemdnsa.mchelden.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wo die Arena liegt, aus Sicht des Clients.
 *
 * <p>Eigener Record statt zweier weiterer Felder im {@link StateSyncPayload} — aus demselben
 * Grund wie beim {@link BountyView}: dessen {@code composite} hoert bei sechs Feldern auf.
 *
 * <p>Der Client braucht die Mitte, weil Kuppel und Trennwand bei ihm gezeichnet werden und
 * die Kollision auf beiden Seiten laeuft. Ohne sie stuende beides weiter bei 0,0.
 */
public record ArenaView(double centerX, double centerZ) {

    /** Die Weltmitte. Solange niemand etwas verschiebt, gilt die. */
    public static final ArenaView ORIGIN = new ArenaView(0.0, 0.0);

    public static final StreamCodec<ByteBuf, ArenaView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ArenaView::centerX,
            ByteBufCodecs.DOUBLE, ArenaView::centerZ,
            ArenaView::new);
}
