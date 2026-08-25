package net.bananemdnsa.mchelden.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Alle spielersichtbaren Texte an einer Stelle.
 *
 * <p>Die Formulierungen sind vorläufig und noch nicht abgestimmt — sie stehen hier
 * gebündelt, damit eine Wortlaut-Entscheidung genau eine Datei anfasst.
 */
public final class HeldenText {
    private HeldenText() {
    }

    public static Component eliminationKickScreen() {
        return Component.literal("Du bist ausgeschieden.")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("\n\nDeine drei Herzen sind aufgebraucht.\nFür dich ist Minecraft Heroes 1 vorbei.")
                        .withStyle(ChatFormatting.GRAY));
    }

    public static Component eliminationTitle(String victim) {
        return Component.literal(victim + " ist ausgeschieden").withStyle(ChatFormatting.RED);
    }

    public static Component eliminationSubtitle(String killer) {
        return killer.isEmpty()
                ? Component.empty()
                : Component.literal("getötet von " + killer).withStyle(ChatFormatting.GRAY);
    }

    public static Component survivorCount(int alive) {
        return Component.literal("noch ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(alive)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(alive == 1 ? " Spieler übrig" : " Spieler übrig")
                        .withStyle(ChatFormatting.GRAY));
    }

    public static Component heartLost(int remaining) {
        return Component.literal("Ein Herz verloren — noch " + remaining)
                .withStyle(ChatFormatting.RED);
    }

    public static Component heartGained(int total) {
        return Component.literal("Ein Herz dazu — jetzt " + total)
                .withStyle(ChatFormatting.AQUA);
    }
}
