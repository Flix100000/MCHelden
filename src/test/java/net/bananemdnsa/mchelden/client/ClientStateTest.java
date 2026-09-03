package net.bananemdnsa.mchelden.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.bananemdnsa.mchelden.world.DividerWall;

import org.junit.jupiter.api.Test;

/**
 * Das Absinken der Trennwand laeuft auf beiden Seiten durch {@link DividerWall#edgeAt},
 * aber an zwei verschiedenen Uhren: der Server zaehlt Serverticks, der Client seine eigenen
 * in echter Zeit. Unter Last war die Wand auf dem Client eher unten als auf dem Server —
 * man rennt durch die Luecke und wird von einer Wand gestoppt, die nicht mehr da ist.
 *
 * <p>Der Server schickt deswegen mit, wie lange das Absinken bei ihm schon laeuft.
 */
class ClientStateTest {

    @Test
    void derStandVomServerSetztDieKante() {
        ClientState.onWallDrop(true, 137);

        assertEquals(DividerWall.edgeAt(137), ClientState.wallEdge(0f));
    }

    /** Sinkt sie nicht, reicht die Wand bis oben — daran haengt die Kollision. */
    @Test
    void ohneAbsinkenReichtDieWandBisOben() {
        ClientState.onWallDrop(false, 0);

        assertEquals(Double.MAX_VALUE, ClientState.wallEdge(0f));
    }
}
