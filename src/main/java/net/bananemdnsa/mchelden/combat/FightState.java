package net.bananemdnsa.mchelden.combat;

import java.util.UUID;

import net.bananemdnsa.mchelden.duel.DuelManager;

/**
 * „Steckt dieser Spieler in einem Kampf?"
 *
 * <p>Es gibt zwei Arten davon — den Combat-Timer und das Duell —, und drei Systeme
 * interessiert der Unterschied nicht: die Container-Sperre, das Item-Kontingent und die
 * Safezone. Sie fragen hier, statt beide Timer einzeln abzuklappern und dabei mit der Zeit
 * auseinanderzulaufen.
 *
 * <p>Der Spielzeit-Kick fragt bewusst <em>nicht</em> hier: er wird nur vom Combat-Timer
 * aufgeschoben. Im Duell steht kein Herz im Feuer, also gibt es auch nichts aufzuschieben.
 */
public final class FightState {
    private FightState() {
    }

    public static boolean isFighting(UUID uuid) {
        return CombatTracker.isInCombat(uuid) || DuelManager.isDueling(uuid);
    }
}
