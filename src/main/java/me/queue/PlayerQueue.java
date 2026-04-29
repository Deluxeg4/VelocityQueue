package me.queue;

import com.velocitypowered.api.proxy.Player;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PlayerQueue {
    private final ConcurrentLinkedDeque<Player> players;

    public PlayerQueue() {
        this.players = new ConcurrentLinkedDeque<>();
    }

    public void addToQueue(Player player) {
        if (!players.contains(player)) {
            players.offer(player);
        }
    }

    public int getQueuePosition(Player player) {
        int position = 1;
        for (Player queuedPlayer : players) {
            if (queuedPlayer.equals(player)) {
                return position;
            }
            position++;
        }
        return -1;
    }

    public void removeFromQueue(Player player) {
        players.remove(player);
    }

    public boolean isInQueue(Player player) {
        return players.contains(player);
    }

    public Collection<Player> getPlayersInQueue() {
        return List.copyOf(players);
    }

    public int queueLength() {
        return players.size();
    }
}
