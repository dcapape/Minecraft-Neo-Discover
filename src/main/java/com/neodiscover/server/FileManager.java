package com.neodiscover.server;

import com.neodiscover.NeoDiscover;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileManager {
    private static final String DOWNLOADS_FOLDER = "downloads";
    private Path downloadsPath;
    private MinecraftServer server;
    private int httpPort;
    private String serverHost;

    public FileManager(int httpPort) {
        this.httpPort = httpPort;
        this.serverHost = "localhost"; // Valor por defecto
        String serverDir = System.getProperty("user.dir");
        if (serverDir != null) {
            downloadsPath = Paths.get(serverDir, DOWNLOADS_FOLDER);
        } else {
            downloadsPath = Paths.get(DOWNLOADS_FOLDER);
        }
    }

    public void setServerHost(String host) {
        this.serverHost = host != null && !host.isEmpty() ? host : "localhost";
        NeoDiscover.LOGGER.info("Server host establecido en: {}", this.serverHost);
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        if (server != null) {
            try {
                Path serverPath = server.getWorldPath(LevelResource.ROOT).getParent().getParent();
                downloadsPath = serverPath.resolve(DOWNLOADS_FOLDER);
                ensureDownloadsFolderExists();
            } catch (Exception e) {
                NeoDiscover.LOGGER.warn("No se pudo actualizar la ruta de downloads con el servidor", e);
            }
        }
    }

    private void ensureDownloadsFolderExists() {
        try {
            if (!Files.exists(downloadsPath)) {
                Files.createDirectories(downloadsPath);
                NeoDiscover.LOGGER.info("Carpeta downloads creada: {}", downloadsPath);
            }
        } catch (Exception e) {
            NeoDiscover.LOGGER.error("Error al crear carpeta downloads", e);
        }
    }

    public Path saveFile(String fileName, InputStream fileStream) throws IOException {
        ensureDownloadsFolderExists();
        Path filePath = downloadsPath.resolve(fileName);
        Files.copy(fileStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        NeoDiscover.LOGGER.info("Archivo guardado: {}", filePath);
        return filePath;
    }

    public Path saveModFile(String fileName, InputStream fileStream) throws IOException {
        ensureDownloadsFolderExists();
        Path modsPath = downloadsPath.resolve("mods");
        if (!Files.exists(modsPath)) {
            Files.createDirectories(modsPath);
        }
        Path filePath = modsPath.resolve(fileName);
        Files.copy(fileStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        NeoDiscover.LOGGER.info("Mod guardado: {}", filePath);
        return filePath;
    }

    public Path saveShaderFile(String fileName, InputStream fileStream) throws IOException {
        ensureDownloadsFolderExists();
        Path shadersPath = downloadsPath.resolve("shaders");
        if (!Files.exists(shadersPath)) {
            Files.createDirectories(shadersPath);
        }
        Path filePath = shadersPath.resolve(fileName);
        Files.copy(fileStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        NeoDiscover.LOGGER.info("Shader guardado: {}", filePath);
        return filePath;
    }

    public Path saveResourcePackFile(String fileName, InputStream fileStream) throws IOException {
        ensureDownloadsFolderExists();
        Path resourcePacksPath = downloadsPath.resolve("resourcepacks");
        if (!Files.exists(resourcePacksPath)) {
            Files.createDirectories(resourcePacksPath);
        }
        Path filePath = resourcePacksPath.resolve(fileName);
        Files.copy(fileStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        NeoDiscover.LOGGER.info("Resource pack guardado: {}", filePath);
        return filePath;
    }

    public String getShaderUrl(String shaderFileName) {
        return String.format("http://%s:%d/downloads/shaders/%s", serverHost, httpPort, shaderFileName);
    }

    public String getResourcePackUrl(String rpFileName) {
        return String.format("http://%s:%d/downloads/resourcepacks/%s", serverHost, httpPort, rpFileName);
    }

    public Path getDownloadsPath() {
        return downloadsPath;
    }

    public List<String> listDownloadedFiles() {
        List<String> files = new ArrayList<>();
        try {
            if (Files.exists(downloadsPath)) {
                // Listar archivos en la raíz de downloads
                Files.list(downloadsPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> files.add(path.getFileName().toString()));
                
                // Listar mods en downloads/mods
                Path modsPath = downloadsPath.resolve("mods");
                if (Files.exists(modsPath)) {
                    Files.list(modsPath)
                        .filter(Files::isRegularFile)
                        .forEach(path -> files.add("mods/" + path.getFileName().toString()));
                }
            }
        } catch (Exception e) {
            NeoDiscover.LOGGER.error("Error al listar archivos en downloads", e);
        }
        return files;
    }

    public Path getFile(String fileName) {
        if (downloadsPath != null && Files.exists(downloadsPath.resolve(fileName))) {
            return downloadsPath.resolve(fileName);
        }
        return null;
    }

    public String getFileUrl(String fileName) {
        return String.format("http://%s:%d/downloads/%s", serverHost, httpPort, fileName);
    }

    public String getModUrl(String modFileName) {
        return String.format("http://%s:%d/downloads/mods/%s", serverHost, httpPort, modFileName);
    }
}

