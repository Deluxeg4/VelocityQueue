package me.queue.workers;

import com.velocitypowered.api.proxy.Player;
import me.queue.Main;
import me.queue.PlayerQueue;
import me.queue.Reloadable;
import me.queue.util.Utils;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.queue.util.MessageUtil.sendMessage;
import static me.queue.util.MessageUtil.translateChars;

public class QueueWorker implements Runnable, Reloadable {
    private final Main plugin;
    private final PlayerQueue normalQueue;
    private final PlayerQueue prioQueue;
    private String queueEndMessage;
    private List<String> tabHeader;
    private Component prioFooter;
    private Component normalFooter;
    private final Set<UUID> connectingPlayers;
    private final Map<UUID, Long> retryAfter;

    public QueueWorker(Main plugin) {
        this.plugin = plugin;
        prioQueue = plugin.getPrioQueue();
        normalQueue = plugin.getNormalQueue();
        connectingPlayers = ConcurrentHashMap.newKeySet();
        retryAfter = new ConcurrentHashMap<>();
        reloadConfig();
    }

    @Override
    public void run() {
        boolean serverHasSlot = plugin.doesServerHaveSlot();
        for (Player player : prioQueue.getPlayersInQueue()) {
            int queuePos = prioQueue.getQueuePosition(player);
            player.sendPlayerListHeaderAndFooter(parseHeader(queuePos, serverHasSlot), prioFooter);
            if (serverHasSlot && queuePos == 1) {
                connectQueuedPlayer(player, prioQueue);
                serverHasSlot = plugin.doesServerHaveSlot();
                break;
            }
        }
        for (Player player : normalQueue.getPlayersInQueue()) {
            int queuePos = normalQueue.getQueuePosition(player);
            player.sendPlayerListHeaderAndFooter(parseHeader(queuePos, serverHasSlot), normalFooter);
            if (serverHasSlot && queuePos == 1) {
                connectQueuedPlayer(player, normalQueue);
                break;
            }
        }
    }

    @Override
    public void reloadConfig() {
        try {
            queueEndMessage = plugin.getConfig().node("messages", "queue-end").getString("");
            List<String> headerList = plugin.getConfig().node("tablist", "header").getList(String.class);
            tabHeader = (headerList != null) ? headerList : new ArrayList<>();
            
            List<String> prioFooterList = plugin.getConfig().node("tablist", "priority-queue-footer").getList(String.class);
            prioFooter = parseFooter(prioFooterList != null ? prioFooterList : new ArrayList<>());
            
            List<String> normalFooterList = plugin.getConfig().node("tablist", "normal-queue-footer").getList(String.class);
            normalFooter = parseFooter(normalFooterList != null ? normalFooterList : new ArrayList<>());
        } catch (Throwable t) {
            plugin.getLogger().atError().setCause(t).log("Failed to load config. Please check stacktrace for more info");
        }
    }

    private void connectQueuedPlayer(Player player, PlayerQueue queue) {
        if (plugin.getMainServer() == null) return;
        if (isConnectedToMain(player)) {
            queue.removeFromQueue(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (retryAfter.getOrDefault(player.getUniqueId(), 0L) > now) return;
        if (!connectingPlayers.add(player.getUniqueId())) return;
        sendMessage(player, queueEndMessage);
        player.createConnectionRequest(plugin.getMainServer()).connect().whenComplete((result, throwable) -> {
            connectingPlayers.remove(player.getUniqueId());
            if (throwable != null) {
                retryAfter.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
                plugin.getLogger().atWarn().setCause(throwable).log("Failed to connect {} from queue", player.getUsername());
                return;
            }
            if (!result.isSuccessful()) {
                if (isConnectedToMain(player)) {
                    queue.removeFromQueue(player);
                    retryAfter.remove(player.getUniqueId());
                    return;
                }
                switch (result.getStatus()) {
                    case CONNECTION_CANCELLED, CONNECTION_IN_PROGRESS ->
                            retryAfter.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                    case SERVER_DISCONNECTED -> {
                        retryAfter.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
                        String reason = result.getReasonComponent().map(Component::toString).orElse("no reason component");
                        plugin.getLogger().warn("Failed to connect {} from queue: status={}, reason={}", player.getUsername(), result.getStatus(), reason);
                    }
                    case ALREADY_CONNECTED -> {
                        queue.removeFromQueue(player);
                        retryAfter.remove(player.getUniqueId());
                    }
                    default ->
                            retryAfter.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
                }
                return;
            }
            queue.removeFromQueue(player);
            retryAfter.remove(player.getUniqueId());
        });
    }

    private boolean isConnectedToMain(Player player) {
        return plugin.getMainServer() != null && player.getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName().equals(plugin.getMainServer().getServerInfo().getName()))
                .orElse(false);
    }

    private Component parseHeader(int posInQueue, boolean serverHasSlot) {
        List<String> processed = new ArrayList<>();
        String status = serverHasSlot
                ? plugin.getConfig().node("messages", "server-not-full").getString("")
                : plugin.getConfig().node("messages", "server-full").getString("");
        for (String line : tabHeader) {
            String raw = line.replace("%position%", String.valueOf(posInQueue));
            raw = raw.replace("%wait%", Utils.getFormattedInterval(posInQueue * 60000L));
            raw = raw.replace("%online%", String.valueOf(plugin.getServer().getPlayerCount()));
            raw = raw.replace("%status%", status);
            processed.add(raw);
        }
        return translateChars(String.join("\n", processed));
    }

    private Component parseFooter(List<String> input) {
        String raw = String.join("\n", input);
        return translateChars(raw);
    }
}
