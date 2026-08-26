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
     */
    @Nullable
    public static Component of(ServerPlayer victim) {
        String opponent = CombatTracker.consumeCombatDeath(victim.getUUID());
        if (opponent == null) {
            return null;
        }

        DamageSource source = victim.getLastDamageSource();
        if (source != null && source.getEntity() instanceof ServerPlayer killer
                && killer.getGameProfile().getName().equals(opponent)) {
            return null;
        }

        return Component.translatable(keyFor(source),
                victim.getGameProfile().getName(), opponent);
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
