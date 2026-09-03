package net.bananemdnsa.mchelden.duel;

import java.util.Comparator;
import java.util.UUID;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.combat.CombatTracker;
import net.bananemdnsa.mchelden.combat.ItemQuota;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;
import net.bananemdnsa.mchelden.world.SafeZone;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundSource;

/**
 * Einziger Weg, ein Duell zu eroeffnen oder zu beenden.
 *
 * <p>Ein Duell ist die zweite Ausnahme von der Todesdefinition, neben dem Bounty-Kill: wer
 * mit laufendem Duell-Timer stirbt, verliert kein Herz. Geschuetzt ist auch hier nur das
 * Herz — Grab, Itemsplit und XP laufen unveraendert.
 *
 * <p>Nichts davon wird gespeichert. Ein Logout beendet das Duell, es kann also gar keinen
 * gespeicherten Duellzustand geben, den man beim Neustart wiederherstellen muesste.
 * Dieselbe Begruendung wie beim {@link CombatTracker}.
 */
public final class DuelManager {
    /** Wie weit eine Anfrage reichen darf. Sonst laeuft der Timer los, waehrend beide sich suchen. */
    public static final int MAX_REQUEST_DISTANCE = 100;

    /** Wie lange die Anzeige im Alleingang laeuft: ein voller Balkenabschnitt. */
    private static final int SHOWCASE_TICKS = 30 * 20;
    /** In welchem Umkreis der Alleingang nach etwas zum Leuchten sucht. */
    private static final double SHOWCASE_RADIUS = 32.0;

    /**
     * Wie oft der Server den Balken des Clients nachzieht. Aus demselben Grund wie beim
     * Combat-Timer: siehe {@code CombatTracker.CORRECTION_GAP}.
     */
    private static final int CORRECTION_GAP = 20;

    private static final DuelRequests REQUESTS = new DuelRequests();
    private static final DuelRegistry DUELS = new DuelRegistry();

    private DuelManager() {
    }

    // ------------------------------------------------------------------ Abfragen

    public static boolean isDueling(UUID uuid) {
        return DUELS.isDueling(uuid);
    }

    @Nullable
    public static UUID partnerOf(UUID uuid) {
        return DUELS.partnerOf(uuid);
    }

    public static boolean arePartners(UUID first, UUID second) {
        return DUELS.arePartners(first, second);
    }

    public static int remainingTicks(UUID uuid) {
        return DUELS.remainingTicks(uuid);
    }

    // ------------------------------------------------------------------ Anfragen

    /**
     * Stellt eine Anfrage, wenn alle Bedingungen stimmen.
     *
     * <p>Jede verletzte Bedingung bekommt ihre eigene Meldung. Eine pauschale Absage laesst
     * den Spieler raten, woran es lag, und er versucht es sofort wieder.
     */
    public static void request(ServerPlayer requester, ServerPlayer target) {
        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(HeldenText.duelDenied("mchelden.duel.deny.self"));
            return;
        }

        String targetName = target.getGameProfile().getName();
        Component problem = blocker(requester, target, targetName);
        if (problem != null) {
            requester.sendSystemMessage(problem);
            return;
        }

        REQUESTS.open(requester.getUUID(), target.getUUID());
        requester.sendSystemMessage(HeldenText.duelRequestSent(targetName));
        target.sendSystemMessage(HeldenText.duelRequest(requester.getGameProfile().getName()));
        target.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7f, 1.2f);
    }

    /**
     * Was gegen eine Anfrage zwischen diesen beiden spricht, oder {@code null}.
     *
     * <p>Die Meldungen sind aus der Sicht des Anfragenden formuliert: {@code .self} ist er
     * selbst, {@code .other} sein Gegenueber.
     */
    @Nullable
    private static Component blocker(ServerPlayer requester, ServerPlayer target, String targetName) {
        if (CombatTracker.isInCombat(requester.getUUID())) {
            return HeldenText.duelDenied("mchelden.duel.deny.combat.self");
        }
        if (CombatTracker.isInCombat(target.getUUID())) {
            return HeldenText.duelDenied("mchelden.duel.deny.combat.other", targetName);
        }
        if (DUELS.isDueling(requester.getUUID()) || REQUESTS.isInvolved(requester.getUUID())) {
            return HeldenText.duelDenied("mchelden.duel.deny.busy.self");
        }
        if (DUELS.isDueling(target.getUUID()) || REQUESTS.isInvolved(target.getUUID())) {
            return HeldenText.duelDenied("mchelden.duel.deny.busy.other", targetName);
        }
        return sharedBlocker(requester, target, targetName);
    }

    /**
     * Die Bedingungen, die sich in den sechzig Sekunden Wartezeit aendern koennen und
     * deswegen beim Annehmen noch einmal geprueft werden.
     *
     * @param other der jeweils andere, aus Sicht dessen, der die Meldung bekommt
     */
    @Nullable
    private static Component sharedBlocker(ServerPlayer self, ServerPlayer other, String otherName) {
        if (SafeZone.covers(self)) {
            return HeldenText.duelDenied("mchelden.duel.deny.safezone.self");
        }
        if (SafeZone.covers(other)) {
            return HeldenText.duelDenied("mchelden.duel.deny.safezone.other", otherName);
        }
        if (isEliminated(other)) {
            return HeldenText.duelDenied("mchelden.duel.deny.eliminated", otherName);
        }
        if (self.distanceToSqr(other) > (double) MAX_REQUEST_DISTANCE * MAX_REQUEST_DISTANCE) {
            return HeldenText.duelDeniedDistance(otherName, MAX_REQUEST_DISTANCE);
        }
        return null;
    }

    private static boolean isEliminated(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        PlayerState state = PlayerStateStore.get(server).find(player.getUUID());
        return state != null && state.isEliminated();
    }

    /**
     * Nimmt die Anfrage dieses Herausforderers an.
     *
     * <p>Die Bedingungen werden hier ein zweites Mal geprueft — in der Wartezeit kann jeder
     * von beiden in einen Kampf geraten oder in die Safezone gelaufen sein. Nur die
     * Belegt-Pruefung faellt weg: die offene Anfrage der beiden ist genau die Belegung, die
     * hier gerade aufgeloest wird.
     */
    public static void accept(ServerPlayer target, ServerPlayer requester) {
        String requesterName = requester.getGameProfile().getName();
        if (REQUESTS.between(requester.getUUID(), target.getUUID()) == null) {
            target.sendSystemMessage(
                    HeldenText.duelDenied("mchelden.duel.request.none", requesterName));
            return;
        }

        if (CombatTracker.isInCombat(target.getUUID())) {
            target.sendSystemMessage(HeldenText.duelDenied("mchelden.duel.deny.combat.self"));
            return;
        }
        if (CombatTracker.isInCombat(requester.getUUID())) {
            target.sendSystemMessage(
                    HeldenText.duelDenied("mchelden.duel.deny.combat.other", requesterName));
            return;
        }

        Component problem = sharedBlocker(target, requester, requesterName);
        if (problem != null) {
            target.sendSystemMessage(problem);
            return;
        }

        REQUESTS.close(requester.getUUID());
        open(requester, target);
    }

    /** Lehnt die Anfrage dieses Herausforderers ab. */
    public static void deny(ServerPlayer target, ServerPlayer requester) {
        String requesterName = requester.getGameProfile().getName();
        if (REQUESTS.between(requester.getUUID(), target.getUUID()) == null) {
            target.sendSystemMessage(
                    HeldenText.duelDenied("mchelden.duel.request.none", requesterName));
            return;
        }

        REQUESTS.close(requester.getUUID());
        target.sendSystemMessage(
                HeldenText.duelLine("mchelden.duel.request.denied.target", requesterName));
        requester.sendSystemMessage(HeldenText.duelLine("mchelden.duel.request.denied.requester",
                target.getGameProfile().getName()));
    }

    /**
     * Zieht die eigene Anfrage zurueck oder gibt ein laufendes Duell auf.
     *
     * @return false, wenn es weder das eine noch das andere gab
     */
    public static boolean cancel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        DuelRequests.Request request = REQUESTS.byRequester(player.getUUID());
        if (request != null) {
            REQUESTS.close(player.getUUID());
            ServerPlayer target = server.getPlayerList().getPlayer(request.target());
            player.sendSystemMessage(HeldenText.duelLine("mchelden.duel.request.withdrawn.requester",
                    nameOf(server, request.target())));
            if (target != null) {
                target.sendSystemMessage(HeldenText.duelLine("mchelden.duel.request.withdrawn.target",
                        player.getGameProfile().getName()));
            }
            return true;
        }

        if (!DUELS.isDueling(player.getUUID())) {
            return false;
        }

        // Das Aufgeben ist keine Einmischung: es wird nichts in den Combat-Timer
        // uebernommen, das Duell endet ersatzlos fuer beide.
        UUID partnerId = DUELS.partnerOf(player.getUUID());
        close(server, player.getUUID());

        player.sendSystemMessage(HeldenText.duelLine("mchelden.duel.end.surrendered.self",
                nameOf(server, partnerId)));
        ServerPlayer partner = partnerId != null ? server.getPlayerList().getPlayer(partnerId) : null;
        if (partner != null) {
            partner.sendSystemMessage(HeldenText.duelLine("mchelden.duel.end.surrendered.other",
                    player.getGameProfile().getName()));
        }
        return true;
    }

    // ------------------------------------------------------------------ Duell

    /** Oeffnet das Duell und sagt es beiden an. */
    private static void open(ServerPlayer first, ServerPlayer second) {
        DUELS.open(first.getUUID(), second.getUUID());

        announceStart(first, second.getGameProfile().getName());
        announceStart(second, first.getGameProfile().getName());
    }

    private static void announceStart(ServerPlayer player, String opponent) {
        player.sendSystemMessage(HeldenText.duelHighlight("mchelden.duel.start", opponent));
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.MASTER, 0.9f, 0.8f);
        NetworkHandler.sendDuelHit(player);
    }

    /**
     * Oeffnet ein Duell ohne Anfrage und ohne Bedingungen. Fuer {@code /helden debug duell}.
     *
     * <p>Bestehende Duelle und Anfragen der beiden werden vorher geraeumt. Ohne das bliebe
     * ein alter Partner auf ein Duell zeigen, das es nicht mehr gibt — und genau die
     * Gegenseitigkeit ist es, an der der Herzschutz haengt.
     */
    public static void forceOpen(MinecraftServer server, ServerPlayer first, ServerPlayer second) {
        REQUESTS.forget(first.getUUID());
        REQUESTS.forget(second.getUUID());
        close(server, first.getUUID());
        close(server, second.getUUID());

        open(first, second);
    }

    /**
     * Schickt nur die Anzeige eines Duells, ohne eines zu eroeffnen. Fuer den Alleingang im
     * Einzelspieler.
     *
     * <p>Ein Duell mit sich selbst waere ein kaputter Zustand, hier entsteht deswegen kein
     * Eintrag im Register — nur ein Paket. Der Balken laeuft dreissig Sekunden auf dem
     * Client herunter und verschwindet dann von selbst, weil der Client seinen Timer
     * ohnehin selbst zaehlt. Was am Timer haengt — Container-Sperre, Kontingent,
     * Safezone —, gilt dabei folglich nicht.
     *
     * <p>Als leuchtenden Gegner bekommt der Spieler die naechste Kreatur. Der Glow haengt an
     * einer UUID und fragt nicht danach, wem sie gehoert: an einer Kuh sieht man genauso, ob
     * die Farbe stimmt und ob der Umriss durch Waende zu sehen ist.
     *
     * @return die leuchtende Kreatur, oder {@code null} wenn keine in der Naehe war
     */
    @Nullable
    public static LivingEntity showcase(ServerPlayer player) {
        LivingEntity glowing = nearestCreature(player);
        NetworkHandler.sendDuelShowcase(player,
                glowing != null ? glowing.getUUID() : null, SHOWCASE_TICKS);
        return glowing;
    }

    @Nullable
    private static LivingEntity nearestCreature(ServerPlayer player) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(SHOWCASE_RADIUS),
                        entity -> entity != player && entity.isAlive())
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    /** Ein Treffer zwischen den beiden Duellanten. Verlaengert den geteilten Timer. */
    public static void onHit(ServerPlayer attacker, ServerPlayer victim) {
        DUELS.hit(attacker.getUUID());
        NetworkHandler.sendDuelHit(attacker);
        NetworkHandler.sendDuelHit(victim);
    }

    /**
     * Ein Dritter hat sich eingemischt: das Duell platzt fuer beide.
     *
     * <p>Der Duell-Timer laeuft dabei als Combat-Timer weiter, mit Restzeit und
     * Trefferzaehler. Sonst waere das Platzen ein Schlupfloch: wer im Duell hinten liegt,
     * laesst sich von einem Freund einmal anhauen und steht danach ganz ohne Timer da —
     * frei zum Ausloggen oder zum Marsch in die Safezone, mitten aus einem verlorenen
     * Kampf heraus.
     *
     * @return true, wenn tatsaechlich ein Duell geplatzt ist
     */
    public static boolean breakUp(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        DuelRegistry.Duel duel = DUELS.close(player.getUUID());
        if (duel == null) {
            return false;
        }

        ServerPlayer first = server.getPlayerList().getPlayer(duel.first());
        ServerPlayer second = server.getPlayerList().getPlayer(duel.second());
        carryOver(duel, first, second);
        carryOver(duel, second, first);
        return true;
    }

    /** Uebernimmt den Duell-Timer als Combat-Timer, mit dem Duellpartner als Gegner. */
    private static void carryOver(DuelRegistry.Duel duel, @Nullable ServerPlayer player,
                                  @Nullable ServerPlayer opponent) {
        if (player == null) {
            return;
        }

        NetworkHandler.sendDuel(player);
        CombatTracker.adopt(player, duel.timer(),
                opponent != null ? opponent.getGameProfile().getName() : "",
                opponent != null ? opponent.getUUID() : null);
        player.sendSystemMessage(HeldenText.duelLine("mchelden.duel.end.burst"));
    }

    /**
     * Prueft, ob dieser Tod ein Duell-Tod war, und schliesst das Duell.
     *
     * <p>Wenn ja, kostet der Tod kein Herz — der Aufrufer muss den Herzabzug dann
     * ueberspringen. Wie beim Bounty-Kill ist nur das Herz geschuetzt.
     *
     * @return true, wenn es ein Duell-Tod war
     */
    public static boolean onDeath(MinecraftServer server, ServerPlayer victim) {
        if (!DUELS.isDueling(victim.getUUID())) {
            return false;
        }

        UUID partnerId = DUELS.partnerOf(victim.getUUID());
        String partnerName = nameOf(server, partnerId);

        // Vor dem Schliessen merken: die Todesnachricht wird erst danach gebaut, und bis
        // dahin waere die Paarung sonst schon weg.
        CombatTracker.recordDuelDeath(victim.getUUID(), partnerName);
        close(server, victim.getUUID());

        victim.sendSystemMessage(HeldenText.duelLine("mchelden.duel.end.death.loser", partnerName));
        ServerPlayer partner = partnerId != null ? server.getPlayerList().getPlayer(partnerId) : null;
        if (partner != null) {
            partner.sendSystemMessage(HeldenText.duelHighlight("mchelden.duel.end.death.winner",
                    victim.getGameProfile().getName()));
        }
        return true;
    }

    /**
     * Logout im Duell. Folgenlos: es stand kein Herz auf dem Spiel, es gibt also nichts,
     * wovor ein Logout schuetzen koennte. Raeumt nebenbei eine offene Anfrage weg — die
     * Schaltflaeche wuerde sonst auf jemanden zeigen, der nicht mehr da ist.
     */
    public static void onLogout(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        DuelRequests.Request request = REQUESTS.forget(player.getUUID());
        if (request != null) {
            UUID other = request.requester().equals(player.getUUID())
                    ? request.target()
                    : request.requester();
            tell(server, other, "mchelden.duel.request.expired.requester",
                    player.getGameProfile().getName());
        }

        if (!DUELS.isDueling(player.getUUID())) {
            return;
        }

        UUID partnerId = DUELS.partnerOf(player.getUUID());
        close(server, player.getUUID());
        if (partnerId != null) {
            tell(server, partnerId, "mchelden.duel.end.logout", player.getGameProfile().getName());
        }
    }

    /** Beendet ein Duell von Hand. Reparaturwerkzeug fuer {@code /helden duell clear}. */
    public static boolean clear(MinecraftServer server, UUID uuid) {
        if (!DUELS.isDueling(uuid)) {
            return false;
        }

        UUID partnerId = DUELS.partnerOf(uuid);
        close(server, uuid);

        notifyCleared(server, uuid);
        if (partnerId != null) {
            notifyCleared(server, partnerId);
        }
        return true;
    }

    private static void notifyCleared(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(HeldenText.duelLine("mchelden.duel.end.cleared"));
        }
    }

    /**
     * Schliesst das Duell und raeumt hinterher auf: Kontingent zurueck, Stand zum Client.
     *
     * <p>Das Kontingent haengt am Kampf, und das Duell ist einer — es muss mit dem Duell
     * enden, sonst nimmt jemand seine leeren Perlen in den naechsten Kampf mit.
     */
    private static void close(MinecraftServer server, UUID uuid) {
        DuelRegistry.Duel duel = DUELS.close(uuid);
        if (duel == null) {
            return;
        }

        refresh(server, duel.first());
        refresh(server, duel.second());
    }

    private static void refresh(MinecraftServer server, UUID uuid) {
        ItemQuota.reset(uuid);
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            NetworkHandler.sendDuel(player);
        }
    }

    /** Zaehlt Anfragen und Duelle herunter. Aus dem Server-Tick aufrufen. */
    public static void tick(MinecraftServer server) {
        for (DuelRequests.Request request : REQUESTS.tick()) {
            tell(server, request.requester(), "mchelden.duel.request.expired.requester",
                    nameOf(server, request.target()));
            tell(server, request.target(), "mchelden.duel.request.expired.target",
                    nameOf(server, request.requester()));
        }

        for (DuelRegistry.Duel duel : DUELS.tick()) {
            expire(server, duel.first(), duel.second());
            expire(server, duel.second(), duel.first());
        }

        correct(server);
    }

    /** Zieht die Balken aller Duellanten nach, einmal pro Sekunde. */
    private static void correct(MinecraftServer server) {
        if (server.getTickCount() % CORRECTION_GAP != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Nur wer laut Register wirklich duelliert: die Anzeige aus dem Alleingang
            // steht hinter keinem Duell und laeuft allein auf dem Client ab. Eine
            // Korrektur wuerde sie sofort abraeumen.
            if (remainingTicks(player.getUUID()) > 0) {
                NetworkHandler.sendDuel(player);
            }
        }
    }

    private static void expire(MinecraftServer server, UUID uuid, UUID partner) {
        ItemQuota.reset(uuid);
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(HeldenText.duelLine("mchelden.duel.end.timeout",
                    nameOf(server, partner)));
            NetworkHandler.sendDuel(player);
        }
    }

    private static void tell(MinecraftServer server, UUID uuid, String key, String argument) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(HeldenText.duelLine(key, argument));
        }
    }

    private static String nameOf(MinecraftServer server, @Nullable UUID uuid) {
        if (uuid == null) {
            return "";
        }

        PlayerState state = PlayerStateStore.get(server).find(uuid);
        return state != null ? state.getName() : "";
    }
}
