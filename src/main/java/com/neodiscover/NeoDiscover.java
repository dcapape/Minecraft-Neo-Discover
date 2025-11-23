package com.neodiscover;

import com.neodiscover.config.ConfigManager;
import com.neodiscover.events.ServerEvents;
import com.neodiscover.server.FileManager;
import com.neodiscover.server.ProfilesHttpServer;
import com.neodiscover.server.ServerInfoCollector;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(NeoDiscover.MODID)
public class NeoDiscover {
    public static final String MODID = "neodiscover";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    
    private ProfilesHttpServer httpServer;
    private ServerInfoCollector serverInfoCollector;
    private ConfigManager configManager;
    private FileManager fileManager;

    public NeoDiscover(IEventBus modEventBus) {
        LOGGER.info("Inicializando NeoDiscover...");
        
        try {
            // Inicializar gestor de configuración
            configManager = new ConfigManager();
            configManager.loadConfig();
            
            // Inicializar gestor de archivos
            fileManager = new FileManager(25080);
            
            // Inicializar recolector de información del servidor
            serverInfoCollector = new ServerInfoCollector(configManager);
            serverInfoCollector.setFileManager(fileManager);
            
            // Registrar el recolector, config manager y file manager en los eventos del servidor
            ServerEvents.setInfoCollector(serverInfoCollector);
            ServerEvents.setConfigManager(configManager);
            ServerEvents.setFileManager(fileManager);
            ServerEvents.register();
            
            // Inicializar servidor HTTP
            httpServer = new ProfilesHttpServer(25080, serverInfoCollector, configManager, fileManager);
            httpServer.start();
            
            LOGGER.info("NeoDiscover iniciado correctamente. Servidor HTTP en puerto 25080");
            LOGGER.info("API Key: {}", configManager.getApiKey());
        } catch (Exception e) {
            LOGGER.error("Error al inicializar NeoDiscover", e);
        }
    }
}

