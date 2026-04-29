package me.queue.workers;

import me.queue.Main;
import me.queue.PlayerQueue;
import me.queue.Reloadable;

import static me.queue.util.MessageUtil.*;

public class MessageWorker implements Runnable, Reloadable {
    private final Main plugin;
    private final PlayerQueue prioQueue;
    private final PlayerQueue normalQueue;
    private String queuePositionMessage;

    public MessageWorker(Main plugin) {
        this.plugin = plugin;
        prioQueue = plugin.getPrioQueue();
        normalQueue = plugin.getNormalQueue();
        reloadConfig();
    }

    @Override
    public void run() {
        prioQueue.getPlayersInQueue().forEach(p -> {
            int pos = prioQueue.getQueuePosition(p);
            sendMessage(p, queuePositionMessage, pos);
            sendActionBar(p, queuePositionMessage, pos);
            sendTitle(p, "", queuePositionMessage, pos);
        });
        normalQueue.getPlayersInQueue().forEach(p -> {
            int pos = normalQueue.getQueuePosition(p);
            sendMessage(p, queuePositionMessage, pos);
            sendActionBar(p, queuePositionMessage, pos);
            sendTitle(p, "", queuePositionMessage, pos);
        });
    }

    @Override
    public void reloadConfig() {
        queuePositionMessage = plugin.getConfig().node("messages", "queue-position").getString("");
    }
}
