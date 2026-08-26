package net.bananemdnsa.mchelden.hearts;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Einziger Weg, Herzen zu ändern. Alles läuft hier durch, damit der Deckel bei vier
 * und die Elimination bei null nirgends umgangen werden können.
 */
public final class HeartManager {
    /**
     * Spieler, die einen Herzverlust noch angezeigt bekommen müssen.
     *
     * <p>Rein transient. Wer beim Ausloggen darin steht, braucht die Animation nicht mehr —
     * der Verlust selbst steckt längst im persistenten Zustand.
     */
    private static final Set<UUID> PENDING_LOSS_ANIMATION = ConcurrentHashMap.newKeySet();

    /**
     * Auslieferungen, die noch ein paar Ticks warten.
     *
     * <p>Beim Join laedt noch die Welt. Ohne Verzoegerung liefe die Animation hinter einem
     * grauen Bildschirm ab und der Spieler saehe von seinem verlorenen Herz nichts.
     */
    private static final Map<UUID, Integer> DELAYED_DELIVERY = new ConcurrentHashMap<>();

    /** Wie lange nach dem Join gewartet wird, bis die Animation laeuft. */
    public static final int JOIN_DELAY_TICKS = 25;

    private HeartManager() {
    }

    public static int get(MinecraftServer server, UUID uuid) {
        return PlayerStateStore.get(server).getOrCreate(uuid).getHearts();
    }

    /**
     * Setzt den Herzstand. Fällt er dabei auf null, scheidet der Spieler aus; sinkt er
     * nur, wird der Verlust angezeigt.
     *
     * @param killerName Name des Verursachers für die Ansage, leer wenn keiner
     * @return der tatsächlich gesetzte Stand nach dem Deckeln
     */
    public static int set(MinecraftServer server, UUID uuid, int hearts, String killerName) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(uuid);

        int before = state.getHearts();
        state.setHearts(hearts);
        store.setDirty();
        int after = state.getHearts();

        if (after <= 0 && !state.isEliminated()) {
            Elimination.eliminate(server, uuid, killerName);
            return after;
        }

        sync(server, uuid);

        if (after < before) {
            announceLoss(server, uuid, after);
        } else if (after > before) {
            announceGain(server, uuid, after);
        }
        return after;
    }

    public static int add(MinecraftServer server, UUID uuid, int delta, String killerName) {
        return set(server, uuid, get(server, uuid) + delta, killerName);
    }

    /** Herzverlust nach einem Tod durch einen Spieler. */
    public static void loseHeart(MinecraftServer server, UUID uuid, String killerName) {
        add(server, uuid, -1, killerName);
    }

    /** Herzgewinn durch den Bounty-Kill. Der Deckel bei vier greift im Setter. */
    public static void gainHeart(MinecraftServer server, UUID uuid) {
        add(server, uuid, 1, "");
    }

    /**
     * Zeigt den Verlust an, sobald der Spieler ihn sehen kann.
     *
     * <p>Lebt er, passiert das sofort. Ist er tot oder offline, wird es vorgemerkt und beim
     * Respawn nachgeholt — auf dem Todesbildschirm sähe die Animation niemand.
     */
    private static void announceLoss(MinecraftServer server, UUID uuid, int remaining) {
        ServerPlayer player = online(server, uuid);
        if (player == null || player.isDeadOrDying()) {
            PENDING_LOSS_ANIMATION.add(uuid);
            return;
        }

        NetworkHandler.sendHeartLost(player, remaining);
        player.sendSystemMessage(HeldenText.heartLost(remaining));
    }

    private static void announceGain(MinecraftServer server, UUID uuid, int total) {
        ServerPlayer player = online(server, uuid);
        if (player != null) {
            player.sendSystemMessage(HeldenText.heartGained(total));
        }
    }

    /** Liefert eine vorgemerkte Anzeige mit Verzoegerung. Beim Join aufrufen. */
    public static void deliverPendingLossDelayed(UUID uuid) {
        if (PENDING_LOSS_ANIMATION.contains(uuid)) {
            DELAYED_DELIVERY.put(uuid, JOIN_DELAY_TICKS);
        }
    }

    /** Zaehlt wartende Auslieferungen herunter. Aus dem Server-Tick aufrufen. */
    public static void tick(MinecraftServer server) {
        if (DELAYED_DELIVERY.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> entries = DELAYED_DELIVERY.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }

            entries.remove();
            ServerPlayer player = online(server, entry.getKey());
            if (player != null) {
                deliverPendingLoss(player);
            }
        }
    }

    /** Holt eine vorgemerkte Verlust-Anzeige nach. Beim Respawn aufrufen. */
    public static void deliverPendingLoss(ServerPlayer player) {
        if (!PENDING_LOSS_ANIMATION.remove(player.getUUID()) || player.getServer() == null) {
            return;
        }

        int remaining = get(player.getServer(), player.getUUID());
        NetworkHandler.sendHeartLost(player, remaining);
        player.sendSystemMessage(HeldenText.heartLost(remaining));
    }

    static void sync(MinecraftServer server, UUID uuid) {
        ServerPlayer player = online(server, uuid);
        if (player != null) {
            NetworkHandler.syncTo(player);
        }
    }

    @Nullable
    static ServerPlayer online(MinecraftServer server, UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }
}
