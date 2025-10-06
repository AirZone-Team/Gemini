package geminiclient.gemini.utils;

public class TimerUtils {
    public long time = System.currentTimeMillis();

    private long lastTime;

    public TimerUtils() {
        reset();
    }

    public long getTimeElapsed() {
        return System.currentTimeMillis() - lastTime;
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.currentTimeMillis() - this.time > time) {
            if (reset) reset();
            return true;
        }

        return false;
    }

    public void setTimeElapsed(long time) {
        this.lastTime = System.currentTimeMillis() - time;
    }

    public void reset() {
        lastTime = System.currentTimeMillis();
    }

    public boolean reached(long currentTime) {
        return Math.max(0L, System.currentTimeMillis() - this.time) >= currentTime;
    }
}