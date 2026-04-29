package me.queue.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import me.queue.Main;
import me.queue.PlayerQueue;

public class DisconnectListener {
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;

    public DisconnectListener(Main plugin) {
        normalQueue = plugin.getNormalQueue();
        prioQueue = plugin.getPrioQueue();
    }


    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        onDisconnect(event.getPlayer());
    }

    @Subscribe
    public void onKick(KickedFromServerEvent event) {
        onDisconnect(event.getPlayer());
    }
    private void onDisconnect(Player player) {
        if (prioQueue.isInQueue(player)) prioQueue.removeFromQueue(player);
        if (normalQueue.isInQueue(player)) normalQueue.removeFromQueue(player);
    }
}
