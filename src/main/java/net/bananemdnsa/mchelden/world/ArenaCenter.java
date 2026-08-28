package net.bananemdnsa.mchelden.world;

import net.bananemdnsa.mchelden.MCHeldenConfig;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.GameState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Wo die Arena liegt. Safezone, Trennwand und Weltborder haengen alle drei daran.
 *
 * <p>Vorher stand die Mitte an rund einem Dutzend Stellen als Ursprung im Code: die Safezone
 * rechnete {@code x*x + z*z}, die Wand entschied ueber das Vorzeichen von X. Das war kein
 * Fehler, solange sich nichts verschieben liess — aber es hiess auch, dass es keinen Ort
 * gab, an dem die Mitte <em>steht</em>.
 *
 * <p>Jetzt steht sie hier. Die reinen Rechenfunktionen der Safezone bleiben unveraendert und
 * rechnen weiterhin gegen den Ursprung; sie bekommen ihre Koordinaten nur bereits um die
 * Mitte verschoben herein. Das haelt sie pruefbar, ohne dass jede von ihnen die Mitte kennen
 * muesste.
 *
 * <p>Zwei Felder statt eines, weil ein Client keinen {@link GameState} hat und der Server
 * das Feld eines Clients nicht anfassen darf — dieselbe Aufteilung wie bei der Trennwand.
 */
public final class ArenaCenter {
    private static double clientX;
    private static double clientZ;

    private ArenaCenter() {
    }

    public static double x(Level level) {
        if (level.isClientSide()) {
            return clientX;
        }
        MinecraftServer server = level.getServer();
        return server == null ? 0.0 : x(server);
    }

    public static double z(Level level) {
        if (level.isClientSide()) {
            return clientZ;
        }
        MinecraftServer server = level.getServer();
        return server == null ? 0.0 : z(server);
    }

    public static double x(MinecraftServer server) {
        return GameState.get(server).getCenterX();
    }

    public static double z(MinecraftServer server) {
        return GameState.get(server).getCenterZ();
    }

    /** Beim Empfang des Spielzustands setzen. Nur auf dem Client. */
    public static void setClient(double x, double z) {
        clientX = x;
        clientZ = z;
    }

    /**
     * Schiebt die ganze Arena.
     *
     * <p>Der einzige Weg, die Mitte zu aendern. Alles drei muss zusammen wandern: stuende
     * die Wand weiter bei X=0, waehrend die Border woanders liegt, haette eine Seite mehr
     * Land als die andere.
     */
    public static void move(MinecraftServer server, double x, double z) {
        GameState.get(server).setCenter(x, z);
        border(server).setCenter(x, z);

        // Ohne Sync zeichnen die Clients Kuppel und Wand weiter am alten Fleck — beide
        // Renderer rechnen gegen die Mitte, und die kennen sie nur aus dem Zustandspaket.
        NetworkHandler.syncAll(server);
    }

    /** Wo eine frische Welt ihre Arena hinstellt. Aus der Serverconfig. */
    public static double defaultX() {
        return MCHeldenConfig.CENTER_X.get();
    }

    public static double defaultZ() {
        return MCHeldenConfig.CENTER_Z.get();
    }

    /**
     * Stellt die Arena beim allerersten Start einer Welt auf den eingestellten Fleck.
     *
     * <p>Nur einmal, am selben Schalter wie die Bordergroesse: bei jedem Start zu setzen
     * wuerde eine im Spiel verschobene Arena bei jedem Neustart zurueckwerfen. Und deswegen
     * <em>vor</em> {@link BorderController#initialise} — der legt den Schalter um.
     */
    public static void initialise(MinecraftServer server) {
        if (GameState.get(server).isBorderSet()) {
            return;
        }
        move(server, defaultX(), defaultZ());
    }

    /**
     * Was die Weltborder tatsaechlich sagt.
     *
     * <p>Getrennt abfragbar, weil beide auseinanderlaufen koennen: ein Op darf jederzeit
     * {@code /worldborder center} von Hand setzen, und dann steht die Kuppel woanders als
     * die Weltgrenze. Die Ausgabe von {@code /helden center} nennt deswegen beide.
     */
    public static double borderCenterX(MinecraftServer server) {
        return border(server).getCenterX();
    }

    public static double borderCenterZ(MinecraftServer server) {
        return border(server).getCenterZ();
    }

    private static WorldBorder border(MinecraftServer server) {
        return server.overworld().getWorldBorder();
    }
}
