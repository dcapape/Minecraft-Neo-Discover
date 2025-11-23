package com.neodiscover.server;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Scanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neodiscover.NeoDiscover;
import com.neodiscover.config.ConfigManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

public class ServerInfoCollector {
    private final ConfigManager configManager;
    private FileManager fileManager;
    private MinecraftServer server;

    public ServerInfoCollector(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public void setFileManager(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public JsonObject collectServerInfo() {
        // Recargar configuración para obtener los valores más recientes
        configManager.loadConfig();
        
        // Obtener y establecer la IP/hostname del servidor para FileManager
        String serverHost = getServerHost();
        if (fileManager != null) {
            fileManager.setServerHost(serverHost);
        }
        
        // Debug: verificar qué valores tiene la configuración
        com.google.gson.JsonObject configData = configManager.getConfigData();
        NeoDiscover.LOGGER.info("Config data keys: {}", configData.keySet());
        if (configData.has("name")) {
            NeoDiscover.LOGGER.info("Config 'name' value: {}", configData.get("name"));
        }
        if (configData.has("description")) {
            NeoDiscover.LOGGER.info("Config 'description' value: {}", configData.get("description"));
        }
        if (configData.has("id")) {
            NeoDiscover.LOGGER.info("Config 'id' value: {}", configData.get("id"));
        }
        
        JsonObject root = new JsonObject();
        
        // Información del servidor
        String serverName = getServerName();
        root.addProperty("server_name", serverName);
        root.addProperty("server_url", configManager.getConfigValue("server_url", ""));
        
        // Perfiles
        JsonArray profiles = new JsonArray();
        JsonObject profile = createProfile(serverName);
        profiles.add(profile);
        root.add("profiles", profiles);
        
        return root;
    }

    private String getServerHost() {
        // 1. Intentar obtener de la configuración (config.server_ip) - PRIORIDAD MÁXIMA
        com.google.gson.JsonElement configElement = configManager.getConfigElement("config");
        if (configElement != null && configElement.isJsonObject()) {
            com.google.gson.JsonObject configObj = configElement.getAsJsonObject();
            if (configObj.has("server_ip")) {
                String configIp = configObj.get("server_ip").getAsString();
                if (configIp != null && !configIp.isEmpty()) {
                    // Si es "localhost", intentar obtener IP pública automáticamente
                    if (configIp.equals("localhost")) {
                        String publicIp = getPublicIp();
                        if (publicIp != null && !publicIp.isEmpty()) {
                            NeoDiscover.LOGGER.info("Server IP configurado como 'localhost', usando IP pública detectada: {}", publicIp);
                            return publicIp;
                        }
                        NeoDiscover.LOGGER.info("Server IP desde configuración (config.server_ip): localhost");
                        return "localhost";
                    }
                    NeoDiscover.LOGGER.info("Server IP desde configuración (config.server_ip): {}", configIp);
                    return configIp;
                }
            }
        }
        
        // 2. Intentar obtener directamente de la configuración (server_ip en raíz)
        String configIp = configManager.getConfigValue("server_ip");
        if (configIp != null && !configIp.isEmpty()) {
            // Si es "localhost", intentar obtener IP pública automáticamente
            if (configIp.equals("localhost")) {
                String publicIp = getPublicIp();
                if (publicIp != null && !publicIp.isEmpty()) {
                    NeoDiscover.LOGGER.info("Server IP configurado como 'localhost', usando IP pública detectada: {}", publicIp);
                    return publicIp;
                }
            }
            NeoDiscover.LOGGER.info("Server IP desde configuración (raíz): {}", configIp);
            return configIp;
        }
        
        // 3. Intentar obtener IP pública automáticamente (solo si no hay configuración)
        String publicIp = getPublicIp();
        if (publicIp != null && !publicIp.isEmpty()) {
            NeoDiscover.LOGGER.info("IP pública detectada automáticamente: {}", publicIp);
            return publicIp;
        }
        
        // 4. Fallback a localhost
        NeoDiscover.LOGGER.info("Usando localhost como fallback");
        return "localhost";
    }

    private String getPublicIp() {
        // Lista de servicios para obtener IP pública
        String[] services = {
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip",
            "https://checkip.amazonaws.com"
        };
        
        for (String service : services) {
            try {
                URL url = new URL(service);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("GET");
                
                try (InputStream inputStream = connection.getInputStream();
                     Scanner scanner = new Scanner(inputStream).useDelimiter("\\A")) {
                    if (scanner.hasNext()) {
                        String ip = scanner.next().trim();
                        // Validar que sea una IP válida
                        if (ip.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
                            return ip;
                        }
                    }
                }
            } catch (Exception e) {
                NeoDiscover.LOGGER.debug("No se pudo obtener IP pública desde {}: {}", service, e.getMessage());
            }
        }
        
        return null;
    }

    private String getServerName() {
        // Priorizar configuración sobre MOTD
        String configName = configManager.getConfigValue("server_name");
        if (configName != null && !configName.isEmpty()) {
            return configName;
        }
        
        // Si no hay en configuración, intentar obtener del MOTD
        if (server != null && server instanceof DedicatedServer dedicatedServer) {
            String motd = dedicatedServer.getProperties().motd;
            if (motd != null && !motd.isEmpty()) {
                return motd;
            }
        }
        
        return "Mi Servidor";
    }

    private JsonObject createProfile(String serverName) {
        JsonObject profile = new JsonObject();
        
        // ID del perfil (de configuración, con fallback)
        String profileId = configManager.getConfigValue("id");
        NeoDiscover.LOGGER.info("Profile ID desde config 'id': {}", profileId);
        if (profileId == null || profileId.isEmpty()) {
            profileId = configManager.getConfigValue("profile_id", "mi-servidor-neoforge");
            NeoDiscover.LOGGER.info("Profile ID desde config 'profile_id': {}", profileId);
        }
        profile.addProperty("id", profileId);
        
        // Nombre del perfil (PRIORIDAD: configuración "name" > server_name > MOTD)
        String profileName = configManager.getConfigValue("name");
        NeoDiscover.LOGGER.info("Profile name desde config 'name': {}", profileName);
        if (profileName == null || profileName.isEmpty()) {
            // Si no hay "name" en configuración, usar server_name (que ya prioriza configuración)
            profileName = serverName;
            NeoDiscover.LOGGER.info("Profile name usando serverName: {}", profileName);
        }
        profile.addProperty("name", profileName);
        
        // Descripción (PRIORIDAD: configuración "description" > MOTD > valor por defecto)
        String description = configManager.getConfigValue("description");
        NeoDiscover.LOGGER.info("Profile description desde config 'description': {}", description);
        if (description == null || description.isEmpty()) {
            // Si no hay en configuración, intentar obtener del MOTD
            if (server != null && server instanceof DedicatedServer dedicatedServer) {
                String motd = dedicatedServer.getProperties().motd;
                if (motd != null && !motd.isEmpty()) {
                    description = motd;
                    NeoDiscover.LOGGER.info("Profile description usando MOTD: {}", description);
                }
            }
            // Si aún no hay, usar valor por defecto
            if (description == null || description.isEmpty()) {
                description = "Servidor NeoForge con mods personalizados";
                NeoDiscover.LOGGER.info("Profile description usando valor por defecto: {}", description);
            }
        }
        profile.addProperty("description", description);
        
        // Versión base
        JsonObject versionBase = createVersionBase();
        profile.add("version_base", versionBase);
        
        // Mods
        JsonArray mods = collectMods();
        profile.add("mods", mods);
        
        // Shaders (de configuración)
        JsonArray shaders = collectShaders();
        // Agregar URLs a los shaders si fileManager está disponible
        if (fileManager != null) {
            for (int i = 0; i < shaders.size(); i++) {
                com.google.gson.JsonElement shaderElement = shaders.get(i);
                if (shaderElement.isJsonObject()) {
                    com.google.gson.JsonObject shader = shaderElement.getAsJsonObject();
                    if (shader.has("name") && !shader.has("url")) {
                        String shaderName = shader.get("name").getAsString();
                        shader.addProperty("url", fileManager.getShaderUrl(shaderName));
                    }
                }
            }
        }
        profile.add("shaders", shaders);
        
        // Resource packs (de configuración)
        JsonArray resourcePacks = collectResourcePacks();
        // Agregar URLs a los resource packs si fileManager está disponible
        if (fileManager != null) {
            for (int i = 0; i < resourcePacks.size(); i++) {
                com.google.gson.JsonElement rpElement = resourcePacks.get(i);
                if (rpElement.isJsonObject()) {
                    com.google.gson.JsonObject rp = rpElement.getAsJsonObject();
                    if (rp.has("name") && !rp.has("url")) {
                        String rpName = rp.get("name").getAsString();
                        rp.addProperty("url", fileManager.getResourcePackUrl(rpName));
                    }
                }
            }
        }
        profile.add("resourcepacks", resourcePacks);
        
        // Opciones (de configuración)
        JsonObject options = collectOptions();
        profile.add("options", options);
        
        // Datapacks
        JsonArray datapacks = new JsonArray();
        profile.add("datapacks", datapacks);
        
        // Archivos descargados (si fileManager está disponible)
        // Excluir mods ya que están en el nodo "mods" arriba
        if (fileManager != null) {
            JsonArray downloads = new JsonArray();
            for (String fileName : fileManager.listDownloadedFiles()) {
                // Excluir mods (ya están en el nodo "mods")
                if (!fileName.startsWith("mods/")) {
                    JsonObject download = new JsonObject();
                    download.addProperty("name", fileName);
                    
                    // Generar URL según el tipo
                    if (fileName.startsWith("shaders/")) {
                        String shaderName = fileName.substring("shaders/".length());
                        download.addProperty("url", fileManager.getShaderUrl(shaderName));
                    } else if (fileName.startsWith("resourcepacks/")) {
                        String rpName = fileName.substring("resourcepacks/".length());
                        download.addProperty("url", fileManager.getResourcePackUrl(rpName));
                    } else {
                        download.addProperty("url", fileManager.getFileUrl(fileName));
                    }
                    
                    downloads.add(download);
                }
            }
            profile.add("downloads", downloads);
        }
        
        // Configuración del servidor
        JsonObject config = createServerConfig();
        profile.add("config", config);
        
        return profile;
    }


    private JsonObject createVersionBase() {
        JsonObject versionBase = new JsonObject();
        versionBase.addProperty("type", "neoforge");
        
        // Obtener versión de Minecraft
        String minecraftVersion = getMinecraftVersion();
        versionBase.addProperty("minecraft_version", minecraftVersion);
        
        // Obtener versión de NeoForge
        String neoforgeVersion = getNeoForgeVersion();
        versionBase.addProperty("neoforge_version", neoforgeVersion);
        
        // URL del instalador
        String installerUrl = String.format(
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s-installer.jar",
            neoforgeVersion, neoforgeVersion
        );
        versionBase.addProperty("installer_url", installerUrl);
        
        return versionBase;
    }

    private String getMinecraftVersion() {
        // Obtener versión de Minecraft desde el servidor
        if (server != null) {
            try {
                // Intentar obtener desde el servidor
                String version = server.getServerVersion();
                if (version != null && !version.isEmpty()) {
                    // Extraer solo el número de versión (ej: "1.21.1")
                    String[] parts = version.split(" ");
                    if (parts.length > 0) {
                        return parts[0];
                    }
                }
            } catch (Exception e) {
                NeoDiscover.LOGGER.warn("No se pudo obtener versión de Minecraft del servidor", e);
            }
        }
        
        // Fallback a configuración o valor por defecto
        return configManager.getConfigValue("minecraft_version", "1.21.1");
    }

    private String getNeoForgeVersion() {
        // Obtener versión de NeoForge
        try {
            Optional<? extends ModContainer> neoforgeMod = ModList.get().getModContainerById("neoforge");
            if (neoforgeMod.isPresent()) {
                String version = neoforgeMod.get().getModInfo().getVersion().toString();
                // Limpiar la versión si es necesario
                return version;
            }
        } catch (Exception e) {
            NeoDiscover.LOGGER.warn("No se pudo obtener versión de NeoForge", e);
        }
        
        return configManager.getConfigValue("neoforge_version", "21.1.215");
    }

    private JsonArray collectMods() {
        JsonArray modsArray = new JsonArray();
        
        try {
            // Leer mods desde downloads/mods en lugar de la carpeta mods del servidor
            if (fileManager != null) {
                Path downloadsModsPath = fileManager.getDownloadsPath().resolve("mods");
                
                if (Files.exists(downloadsModsPath)) {
                    Files.list(downloadsModsPath)
                        .filter(path -> path.toString().endsWith(".jar"))
                        .forEach(path -> {
                            JsonObject mod = new JsonObject();
                            String fileName = path.getFileName().toString();
                            mod.addProperty("name", fileName);
                            
                            // URL del mod (de configuración, desde downloads, o construir desde nombre)
                            String modUrl = configManager.getConfigValue("mod_url_" + fileName);
                            
                            // Si no hay URL configurada, generar una desde downloads
                            if (modUrl == null || modUrl.isEmpty()) {
                                modUrl = fileManager.getModUrl(fileName);
                            }
                            
                            mod.addProperty("url", modUrl != null ? modUrl : "");
                            mod.addProperty("required", true);
                            
                            modsArray.add(mod);
                        });
                    
                    NeoDiscover.LOGGER.info("Mods recopilados desde downloads/mods: {}", modsArray.size());
                } else {
                    NeoDiscover.LOGGER.info("Carpeta downloads/mods no existe aún");
                }
            } else {
                NeoDiscover.LOGGER.warn("fileManager es null, no se pueden recopilar mods desde downloads");
            }
        } catch (Exception e) {
            NeoDiscover.LOGGER.error("Error al recopilar mods desde downloads/mods", e);
        }
        
        return modsArray;
    }

    private JsonArray collectShaders() {
        JsonArray shadersArray = new JsonArray();
        
        // Obtener shaders de la configuración
        com.google.gson.JsonElement shadersElement = configManager.getConfigElement("shaders");
        if (shadersElement != null && shadersElement.isJsonArray()) {
            return shadersElement.getAsJsonArray();
        }
        
        return shadersArray;
    }

    private JsonArray collectResourcePacks() {
        JsonArray resourcePacksArray = new JsonArray();
        
        // Obtener resource packs de la configuración
        com.google.gson.JsonElement resourcePacksElement = configManager.getConfigElement("resourcepacks");
        if (resourcePacksElement != null && resourcePacksElement.isJsonArray()) {
            return resourcePacksElement.getAsJsonArray();
        }
        
        return resourcePacksArray;
    }

    private JsonObject collectOptions() {
        JsonObject options = new JsonObject();
        
        // Obtener opciones de la configuración
        com.google.gson.JsonElement optionsElement = configManager.getConfigElement("options");
        if (optionsElement != null && optionsElement.isJsonObject()) {
            return optionsElement.getAsJsonObject();
        }
        
        return options;
    }

    private JsonObject createServerConfig() {
        JsonObject config = new JsonObject();
        
        // Usar el mismo método getServerHost() que ya funciona correctamente
        String serverIp = getServerHost();
        config.addProperty("server_ip", serverIp);
        
        int serverPort = 25565;
        if (server != null && server instanceof DedicatedServer dedicatedServer) {
            serverPort = dedicatedServer.getProperties().serverPort;
        } else {
            String portStr = configManager.getConfigValue("server_port");
            if (portStr != null) {
                try {
                    serverPort = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    // Usar valor por defecto
                }
            }
        }
        config.addProperty("server_port", serverPort);
        
        String autoConnect = configManager.getConfigValue("auto_connect", "true");
        config.addProperty("auto_connect", Boolean.parseBoolean(autoConnect));
        
        return config;
    }
}

