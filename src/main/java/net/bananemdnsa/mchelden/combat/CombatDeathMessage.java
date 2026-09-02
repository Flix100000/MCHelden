package net.bananemdnsa.mchelden.combat;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Todesnachrichten für Tode im Combat-Timer.
 *
 * <p>Ohne das behauptet der Chat etwas anderes als die Regel: es steht dann
 * {@code X ist zu tief gefallen}, obwohl der Kampf lief und der Sturz ein Herz gekostet hat.
 * Genau daraus entstehen Diskussionen, weil niemand versteht, warum das Leben weg ist.
 */
public final class CombatDeathMessage {
    private CombatDeathMessage() {
    }

    /**
     * Die passende Nachricht, oder {@code null} wenn Vanilla übernehmen soll.
     *
     * <p>Bei einem direkten Kill durch den Gegner bleibt Vanilla zuständig — dessen
     * Nachrichten sind dort bereits richtig und kennen sogar die verwendete Waffe.
     *
     * <p>Im Duell gilt das nicht: dass es ein Duell war, steht in keiner Vanilla-Nachricht,
     * und ohne diesen Hinweis versteht der Chat nicht, warum kein Herz gefallen ist.
     */
    @Nullable
    public static Component of(ServerPlayer victim) {
        CombatTracker.Death death = CombatTracker.consumeDeath(victim.getUUID());
        if (death == null) {
            return null;
        }

        DamageSource source = victim.getLastDamageSource();
        boolean directKill = source != null && source.getEntity() instanceof ServerPlayer killer
                && killer.getGameProfile().getName().equals(death.opponent());

        if (directKill && !death.duel()) {
            return null;
        }

        return Component.translatable(
                death.duel() ? duelKeyFor(source, directKill) : keyFor(source),
                victim.getGameProfile().getName(), death.opponent());
    }

    /** Dieselbe Aufteilung fuer das Duell, plus der Fall, den es im Kampf nicht gibt. */
    private static String duelKeyFor(@Nullable DamageSource source, boolean directKill) {
        if (directKill) {
            return "mchelden.death.duel.killed";
        }
        if (source == null) {
            return "mchelden.death.duel.generic";
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return "mchelden.death.duel.fall";
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return "mchelden.death.duel.drown";
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return "mchelden.death.duel.fire";
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return "mchelden.death.duel.explosion";
        }
        return "mchelden.death.duel.generic";
    }

    private static String keyFor(@Nullable DamageSource source) {
        if (source == null) {
            return "mchelden.death.generic";
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return "mchelden.death.fall";
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return "mchelden.death.drown";
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return "mchelden.death.fire";
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return "mchelden.death.explosion";
        }
        return "mchelden.death.generic";
    }
}
