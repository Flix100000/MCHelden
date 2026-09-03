package net.bananemdnsa.mchelden.grave;

import java.util.Optional;

import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Die Zeile im Chat, die dem Toten sagt, wo sein Grab steht.
 *
 * <p>Ohne sie hat ein Spieler die Koordinaten seines Todes nie gesehen: er wacht am Bett
 * oder am Weltspawn auf, und der Lichtstrahl hilft nur, wer in Sichtweite wieder auftaucht.
 * Damit war das Grab für jeden nutzlos, der weit weg gestorben ist — und das ist genau der
 * Fall, in dem der Nachlass am meisten wert ist.
 *
 * <p><b>Nur das neueste Grab.</b> Wer zweimal stirbt, ohne das erste zu leeren, hört vom
 * ersten nichts mehr. Das ist selbst verschuldet, und eine Liste alter Gräber nach jedem Tod
 * verwässert die eine Zeile, die zählt.
 *
 * <p>Alles kommt aus dem Verzeichnis und dem Block, nichts aus einer Zwischenablage. Der
 * Grund ist der Combat-Logout: dessen Respawn wird beim nächsten Join nachgeholt, und
 * dazwischen kann ein Serverneustart liegen. Ein Schnappschuss vom Todeszeitpunkt wäre dann
 * verloren, das Verzeichnis ist es nicht.
 */
public final class GraveNotice {

    private GraveNotice() {
    }

    /**
     * Schickt dem Spieler die Zeilen zu seinem jüngsten Grab.
     *
     * <p>Ohne Grab im Verzeichnis passiert nichts. Wer stirbt, ohne dass ein Grab entsteht —
     * {@link GraveEvents} fand keinen sicheren Platz und hat alles fallengelassen —, soll
     * keine Zeile über ein Grab bekommen, das es nicht gibt.
     */
    public static void send(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // Die Oberwelt, nicht die Welt des Spielers: das Verzeichnis speichert eine
        // BlockPos ohne Dimension und haengt am Datenspeicher der Oberwelt. Ohne Nether
        // und End gibt es nichts zu unterscheiden, und `reset graves` rechnet genauso.
        ServerLevel level = server.overworld();

        Optional<BlockPos> newest = GraveRegistry.get(server).newestOf(player.getUUID());
        if (newest.isEmpty()) {
            return;
        }

        BlockPos grave = newest.get();

        // Der Chunk wird bei Bedarf nachgeladen. Genau dafuer gibt es das Verzeichnis: nach
        // einem Neustart ist der Chunk mit dem Grab garantiert nicht geladen.
        if (!(level.getBlockEntity(grave) instanceof GraveBlockEntity entity)) {
            // Eintrag da, Block weg: jemand war schneller, waehrend der Tote noch auf dem
            // Todesbildschirm sass. Keine Koordinaten, keine Reise ins Nichts.
            player.sendSystemMessage(HeldenText.graveRespawnGone());
            return;
        }

        player.sendSystemMessage(where(player.blockPosition(), grave));
        player.sendSystemMessage(
                HeldenText.graveRespawnContents(countItems(entity), entity.getStoredXp()));
        player.sendSystemMessage(HeldenText.graveRespawnHurry());
    }

    /**
     * Wo das Grab liegt, gemessen von der Stelle, an der der Spieler gerade steht.
     *
     * <p>Wer an seinem Bett stirbt, respawnt in derselben Saeule. „0 Bloecke noerdlich" waere
     * dort Unsinn, deswegen der eigene Satz.
     */
    private static Component where(BlockPos from, BlockPos grave) {
        String coordinates = coordinates(grave);

        if (GraveDirection.sameSpot(from, grave)) {
            return HeldenText.graveRespawnWhereHere(coordinates);
        }

        return HeldenText.graveRespawnWhere(coordinates,
                GraveDirection.distance(from, grave),
                GraveDirection.key(from, grave));
    }

    /** Wie viele Stapel im Grab liegen. Nicht die Stueckzahl — die Slots sind das Mass. */
    public static int countItems(GraveBlockEntity entity) {
        int count = 0;

        for (int slot = 0; slot < entity.getContainerSize(); slot++) {
            ItemStack stack = entity.getItem(slot);
            if (!stack.isEmpty()) {
                count++;
            }
        }

        return count;
    }

    /** Die Position, wie sie im Chat steht — und wie sie ein Befehl wieder entgegennimmt. */
    public static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
