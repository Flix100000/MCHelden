package net.bananemdnsa.mchelden.world;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.state.Side;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Der Startspawn: zufaellige Position, aber ausgeglichene Seiten.
 *
 * <p>Echter Zufall wuerde bei zwanzig Spielern leicht vierzehn zu sechs ergeben und die
 * Aufteilung von Anfang an kaputtmachen. Deswegen wird die Seite nicht gewuerfelt, sondern
 * zugeteilt — immer die mit weniger Leuten. Nur die Position darin ist Zufall.
 */
public final class SpawnPlacer {
    /** Mindestabstand zur Trennwand, damit niemand an der Linie klebt. */
    private static final int WALL_MARGIN = 120;
    /** Mindestabstand zum Weltrand. */
    private static final int BORDER_MARGIN = 60;
    /**
     * Mindestabstand zwischen zwei Startspawns.
     *
     * <p>Bei zehn Leuten auf tausend mal zweitausend Bloecken setzt echter Zufall sonst
     * ohne Weiteres zwei Spieler funfzehn Bloecke nebeneinander — und ruiniert damit den
     * Start fuer beide.
     */
    private static final int PLAYER_MARGIN = 150;
    /** Wie viele Stellen ueberhaupt in Betracht gezogen werden. Kostet nur Rechenzeit. */
    private static final int ATTEMPTS = 64;
    /**
     * Wie viele davon tatsaechlich geladen und vermessen werden.
     *
     * <p>Jede Pruefung erzeugt notfalls einen Chunk. Vier sind genug, um Wasser und Lava
     * auszuweichen, ohne den Join sekundenlang aufzuhalten.
     */
    private static final int VERIFY_LIMIT = 4;

    private SpawnPlacer() {
    }

    /**
     * Weist einem Spieler beim ersten Join Seite und Startpunkt zu und setzt ihn dorthin.
     *
     * <p>Passiert genau einmal. Wer schon eine Seite hat, behaelt sie — sonst stuende
     * jemand nach einem Serverneustart auf der anderen Seite seiner eigenen Basis.
     *
     * @return true, wenn zugeteilt wurde
     */
    public static boolean placeOnFirstJoin(MinecraftServer server, ServerPlayer player) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.getOrCreate(player.getUUID());
        if (state.getSide() != null) {
            return false;
        }

        Side side = smallerSide(store);
        ServerLevel level = server.overworld();
        BlockPos spawn = findSpawn(level, side, level.getRandom(), takenSpawns(store));

        state.setSide(side);
        state.setStartSpawn(spawn);
        store.setDirty();

        teleport(player, level, spawn);
        return true;
    }

    /**
     * Wuerfelt einen frischen Startpunkt auf der eigenen Seite und setzt den Spieler dorthin.
     *
     * <p>Fuer den Respawn ohne Bett: der Weltspawn liegt auf 0,0 und damit mitten in der
     * Trennwand.
     *
     * <p><b>Jedes Mal neu, nicht immer derselbe Fleck.</b> Frueher war der einmal gezogene
     * Startpunkt zugleich ein erzwungener Vanilla-Respawnpunkt — man wachte damit bei jedem
     * Tod exakt dort auf, und das liest sich wie ein fester Spawn. Wer kein Bett hat, ist
     * jetzt verschollen, und zwar jedes Mal woanders.
     *
     * <p>Ein Bett bleibt davon unberuehrt: dann hat Vanilla einen Respawnpunkt, und diese
     * Methode wird gar nicht erst gerufen.
     *
     * <p>Weil {@code takenSpawns} auch den bisherigen eigenen Punkt enthaelt, liegt der neue
     * garantiert weit vom alten weg — und ebenso von denen der anderen.
     */
    public static void returnToStart(MinecraftServer server, ServerPlayer player) {
        PlayerStateStore store = PlayerStateStore.get(server);
        PlayerState state = store.find(player.getUUID());
        if (state == null || state.getSide() == null) {
            return;
        }

        ServerLevel level = server.overworld();

        // Jedes Mal frisch gewuerfelt. Der Startpunkt ist kein fester Wohnsitz, sondern der
        // Ort, an dem man gerade aufwacht — wer kein Bett hat, ist verschollen, und zwar
        // jedes Mal woanders.
        //
        // `takenSpawns` enthaelt auch den eigenen bisherigen Punkt, der neue liegt also
        // garantiert weit vom alten weg.
        BlockPos spawn = findSpawn(level, state.getSide(), level.getRandom(), takenSpawns(store));

        state.setStartSpawn(spawn);
        store.setDirty();

        teleport(player, level, spawn);
    }

    /**
     * Raeumt auf, was Vanilla beim Respawn falsch entschieden hat.
     *
     * <p>Drei Faelle: wer kein Bett hat, wird auf einen frisch gewuerfelten Punkt seiner
     * Seite gesetzt statt auf den Weltspawn — der liegt auf 0,0 und damit mitten in der
     * Trennwand. Wer sein Bett auf der falschen Seite hat, stuende jenseits einer Wand,
     * durch die er nicht zurueckkommt. Und wer unter der Welt landet, hat einen kaputten
     * gespeicherten Punkt erwischt.
     */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        PlayerState state = PlayerStateStore.get(server).find(player.getUUID());
        Side side = state != null ? state.getSide() : null;
        if (side == null) {
            return;
        }

        // Unter der Welt gelandet: das kann nur ein kaputter gespeicherter Startpunkt sein,
        // und der wuerde sich bei jedem weiteren Tod wiederholen.
        boolean belowWorld = player.getY() <= server.overworld().getMinBuildHeight();
        // Der Abstand zur Linie zaehlt mit: bei exakt x = 0 besteht ein Ost-Spieler die
        // Seitenpruefung — genau null zaehlt als Osten — und stuende trotzdem in der Wand.
        double center = ArenaCenter.x(server);
        boolean wrongSide = DividerWall.isUp(server)
                && (!side.contains(player.getX() - center)
                        || Math.abs(player.getX() - center) < DividerWall.MARGIN);

        // Kein Bett und kein Anker: dann setzt Vanilla auf den Weltspawn, und der liegt
        // auf 0,0 — mitten auf der Trennlinie. Stattdessen wird gewuerfelt.
        //
        // <b>Der Startpunkt ist bewusst kein Vanilla-Respawnpunkt mehr.</b> Waere er einer,
        // liesse sich "hat ein Bett" nicht mehr von "hat unseren Punkt" unterscheiden — und
        // man wachte bei jedem Tod am selben Fleck auf, was sich wie ein fester Spawn liest.
        // So ist die Frage genau Vanillas eigene: gibt es ein Bett oder nicht.
        boolean noBed = player.getRespawnPosition() == null;

        if (belowWorld || wrongSide || noBed) {
            returnToStart(server, player);
        }

        // Zuletzt, damit es auch nach dem Zurueckholen auf den Startpunkt noch greift: der
        // liegt mindestens 120 Bloecke von der Wand entfernt, die Endarena misst plusminus
        // 80. Im Final War landet also *jeder ohne Bett* ausserhalb.
        pullInsideBorder(server, player);
    }

    /**
     * Holt einen Respawnten in die Border zurueck.
     *
     * <p>Haengt an der Border, nicht an der Phase — aus demselben Grund wie das Gewitter an
     * ihrer Kante: ein {@code border shrink} als Werkzeug liesse die Luecke sonst offen.
     *
     * <p>Das Bett wird dabei nicht angefasst. Die Korrektur wiederholt sich beim naechsten
     * Tod von selbst und ist damit ohne Gedaechtnis richtig.
     */
    private static void pullInsideBorder(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();

        if (!BorderController.isOutside(border, player.getX(), player.getZ())) {
            return;
        }

        double[] inside = BorderController.clampInside(border, player.getX(), player.getZ());
        BlockPos surface = surfaceAt(level,
                (int) Math.floor(inside[0]), (int) Math.floor(inside[1]));

        // Kein tauglicher Boden an der Stelle: dann lieber auf die Hoehe des Spielers setzen
        // als ihn draussen stehen zu lassen. Ein Sturz ist reparabel, Borderschaden nicht.
        BlockPos target = surface != null
                ? surface
                : BlockPos.containing(inside[0], player.getY(), inside[1]);

        teleport(player, level, target);
        player.displayClientMessage(HeldenText.borderRescued(), false);
    }

    /** Die Seite mit weniger zugeteilten Spielern. Bei Gleichstand der Westen. */
    private static Side smallerSide(PlayerStateStore store) {
        int west = 0;
        int east = 0;
        for (PlayerState state : store.all()) {
            if (state.getSide() == Side.WEST) {
                west++;
            } else if (state.getSide() == Side.EAST) {
                east++;
            }
        }
        return east < west ? Side.EAST : Side.WEST;
    }

    private static List<BlockPos> takenSpawns(PlayerStateStore store) {
        List<BlockPos> taken = new ArrayList<>();
        for (PlayerState state : store.all()) {
            if (state.getStartSpawn() != null) {
                taken.add(state.getStartSpawn());
            }
        }
        return taken;
    }

    /**
     * Sucht einen Platz auf der angegebenen Seite.
     *
     * <p><b>In zwei Stufen, und das ist der Kern der Sache.</b> Die Auswahl laeuft ueber den
     * Weltgenerator, der die Gelaendehoehe einer Stelle <em>berechnen</em> kann, ohne den
     * Chunk zu erzeugen — sonst wuerde ein einziger Join dutzende Chunks generieren.
     * Erst der Gewinner wird tatsaechlich geladen und genau vermessen.
     *
     * <p>Der naheliegende Weg, {@code level.getHeightmapPos}, ist eine Falle: der laedt
     * nichts nach und gibt fuer jeden ungeladenen Chunk kommentarlos die Weltuntergrenze
     * zurueck. In einer frischen Welt ist ausser den Spawn-Chunks nichts geladen — jeder
     * Startpunkt laege also bei Y = -64, und der Spieler faellt beim Betreten aus der Welt.
     */
    public static BlockPos findSpawn(ServerLevel level, Side side, RandomSource random,
                                     List<BlockPos> taken) {
        List<BlockPos> shortlist = new ArrayList<>();

        for (int attempt = 0; attempt < ATTEMPTS && shortlist.size() < VERIFY_LIMIT; attempt++) {
            int x = randomX(level, side, random);
            int z = randomZ(level, random);
            BlockPos estimate = new BlockPos(x, estimateHeight(level, x, z), z);

            if (nearest(estimate, taken) >= PLAYER_MARGIN) {
                shortlist.add(estimate);
            }
        }

        // Keiner weit genug weg? Dann zaehlt Platz mehr als Abstand — ein schlechter
        // Startpunkt ist besser als ein fehlgeschlagener Join.
        if (shortlist.isEmpty()) {
            int x = randomX(level, side, random);
            int z = randomZ(level, random);
            shortlist.add(new BlockPos(x, estimateHeight(level, x, z), z));
        }

        for (BlockPos candidate : shortlist) {
            BlockPos surface = surfaceAt(level, candidate.getX(), candidate.getZ());
            if (surface != null) {
                return surface;
            }
        }

        return surfaceFallback(level, side);
    }

    /**
     * Die Gelaendehoehe an einer Stelle, ohne den Chunk zu erzeugen.
     *
     * <p>Derselbe Weg, den Vanilla fuer die Suche nach dem Weltspawn geht. Der Wert ist eine
     * Schaetzung — Baeume, Seen und Hoehlen kennt er nicht —, aber er genuegt, um Kandidaten
     * gegeneinander abzuwaegen, bevor einer davon wirklich geladen wird.
     */
    private static int estimateHeight(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(
                x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level,
                level.getChunkSource().randomState());
    }

    private static int randomX(ServerLevel level, Side side, RandomSource random) {
        int limit = halfWidth(level) - BORDER_MARGIN;
        return (int) ArenaCenter.x(level)
                + side.getSign() * (WALL_MARGIN + random.nextInt(Math.max(1, limit - WALL_MARGIN)));
    }

    private static int randomZ(ServerLevel level, RandomSource random) {
        int limit = halfWidth(level) - BORDER_MARGIN;
        return (int) ArenaCenter.z(level) + random.nextInt(2 * limit) - limit;
    }

    private static int halfWidth(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        return (int) (border.getSize() / 2);
    }

    /**
     * Die tatsaechliche Oberflaeche an dieser Stelle, oder {@code null}, wenn sie nicht taugt.
     *
     * <p>Laedt den Chunk und liest seine Heightmap direkt aus, statt ueber
     * {@code level.getHeightmapPos} zu gehen — das liefert fuer ungeladene Chunks
     * stillschweigend die Weltuntergrenze.
     *
     * <p>Abgelehnt wird alles, was auf Wasser oder Lava steht. Das Laub uebergeht die
     * Heightmap schon von sich aus, sonst startete jemand auf einem Baum.
     */
    @Nullable
    private static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z));

        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        if (y <= level.getMinBuildHeight()) {
            return null;
        }

        BlockPos surface = new BlockPos(x, y, z);
        return level.getFluidState(surface.below()).isEmpty() ? surface : null;
    }

    /**
     * Wenn gar nichts taugt: die Mitte der Haelfte, komme was wolle.
     *
     * <p>Auch hier wird der Chunk geladen. Der Rueckfall darf nicht die Falle sein, gegen
     * die er absichern soll — genau daran ist die erste Fassung gescheitert.
     */
    private static BlockPos surfaceFallback(ServerLevel level, Side side) {
        int x = (int) ArenaCenter.x(level) + side.getSign() * (halfWidth(level) / 2);
        int z = (int) ArenaCenter.z(level);
        BlockPos surface = surfaceAt(level, x, z);
        return surface != null ? surface : new BlockPos(x, level.getSeaLevel() + 1, z);
    }

    private static double nearest(BlockPos candidate, List<BlockPos> taken) {
        double nearest = Double.MAX_VALUE;
        for (BlockPos other : taken) {
            nearest = Math.min(nearest, Math.sqrt(candidate.distSqr(other)));
        }
        return nearest == Double.MAX_VALUE ? Double.MAX_VALUE : nearest;
    }

    /**
     * Setzt einen Spieler an eine Stelle und laesst ihn dort heil ankommen.
     *
     * <p>Der Chunk wird vorher geladen, Schwung und Fallhoehe zurueckgesetzt: ein Teleport
     * mitten in den Join hinein ist genau der Moment, in dem jemand sonst durch noch nicht
     * geladenes Gelaende faellt und unten ankommt, bevor irgendetwas da ist.
     */
    private static void teleport(ServerPlayer player, Level level, BlockPos pos) {
        ServerLevel server = (ServerLevel) level;
        server.getChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));

        player.teleportTo(server, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYRot(), player.getXRot());

        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.clearFire();
    }
}
