package com.neodiscover.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.neodiscover.NeoDiscover;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final String CONFIG_FILE = "neodiscover_config.txt";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private JsonObject configData;
    private String apiKey;
    private Path configPath;
    private MinecraftServer server;

    public ConfigManager() {
        // Determinar la ruta del archivo de configuración
        // Inicialmente usar el directorio actual de trabajo
        String serverDir = System.getProperty("user.dir");
        if (serverDir != null) {
            configPath = Paths.get(serverDir, CONFIG_FILE);
        } else {
            configPath = Paths.get(CONFIG_FILE);
        }
        
        configData = new JsonObject();
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        // Actualizar la ruta del archivo de configuración cuando el servidor esté disponible
        if (server != null) {
            try {
                Path serverPath = server.getWorldPath(LevelResource.ROOT).getParent().getParent();
                configPath = serverPath.resolve(CONFIG_FILE);
                // Recargar configuración desde la nueva ubicación si existe
                if (Files.exists(configPath)) {
                    loadConfig();
                }
            } catch (Exception e) {
                NeoDiscover.LOGGER.warn("No se pudo actualizar la ruta de configuración con el servidor", e);
            }
        }
    }

    public void loadConfig() {
        try {
            NeoDiscover.LOGGER.info("Cargando configuración desde: {}", configPath);
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                if (content != null && !content.trim().isEmpty()) {
                    try {
                        configData = gson.fromJson(content, JsonObject.class);
                        if (configData == null) {
                            configData = new JsonObject();
                        } else {
                            NeoDiscover.LOGGER.info("Configuración cargada. Keys disponibles: {}", configData.keySet());
                            if (configData.has("name")) {
                                NeoDiscover.LOGGER.info("Config 'name' = {}", configData.get("name"));
                            }
                            if (configData.has("description")) {
                                NeoDiscover.LOGGER.info("Config 'description' = {}", configData.get("description"));
                            }
                            if (configData.has("id")) {
                                NeoDiscover.LOGGER.info("Config 'id' = {}", configData.get("id"));
                            }
                        }
                    } catch (com.google.gson.JsonSyntaxException e) {
                        NeoDiscover.LOGGER.warn("El archivo de configuración no es JSON válido, creando uno nuevo", e);
                        configData = new JsonObject();
                    }
                } else {
                    configData = new JsonObject();
                }
                
                if (configData.has("api_key")) {
                    apiKey = configData.get("api_key").getAsString();
                    NeoDiscover.LOGGER.info("API Key cargada desde configuración: " + apiKey);
                } else {
                    // Generar nueva API key si no existe
                    generateApiKey();
                    saveConfig(); // Guardar inmediatamente la nueva API key
                }
            } else {
                NeoDiscover.LOGGER.warn("Archivo de configuración no existe en: {}", configPath);
                // Crear archivo de configuración por defecto
                configData = new JsonObject();
                generateApiKey();
                saveConfig();
            }
        } catch (Exception e) {
            NeoDiscover.LOGGER.error("Error al cargar configuración desde: " + configPath, e);
            configData = new JsonObject();
            generateApiKey();
        }
    }

    private void generateApiKey() {
        // Generar una API key aleatoria
        apiKey = java.util.UUID.randomUUID().toString().replace("-", "");
        configData.addProperty("api_key", apiKey);
        NeoDiscover.LOGGER.info("API Key generada: " + apiKey);
    }

    public void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, gson.toJson(configData));
        } catch (Exception e) {
            NeoDiscover.LOGGER.error("Error al guardar configuración", e);
        }
    }

    public boolean validateApiKey(String providedKey) {
        return apiKey != null && apiKey.equals(providedKey);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void updateConfig(JsonObject updates) {
        // Actualizar configuración con los datos recibidos
        for (Map.Entry<String, com.google.gson.JsonElement> entry : updates.entrySet()) {
            String key = entry.getKey();
            // No permitir actualizar la API key desde fuera
            if (!"api_key".equals(key)) {
                configData.add(key, entry.getValue());
                NeoDiscover.LOGGER.debug("Configuración actualizada: {} = {}", key, entry.getValue());
            } else {
                NeoDiscover.LOGGER.warn("Intento de actualizar api_key ignorado (protegido)");
            }
        }
        saveConfig();
        NeoDiscover.LOGGER.info("Configuración guardada correctamente");
    }

    public JsonObject getConfigData() {
        return configData;
    }

    public String getConfigValue(String key) {
        if (configData.has(key)) {
            com.google.gson.JsonElement element = configData.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            }
            // Si no es un string, devolver null
            return null;
        }
        return null;
    }

    public String getConfigValue(String key, String defaultValue) {
        String value = getConfigValue(key);
        return value != null ? value : defaultValue;
    }
    
    public com.google.gson.JsonElement getConfigElement(String key) {
        if (configData.has(key)) {
            return configData.get(key);
        }
        return null;
    }
}

