package net.bananemdnsa.mchelden.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Was der Client ueber die Spielzeit-Uhr wissen muss.
 *
 * <p>Eigener Record statt eines weiteren Feldes im {@link StateSyncPayload}: dessen
 * {@code composite} hoert bei sechs Feldern auf — siehe {@link BountyView}, wo genau
 * dieselbe Grenze schon einmal zu einem eigenen Record gefuehrt hat.
 */
public record PlaytimeView(int remainingSeconds, boolean paused) {

    public static final StreamCodec<ByteBuf, PlaytimeView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlaytimeView::remainingSeconds,
            ByteBufCodecs.BOOL, PlaytimeView::paused,
            PlaytimeView::new);
}
