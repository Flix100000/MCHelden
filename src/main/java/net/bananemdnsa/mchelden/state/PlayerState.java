package net.bananemdnsa.mchelden.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Persistenter Zustand eines Spielers. Haelt alles, was ueber eine Session hinaus
 * ueberleben muss. Der Combat-Timer gehoert bewusst nicht dazu: ein Logout im Kampf
 * zaehlt als Tod, es gibt also nie einen gespeicherten Kampfzustand.
 */
public class PlayerState {
    public static final int DEFAULT_HEARTS = 3;
    public static final int MAX_HEARTS = 4;

    /** Tageskontingent in der Aufbauphase: eine Stunde. */
    public static final int DAILY_PLAYTIME_SECONDS = 60 * 60;

    private static final String KEY_UUID = "uuid";
    private static final String KEY_NAME = "name";
    private static final String KEY_HEARTS = "hearts";
    private static final String KEY_BOUNTY_TARGET = "bountyTarget";
    private static final String KEY_BOUNTY_RESOLVED = "bountyResolved";
    private static final String KEY_PLAYTIME_USED = "playtimeUsed";
    private static final String KEY_PLAYTIME_DAY = "playtimeDay";
    private static final String KEY_ELIMINATED = "eliminated";
    private static final String KEY_PENDING_RESPAWN = "pendingRespawn";
    private static final String KEY_PENDING_BOUNTY_ROLL = "pendingBountyRoll";
    private static final String KEY_SIDE = "side";
    private static final String KEY_START_SPAWN = "startSpawn";

    private final UUID uuid;
    private String name = "";
    private int hearts = DEFAULT_HEARTS;
    @Nullable
    private UUID bountyTarget;
    private boolean bountyResolved;
    private int playtimeUsedSeconds;
    private long playtimeResetDay = -1L;
    private boolean eliminated;
    private boolean pendingRespawn;
    private boolean pendingBountyRoll;
    @Nullable
    private Side side;
    @Nullable
    private BlockPos startSpawn;

    public PlayerState(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getHearts() {
        return hearts;
    }

    /** Klemmt auf 0 bis {@link #MAX_HEARTS}. Der Deckel bei 4 ist eine Spielregel, keine Einstellung. */
    public void setHearts(int hearts) {
        this.hearts = Math.max(0, Math.min(MAX_HEARTS, hearts));
    }

    @Nullable
    public UUID getBountyTarget() {
        return bountyTarget;
    }

    public void setBountyTarget(@Nullable UUID bountyTarget) {
        this.bountyTarget = bountyTarget;
    }

    public boolean isBountyResolved() {
        return bountyResolved;
    }

    public void setBountyResolved(boolean bountyResolved) {
        this.bountyResolved = bountyResolved;
    }

    public int getPlaytimeUsedSeconds() {
        return playtimeUsedSeconds;
    }

    public void setPlaytimeUsedSeconds(int playtimeUsedSeconds) {
        this.playtimeUsedSeconds = Math.max(0, playtimeUsedSeconds);
    }

    public long getPlaytimeResetDay() {
        return playtimeResetDay;
    }

    public void setPlaytimeResetDay(long playtimeResetDay) {
        this.playtimeResetDay = playtimeResetDay;
    }

    /**
     * Der Spieler ist im Kampf ausgeloggt und muss beim naechsten Join noch respawnt werden.
     *
     * <p>Persistent, weil zwischen Ausloggen und Wiederkommen ein Serverneustart liegen kann.
     */
    public boolean isPendingRespawn() {
        return pendingRespawn;
    }

    public void setPendingRespawn(boolean pendingRespawn) {
        this.pendingRespawn = pendingRespawn;
    }

    /**
     * Der Spieler war beim Bounty-Roll offline und bekommt ihn beim naechsten Join
     * nachgespielt.
     *
     * <p>Persistent, weil zwischen Roll und Wiederkommen ein Serverneustart liegen kann.
     * Ginge die Vormerkung dabei verloren, fiele er ohne Ankuendigung aus der Auslosung —
     * und damit dauerhaft aus dem Rennen um das vierte Herz.
     */
    public boolean isPendingBountyRoll() {
        return pendingBountyRoll;
    }

    public void setPendingBountyRoll(boolean pendingBountyRoll) {
        this.pendingBountyRoll = pendingBountyRoll;
    }

    /**
     * Die Welthaelfte dieses Spielers, oder {@code null}, solange er noch nie da war.
     *
     * <p>Entscheidet, wohin er respawnt und welche Haelfte er ueberhaupt betreten darf.
     * Vergeben wird sie einmalig beim ersten Join, danach nie wieder — sonst stuende jemand
     * nach einem Serverneustart auf der anderen Seite seiner eigenen Basis.
     */
    @Nullable
    public Side getSide() {
        return side;
    }

    public void setSide(@Nullable Side side) {
        this.side = side;
    }

    /**
     * Wo dieser Spieler ins Projekt gestartet ist.
     *
     * <p>Zugleich sein Rueckfall-Respawn. Der Weltspawn liegt auf 0,0 und damit mitten in
     * der Trennwand — wer ohne Bett stirbt, muesste sonst dort landen.
     */
    @Nullable
    public BlockPos getStartSpawn() {
        return startSpawn;
    }

    public void setStartSpawn(@Nullable BlockPos startSpawn) {
        this.startSpawn = startSpawn;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_UUID, uuid);
        tag.putString(KEY_NAME, name);
        tag.putInt(KEY_HEARTS, hearts);
        if (bountyTarget != null) {
            tag.putUUID(KEY_BOUNTY_TARGET, bountyTarget);
        }
        tag.putBoolean(KEY_BOUNTY_RESOLVED, bountyResolved);
        tag.putInt(KEY_PLAYTIME_USED, playtimeUsedSeconds);
        tag.putLong(KEY_PLAYTIME_DAY, playtimeResetDay);
        tag.putBoolean(KEY_ELIMINATED, eliminated);
        tag.putBoolean(KEY_PENDING_RESPAWN, pendingRespawn);
        tag.putBoolean(KEY_PENDING_BOUNTY_ROLL, pendingBountyRoll);
        if (side != null) {
            tag.putString(KEY_SIDE, side.getId());
        }
        if (startSpawn != null) {
            tag.putLong(KEY_START_SPAWN, startSpawn.asLong());
        }
        return tag;
    }

    public static PlayerState load(CompoundTag tag) {
        PlayerState state = new PlayerState(tag.getUUID(KEY_UUID));
        state.name = tag.getString(KEY_NAME);
        state.hearts = tag.getInt(KEY_HEARTS);
        state.bountyTarget = tag.hasUUID(KEY_BOUNTY_TARGET) ? tag.getUUID(KEY_BOUNTY_TARGET) : null;
        state.bountyResolved = tag.getBoolean(KEY_BOUNTY_RESOLVED);
        state.playtimeUsedSeconds = tag.getInt(KEY_PLAYTIME_USED);
        state.playtimeResetDay = tag.getLong(KEY_PLAYTIME_DAY);
        state.eliminated = tag.getBoolean(KEY_ELIMINATED);
        state.pendingRespawn = tag.getBoolean(KEY_PENDING_RESPAWN);
        state.pendingBountyRoll = tag.getBoolean(KEY_PENDING_BOUNTY_ROLL);
        state.side = Side.byId(tag.getString(KEY_SIDE));
        state.startSpawn = tag.contains(KEY_START_SPAWN)
                ? BlockPos.of(tag.getLong(KEY_START_SPAWN))
                : null;
        return state;
    }
}
