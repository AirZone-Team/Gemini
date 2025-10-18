package geminiclient.gemini.utils;

public class TimerUtils {
    private long startTime;
    private long lastTime;

    public TimerUtils() {
        reset();
    }

    public long getTimeElapsed() {
        return System.currentTimeMillis() - lastTime;
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= time) {
            if (reset) {
                this.startTime = System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }

    public void setTimeElapsed(long time) {
        this.lastTime = System.currentTimeMillis() - time;
        this.startTime = System.currentTimeMillis() - time;
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.lastTime = System.currentTimeMillis();
    }

    public boolean reached(long targetTime) {
        return System.currentTimeMillis() - startTime >= targetTime;
    }

    public long getTotalTimeElapsed() {
        return System.currentTimeMillis() - startTime;
    }

    public boolean hasReached(long time) {
        return reached(time);
    }
}