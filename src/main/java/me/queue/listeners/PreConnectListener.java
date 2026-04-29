package me.queue.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.queue.Main;
import me.queue.PlayerQueue;
import me.queue.Reloadable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.queue.util.MessageUtil.sendMessage;

public class PreConnectListener implements Reloadable {
    private final Main plugin;
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;
    private final Set<UUID> serverNotFullNotified;
    private String serverFullMessage;
    private String serverNotFullMessage;

    public PreConnectListener(Main plugin) {
        this.plugin = plugin;
        normalQueue = plugin.getNormalQueue();
        prioQueue = plugin.getPrioQueue();
        serverNotFullNotified = ConcurrentHashMap.newKeySet();
        reloadConfig();
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer server = event.getOriginalServer();
        RegisteredServer mainServer = plugin.getMainServer();
        RegisteredServer queueServer = plugin.getQueueServer();
        if (mainServer == null || queueServer == null) return;
        if (player.hasPermission("queue.bypass")) return;
        
        if (server.getServerInfo().getName().equals(mainServer.getServerInfo().getName())) {
            if (!plugin.doesServerHaveSlot()) {
                sendMessage(player, serverFullMessage);
                addToQueue(player);
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(queueServer));
            } else {
                sendServerNotFullMessage(player);
            }
        }
    }

    @Subscribe
    public void onServerConnect(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer server = event.getServer();
        RegisteredServer queueServer = plugin.getQueueServer();
        RegisteredServer mainServer = plugin.getMainServer();
        if (mainServer != null && server.getServerInfo().getName().equals(mainServer.getServerInfo().getName())) {
            removeFromQueue(player);
            return;
        }
        if (queueServer != null && server.getServerInfo().getName().equals(queueServer.getServerInfo().getName())) {
            addToQueue(player);
        }
    }

    private void addToQueue(Player player) {
        if (player.hasPermission("queue.priority")) {
            if (!prioQueue.isInQueue(player)) prioQueue.addToQueue(player);
        } else {
            if (!normalQueue.isInQueue(player)) normalQueue.addToQueue(player);
        }
    }

    private void removeFromQueue(Player player) {
        if (prioQueue.isInQueue(player)) prioQueue.removeFromQueue(player);
        if (normalQueue.isInQueue(player)) normalQueue.removeFromQueue(player);
    }

    private void sendServerNotFullMessage(Player player) {
        if (serverNotFullNotified.add(player.getUniqueId())) {
            sendMessage(player, serverNotFullMessage);
        }
    }

    @Override
    public void reloadConfig() {
        try {
            serverFullMessage = plugin.getConfig().node("messages", "server-full").getString("");
            serverNotFullMessage = plugin.getConfig().node("messages", "server-not-full").getString("");
        } catch (Throwable t) {
            plugin.getLogger().atError().setCause(t).log("Failed to load config. Please check stacktrace for more info");
        }
    }
}
