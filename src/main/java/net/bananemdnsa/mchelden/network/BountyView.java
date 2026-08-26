package net.bananemdnsa.mchelden.network;

import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Alles, was der Client ueber das eigene Bounty wissen muss.
 *
 * <p>Eigener Record statt vier weiterer Felder im {@link StateSyncPayload}: dessen
 * {@code composite} hoert bei sechs Feldern auf.
 *
 * <p>{@code targetEliminated} ist bewusst nicht gespeichert, sondern wird beim Verschicken
 * aus dem Zustand des Ziels abgeleitet. Ein {@code /helden revive} hebt die Ausgrauung im
 * HUD damit von selbst wieder auf.
 */
public record BountyView(String targetName, Optional<UUID> targetId, boolean resolved,
                         boolean targetEliminated) {

    /** Vor dem Roll: kein Ziel, nichts aufgeloest. Das HUD zeigt dann das Fragezeichen. */
    public static final BountyView NONE = new BountyView("", Optional.empty(), false, false);

    public static final StreamCodec<ByteBuf, BountyView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BountyView::targetName,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), BountyView::targetId,
            ByteBufCodecs.BOOL, BountyView::resolved,
            ByteBufCodecs.BOOL, BountyView::targetEliminated,
            BountyView::new);
}
