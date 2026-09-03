package net.bananemdnsa.mchelden.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.bananemdnsa.mchelden.MCHelden;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Die Rechte, mit denen {@code /helden} aufgeteilt ist.
 *
 * <p>Frueher hing der ganze Baum an einer einzigen Pruefung an der Wurzel. Damit gab es fuer
 * eine Rechteverwaltung nur eine Entscheidung: alles oder nichts. Wer Bounties vergeben
 * durfte, konnte damit auch die naechste Phase ausloesen.
 *
 * <p>Jeder Zweig fragt jetzt seinen eigenen Node. Die <b>Namen sind die Befehlspfade</b> —
 * {@code mchelden.command.phase.next} ist {@code /helden phase next}. Wo die Blaetter eines
 * Zweiges dieselbe Macht haben, hoert der Name beim Zweig auf: {@code heart} deckt
 * {@code give}, {@code remove} und {@code set} gemeinsam ab, denn wer Herzen geben darf,
 * darf sie auch nehmen.
 *
 * <p><b>Ohne Rechteverwaltung aendert sich nichts.</b> Der Default-Resolver jedes Nodes
 * gibt Op-Stufe {@value #FALLBACK_LEVEL} zurueck — genau die Schwelle, die vorher fuer den
 * ganzen Baum galt.
 *
 * <p>Auf NeoForge traegt sich LuckPerms selbst als Handler der Permission-API ein und liest
 * {@link PermissionNode#getNodeName()} unveraendert als eigenen Node. Die Namen hier sind
 * also eins zu eins die Strings fuer {@code /lp group ... permission set}, Wildcards
 * eingeschlossen: {@code mchelden.command.bounty.*} erwischt alle vier Bounty-Zweige.
 */
public enum HeldenPermission {

    INFO("info"),
    HEART("heart"),
    REVIVE("revive"),
    COMBAT("combat"),
    DUELL("duell"),
    DEBUG("debug"),

    BOUNTY_SHOW("bounty.show"),
    BOUNTY_ROLL("bounty.roll"),
    BOUNTY_SET("bounty.set"),
    BOUNTY_CLEAR("bounty.clear"),

    PHASE_INFO("phase.info"),
    // Getrennt von `next`, weil `set` auch rueckwaerts springen kann.
    PHASE_NEXT("phase.next"),
    PHASE_SET("phase.set"),

    // Zwei Namen ohne eigenes Literal, wie `center.show`/`center.set`: `info` liest,
    // `run` startet und beendet. Starten und Beenden sind dieselbe Macht, also ein Node.
    EVENT_INFO("event.info"),
    EVENT_RUN("event.run"),

    WALL("wall"),
    FINALWAR("finalwar"),
    BORDER("border"),

    // Die beiden einzigen Namen, die keinem Literal entsprechen: `/helden center` ohne
    // Argument zeigt die Mitte, `here`, `reset` und `<x> <z>` verschieben sie. Der Schnitt
    // liegt zwischen Lesen und Schreiben, nicht an einem Wort im Befehl.
    CENTER_SHOW("center.show"),
    CENTER_SET("center.set"),

    // Lesen und Handeln getrennt, wie bei `bounty` und `time`. `info` zeigt den Inhalt
    // eines fremden Grabes und ist damit eine andere Auskunft als eine Positionsliste.
    GRAVE_LIST("grave.list"),
    GRAVE_INFO("grave.info"),
    GRAVE_TP("grave.tp"),
    GRAVE_REMOVE("grave.remove"),

    TIME_CHECK("time.check"),
    TIME_ADD("time.add"),
    TIME_SET("time.set"),

    RESET_HEARTS("reset.hearts"),
    RESET_BOUNTY("reset.bounty"),
    RESET_TIME("reset.time"),
    RESET_GRAVES("reset.graves"),
    RESET_ALL("reset.all");

    /** Ab dieser Op-Stufe gilt ein Node als erteilt, solange niemand ihn verwaltet. */
    private static final int FALLBACK_LEVEL = 2;

    private static final String PREFIX = MCHelden.MODID + ".command.";

    private final PermissionNode<Boolean> node;

    HeldenPermission(String path) {
        this.node = new PermissionNode<>(
                MCHelden.MODID,
                "command." + path,
                PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.hasPermissions(FALLBACK_LEVEL));
    }

    /**
     * Meldet die Nodes bei der Permission-API an.
     *
     * <p>Ohne diesen Schritt wirft {@link PermissionAPI#getPermission} beim ersten Zugriff.
     */
    public static void gather(PermissionGatherEvent.Nodes event) {
        for (HeldenPermission permission : values()) {
            event.addNodes(permission.node);
        }
    }

    /**
     * Ob die Quelle diesen Zweig benutzen darf.
     *
     * <p>Die Permission-API kennt nur Spieler. Serverkonsole und Befehlsbloecke haben
     * keinen, fuer sie bleibt es bei der Op-Stufe.
     */
    public boolean granted(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.hasPermission(FALLBACK_LEVEL);
        }

        return Boolean.TRUE.equals(PermissionAPI.getPermission(player, this.node));
    }

    /**
     * Die Pruefung fuer einen Zweig, unter dem mehrere Nodes liegen.
     *
     * <p>Brigadier raeumt Zweige nicht weg, nur weil kein Kind darunter benutzbar ist. Ohne
     * das hier saehe eine Rolle ohne jedes Bounty-Recht {@code /helden bounty} in der
     * Vervollstaendigung stehen und liefe erst beim Ausfuehren gegen die Wand.
     *
     * <p>Die Gruppe wird einmal beim Bau des Befehlsbaums bestimmt, nicht bei jeder Pruefung.
     */
    public static Predicate<CommandSourceStack> branch(String path) {
        List<HeldenPermission> group = under(PREFIX + path + ".");
        return source -> anyOf(group, source);
    }

    /**
     * Die Pruefung fuer die Wurzel {@code /helden}.
     *
     * <p>Muss durchlaessig sein, sonst blendet Brigadier den ganzen Baum aus, bevor die
     * Zweige ueberhaupt gefragt werden. Wer keinen einzigen Node hat, sieht den Befehl
     * dafuer gar nicht erst.
     */
    public static Predicate<CommandSourceStack> root() {
        List<HeldenPermission> all = under(PREFIX);
        return source -> anyOf(all, source);
    }

    private static List<HeldenPermission> under(String prefix) {
        List<HeldenPermission> group = new ArrayList<>();
        for (HeldenPermission permission : values()) {
            if (permission.node.getNodeName().startsWith(prefix)) {
                group.add(permission);
            }
        }
        return group;
    }

    private static boolean anyOf(List<HeldenPermission> group, CommandSourceStack source) {
        for (HeldenPermission permission : group) {
            if (permission.granted(source)) {
                return true;
            }
        }
        return false;
    }
}
