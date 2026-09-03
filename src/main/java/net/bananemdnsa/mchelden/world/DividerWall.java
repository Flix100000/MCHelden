package net.bananemdnsa.mchelden.world;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.state.Side;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Die Trennwand durch die Arenamitte.
 *
 * <p>Minecraft kann pro Dimension nur <em>eine</em> Worldborder, und die ist von der
 * Weltgrenze belegt. Die Wand ist deswegen nachgebaut: hier die Koordinatenpruefung,
 * clientseitig ein eigener Renderer. Die Hoehe erledigt sich dabei von selbst — eine
 * Koordinatenpruefung kennt kein Oben und Unten.
 *
 * <p><b>Nichts kommt rueber.</b> Keine Spieler, keine Enderperlen, keine Haken, keine
 * Pfeile, keine geworfenen Items. Eine Regel ohne Ausnahmen muss niemand nachschlagen.
 */
public final class DividerWall {
    /**
     * Wie nah eine Entitaet der Linie kommen darf.
     *
     * <p>Etwas mehr als eine halbe Spielerbreite: sonst ragt der Koerper durch die Wand,
     * und aus der Ferne sieht es aus, als stuende jemand mitten darin.
     */
    public static final double MARGIN = 0.4;

    /**
     * Ob die Wand steht, aus Sicht des Clients.
     *
     * <p>Die Kollision laeuft auf beiden Seiten, der Client muss den Zustand also selbst
     * kennen. Er wird beim Sync gesetzt; auf dem Server steht die Wahrheit im
     * {@link GameState}. Zwei Felder statt eines, weil ein Client keinen {@code GameState}
     * hat und der Server das Feld eines Clients nicht anfassen darf.
     */
    private static boolean clientWallUp = true;

    /**
     * Wo die Oberkante der sinkenden Wand gerade steht, in Welt-Y.
     *
     * <p>{@link Double#MAX_VALUE} heisst: sie sinkt nicht, die Wand reicht bis oben.
     *
     * <p>Getrennt je Seite, aus demselben Grund wie {@link #clientWallUp}. Beide rechnen mit
     * {@link #edgeAt} — dieselbe Formel, aber an zwei Uhren: der Client zaehlt in echter
     * Zeit, dieser Tick in Serverticks. Damit die Kanten trotzdem zusammenbleiben, schickt
     * {@link #tickDrop} den Stand einmal pro Sekunde mit.
     *
     * <p><b>Die Kollision liest denselben Wert wie der Renderer.</b> Sonst stuende man vor
     * einer sichtbar abgesunkenen Wand, durch die man trotzdem nicht hindurchkommt — und
     * das liest sich wie ein Fehler, nicht wie eine Inszenierung.
     */
    private static double clientEdge = Double.MAX_VALUE;
    private static double serverEdge = Double.MAX_VALUE;

    /** Ab hier lohnt sich die genaue Pruefung. Alles Weitere ist ein Vergleich pro Tick. */
    private static final double CHECK_RANGE = 6.0;

    /** Wie viele Ticks zwischen zwei Hinweisen liegen muessen. */
    private static final int DENIAL_GAP = 20;
    private static int lastDenial = -DENIAL_GAP;

    /**
     * Wie lange das Absinken dauert. Muss zum Phasen-Countdown passen.
     *
     * <p>Steht hier und nicht im {@code PhaseManager}: der Client braucht dieselbe Zahl,
     * und die Wand ist der Gegenstand, um den es geht.
     */
    public static final int DROP_TICKS = 240;

    /**
     * Von wo bis wohin die Oberkante wandert.
     *
     * <p>Der Startwert liegt bewusst weit ueber der Bauhoehe: solange die Kante darueber
     * steht, deckelt der Renderer die Wand nicht, und es gibt beim Beginn des Absinkens
     * keinen sichtbaren Sprung. Das Ende liegt unter dem Grundgestein, damit auch in einer
     * Hoehle nichts stehenbleibt.
     */
    private static final double DROP_TOP = 600.0;
    private static final double DROP_BOTTOM = -80.0;

    /**
     * Ab welcher Hoehe die Kante ins Blickfeld kommt, und wie viel Zeit sie bis dahin
     * bekommt.
     *
     * <p>Der Weg zerfaellt in zwei sehr ungleiche Stuecke: dreihundertvierzig Bloecke
     * Himmel, den ohnehin niemand sieht, und danach der Bereich, in dem die Kante am
     * Horizont und schliesslich vor den eigenen Fuessen vorbeizieht. Ohne die Trennung
     * verbringt sie den Grossteil der Zeit ausser Sicht und rauscht dann durch das
     * Blickfeld — die erste Fassung war genau deswegen zu schnell.
     */
    private static final double DROP_VISIBLE = 260.0;
    private static final double DROP_SKY_SHARE = 0.15;

    /** Wie hoch und wie weit die Funken entlang der Kante streuen. */
    private static final double EDGE_SPREAD_Y = 1.5;
    private static final double EDGE_SPREAD_Z = 30.0;
    /** Wie viele Funken je Spieler und Tick. */
    private static final int EDGE_COUNT = 55;
    /** Wie weit die Glut ueber und unter der Kante nachzieht. */
    private static final double EDGE_TRAIL_Y = 7.0;

    /** Wie oft es waehrend des Absinkens grollt. */
    private static final int RUMBLE_GAP = 14;

    /** Wie oft der Server den Stand des Absinkens beim Client nachzieht. */
    private static final int PROGRESS_GAP = 20;

    /**
     * Der Dampf am Boden, dort wo die Wand hindurchgegangen ist.
     *
     * <p>Beprobt werden ein paar Stellen entlang der Linie in Sichtweite des Spielers. Der
     * Boden ist selten flach: auf einem Huegel zieht der Dampf frueher auf als im Tal, und
     * genau das macht aus einer geraden Linie eine Landschaft.
     */
    private static final int STEAM_SAMPLES = 7;
    private static final double STEAM_SPREAD_Z = 34.0;
    /** Ab wie vielen Bloecken ueber dem Boden die Kante Dampf ausloest. */
    private static final double STEAM_TRIGGER = 12.0;
    /**
     * Wie lange der Dampf nachzieht, nachdem die Wand weg ist.
     *
     * <p>Ohne das hoert er in dem Moment auf, in dem der Krieg beginnt — und die Stelle,
     * an der eben noch eine Wand stand, waere sofort wieder ein gewoehnliches Stueck Wiese.
     */
    private static final int STEAM_LINGER = 90;

    private static int steamTicks;

    private DividerWall() {
    }

    public static boolean isUp(MinecraftServer server) {
        return GameState.get(server).isWallUp();
    }

    /** Beim Empfang des Spielzustands setzen. Nur auf dem Client. */
    public static void setClientWallUp(boolean up) {
        clientWallUp = up;
    }

    /** Aus dem Client-Tick setzen, solange die Wand sinkt. */
    public static void setClientEdge(double edge) {
        clientEdge = edge;
    }

    /** Wo die Oberkante gerade steht. {@link Double#MAX_VALUE}, solange sie nicht sinkt. */
    public static double edgeFor(Level level) {
        return level.isClientSide() ? clientEdge : serverEdge;
    }

    /**
     * Die Hoehe der Oberkante nach so vielen Ticks.
     *
     * <p>Beide Seiten rechnen mit dieser Methode, damit Bild und Kollision nicht
     * auseinanderlaufen koennen.
     */
    public static double edgeAt(double elapsedTicks) {
        double progress = Math.min(1.0, Math.max(0.0, elapsedTicks / DROP_TICKS));

        // Zuerst schnell durch den Himmel, dann gleichmaessig und langsam durch alles, was
        // man tatsaechlich sieht.
        if (progress < DROP_SKY_SHARE) {
            return lerp(progress / DROP_SKY_SHARE, DROP_TOP, DROP_VISIBLE);
        }

        double rest = (progress - DROP_SKY_SHARE) / (1.0 - DROP_SKY_SHARE);
        return lerp(rest, DROP_VISIBLE, DROP_BOTTOM);
    }

    /** Wie weit das Absinken fortgeschritten ist, 0 bis 1. Fuer die Farbe der Wand. */
    public static double dropHeat(double edge) {
        if (edge >= DROP_VISIBLE) {
            return 0.0;
        }
        return Math.min(1.0, (DROP_VISIBLE - edge) / (DROP_VISIBLE - DROP_BOTTOM));
    }

    private static double lerp(double amount, double from, double to) {
        return from + (to - from) * amount;
    }

    /** Steht die Wand — egal ob gerade auf dem Server oder auf dem Client gefragt wird. */
    public static boolean isUp(Level level) {
        if (level.isClientSide()) {
            return clientWallUp;
        }

        MinecraftServer server = level.getServer();
        return server != null && isUp(server);
    }

    /**
     * Beschneidet eine Bewegung, damit sie die Linie nicht ueberquert.
     *
     * <p>Wird aus {@code Entity.collide} heraus aufgerufen und laeuft damit auf Server und
     * Client. Die Wand fuehlt sich dadurch an wie eine Wand aus Bloecken, statt den Spieler
     * jeden Tick zurueckzuzerren.
     *
     * @return die erlaubte Bewegung, oder {@code null}, wenn nichts zu beschneiden ist
     */
    @Nullable
    public static Vec3 limit(Entity entity, Vec3 movement) {
        if (movement.x == 0.0 || !isUp(entity.level())) {
            return null;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return null;
        }

        // Ueber der abgesunkenen Kante ist keine Wand mehr.
        if (entity.getY() > edgeFor(entity.level())) {
            return null;
        }

        Double allowed = slide(entity.getX(), ArenaCenter.x(entity.level()), movement.x);
        return allowed == null ? null : new Vec3(allowed, movement.y, movement.z);
    }

    /**
     * Beschneidet einen Schritt entlang X so, dass er die Linie nicht ueberquert.
     *
     * <p>Reine Rechnung, damit sie ohne Spielstart pruefbar ist.
     *
     * @param x wo die Entitaet steht, in Weltkoordinaten
     * @param center wo die Arena liegt
     * @param dx wie weit sie sich bewegen will
     * @return die erlaubte Bewegung, oder {@code null}, wenn nichts zu beschneiden ist
     */
    @Nullable
    public static Double slide(double x, double center, double dx) {
        // Wer schon in der Linie steht, wird nicht eingesperrt — sonst kaeme er nie heraus.
        double from = x - center;
        if (Math.abs(from) < MARGIN) {
            return null;
        }

        double limit = Math.signum(from) * MARGIN;
        double to = from + dx;
        boolean crossing = from < 0 ? to > limit : to < limit;

        return crossing ? limit - from : null;
    }

    /**
     * Darf dieser Spieler an dieser X-Koordinate ueberhaupt etwas anrichten?
     *
     * <p>Eine Wand, durch die man bauen, abbauen und zuschlagen kann, ist keine Wand. Zwei
     * Spieler stehen an der Linie nur gut einen halben Block auseinander — ohne diese
     * Pruefung koennten sie sich ueber die Trennung hinweg verpruegeln, waehrend keiner von
     * beiden hindurchkann.
     *
     * <p>Kreativ- und Zuschauermodus sind ausgenommen, damit Ops aufraeumen koennen.
     */
    public static boolean isAcross(Player player, double x) {
        if (!isUp(player.level()) || player.isCreative() || player.isSpectator()) {
            return false;
        }
        double center = ArenaCenter.x(player.level());
        return Side.of(player.getX() - center) != Side.of(x - center);
    }

    /** Dasselbe fuer einen Block: gerechnet wird mit seiner Mitte, nicht mit seiner Ecke. */
    public static boolean isAcross(Player player, BlockPos pos) {
        return isAcross(player, pos.getX() + 0.5);
    }

    /** Abbauen jenseits der Linie. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isAcross(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
            deny(event.getPlayer());
        }
    }

    /** Setzen jenseits der Linie. */
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && isAcross(player, event.getPos())) {
            event.setCanceled(true);
            deny(player);
        }
    }

    /**
     * Anfassen jenseits der Linie — Hebel, Tueren, Kisten, Werfen gegen die Wand.
     *
     * <p>Faengt auch das Setzen ab, bevor es passiert: das Platzieren-Ereignis feuert erst,
     * wenn der Block schon steht.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isAcross(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
            deny(event.getEntity());
        }
    }

    /** Draufschlagen jenseits der Linie. */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isAcross(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
            deny(event.getEntity());
        }
    }

    /** Und zuschlagen ueber die Linie hinweg. */
    public static void onAttack(AttackEntityEvent event) {
        if (isAcross(event.getEntity(), event.getTarget().getX())) {
            event.setCanceled(true);
            deny(event.getEntity());
        }
    }

    /**
     * Sagt einmal, warum nichts passiert ist.
     *
     * <p>In der Actionbar und gedrosselt: ein Linksklick feuert waehrend des Abbauens
     * mehrmals pro Sekunde, und eine Zeile pro Tick waere unbrauchbar.
     */
    private static void deny(Player player) {
        if (!(player instanceof ServerPlayer server) || player.tickCount - lastDenial < DENIAL_GAP) {
            return;
        }

        lastDenial = player.tickCount;
        server.displayClientMessage(HeldenText.wallBlocked(), true);
    }

    /**
     * Setzt die Wand und schickt den neuen Stand an alle.
     *
     * <p>Der Client zeichnet sie nur, wenn er weiss, dass sie steht — anders als bei der
     * Kollision gibt es dafuer keine Ableitung aus etwas anderem.
     */
    public static void setUp(MinecraftServer server, boolean up) {
        GameState.get(server).setWallUp(up);

        // Steht sie wieder, ist ein laufender Bruch hinfaellig. Ohne den Abbruch bliebe die
        // Luecke stehen, wenn ein Op den Phasenwechsel mitten im Countdown zuruecknimmt.
        if (up) {
            serverEdge = Double.MAX_VALUE;
            NetworkHandler.sendWallDrop(server, false);
        }

        NetworkHandler.syncAll(server);
    }

    /**
     * Haelt Entitaeten auf ihrer Seite.
     *
     * <p>Laeuft fuer jede Entitaet in jedem Tick, deswegen steht der billige Abstandstest
     * vorn: alles weiter als ein paar Bloecke von der Linie kostet einen Vergleich.
     */
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || Math.abs(entity.getX()) > CHECK_RANGE) {
            return;
        }

        MinecraftServer server = entity.getServer();
        if (server == null || !isUp(server)) {
            return;
        }

        if (entity instanceof ServerPlayer player) {
            holdPlayer(server, player);
            return;
        }

        // Projektile verschwinden an der Wand, statt daran abzuprallen: ein Pfeil, der
        // zurueckfliegt, sieht aus wie ein Fehler. Alles andere prallt ab — ein geworfenes
        // Item zu loeschen waere ein Verlust, den niemand versteht.
        double center = ArenaCenter.x(entity.level());
        Side from = Side.of(entity.xo - center);
        if (!from.contains(entity.getX() - center)) {
            if (entity instanceof Projectile) {
                absorb(entity);
            } else {
                push(entity, from, center);
            }
        }
    }

    /**
     * Das Sicherheitsnetz fuer Spieler auf der falschen Seite.
     *
     * <p>Gegen die Wand laufen faengt der Kollisions-Mixin ab, ohne Ruckeln. Hier bleibt
     * nur der Notfall: wer tatsaechlich <em>jenseits</em> seiner Seite steht — durch einen
     * Teleport, ein Portal oder einen Fehler — wird zurueckgeholt. Das passiert selten
     * genug, dass ein harter Sprung dabei in Ordnung ist.
     *
     * <p>Kreativ- und Zuschauermodus sind ausgenommen: sonst kaeme kein Op zum Aufraeumen
     * an die andere Haelfte.
     */
    private static void holdPlayer(MinecraftServer server, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        PlayerState state = PlayerStateStore.get(server).find(player.getUUID());
        Side side = state != null ? state.getSide() : null;
        double center = ArenaCenter.x(server);
        if (side == null || side.contains(player.getX() - center)) {
            return;
        }

        // Nicht zurueckholen, wer ueber die abgesunkene Kante hinuebergegangen ist.
        if (player.getY() > serverEdge) {
            return;
        }

        player.teleportTo(center + side.getSign() * MARGIN, player.getY(), player.getZ());
        player.setDeltaMovement(0, player.getDeltaMovement().y, player.getDeltaMovement().z);
        player.hurtMarked = true;
    }

    private static void push(Entity entity, Side from, double center) {
        entity.setPos(center + from.getSign() * MARGIN, entity.getY(), entity.getZ());

        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(-movement.x * 0.5, movement.y, movement.z);
        entity.hurtMarked = true;
    }

    private static void absorb(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(), entity.getZ(),
                    6, 0.1, 0.1, 0.1, 0.02);
        }
        entity.discard();
    }

    /**
     * Das Absinken der Wand.
     *
     * <p>Sie sinkt ueberall gleichzeitig, statt von der Mitte nach aussen aufzubrechen.
     * Der Grund ist einfach: bei zweitausend Bloecken Laenge saehe eine durchlaufende Welle
     * nur, wer zufaellig nahe am Ursprung steht. Alle anderen schauen sekundenlang auf eine
     * unveraenderte Wand, und dann ist sie weg.
     *
     * <p>Die Funken liegen deswegen auch nicht an einer wandernden Stelle, sondern
     * <b>bei jedem Spieler an seinem eigenen Z</b> — jeder sieht die Kante direkt vor sich
     * absinken.
     *
     * @param elapsedTicks wie lange das Absinken schon laeuft
     */
    public static void tickDrop(MinecraftServer server, int elapsedTicks) {
        serverEdge = edgeAt(elapsedTicks);

        // Einmal pro Sekunde den Stand nachziehen: der Client zaehlt selbst mit, aber an
        // seiner eigenen Uhr. Zwoelf Pakete fuer den ganzen Vorgang.
        if (elapsedTicks % PROGRESS_GAP == 0) {
            NetworkHandler.sendWallDropProgress(server, elapsedTicks);
        }

        // Der Dampf laeuft ueber den eigenen Ticker weiter, damit er das Ende des
        // Countdowns ueberdauert.
        steamTicks = STEAM_LINGER;

        ServerLevel level = server.overworld();
        for (ServerPlayer player : level.players()) {
            spawnEdge(level, player);
        }

        // Ein Grollen im Takt, damit das Absinken auch dann ankommt, wenn man gerade
        // woanders hinschaut. Die Tonhoehe steigt, je tiefer die Kante steht.
        if (elapsedTicks % RUMBLE_GAP == 0) {
            float step = (float) Math.min(1.0, elapsedTicks / (double) DROP_TICKS);
            for (ServerPlayer player : level.players()) {
                player.playNotifySound(SoundEvents.DEEPSLATE_BREAK, SoundSource.MASTER,
                        0.8f, 0.45f + step * 0.35f);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER,
                        0.5f, 0.5f + step * 0.3f);
            }
        }
    }

    /**
     * Funken entlang der sinkenden Kante, in Sichtweite dieses Spielers.
     *
     * <p>Verschickt mit gesetztem Fernflag. Der bequeme Weg,
     * {@code level.sendParticles(...)}, erreicht nur Spieler im Umkreis von zweiunddreissig
     * Bloecken um die Partikelposition — die Kante liegt aber auf der Linie, und wer
     * fuenfzig Bloecke daneben steht, bekaeme kein einziges Partikel zu sehen.
     */
    private static void spawnEdge(ServerLevel level, ServerPlayer player) {
        double x = ArenaCenter.x(level);
        double z = player.getZ();

        // Die Schnittkante selbst: hell, dicht, schmal.
        level.sendParticles(player, ParticleTypes.END_ROD, true,
                x, serverEdge, z, EDGE_COUNT, 0.4, EDGE_SPREAD_Y, EDGE_SPREAD_Z, 0.0);
        level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true,
                x, serverEdge, z, EDGE_COUNT / 2, 0.4, EDGE_SPREAD_Y, EDGE_SPREAD_Z, 0.05);

        // Glut, die nach unten wegfaellt: das Stueck Wand, das gerade verloren geht.
        level.sendParticles(player, ParticleTypes.FLAME, true,
                x, serverEdge - EDGE_TRAIL_Y / 2.0, z,
                EDGE_COUNT / 2, 0.4, EDGE_TRAIL_Y / 2.0, EDGE_SPREAD_Z, 0.0);

        // Und Rauch, der darueber stehenbleibt, wo eben noch Wand war.
        level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true,
                x, serverEdge + EDGE_TRAIL_Y, z,
                EDGE_COUNT / 4, 0.5, EDGE_TRAIL_Y, EDGE_SPREAD_Z, 0.01);
    }

    /**
     * Laesst den Dampf nachziehen. Aus dem Server-Tick aufrufen.
     *
     * <p>Getrennt vom Absinken, weil er es ueberdauern soll: die Stelle, an der eine Wand
     * stand, raucht noch, wenn der Krieg schon begonnen hat.
     */
    public static void tick(MinecraftServer server) {
        if (steamTicks <= 0) {
            return;
        }

        steamTicks--;
        ServerLevel level = server.overworld();
        for (ServerPlayer player : level.players()) {
            spawnSteam(level, player);
        }
    }

    /**
     * Dampf am Boden entlang der Linie, in Sichtweite dieses Spielers.
     *
     * <p>Die Bodenhoehe wird je Stelle nachgeschlagen. Liegt der Chunk nicht vor, kommt
     * dabei die Weltuntergrenze heraus — die Stelle wird dann uebergangen, statt Dampf im
     * Nichts zu erzeugen.
     */
    private static void spawnSteam(ServerLevel level, ServerPlayer player) {
        double x = ArenaCenter.x(level);
        double along = player.getZ();

        for (int sample = 0; sample < STEAM_SAMPLES; sample++) {
            double z = along - STEAM_SPREAD_Z
                    + 2.0 * STEAM_SPREAD_Z * sample / (STEAM_SAMPLES - 1.0);

            int ground = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
            if (ground <= level.getMinBuildHeight() || serverEdge > ground + STEAM_TRIGGER) {
                continue;
            }

            level.sendParticles(player, ParticleTypes.CLOUD, true,
                    x, ground + 0.2, z, 5, 0.25, 0.1, 2.0, 0.012);
            level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    x, ground + 0.6, z, 2, 0.25, 0.2, 2.0, 0.004);
        }
    }

    /** Ein Ton, den alle hoeren, egal wo sie stehen. */
    public static void playForEveryone(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0f, 0.6f);
        }
    }

    /** Die Seite eines Spielers, oder {@code null}, solange keine zugeteilt ist. */
    @Nullable
    public static Side sideOf(MinecraftServer server, ServerPlayer player) {
        PlayerState state = PlayerStateStore.get(server).find(player.getUUID());
        return state != null ? state.getSide() : null;
    }
}
