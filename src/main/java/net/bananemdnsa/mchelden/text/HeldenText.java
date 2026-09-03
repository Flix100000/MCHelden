package net.bananemdnsa.mchelden.text;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.bananemdnsa.mchelden.event.EventType;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

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

    /**
     * <p>Die Zahl steht im Text, weil die Arena verschiebbar ist: fest eingebaut waere sie
     * nach dem ersten {@code /helden center} eine Falschauskunft an jeden, der joint.
     */
    public static Component welcomeWall(String x) {
        return Component.translatable("mchelden.welcome.wall", x).withStyle(ChatFormatting.GRAY);
    }

    /** Kurz und in der Actionbar: der Hinweis kommt beim Bauen oft. */
    public static Component safeZoneEntered() {
        return Component.translatable("mchelden.safezone.entered").withStyle(ChatFormatting.GREEN);
    }

    public static Component safeZoneLeft() {
        return Component.translatable("mchelden.safezone.left").withStyle(ChatFormatting.GRAY);
    }

    /** Muss den Grund nennen: eine unsichtbare Wand ohne Erklaerung wirkt wie ein Fehler. */
    public static Component safeZoneDenied() {
        return Component.translatable("mchelden.safezone.denied").withStyle(ChatFormatting.RED);
    }

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

    /**
     * Die Beschriftung der Bossbar.
     *
     * <p>Nur die Restzeit. Die Groesse steht schon im Balken daneben — sie als Zahl zu
     * wiederholen sagt nichts dazu, was der Balken nicht schon zeigt.
     *
     * <p>Die Zeit kommt jeden Tick frisch aus der Border. Steht sie, waere eine Restzeit
     * von 0:00 gelogen — dann sagt die Zeile stattdessen, dass die Arena steht.
     */
    public static Component finalWarBar(long remainingMillis) {
        return remainingMillis <= 0L
                ? Component.translatable("mchelden.finalwar.bar.done")
                : Component.translatable("mchelden.finalwar.bar",
                        DurationText.clock(remainingMillis));
    }

    public static Component finalWarStarting(String duration) {
        return Component.translatable("mchelden.command.finalwar.starting", duration)
                .withStyle(ChatFormatting.RED);
    }

    public static Component finalWarAlready() {
        return Component.translatable("mchelden.command.finalwar.already");
    }

    public static Component finalWarNotRunning() {
        return Component.translatable("mchelden.command.finalwar.notrunning");
    }

    public static Component finalWarStopped() {
        return Component.translatable("mchelden.command.finalwar.stopped")
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component borderShrinking(int blocks, String duration) {
        return Component.translatable("mchelden.command.border.shrinking", blocks, duration)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Wo die Arena liegt, plus die Weltborder zum Abgleich. */
    public static Component centerShow(String center, String border) {
        return Component.translatable("mchelden.command.center.show", center, border)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component centerMoved(String center) {
        return Component.translatable("mchelden.command.center.moved", center)
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * Warnt, wenn die Weltborder woanders steht als die Arena.
     *
     * <p>Ein Op darf {@code /worldborder center} jederzeit von Hand setzen. Dann stuenden
     * Kuppel und Wand woanders als die Weltgrenze, ohne dass es jemandem auffiele.
     */
    public static Component centerMismatch() {
        return Component.translatable("mchelden.command.center.mismatch")
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component borderReset(int blocks) {
        return Component.translatable("mchelden.command.border.reset", blocks)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Nennt Beispiele statt einer Grammatik — die liest ohnehin niemand. */
    public static Component durationInvalid() {
        return Component.translatable("mchelden.command.duration.invalid")
                .withStyle(ChatFormatting.RED);
    }

    /** Wenn jemand nach dem Respawn in die Border geholt werden musste. */
    public static Component borderRescued() {
        return Component.translatable("mchelden.border.rescued").withStyle(ChatFormatting.GRAY);
    }

    /** Ein Strich, wo nichts anliegt. Schneller zu lesen als ein ausgeschriebenes Nichts. */
    public static Component infoNone() {
        return Component.literal("—").withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component resetHearts(String target) {
        return Component.translatable("mchelden.command.reset.hearts", target)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component resetBounty(String target) {
        return Component.translatable("mchelden.command.reset.bounty", target)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component resetTime(String target) {
        return Component.translatable("mchelden.command.reset.time", target)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component resetGraves(String target, int count) {
        return Component.translatable("mchelden.command.reset.graves", target, count)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Die Warnung beim ersten Aufruf von {@code reset all}. */
    public static Component resetAllWarning() {
        return Component.translatable("mchelden.command.reset.all.warning")
                .withStyle(ChatFormatting.RED);
    }

    /** Was genau verlorengeht. Steht als eigene Zeile, damit es sich lesen laesst. */
    public static Component resetAllList() {
        return Component.translatable("mchelden.command.reset.all.list")
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component resetAllConfirm(int seconds) {
        return Component.translatable("mchelden.command.reset.all.confirm", seconds)
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component resetAllDone() {
        return Component.translatable("mchelden.command.reset.all.done")
                .withStyle(ChatFormatting.GRAY);
    }

    /** Alle statt eines Einzelnen. Steht als Platzhalter in den Reset-Zeilen. */
    public static Component resetEveryone() {
        return Component.translatable("mchelden.command.reset.everyone");
    }

    /** Nennt die gueltigen Kennungen, statt nur "geht nicht" zu sagen. */
    public static Component phaseUnknown(String valid) {
        return Component.translatable("mchelden.command.phase.unknown", valid)
                .withStyle(ChatFormatting.RED);
    }

    /**
     * Die Duell-Anfrage mit ihren beiden Schaltflaechen.
     *
     * <p>Die Commands dahinter gibt es auch zum Tippen. Anklickbar sind sie trotzdem: wer
     * mitten im Spiel steht, tippt keinen Namen ab.
     */
    public static Component duelRequest(String requester) {
        return Component.translatable("mchelden.duel.request.received", requester)
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("  "))
                .append(duelButton("mchelden.duel.request.accept", ChatFormatting.GREEN,
                        "/duell accept " + requester))
                .append(Component.literal(" "))
                .append(duelButton("mchelden.duel.request.deny", ChatFormatting.RED,
                        "/duell deny " + requester));
    }

    private static Component duelButton(String key, ChatFormatting color, String command) {
        return Component.translatable(key).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(key + ".hover"))));
    }

    public static Component duelRequestSent(String target) {
        return Component.translatable("mchelden.duel.request.sent", target)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Eine gewoehnliche Duell-Zeile mit einem Namen darin. */
    public static Component duelLine(String key, String player) {
        return Component.translatable(key, player).withStyle(ChatFormatting.GRAY);
    }

    /** Eine ohne Namen — etwa das Platzen, bei dem der Dritte nichts zur Sache tut. */
    public static Component duelLine(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    /** Der Duellbeginn und der Sieg: die beiden Momente, die auffallen sollen. */
    public static Component duelHighlight(String key, String player) {
        return Component.translatable(key, player).withStyle(ChatFormatting.AQUA);
    }

    public static Component duelDenied(String key, String player) {
        return Component.translatable(key, player).withStyle(ChatFormatting.RED);
    }

    public static Component duelDenied(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.RED);
    }

    public static Component duelDeniedDistance(String player, int blocks) {
        return Component.translatable("mchelden.duel.deny.distance", player, blocks)
                .withStyle(ChatFormatting.RED);
    }

    public static Component duelNone() {
        return Component.translatable("mchelden.command.info.duel.none")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component duelValue(String partner, Component remaining) {
        return Component.translatable("mchelden.command.info.duel.value", partner, remaining)
                .withStyle(ChatFormatting.AQUA);
    }

    public static Component duelCleared(String player) {
        return Component.translatable("mchelden.command.duel.cleared", player)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component duelDebug(String opponent) {
        return Component.translatable("mchelden.command.debug.duel", opponent)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Der Alleingang: kein Duell dahinter, nur Balken und Glow. */
    public static Component duelDebugSolo(Component glowing) {
        return Component.translatable("mchelden.command.debug.duel.solo", glowing)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component duelDebugSoloEmpty() {
        return Component.translatable("mchelden.command.debug.duel.solo.empty")
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component duelCommandNone(String player) {
        return Component.translatable("mchelden.command.duel.none", player)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Die Bossbar: Eventname und Restzeit als Uhr. */
    public static Component eventBar(Component name, long remainingMillis) {
        return Component.translatable("mchelden.event.bar", name,
                DurationText.clock(remainingMillis));
    }

    /** Der Titel beim Start ist der Eventname selbst — wie bei den Phasen. */
    public static Component eventTitle(Component name) {
        return name.copy().withStyle(ChatFormatting.GREEN);
    }

    /**
     * Was dieses Event konkret aendert. Eine Zeile je Typ.
     *
     * <p>Steht hier und nicht am Enum, genau wie {@link #phaseSubtitle}: der Typ liefert
     * unformatierte Bausteine, das Einfaerben passiert an einer Stelle.
     */
    public static Component eventSubtitle(EventType type) {
        return Component.translatable("mchelden.event.subtitle." + type.getId())
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * Was mit dem Ende dieses Events wieder gilt.
     *
     * <p>Die Gegenrichtung zu {@link #eventSubtitle}, und nicht nur Zierde: wer sein
     * Kontingent waehrend des Events aufgebraucht hat, erfaehrt sonst erst durch den Kick,
     * dass die Uhr wieder laeuft.
     */
    public static Component eventEndSubtitle(EventType type) {
        return Component.translatable("mchelden.event.subtitle." + type.getId() + ".end")
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component eventStarted(Component name, String duration) {
        return Component.translatable("mchelden.event.start.broadcast", name, duration)
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component eventReplaced() {
        return Component.translatable("mchelden.event.replaced").withStyle(ChatFormatting.GRAY);
    }

    public static Component eventWarning(Component name, int seconds) {
        return Component.translatable("mchelden.event.warning", name, seconds)
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component eventEndTitle() {
        return Component.translatable("mchelden.event.end.title").withStyle(ChatFormatting.GOLD);
    }

    public static Component eventEnded(Component name) {
        return Component.translatable("mchelden.event.end.broadcast", name)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component eventStopped(Component name) {
        return Component.translatable("mchelden.event.end.stopped", name)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component eventEndedByPhase(Component name) {
        return Component.translatable("mchelden.event.end.phase", name)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component eventInfoRunning(Component name, String remaining) {
        return Component.translatable("mchelden.event.info.running", name, remaining)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Dieselbe Zeile fuer "info ohne Event" und "stop ohne Event" — es ist derselbe Satz. */
    public static Component eventNone() {
        return Component.translatable("mchelden.event.none").withStyle(ChatFormatting.GRAY);
    }

    public static Component eventDenyPhase(Component name, Component phase) {
        return Component.translatable("mchelden.event.deny.phase", name, phase)
                .withStyle(ChatFormatting.RED);
    }

    public static Component eventUnknown(String ids) {
        return Component.translatable("mchelden.event.deny.unknown", ids)
                .withStyle(ChatFormatting.RED);
    }

    // ------------------------------------------------------------------
    // Graeber
    // ------------------------------------------------------------------

    /** Wie lange ein Tod her ist, in ganzen Minuten oder Stunden. */
    public static Component graveAge(long ageInTicks) {
        long minutes = Math.max(0, ageInTicks / (20 * 60));

        if (minutes < 1) {
            return Component.translatable("mchelden.grave.just_now");
        }
        if (minutes < 60) {
            return Component.translatable("mchelden.grave.minutes_ago", minutes);
        }
        return Component.translatable("mchelden.grave.hours_ago", minutes / 60);
    }

    /**
     * Fuer ein Grab aus einer Welt von vor dem Zeitstempel im Verzeichnis.
     *
     * <p>Lieber zugeben, dass der Zeitpunkt fehlt, als „gerade eben" behaupten und jemanden
     * zu einem zwei Wochen alten Grab schicken.
     */
    public static Component graveAgeUnknown() {
        return Component.translatable("mchelden.grave.age_unknown");
    }

    /**
     * Die erste Zeile der Respawn-Nachricht: wo das Grab liegt.
     *
     * <p>Die Koordinaten kopieren sich auf Klick in die Zwischenablage — wer eine externe
     * Karte benutzt, braucht sie dort und nicht im Chat.
     */
    public static Component graveRespawnWhere(String coordinates, int distance,
                                              String directionKey) {
        return Component.translatable("mchelden.grave.respawn.where",
                        copyableCoordinates(coordinates),
                        distance,
                        Component.translatable(directionKey))
                .withStyle(ChatFormatting.GRAY);
    }

    /** Dieselbe Zeile, wenn Grab und Respawn in derselben Saeule liegen. */
    public static Component graveRespawnWhereHere(String coordinates) {
        return Component.translatable("mchelden.grave.respawn.where.here",
                        copyableCoordinates(coordinates))
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component copyableCoordinates(String coordinates) {
        return Component.literal(coordinates).withStyle(style -> style
                .withColor(ChatFormatting.GOLD)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coordinates))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("mchelden.grave.respawn.copy"))));
    }

    /**
     * Was im Grab liegt.
     *
     * <p>Ohne den Reststrahl: beim gewoehnlichen Tod folgt der Respawn so schnell, dass
     * dort ohnehin fast immer die vollen vier Minuten stehen wuerden. Eine Zahl, die
     * immer dieselbe ist, sagt nichts. Wer sie wirklich braucht, fragt
     * {@code /helden grave info}.
     */
    public static Component graveRespawnContents(int items, int xp) {
        return indented(Component.translatable("mchelden.grave.respawn.contents",
                items, xp), ChatFormatting.GRAY);
    }

    /** Der Grund, warum die beiden Zeilen darueber dringlich sind. */
    public static Component graveRespawnHurry() {
        return indented(Component.translatable("mchelden.grave.respawn.hurry"),
                ChatFormatting.RED);
    }

    /**
     * Eine eingerueckte Folgezeile.
     *
     * <p>Die Einrueckung steht hier und nicht in der Sprachdatei: sonst muesste ein
     * Uebersetzer fuehrende Leerzeichen erhalten, ohne zu ahnen, dass sie tragen.
     */
    private static Component indented(Component line, ChatFormatting color) {
        return Component.literal("  ").append(line).withStyle(color);
    }

    /** Wenn jemand schneller war, waehrend der Tote noch auf dem Todesbildschirm sass. */
    public static Component graveRespawnGone() {
        return Component.translatable("mchelden.grave.respawn.gone")
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component graveListEmpty() {
        return Component.translatable("mchelden.command.grave.list.empty")
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component graveListHeader(String target, int count) {
        return Component.translatable("mchelden.command.grave.list.header", target, count)
                .withStyle(ChatFormatting.GOLD);
    }

    /** Die Kopfzeile der ungefilterten Liste. Ohne Namen, weil keiner gemeint ist. */
    public static Component graveListHeaderAll(int count) {
        return Component.translatable("mchelden.command.grave.list.header.all", count)
                .withStyle(ChatFormatting.GOLD);
    }

    /**
     * Eine Zeile der Liste: Teleport-Knopf, anklickbare Koordinaten, Besitzer, Alter.
     *
     * <p>Der Besitzer entfaellt, wenn die Liste schon nach einem Spieler gefiltert ist —
     * ihn in jeder Zeile zu wiederholen sagt nichts Neues.
     */
    public static MutableComponent graveListLine(String coordinates, @Nullable String owner,
                                                 Component age) {
        MutableComponent line = Component.literal("  ")
                .append(graveButton("mchelden.command.grave.list.jump",
                        "/helden grave tp " + coordinates,
                        "mchelden.command.grave.tooltip.tp"))
                .append(Component.literal(" "))
                .append(graveLink(coordinates, "/helden grave info " + coordinates,
                        "mchelden.command.grave.tooltip.info"));

        if (owner != null) {
            line.append(bullet()).append(Component.literal(owner).withStyle(ChatFormatting.WHITE));
        }

        return line.append(bullet()).append(age.copy().withStyle(ChatFormatting.GRAY));
    }

    /** Wie viele Graeber die Liste nicht mehr gezeigt hat. */
    public static Component graveListMore(int rest) {
        return indented(Component.translatable("mchelden.command.grave.list.more", rest),
                ChatFormatting.DARK_GRAY);
    }

    /** Der Trenner zwischen den Feldern einer Listenzeile. */
    private static Component bullet() {
        return Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component graveButton(String key, String command, String hoverKey) {
        return Component.translatable(key).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(hoverKey))));
    }

    private static Component graveLink(String label, String command, String hoverKey) {
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(hoverKey))));
    }

    public static Component graveInfoHeader(String owner, String coordinates) {
        return Component.translatable("mchelden.command.grave.info.header", owner, coordinates)
                .withStyle(ChatFormatting.GOLD);
    }

    /** Alter, XP und Reststrahl in einer Zeile. */
    public static Component graveInfoDetails(Component age, int xp, String remaining) {
        return indented(Component.translatable("mchelden.command.grave.info.details",
                age, xp, remaining), ChatFormatting.GRAY);
    }

    /** Dieselbe Zeile, nachdem der Strahl ausgegangen ist. */
    public static Component graveInfoDetailsFaded(Component age, int xp) {
        return indented(Component.translatable("mchelden.command.grave.info.details.faded",
                age, xp), ChatFormatting.GRAY);
    }

    public static Component graveInfoItem(int count, Component item) {
        return indented(Component.translatable("mchelden.command.grave.info.item", count, item),
                ChatFormatting.WHITE);
    }

    /** Wenn der Block schon weg ist, aber der Eintrag noch steht. */
    public static Component graveInfoBlockGone() {
        return indented(Component.translatable("mchelden.command.grave.info.block_gone"),
                ChatFormatting.DARK_GRAY);
    }

    public static Component graveTeleported(String owner, String coordinates) {
        return Component.translatable("mchelden.command.grave.tp.done", owner, coordinates)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component graveRemoved(String owner, String coordinates) {
        return Component.translatable("mchelden.command.grave.remove.done", owner, coordinates)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Fuer `info`, `tp` und `remove`, wenn an der Stelle kein Grab eingetragen ist. */
    public static Component graveNone() {
        return Component.translatable("mchelden.command.grave.none")
                .withStyle(ChatFormatting.RED);
    }
}
