package net.bananemdnsa.mchelden.duel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Die offenen Duell-Anfragen.
 *
 * <p>Wer irgendwo als Anfragender oder als Ziel steht, gilt als belegt und kann weder eine
 * weitere Anfrage stellen noch eine bekommen. Ohne diese Sperre sammelt man Anfragen und
 * nimmt hinterher die an, die gerade am besten passt.
 *
 * <p>Kennt keine Minecraft-Typen: der Ablauf laesst sich damit ohne laufenden Server pruefen.
 */
public final class DuelRequests {
    /** Wie lange eine Anfrage gilt. */
    public static final int EXPIRY_TICKS = 60 * 20;

    private final Map<UUID, Request> byRequester = new ConcurrentHashMap<>();

    /** Eine offene Anfrage. Die Restzeit laeuft in {@link #tick()} herunter. */
    public static final class Request {
        private final UUID requester;
        private final UUID target;
        private int ticksLeft = EXPIRY_TICKS;

        private Request(UUID requester, UUID target) {
            this.requester = requester;
            this.target = target;
        }

        public UUID requester() {
            return requester;
        }

        public UUID target() {
            return target;
        }
    }

    /** Steht dieser Spieler in einer Anfrage — gleich auf welcher Seite? */
    public boolean isInvolved(UUID uuid) {
        for (Request request : byRequester.values()) {
            if (request.requester.equals(uuid) || request.target.equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    public void open(UUID requester, UUID target) {
        byRequester.put(requester, new Request(requester, target));
    }

    /**
     * Die Anfrage genau dieses Paares, in dieser Richtung.
     *
     * <p>Die Richtung zaehlt: {@code between(anna, bert)} findet nur Annas Anfrage an Bert,
     * nicht umgekehrt. Angenommen wird immer die Anfrage des anderen.
     */
    @Nullable
    public Request between(UUID requester, UUID target) {
        Request request = byRequester.get(requester);
        return request != null && request.target.equals(target) ? request : null;
    }

    /** Die eigene offene Anfrage, oder {@code null}. */
    @Nullable
    public Request byRequester(UUID requester) {
        return byRequester.get(requester);
    }

    public void close(UUID requester) {
        byRequester.remove(requester);
    }

    /**
     * Raeumt die Anfrage weg, an der dieser Spieler beteiligt ist — auf beiden Seiten.
     *
     * @return die geraeumte Anfrage, oder {@code null} wenn keine da war
     */
    @Nullable
    public Request forget(UUID uuid) {
        for (Request request : byRequester.values()) {
            if (request.requester.equals(uuid) || request.target.equals(uuid)) {
                byRequester.remove(request.requester);
                return request;
            }
        }
        return null;
    }

    /**
     * Zaehlt alle Anfragen herunter.
     *
     * @return die abgelaufenen Anfragen, bereits entfernt
     */
    public List<Request> tick() {
        if (byRequester.isEmpty()) {
            return List.of();
        }

        List<Request> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, Request>> entries = byRequester.entrySet().iterator();
        while (entries.hasNext()) {
            Request request = entries.next().getValue();
            if (--request.ticksLeft > 0) {
                continue;
            }

            entries.remove();
            expired.add(request);
        }
        return expired;
    }
}
