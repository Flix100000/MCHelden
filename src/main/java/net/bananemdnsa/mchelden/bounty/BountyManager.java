package net.bananemdnsa.mchelden.bounty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.hearts.HeartManager;
import net.bananemdnsa.mchelden.network.BountyRollPayload;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Einziger Weg, ein Bounty zu setzen oder aufzuloesen.
 *
 * <p>Die Paarung ist gegenseitig, und genau daran haengt alles: ein Zustand, in dem einer
 * ein Ziel hat und das Ziel ihn nicht, waere von aussen nicht erkennbar und wuerde die
 * Auflösung stillschweigend aushebeln. Deswegen fasst niemand sonst
 * {@link PlayerState#setBountyTarget} an.
 */
public final class BountyManager {
    /**
     * Wie lange das Gluecksrad laeuft, bis der Kopf in seinem Kasten angekommen ist.
     *
     * <p>Erst danach kommt die persoenliche Chat-Zeile — sonst stuende das Ergebnis im
     * Chat, bevor der Streifen es preisgibt.
     */
    public static final int ROLL_ANNOUNCE_TICKS = BountyRollTiming.TOTAL_TICKS;

    /** Wartende Chat-Zeilen nach einem Roll. Rein transient: der Roll ist in Sekunden vorbei. */
    private static final Map<UUID, Integer> PENDING_ANNOUNCE = new ConcurrentHashMap<>();

    /**
     * Nachgeholte Rolls, die noch ein paar Ticks warten.
     *
     * <p>Beim Join laedt noch die Welt. Ohne Verzoegerung liefe das Gluecksrad hinter einem
     * grauen Bildschirm ab. Die Wartezeit ist laenger als beim Herzverlust, weil beides
     * zusammentreffen kann — wer sich im Kampf ausgeloggt und dabei den Roll verpasst hat,
     * soll nicht beide Inszenierungen uebereinander sehen.
     */
    private static final Map<UUID, Integer> PENDING_DELIVERY = new ConcurrentHashMap<>();

    /** Wie lange ein nachgeholter Roll nach dem Join wartet. */
    public static final int JOIN_DELAY_TICKS = 60;

    private BountyManager() {
    }

    /**
     * Losst alle bekannten Spieler gegenseitig aus. Alte Paarungen fallen dabei weg.
     *
     * <p>Gepaart wird jeder, der schon einmal auf dem Server war und nicht ausgeschieden
     * ist — auch die gerade Abwesenden. Nur die Anwesenden zu paaren waere die einfachere
     * Regel, wuerde bei einem Tageslimit von einer Stunde aber regelmaessig jemanden
     * dauerhaft vom vierten Herz aussperren, ohne dass es jemandem auffiele.
     *
     * <p>Wer offline ist, bekommt das Gluecksrad beim naechsten Join nachgespielt.
     *
     * @return Anzahl der entstandenen Paare
     */
    public static int roll(MinecraftServer server) {
        PlayerStateStore store = PlayerStateStore.get(server);

        List<UUID> candidates = new ArrayList<>();
        for (PlayerState state : store.all()) {
            if (!state.isEliminated()) {
                candidates.add(state.getUuid());
            }
        }

        // Ein neuer Roll raeumt alles ab, auch bereits aufgeloeste Bounties: sonst blieben
        // Spieler aus der vorigen Runde ohne Anzeige zurueck.
        clearAll(server);

        List<BountyPairing.Pair> pairs = BountyPairing.pair(candidates, server.overworld().getRandom());
        for (BountyPairing.Pair pair : pairs) {
            link(store, pair.first(), pair.second());
        }

        for (UUID uuid : candidates) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                startRollFor(server, player);
            } else {
                store.getOrCreate(uuid).setPendingBountyRoll(true);
            }
        }
        store.setDirty();
        return pairs.size();
    }

    /**
     * Steht fuer diesen Spieler noch ein Roll aus?
     *
     * <p>Solange das so ist, darf sein Bounty nicht zu ihm gesynct werden: der Zustand
     * kaeme beim Join vor dem nachgeholten Rad an, und der Kopf stuende in der Ecke, bevor
     * der Streifen ihn preisgibt.
     */
    public static boolean isRollPending(MinecraftServer server, UUID uuid) {
        if (PENDING_DELIVERY.containsKey(uuid)) {
            return true;
        }

        PlayerState state = PlayerStateStore.get(server).find(uuid);
        return state != null && state.isPendingBountyRoll();
    }

    /**
     * Holt einen verpassten Roll nach. Beim Join aufrufen.
     *
     * <p>Die Vormerkung wird sofort geloescht, nicht erst nach dem Abspielen: sonst
     * bekaeme jemand, der waehrend der Verzoegerung wieder rausfliegt, den Roll beim
     * naechsten Mal noch einmal.
     */
    public static void deliverPendingRollDelayed(MinecraftServer server, UUID uuid) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.find(uuid);
        if (state == null || !state.isPendingBountyRoll()) {
            return;
        }

        state.setPendingBountyRoll(false);
        store.setDirty();
        PENDING_DELIVERY.put(uuid, JOIN_DELAY_TICKS);
    }

    /** Schickt einem Spieler Ansage und Gluecksrad und merkt seine Chat-Zeile vor. */
    private static void startRollFor(MinecraftServer server, ServerPlayer player) {
        UUID target = PlayerStateStore.get(server).getOrCreate(player.getUUID()).getBountyTarget();

        // Die Ansage zeichnet das Overlay selbst, als erster Zug des Rades. Der
        // Vanilla-Titel kann nicht anders als vierfach vergroessert — lange Zeilen haengen
        // damit links und rechts aus dem Bild, und er laege ausserdem ueber dem Band.
        sendRoll(server, player, target);
        NetworkHandler.syncTo(player);
        PENDING_ANNOUNCE.put(player.getUUID(), ROLL_ANNOUNCE_TICKS);
    }

    /** Spielt das Gluecksrad ab. Ein leeres Ziel laesst es auf dem Fragezeichen halten. */
    public static void sendRoll(MinecraftServer server, ServerPlayer player, @Nullable UUID target) {
        PacketDistributor.sendToPlayer(player,
                new BountyRollPayload(Optional.ofNullable(target), nameOf(server, target)));
    }

    /**
     * Setzt eine Paarung von Hand. Beide verlieren dabei ihre bisherigen Partner — sonst
     * bliebe deren Ziel auf jemanden zeigen, der es nicht mehr jagt.
     */
    public static void set(MinecraftServer server, UUID player, UUID target) {
        clear(server, player);
        clear(server, target);

        PlayerStateStore store = PlayerStateStore.get(server);
        link(store, player, target);
        store.setDirty();

        syncIfOnline(server, player);
        syncIfOnline(server, target);
    }

    /**
     * Loest ein Bounty auf — immer beidseitig.
     *
     * <p>Danach steht der Spieler wieder auf "noch kein Bounty", nicht auf "erledigt": der
     * Command ist Reparaturwerkzeug, und nach einer Reparatur soll ein neuer Roll greifen.
     */
    public static void clear(MinecraftServer server, UUID uuid) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.find(uuid);
        if (state == null) {
            return;
        }

        UUID partner = state.getBountyTarget();
        unlink(store, uuid, false);
        if (partner != null) {
            unlink(store, partner, false);
        }
        store.setDirty();

        syncIfOnline(server, uuid);
        if (partner != null) {
            syncIfOnline(server, partner);
        }
    }

    public static void clearAll(MinecraftServer server) {
        PlayerStateStore store = PlayerStateStore.get(server);
        for (PlayerState state : store.all()) {
            state.setBountyTarget(null);
            state.setBountyResolved(false);
        }
        store.setDirty();
        NetworkHandler.syncAll(server);
    }

    @Nullable
    public static UUID targetOf(MinecraftServer server, UUID uuid) {
        PlayerState state = PlayerStateStore.get(server).find(uuid);
        return state != null ? state.getBountyTarget() : null;
    }

    /**
     * Prueft, ob dieser Tod der Bounty-Kampf war, und loest ihn gegebenenfalls auf.
     *
     * <p>Wenn ja, kostet der Tod kein Herz — der Aufrufer muss den Herzabzug dann
     * ueberspringen. Geschuetzt ist nur das Herz: Grab, Itemsplit und XP laufen normal.
     *
     * @param killer wer zuletzt getroffen hat, {@code null} wenn niemand
     * @return true, wenn es ein Bounty-Kill war
     */
    public static boolean resolve(MinecraftServer server, UUID victim, @Nullable UUID killer) {
        if (killer == null || killer.equals(victim)) {
            return false;
        }

        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState victimState = store.find(victim);
        PlayerState killerState = store.find(killer);
        if (victimState == null || killerState == null) {
            return false;
        }

        // Beide Richtungen pruefen. Eine einseitige Paarung waere ein kaputter Zustand,
        // aus dem kein viertes Herz entstehen darf.
        if (!killer.equals(victimState.getBountyTarget()) || !victim.equals(killerState.getBountyTarget())) {
            return false;
        }

        String killerName = killerState.getName();
        String victimName = victimState.getName();

        unlink(store, victim, true);
        unlink(store, killer, true);
        store.setDirty();

        announceKill(server, killer, victim, killerName, victimName);

        // Erst danach: der Herzgewinn schickt seinen eigenen Sync und seine eigene Zeile,
        // und die soll nach der Ansage kommen, nicht davor.
        HeartManager.gainHeart(server, killer);
        syncIfOnline(server, victim);
        return true;
    }

    /**
     * Zieht die Anzeige beim Partner nach, wenn sich am Ziel selbst etwas geaendert hat.
     *
     * <p>Wird beim Ausscheiden und beim Zurueckholen gebraucht: der Kopf im HUD wird grau,
     * beziehungsweise wieder normal.
     */
    public static void syncPartnerOf(MinecraftServer server, UUID uuid) {
        for (PlayerState state : PlayerStateStore.get(server).all()) {
            if (uuid.equals(state.getBountyTarget())) {
                syncIfOnline(server, state.getUuid());
            }
        }
    }

    /** Zaehlt beide Wartelisten herunter. Aus dem Server-Tick aufrufen. */
    public static void tick(MinecraftServer server) {
        countDown(server, PENDING_DELIVERY, BountyManager::startRollFor);
        countDown(server, PENDING_ANNOUNCE, BountyManager::announceTarget);
    }

    /** Zaehlt eine Warteliste herunter und fuehrt aus, was faellig ist. */
    private static void countDown(MinecraftServer server, Map<UUID, Integer> waiting,
                                  BiConsumer<MinecraftServer, ServerPlayer> action) {
        if (waiting.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> entries = waiting.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }

            entries.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                action.accept(server, player);
            }
        }
    }

    private static void announceTarget(MinecraftServer server, ServerPlayer player) {
        UUID target = targetOf(server, player.getUUID());
        player.sendSystemMessage(target == null
                ? HeldenText.bountyNoneAssigned()
                : HeldenText.bountyAssigned(nameOf(server, target)));
    }

    /**
     * Der Bounty-Kill bekommt fuer die beiden Beteiligten den vollen Moment, fuer alle
     * anderen eine Chat-Zeile. Dieselbe Regel wie beim Herzverlust: was jeder in voller
     * Staerke sieht, nutzt sich in zwei Tagen ab.
     */
    private static void announceKill(MinecraftServer server, UUID killer, UUID victim,
                                     String killerName, String victimName) {
        ServerPlayer winner = server.getPlayerList().getPlayer(killer);
        if (winner != null) {
            winner.connection.send(new ClientboundSetTitleTextPacket(HeldenText.bountyKillTitle()));
            winner.connection.send(new ClientboundSetSubtitleTextPacket(
                    HeldenText.bountyKillSubtitle(victimName)));
            winner.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.9f, 1.0f);
        }

        ServerPlayer loser = server.getPlayerList().getPlayer(victim);
        if (loser != null) {
            loser.sendSystemMessage(HeldenText.bountyKillVictim(killerName));
        }

        server.getPlayerList().broadcastSystemMessage(
                HeldenText.bountyKillBroadcast(killerName, victimName), false);
    }

    private static void link(PlayerStateStore store, UUID first, UUID second) {
        PlayerState firstState = store.getOrCreate(first);
        PlayerState secondState = store.getOrCreate(second);

        firstState.setBountyTarget(second);
        firstState.setBountyResolved(false);
        secondState.setBountyTarget(first);
        secondState.setBountyResolved(false);
    }

    private static void unlink(PlayerStateStore store, UUID uuid, boolean resolved) {
        PlayerState state = store.getOrCreate(uuid);
        state.setBountyTarget(null);
        state.setBountyResolved(resolved);
    }

    private static void syncIfOnline(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            NetworkHandler.syncTo(player);
        }
    }

    /** Der gespeicherte Name des Ziels. Leer, wenn es keins gibt. */
    public static String nameOf(MinecraftServer server, @Nullable UUID uuid) {
        if (uuid == null) {
            return "";
        }
        PlayerState state = PlayerStateStore.get(server).find(uuid);
        return state != null ? state.getName() : "";
    }
}
