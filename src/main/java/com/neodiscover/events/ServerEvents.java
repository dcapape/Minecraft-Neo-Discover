package com.neodiscover.events;

import com.neodiscover.NeoDiscover;
import com.neodiscover.config.ConfigManager;
import com.neodiscover.server.FileManager;
import com.neodiscover.server.ServerInfoCollector;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public class ServerEvents {
    private static ServerInfoCollector infoCollector;
    private static ConfigManager configManager;
    private static FileManager fileManager;

    public static void setInfoCollector(ServerInfoCollector collector) {
        infoCollector = collector;
    }

    public static void setConfigManager(ConfigManager manager) {
        configManager = manager;
    }

    public static void setFileManager(FileManager manager) {
        fileManager = manager;
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        if (infoCollector != null) {
            infoCollector.setServer(server);
            NeoDiscover.LOGGER.info("Servidor detectado, información actualizada");
        }
        if (configManager != null) {
            configManager.setServer(server);
        }
        if (fileManager != null) {
            fileManager.setServer(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (infoCollector != null) {
            infoCollector.setServer(null);
            NeoDiscover.LOGGER.info("Servidor detenido");
        }
        if (configManager != null) {
            configManager.setServer(null);
        }
    }
}

