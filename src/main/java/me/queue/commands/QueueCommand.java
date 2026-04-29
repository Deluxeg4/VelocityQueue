package me.queue.commands;


import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.RequiredArgsConstructor;
import me.queue.Main;
import me.queue.PlayerQueue;
import me.queue.Reloadable;

import java.util.List;
import java.util.Locale;

import static me.queue.util.MessageUtil.sendMessage;

@RequiredArgsConstructor
public class QueueCommand implements SimpleCommand {
    private final Main plugin;

    @Override
    public void execute(Invocation invocation) {
        String alias = invocation.alias().toLowerCase(Locale.ROOT);
        if (alias.equals("join")) {
            joinQueue(invocation.source());
            return;
        }
        if (alias.equals("leave")) {
            joinQueue(invocation.source());
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            joinQueue(invocation.source());
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!hasAdminPermission(invocation.source())) {
                    sendMessage(invocation.source(), "&cNo Permission");
                    return;
                }
                try {
                    plugin.loadConfig();
                    plugin.getReloadables().forEach(Reloadable::reloadConfig);
                    sendMessage(invocation.source(), "&b[&r&aQUEUE&b]&r&a Reloaded successfully!");
                } catch (Throwable t) {
                    sendMessage(invocation.source(), "&b[&r&aQUEUE&b]&r&c Failed to reload config, Please check the console for more details");
                    plugin.getLogger().atError().setCause(t).log("Failed to load config");
                }
            }
            case "version" -> {
                if (!hasAdminPermission(invocation.source())) {
                    sendMessage(invocation.source(), "&cNo Permission");
                    return;
                }
                sendMessage(invocation.source(), versionString());
            }
            default -> sendMessage(invocation.source(), "&b[&r&aQUEUE&b]&r&6 Usage: /queue, /join, /leave");
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.alias().equalsIgnoreCase("queue")) return List.of();
        if (hasAdminPermission(invocation.source())) return List.of("version", "reload");
        return List.of();
    }

    private void joinQueue(CommandSource source) {
        if (!(source instanceof Player player)) {
            sendMessage(source, "&cOnly players can join the queue.");
            return;
        }

        if (isInQueue(player)) {
            sendMessage(player, configMessage("messages", "queue-already-in", "&cYou are already in the queue."));
            return;
        }

        RegisteredServer mainServer = plugin.getMainServer();
        if (mainServer != null && player.getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName().equals(mainServer.getServerInfo().getName()))
                .orElse(false)) {
            sendMessage(player, configMessage("messages", "server-not-full", "&aYou are already on the main server."));
            return;
        }

        RegisteredServer queueServer = plugin.getQueueServer();
        if (queueServer == null) {
            sendMessage(player, "&cQueue server is not configured.");
            return;
        }

        PlayerQueue queue = player.hasPermission("queue.priority") ? plugin.getPrioQueue() : plugin.getNormalQueue();
        queue.addToQueue(player);
        sendMessage(player, configMessage("messages", "queue-joined", "&aYou joined the queue. Position: &l%d"), queue.getQueuePosition(player));

            if (isConnectedTo(player, queueServer)) {
            return;
        }

        player.createConnectionRequest(queueServer).connect().whenComplete((result, throwable) -> {
            if (throwable != null) {
                queue.removeFromQueue(player);
                plugin.getLogger().atWarn().setCause(throwable).log("Failed to connect {} to queue server", player.getUsername());
                sendMessage(player, "&cFailed to connect to the queue server.");
                return;
            }
            if (!result.isSuccessful()) {
                queue.removeFromQueue(player);
                sendMessage(player, "&cFailed to connect to the queue server.");
            }
        });
    }

    private void leaveQueue(CommandSource source) {
        if (!(source instanceof Player player)) {
            sendMessage(source, "&cOnly players can leave the queue.");
            return;
        }

        if (!removeFromQueue(player)) {
            sendMessage(player, configMessage("messages", "queue-not-in", "&cYou are not in the queue."));
            return;
        }

        sendMessage(player, configMessage("messages", "queue-left", "&aYou left the queue."));
    }

    private void sendQueueStatus(CommandSource source) {
        if (!(source instanceof Player player)) {
            sendMessage(source, versionString());
            return;
        }

        int prioPosition = plugin.getPrioQueue().getQueuePosition(player);
        if (prioPosition != -1) {
            sendMessage(player, configMessage("messages", "queue-status", "&6Position in queue: &l%d"), prioPosition);
            return;
        }

        int normalPosition = plugin.getNormalQueue().getQueuePosition(player);
        if (normalPosition != -1) {
            sendMessage(player, configMessage("messages", "queue-status", "&6Position in queue: &l%d"), normalPosition);
            return;
        }

        sendMessage(player, configMessage("messages", "queue-not-in", "&cYou are not in the queue."));
    }

    private boolean isInQueue(Player player) {
        return plugin.getPrioQueue().isInQueue(player) || plugin.getNormalQueue().isInQueue(player);
    }

    private boolean removeFromQueue(Player player) {
        boolean removed = false;
        if (plugin.getPrioQueue().isInQueue(player)) {
            plugin.getPrioQueue().removeFromQueue(player);
            removed = true;
        }
        if (plugin.getNormalQueue().isInQueue(player)) {
            plugin.getNormalQueue().removeFromQueue(player);
            removed = true;
        }
        return removed;
    }

    private boolean isConnectedTo(Player player, RegisteredServer server) {
        return player.getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName().equals(server.getServerInfo().getName()))
                .orElse(false);
    }

    private boolean hasAdminPermission(CommandSource source) {
        return source.hasPermission("queue.admin");
    }

    private String configMessage(Object... pathAndDefault) {
        Object[] path = new Object[pathAndDefault.length - 1];
        System.arraycopy(pathAndDefault, 0, path, 0, path.length);
        String fallback = pathAndDefault[pathAndDefault.length - 1].toString();
        return plugin.getConfig().node(path).getString(fallback);
    }

    private String versionString() {
        return String.format("&b[&r&aQUEUE&b]&r&6 Version&r&a %s&r&6 by&r&a zeb.co", plugin.getClass().getAnnotation(Plugin.class).version());
    }
}
