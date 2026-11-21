package kz.onay.nearby.logic;

/** Short Russian text for how long ago something happened. */
public final class AgeText {
    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;

    private AgeText() {
    }

    public static String ago(long ageMs) {
        if (ageMs < MINUTE_MS) {
            return "меньше минуты назад";
        }
        if (ageMs < HOUR_MS) {
            return (ageMs / MINUTE_MS) + " мин назад";
        }
        if (ageMs < DAY_MS) {
            return (ageMs / HOUR_MS) + " ч назад";
        }
        return "более суток назад";
    }
}
