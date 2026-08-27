package net.bananemdnsa.mchelden.text;

import net.minecraft.ChatFormatting;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;

import net.minecraft.network.chat.Component;

/**
 * Alle spielersichtbaren Texte an einer Stelle, als Übersetzungsschlüssel.
 * Die Formulierungen selbst stehen in den Sprachdateien unter
 * {@code assets/mchelden/lang/}.
 */
public final class HeldenText {
    private HeldenText() {
    }

    public static Component eliminationKick() {
        return Component.translatable("mchelden.elimination.kick.title").withStyle(ChatFormatting.RED)
                .append(Component.literal("\n\n"))
                .append(Component.translatable("mchelden.elimination.kick.body").withStyle(ChatFormatting.GRAY));
    }

    /** Im Einzelspieler wird nicht gekickt, sondern zugesehen. */
    public static Component eliminationSpectator() {
        return Component.translatable("mchelden.elimination.spectator").withStyle(ChatFormatting.RED);
    }

    public static Component eliminationTitle(String victim) {
        return Component.translatable("mchelden.elimination.title", victim).withStyle(ChatFormatting.RED);
    }

    public static Component eliminationSubtitle(String killer) {
        return killer.isEmpty()
                ? Component.empty()
                : Component.translatable("mchelden.elimination.subtitle", killer).withStyle(ChatFormatting.GRAY);
    }

    public static Component survivorCount(int alive) {
        return Component.translatable("mchelden.survivors", alive).withStyle(ChatFormatting.GRAY);
    }

    public static Component heartLost(int remaining) {
        return Component.translatable("mchelden.heart.lost", remaining).withStyle(ChatFormatting.RED);
    }

    public static Component heartGained(int total) {
        return Component.translatable("mchelden.heart.gained", total).withStyle(ChatFormatting.AQUA);
    }

    public static Component combatLogout(String player, String opponent) {
        return opponent.isEmpty()
                ? Component.translatable("mchelden.death.logout.generic", player)
                        .withStyle(ChatFormatting.RED)
                : Component.translatable("mchelden.death.logout", player, opponent)
                        .withStyle(ChatFormatting.RED);
    }

    public static Component containerLocked() {
        return Component.translatable("mchelden.combat.locked").withStyle(ChatFormatting.RED);
    }

    public static Component quotaEmpty(net.bananemdnsa.mchelden.combat.ItemQuota.Kind kind) {
        return Component.translatable("mchelden.combat.quota.empty",
                Component.translatable(kind.translationKey())).withStyle(ChatFormatting.RED);
    }

    public static Component phaseCurrent(Component phase) {
        return Component.translatable("mchelden.command.phase.current", phase).withStyle(ChatFormatting.GRAY);
    }

    public static Component phaseSet(Component phase) {
        return Component.translatable("mchelden.command.phase.set", phase).withStyle(ChatFormatting.GRAY);
    }

    public static Component infoUnknown() {
        return Component.translatable("mchelden.command.info.unknown").withStyle(ChatFormatting.GRAY);
    }

    public static Component infoLine(String labelKey, Component value) {
        return Component.literal("  ")
                .append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(value);
    }

    public static Component bountyNone() {
        return Component.translatable("mchelden.command.info.bounty.none").withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component bountyResolved() {
        return Component.translatable("mchelden.command.info.bounty.resolved").withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component statusActive() {
        return Component.translatable("mchelden.command.info.status.active").withStyle(ChatFormatting.GREEN);
    }

    public static Component statusEliminated() {
        return Component.translatable("mchelden.command.info.status.eliminated").withStyle(ChatFormatting.RED);
    }

    public static Component playtimeLeft(String duration) {
        return Component.translatable("mchelden.command.info.playtime.left", duration)
                .withStyle(ChatFormatting.WHITE);
    }

    public static Component revived(String player, int hearts) {
        return Component.translatable("mchelden.command.revive", player, hearts).withStyle(ChatFormatting.GREEN);
    }

    public static Component bountyAssigned(String target) {
        return Component.translatable("mchelden.bounty.assigned", target).withStyle(ChatFormatting.AQUA);
    }

    public static Component bountyNoneAssigned() {
        return Component.translatable("mchelden.bounty.none_assigned").withStyle(ChatFormatting.GRAY);
    }

    public static Component bountyKillTitle() {
        return Component.translatable("mchelden.bounty.kill.title").withStyle(ChatFormatting.AQUA);
    }

    public static Component bountyKillSubtitle(String victim) {
        return Component.translatable("mchelden.bounty.kill.subtitle", victim).withStyle(ChatFormatting.GRAY);
    }

    /** Muss ausdruecklich sagen, dass es kein Herz gekostet hat — sonst sucht der Verlierer danach. */
    public static Component bountyKillVictim(String killer) {
        return Component.translatable("mchelden.bounty.kill.victim", killer).withStyle(ChatFormatting.AQUA);
    }

    public static Component bountyKillBroadcast(String killer, String victim) {
        return Component.translatable("mchelden.bounty.kill.broadcast", killer, victim)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component bountyRolled(int pairs) {
        return Component.translatable("mchelden.command.bounty.rolled", pairs).withStyle(ChatFormatting.GRAY);
    }

    public static Component bountySet(String player, String target) {
        return Component.translatable("mchelden.command.bounty.set", player, target)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component bountyCleared(String player) {
        return Component.translatable("mchelden.command.bounty.cleared", player).withStyle(ChatFormatting.GRAY);
    }

    public static Component bountyClearedAll() {
        return Component.translatable("mchelden.command.bounty.cleared_all").withStyle(ChatFormatting.GRAY);
    }

    public static Component bountySelf() {
        return Component.translatable("mchelden.command.bounty.self").withStyle(ChatFormatting.RED);
    }

    public static Component bountyShow(String player, Component target) {
        return Component.literal(player + ": ").withStyle(ChatFormatting.GRAY).append(target);
    }

    public static Component bountyDebug(String target) {
        return Component.translatable("mchelden.command.bounty.debug", target).withStyle(ChatFormatting.GRAY);
    }

    public static Component bountyDebugSolo() {
        return Component.translatable("mchelden.command.bounty.debug.solo").withStyle(ChatFormatting.GRAY);
    }

    public static Component welcomeHeader() {
        return Component.translatable("mchelden.welcome.header").withStyle(ChatFormatting.GOLD);
    }

    public static Component welcomeHearts(int hearts) {
        return Component.literal(hearts + " / " + PlayerState.MAX_HEARTS)
                .withStyle(ChatFormatting.AQUA);
    }

    public static Component welcomeBountyPending() {
        return Component.translatable("mchelden.welcome.bounty.pending")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component welcomeWall() {
        return Component.translatable("mchelden.welcome.wall").withStyle(ChatFormatting.GRAY);
    }

    /** Kurz und in der Actionbar: der Hinweis kommt beim Bauen oft. */
    public static Component wallBlocked() {
        return Component.translatable("mchelden.wall.blocked").withStyle(ChatFormatting.RED);
    }

    public static Component wallDropped() {
        return Component.translatable("mchelden.command.wall.dropped").withStyle(ChatFormatting.GOLD);
    }

    public static Component wallRaised() {
        return Component.translatable("mchelden.command.wall.raised").withStyle(ChatFormatting.GRAY);
    }

    public static Component wallAlready() {
        return Component.translatable("mchelden.command.wall.already").withStyle(ChatFormatting.GRAY);
    }

    public static Component phaseCountdown(int seconds) {
        return Component.translatable("mchelden.phase.countdown", seconds).withStyle(ChatFormatting.GOLD);
    }

    public static Component phaseCountdownSubtitle(Component phase) {
        return Component.translatable("mchelden.phase.countdown.subtitle", phase)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component phaseTitle(Component phase) {
        return phase.copy().withStyle(ChatFormatting.GOLD);
    }

    /** Was sich mit dieser Phase konkret aendert. Eine Zeile je Phase. */
    public static Component phaseSubtitle(Phase phase) {
        return Component.translatable("mchelden.phase.subtitle." + phase.getId())
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component phaseChanged(Component phase) {
        return Component.translatable("mchelden.phase.changed", phase).withStyle(ChatFormatting.GOLD);
    }

    public static Component phaseReverted(Component phase) {
        return Component.translatable("mchelden.phase.reverted", phase).withStyle(ChatFormatting.GRAY);
    }

    public static Component phaseNoNext() {
        return Component.translatable("mchelden.command.phase.no_next").withStyle(ChatFormatting.RED);
    }

    public static Component phaseStarting(Component phase) {
        return Component.translatable("mchelden.command.phase.starting", phase)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component playtimeWarning(int minutes) {
        return Component.translatable("mchelden.playtime.warning", minutes)
                .withStyle(minutes <= 1 ? ChatFormatting.RED : ChatFormatting.GOLD);
    }

    /** Muss sagen, dass es nicht sofort passiert — sonst wirkt der Kick spaeter wie ein Absturz. */
    public static Component playtimeAfterCombat() {
        return Component.translatable("mchelden.playtime.after_combat").withStyle(ChatFormatting.RED);
    }

    public static Component playtimeReset() {
        return Component.translatable("mchelden.playtime.reset").withStyle(ChatFormatting.GREEN);
    }

    /**
     * Der Kick-Screen beim Zeitlimit.
     *
     * <p>Bewusst anders im Ton als der Eliminations-Screen: das hier ist eine Spielregel,
     * keine Strafe, und morgen geht es weiter.
     */
    public static Component playtimeKick() {
        return Component.translatable("mchelden.playtime.kick.title").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("\n\n"))
                .append(Component.translatable("mchelden.playtime.kick.body").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Nennt beim Einschalten gleich die Restzeit.
     *
     * <p>Nach einem Kick steht die naemlich auf null, und wer das Limit sofort wieder
     * einschaltet, fliegt binnen einer Sekunde erneut raus, ohne etwas zu sehen.
     */
    public static Component debugPlaytime(boolean limited, String remaining) {
        return limited
                ? Component.translatable("mchelden.command.debug.playtime.on", remaining)
                        .withStyle(ChatFormatting.GRAY)
                : Component.translatable("mchelden.command.debug.playtime.off")
                        .withStyle(ChatFormatting.GRAY);
    }

    public static Component playtimeExempt() {
        return Component.translatable("mchelden.command.info.playtime.exempt")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component timeAdded(String player, String duration) {
        return Component.translatable("mchelden.command.time.added", player, duration)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component timeSet(String player, String duration) {
        return Component.translatable("mchelden.command.time.set", player, duration)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component playtimeReport(String player, String duration) {
        return Component.literal(player + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("mchelden.command.info.playtime.left", duration)
                        .withStyle(ChatFormatting.WHITE));
    }
}
