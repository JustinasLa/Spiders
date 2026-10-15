package net.tfminecraft.spiders.utilities.events;


public class SeriesScheduler {
    private long time = 0L;

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void sleep(long time) {
        this.time += time;
    }

    public void run(Runnable task) {
        Scheduler.runLater(time, task);
    }
}
