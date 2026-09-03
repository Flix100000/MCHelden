package net.bananemdnsa.mchelden.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/**
 * Die Richtungsangabe ist das Einzige an der Respawn-Nachricht, das falsch sein kann, ohne
 * dass es auffaellt. Wer nach Sueden laeuft, weil dort „suedlich" stand, merkt den Fehler
 * erst nach zweihundert Blöcken — und dann ist der Lichtstrahl aus.
 */
class GraveDirectionTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    private static String directionTo(int x, int z) {
        return GraveDirection.key(ORIGIN, new BlockPos(x, 64, z));
    }

    /** In Minecraft zeigt Norden nach -Z und Osten nach +X. */
    @Test
    void dieVierHauptrichtungen() {
        assertEquals("mchelden.direction.n", directionTo(0, -100));
        assertEquals("mchelden.direction.e", directionTo(100, 0));
        assertEquals("mchelden.direction.s", directionTo(0, 100));
        assertEquals("mchelden.direction.w", directionTo(-100, 0));
    }

    @Test
    void dieVierZwischenrichtungen() {
        assertEquals("mchelden.direction.ne", directionTo(100, -100));
        assertEquals("mchelden.direction.se", directionTo(100, 100));
        assertEquals("mchelden.direction.sw", directionTo(-100, 100));
        assertEquals("mchelden.direction.nw", directionTo(-100, -100));
    }

    /**
     * Jede Richtung traegt ihren Namen mittig: Norden reicht von 337,5 Grad bis 22,5 Grad.
     * Knapp innerhalb der Grenze ist es noch Norden, knapp darueber schon Nordost.
     */
    @Test
    void dieGrenzenLiegenMittigZwischenDenRichtungen() {
        // 22 Grad oestlich von Norden: 1000 * tan(22 Grad) ist gut 404.
        assertEquals("mchelden.direction.n", directionTo(404, -1000));
        // 23 Grad: gut 424.
        assertEquals("mchelden.direction.ne", directionTo(424, -1000));

        // Dasselbe auf der anderen Seite von Norden.
        assertEquals("mchelden.direction.n", directionTo(-404, -1000));
        assertEquals("mchelden.direction.nw", directionTo(-424, -1000));
    }

    /** Der Nordsektor umspannt den Nullpunkt des Winkels — dort bricht eine naive Rechnung. */
    @Test
    void nordenBrichtNichtAmWinkelsprung() {
        assertEquals("mchelden.direction.n", directionTo(1, -1000));
        assertEquals("mchelden.direction.n", directionTo(-1, -1000));
    }

    @Test
    void derAbstandIgnoriertDieHoehe() {
        BlockPos tief = new BlockPos(30, -50, 40);
        BlockPos hoch = new BlockPos(30, 300, 40);

        assertEquals(50, GraveDirection.distance(ORIGIN, tief));
        assertEquals(50, GraveDirection.distance(ORIGIN, hoch));
    }

    @Test
    void derAbstandRundetAufGanzeBloecke() {
        assertEquals(1, GraveDirection.distance(ORIGIN, new BlockPos(1, 64, 1)));
        assertEquals(3, GraveDirection.distance(ORIGIN, new BlockPos(2, 64, 2)));
    }

    /**
     * Wer an seinem Bett stirbt, respawnt in derselben Saeule. „0 Bloecke noerdlich" waere
     * Unsinn, deswegen muss die Nachricht diesen Fall erkennen koennen.
     */
    @Test
    void dieselbeSaeuleIstErkennbar() {
        assertTrue(GraveDirection.sameSpot(ORIGIN, new BlockPos(0, 120, 0)));
        assertFalse(GraveDirection.sameSpot(ORIGIN, new BlockPos(0, 64, 1)));
    }

    /** Auch weit draussen darf die Rechnung nicht ueberlaufen. */
    @Test
    void grosseEntfernungenBleibenRichtig() {
        BlockPos weit = new BlockPos(-2_000_000, 64, -2_000_000);
        assertEquals("mchelden.direction.nw", GraveDirection.key(ORIGIN, weit));
        assertEquals(2_828_427, GraveDirection.distance(ORIGIN, weit));
    }
}
