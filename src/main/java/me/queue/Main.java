package me.queue;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.Getter;
import me.queue.commands.QueueCommand;
import me.queue.listeners.DisconnectListener;
import me.queue.listeners.PreConnectListener;
import me.queue.listeners.ProxyPingListener;
import me.queue.workers.MessageWorker;
import me.queue.workers.QueueWorker;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Plugin(id = "queue",
        name = "LeeesVelocityQueue",
        version = "1.0.1-RELEASE", description = "A 2b2t like queue plugin for Velocity",
        authors = {"zeb.co"})
public class Main implements Reloadable {
    @Getter private final ProxyServer server;
    @Getter private final Logger logger;
    @Getter private PlayerQueue normalQueue;
    @Getter private PlayerQueue prioQueue;
    @Getter private CommentedConfigurationNode config;
    @Getter private RegisteredServer mainServer;
    @Getter private RegisteredServer queueServer;
    @Getter private int maxSlots;
    @Getter private final List<Reloadable> reloadables;

    private ScheduledTask queueNotifyTask;
    private ScheduledTask messageTask;
    private File configFile;
    private int messageInterval;

    @Inject
    public Main(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        reloadables = new ArrayList<>();
        registerReloadable(this);
        try {
            loadConfig();
        } catch (Throwable t) {
            logger.atError().setCause(t).log("Failed to load config");
        }
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        reloadConfig();
        normalQueue = new PlayerQueue();
        prioQueue = new PlayerQueue();
        server.getEventManager().register(this, registerReloadable(new PreConnectListener(this)));
        server.getEventManager().register(this, new DisconnectListener(this));
        server.getEventManager().register(this, registerReloadable(new ProxyPingListener(this)));
        server.getCommandManager().register(server.getCommandManager().metaBuilder("queue").aliases("join", "leave").plugin(this).build(), new QueueCommand(this));
        QueueWorker queueWorker = registerReloadable(new QueueWorker(this));
        MessageWorker messageWorker = registerReloadable(new MessageWorker(this));
        queueNotifyTask = server.getScheduler().buildTask(this, queueWorker).repeat(Duration.ofSeconds(1)).schedule();
        messageTask = server.getScheduler().buildTask(this, messageWorker).repeat(Duration.ofSeconds(messageInterval)).schedule();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (queueNotifyTask != null) queueNotifyTask.cancel();
        if (messageTask != null) messageTask.cancel();
        reloadables.clear();
    }

    private <T extends Reloadable> T registerReloadable(T reloadable) {
        reloadables.add(reloadable);
        return reloadable;
    }

    public void loadConfig() throws Throwable {
        configFile = new File(getPluginDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (is == null) throw new NullPointerException("Missing resource config.yml");
                Files.copy(is, configFile.toPath());
            }
        }
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().file(configFile).build();
        config = loader.load();
    }

    private File getPluginDataFolder() {
        File dataFolder = new File("plugins", getClass().getAnnotation(Plugin.class).id());
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warn("Failed to create plugin data folder: {}", dataFolder.getAbsolutePath());
        }
        return dataFolder;
    }

    @Override
    public void reloadConfig() {
        String mainServerName = getConfig().node("main-server").getString("");
        String queueServerName = getConfig().node("queue-server").getString("");

        mainServer = getServer().getServer(mainServerName).orElse(null);
        queueServer = getServer().getServer(queueServerName).orElse(null);
        if (mainServer == null) getLogger().atError().log("{} is not a valid server, please ensure that the server name in the queue configuration file matches the one in velocity.toml", mainServerName);
        if (queueServer == null) getLogger().atError().log("{} is not a valid server, please ensure that the server name in the queue configuration file matches the one in velocity.toml", queueServerName);
        maxSlots = Math.max(0, getConfig().node("main-server-slots").getInt(0));
        messageInterval = Math.max(1, getConfig().node("messages", "interval").getInt(10));
    }

    public boolean doesServerHaveSlot() {
        if (mainServer == null) return false;
        try {
            ServerPing ping = getMainServer().ping().join();
            ServerPing.Players players = ping.getPlayers().orElse(null);
            if (players == null) return false;
            return players.getOnline() < maxSlots;
        } catch (Exception e) {
            return false;
        }
    }
}
