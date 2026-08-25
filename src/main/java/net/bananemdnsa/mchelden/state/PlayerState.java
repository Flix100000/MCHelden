package net.bananemdnsa.mchelden.state;

import java.util.UUID;

import javax.annotation.Nullable;

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

    private final UUID uuid;
    private String name = "";
    private int hearts = DEFAULT_HEARTS;
    @Nullable
    private UUID bountyTarget;
    private boolean bountyResolved;
    private int playtimeUsedSeconds;
    private long playtimeResetDay = -1L;
    private boolean eliminated;

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
        return state;
    }
}
