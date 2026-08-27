package net.bananemdnsa.mchelden.text;

/**
 * Dauerangaben in beide Richtungen: {@code 2h30m} hinein, {@code 2:30:00} heraus.
 *
 * <p>Reine Rechnung ohne Spielbezug, damit sie ohne Spielstart pruefbar ist. Sie steht
 * zwischen einem Command und einem Abend, der zwei Stunden zu kurz oder zu lang wird.
 */
public final class DurationText {
    /** Zurueckgegeben, wenn die Angabe nicht lesbar ist. */
    public static final long INVALID = -1L;

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;

    /** Obergrenze: zwoelf Stunden. Faengt Vertipper ab, ohne im Weg zu stehen. */
    public static final long MAX_MILLIS = 12L * HOUR;

    private DurationText() {
    }

    /**
     * Liest {@code 2h30m}, {@code 150m}, {@code 2h} oder {@code 90s}.
     *
     * <p>Jeder Teil ist weglassbar, aber mindestens einer muss dastehen. Eine nackte Zahl
     * wird <em>abgelehnt statt geraten</em>: {@code 3} koennte drei Stunden oder drei
     * Minuten heissen, und der Unterschied ist ein ganzer Abend.
     *
     * @return die Dauer in Millisekunden, oder {@link #INVALID}
     */
    public static long parseMillis(String input) {
        if (input == null || input.isEmpty()) {
            return INVALID;
        }

        long total = 0L;
        long digits = -1L;
        boolean sawUnit = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c >= '0' && c <= '9') {
                digits = (digits < 0L ? 0L : digits) * 10L + (c - '0');
                // Faengt absurd lange Ziffernfolgen ab, bevor sie ueberlaufen.
                if (digits > MAX_MILLIS) {
                    return INVALID;
                }
                continue;
            }

            long unit = switch (Character.toLowerCase(c)) {
                case 'h' -> HOUR;
                case 'm' -> MINUTE;
                case 's' -> SECOND;
                default -> 0L;
            };

            if (unit == 0L || digits < 0L) {
                return INVALID;
            }

            total += digits * unit;
            digits = -1L;
            sawUnit = true;
        }

        // Eine Ziffer ohne Einheit am Ende ist genau der Fall, den wir nicht raten wollen.
        if (digits >= 0L || !sawUnit || total <= 0L || total > MAX_MILLIS) {
            return INVALID;
        }

        return total;
    }

    /**
     * Schreibt eine Dauer als Uhr: {@code 2:30:00}, unter einer Stunde {@code 12:05}.
     *
     * <p>Dieselbe Schreibweise wie in einem Videoplayer — die muss niemand nachschlagen.
     */
    public static String clock(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long rest = seconds % 60L;

        return hours > 0L
                ? String.format("%d:%02d:%02d", hours, minutes, rest)
                : String.format("%d:%02d", minutes, rest);
    }
}
