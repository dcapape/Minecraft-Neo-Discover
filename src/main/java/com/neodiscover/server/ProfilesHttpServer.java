package com.neodiscover.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neodiscover.NeoDiscover;
import com.neodiscover.config.ConfigManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class ProfilesHttpServer {
    private final int port;
    private final ServerInfoCollector infoCollector;
    private final ConfigManager configManager;
    private final FileManager fileManager;
    private HttpServer server;
    private boolean running = false;

    public ProfilesHttpServer(int port, ServerInfoCollector infoCollector, ConfigManager configManager, FileManager fileManager) {
        this.port = port;
        this.infoCollector = infoCollector;
        this.configManager = configManager;
        this.fileManager = fileManager;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Endpoint GET para obtener profiles.json
            server.createContext("/profiles.json", new ProfilesHandler());
            
            // Endpoint POST para actualizar configuración (acepta JSON y multipart/form-data)
            server.createContext("/update", new UpdateHandler());
            
            // Endpoint GET para descargar archivos
            server.createContext("/downloads", new DownloadsHandler());
            
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            running = true;
            NeoDiscover.LOGGER.info("Servidor HTTP iniciado en puerto {}", port);
        } catch (IOException e) {
            NeoDiscover.LOGGER.error("Error al iniciar servidor HTTP", e);
        }
    }

    public void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            NeoDiscover.LOGGER.info("Servidor HTTP detenido");
        }
    }

    private class ProfilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }

            try {
                JsonObject profilesJson = infoCollector.collectServerInfo();
                String response = profilesJson.toString();
                
                sendResponse(exchange, 200, response, "application/json");
                NeoDiscover.LOGGER.debug("Profiles.json servido correctamente");
            } catch (Exception e) {
                NeoDiscover.LOGGER.error("Error al generar profiles.json", e);
                sendResponse(exchange, 500, "Internal Server Error", "text/plain");
            }
        }
    }

    private class UpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Log de todas las peticiones
            String remoteAddress = exchange.getRemoteAddress() != null ? 
                exchange.getRemoteAddress().getAddress().getHostAddress() : "unknown";
            String method = exchange.getRequestMethod();
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            
            NeoDiscover.LOGGER.info("=== Petición /update recibida ===");
            NeoDiscover.LOGGER.info("IP remota: {}", remoteAddress);
            NeoDiscover.LOGGER.info("Método: {}", method);
            NeoDiscover.LOGGER.info("Content-Type: {}", contentType);
            
            if (!"POST".equals(method)) {
                NeoDiscover.LOGGER.warn("Método no permitido: {} (esperado: POST)", method);
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }

            try {
                // Manejar multipart/form-data para archivos
                if (contentType != null && contentType.startsWith("multipart/form-data")) {
                    NeoDiscover.LOGGER.info("Procesando petición multipart/form-data");
                    handleMultipartUpdate(exchange);
                    return;
                }
                
                // Manejar JSON normal
                NeoDiscover.LOGGER.info("Procesando petición JSON");
                handleJsonUpdate(exchange);
            } catch (Exception e) {
                NeoDiscover.LOGGER.error("Error al procesar actualización desde IP: {}", remoteAddress, e);
                sendResponse(exchange, 500, "{\"error\":\"Error interno del servidor\"}", "application/json");
            }
        }
        
        private void handleJsonUpdate(HttpExchange exchange) throws IOException {
            // Leer el cuerpo de la petición
            InputStream requestBody = exchange.getRequestBody();
            String body = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
            
            // Limpiar el body (eliminar espacios en blanco al inicio/final)
            body = body.trim();
            
            // Log completo del body recibido
            NeoDiscover.LOGGER.info("Body recibido (tamaño: {} bytes): {}", body.length(), 
                body.length() > 500 ? body.substring(0, 500) + "..." : body);
            
            // Verificar que el body no esté vacío
            if (body.isEmpty()) {
                NeoDiscover.LOGGER.warn("Petición rechazada: Cuerpo de la petición vacío");
                sendResponse(exchange, 400, "{\"error\":\"Cuerpo de la petición vacío\"}", "application/json");
                return;
            }
            
            // Parsear JSON con manejo de errores mejorado
            JsonObject requestJson;
            try {
                requestJson = JsonParser.parseString(body).getAsJsonObject();
                NeoDiscover.LOGGER.info("JSON parseado correctamente. Keys: {}", requestJson.keySet());
            } catch (com.google.gson.JsonSyntaxException e) {
                NeoDiscover.LOGGER.error("Error al parsear JSON. Body recibido: {}", body, e);
                sendResponse(exchange, 400, "{\"error\":\"JSON inválido: " + e.getMessage().replace("\"", "\\\"") + "\"}", "application/json");
                return;
            }
            
            // Validar API key (prioridad: header X-API-Key, luego campo api_key en JSON)
            String providedApiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            if (providedApiKey == null || providedApiKey.isEmpty()) {
                // Si no está en el header, buscar en el JSON
                if (requestJson.has("api_key")) {
                    providedApiKey = requestJson.get("api_key").getAsString();
                    NeoDiscover.LOGGER.info("API key encontrada en JSON body");
                } else {
                    NeoDiscover.LOGGER.warn("Petición rechazada: API key no proporcionada (ni en header X-API-Key ni en JSON)");
                    sendResponse(exchange, 401, "{\"error\":\"API key requerida\"}", "application/json");
                    return;
                }
            } else {
                NeoDiscover.LOGGER.info("API key encontrada en header X-API-Key");
            }
            
            NeoDiscover.LOGGER.info("API key proporcionada: {}...{}", 
                providedApiKey.length() > 8 ? providedApiKey.substring(0, 4) : "****",
                providedApiKey.length() > 8 ? providedApiKey.substring(providedApiKey.length() - 4) : "****");
            
            if (!configManager.validateApiKey(providedApiKey)) {
                NeoDiscover.LOGGER.warn("Petición rechazada: API key inválida");
                sendResponse(exchange, 401, "{\"error\":\"API key inválida\"}", "application/json");
                return;
            }
            
            NeoDiscover.LOGGER.info("API key válida. Procesando actualización...");
            
            // Remover api_key del objeto antes de actualizar (si existe)
            JsonObject updates = requestJson.deepCopy();
            updates.remove("api_key");
            
            NeoDiscover.LOGGER.info("Campos a actualizar: {}", updates.keySet());
            
            // Actualizar configuración
            configManager.updateConfig(updates);
            
            sendResponse(exchange, 200, "{\"status\":\"ok\",\"message\":\"Configuración actualizada correctamente\"}", "application/json");
            NeoDiscover.LOGGER.info("✓ Configuración actualizada correctamente mediante POST JSON");
        }
        
        private void handleMultipartUpdate(HttpExchange exchange) throws IOException {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String boundary = extractBoundary(contentType);
            
            NeoDiscover.LOGGER.info("Boundary extraído: {}", boundary);
            
            if (boundary == null) {
                NeoDiscover.LOGGER.warn("Petición multipart rechazada: Boundary no encontrado en Content-Type");
                sendResponse(exchange, 400, "{\"error\":\"Boundary no encontrado en Content-Type\"}", "application/json");
                return;
            }
            
            InputStream requestBody = exchange.getRequestBody();
            byte[] bodyBytes = requestBody.readAllBytes();
            
            NeoDiscover.LOGGER.info("Body multipart recibido (tamaño: {} bytes)", bodyBytes.length);
            
            // Parsear multipart
            java.util.Map<String, String> formFields = new java.util.HashMap<>();
            java.util.Map<String, java.util.Map<String, Object>> files = new java.util.HashMap<>();
            
            parseMultipart(bodyBytes, boundary, formFields, files);
            
            NeoDiscover.LOGGER.info("Multipart parseado. Campos de formulario: {}, Archivos: {}", 
                formFields.keySet(), files.keySet());
            
            // Paso 1: Validar API Key (prioridad: header X-API-Key, luego campo api_key)
            String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = formFields.get("api_key");
                NeoDiscover.LOGGER.info("API key no encontrada en header, buscando en form_data");
            } else {
                NeoDiscover.LOGGER.info("API key encontrada en header X-API-Key");
            }
            
            if (apiKey == null || apiKey.isEmpty()) {
                NeoDiscover.LOGGER.warn("Petición multipart rechazada: API key no proporcionada");
                sendResponse(exchange, 401, "{\"error\":\"API key requerida o inválida\"}", "application/json");
                return;
            }
            
            NeoDiscover.LOGGER.info("API key proporcionada: {}...{}", 
                apiKey.length() > 8 ? apiKey.substring(0, 4) : "****",
                apiKey.length() > 8 ? apiKey.substring(apiKey.length() - 4) : "****");
            
            if (!configManager.validateApiKey(apiKey)) {
                NeoDiscover.LOGGER.warn("Petición multipart rechazada: API key inválida");
                sendResponse(exchange, 401, "{\"error\":\"API key requerida o inválida\"}", "application/json");
                return;
            }
            
            NeoDiscover.LOGGER.info("API key válida. Procesando actualización multipart...");
            
            // Paso 2: Parsear profile_json y files_metadata
            String profileJsonStr = formFields.get("profile_json");
            String filesMetadataStr = formFields.get("files_metadata");
            
            if (profileJsonStr == null || profileJsonStr.isEmpty()) {
                NeoDiscover.LOGGER.warn("Petición multipart rechazada: profile_json no proporcionado");
                sendResponse(exchange, 400, "{\"error\":\"profile_json requerido\"}", "application/json");
                return;
            }
            
            if (filesMetadataStr == null || filesMetadataStr.isEmpty()) {
                filesMetadataStr = "[]"; // Si no hay metadatos, usar array vacío
            }
            
            JsonObject profileJson;
            com.google.gson.JsonArray filesMetadata;
            
            try {
                profileJson = JsonParser.parseString(profileJsonStr).getAsJsonObject();
                NeoDiscover.LOGGER.info("profile_json parseado correctamente. Keys: {}", profileJson.keySet());
            } catch (Exception e) {
                NeoDiscover.LOGGER.error("Error al parsear profile_json", e);
                sendResponse(exchange, 400, "{\"error\":\"profile_json inválido: " + e.getMessage().replace("\"", "\\\"") + "\"}", "application/json");
                return;
            }
            
            try {
                filesMetadata = JsonParser.parseString(filesMetadataStr).getAsJsonArray();
                NeoDiscover.LOGGER.info("files_metadata parseado. {} archivo(s) en metadatos", filesMetadata.size());
            } catch (Exception e) {
                NeoDiscover.LOGGER.error("Error al parsear files_metadata", e);
                sendResponse(exchange, 400, "{\"error\":\"files_metadata inválido: " + e.getMessage().replace("\"", "\\\"") + "\"}", "application/json");
                return;
            }
            
            // Paso 3: Procesar archivos según metadatos
            java.util.List<String> savedFiles = new java.util.ArrayList<>();
            java.util.List<String> errors = new java.util.ArrayList<>();
            
            for (int i = 0; i < filesMetadata.size(); i++) {
                try {
                    com.google.gson.JsonObject metadata = filesMetadata.get(i).getAsJsonObject();
                    String fieldName = metadata.get("field_name").getAsString();
                    String type = metadata.get("type").getAsString();
                    String fileName = metadata.get("name").getAsString();
                    long expectedSize = metadata.get("size").getAsLong();
                    
                    NeoDiscover.LOGGER.info("Procesando archivo: field_name={}, type={}, name={}, size={}", 
                        fieldName, type, fileName, expectedSize);
                    
                    // Validar tipo
                    if (!type.equals("mods") && !type.equals("shaders") && !type.equals("resourcepacks")) {
                        String error = String.format("Tipo inválido para %s: %s", fileName, type);
                        NeoDiscover.LOGGER.warn(error);
                        errors.add(error);
                        continue;
                    }
                    
                    // Buscar el archivo en el multipart
                    java.util.Map<String, Object> fileData = files.get(fieldName);
                    if (fileData == null) {
                        String error = String.format("Archivo %s (field_name: %s) no encontrado", fileName, fieldName);
                        NeoDiscover.LOGGER.warn(error);
                        errors.add(error);
                        continue;
                    }
                    
                    byte[] fileContent = (byte[]) fileData.get("content");
                    if (fileContent == null) {
                        String error = String.format("Contenido del archivo %s está vacío", fileName);
                        NeoDiscover.LOGGER.warn(error);
                        errors.add(error);
                        continue;
                    }
                    
                    // Validar tamaño
                    if (fileContent.length != expectedSize) {
                        String error = String.format("Tamaño no coincide para %s: esperado %d, recibido %d", 
                            fileName, expectedSize, fileContent.length);
                        NeoDiscover.LOGGER.warn(error);
                        errors.add(error);
                        continue;
                    }
                    
                    // Guardar archivo según tipo
                    java.io.ByteArrayInputStream fileStream = new java.io.ByteArrayInputStream(fileContent);
                    String savedPath;
                    
                    switch (type) {
                        case "mods":
                            fileManager.saveModFile(fileName, fileStream);
                            savedPath = "mods/" + fileName;
                            break;
                        case "shaders":
                            fileManager.saveShaderFile(fileName, fileStream);
                            savedPath = "shaders/" + fileName;
                            break;
                        case "resourcepacks":
                            fileManager.saveResourcePackFile(fileName, fileStream);
                            savedPath = "resourcepacks/" + fileName;
                            break;
                        default:
                            continue; // Ya validado arriba
                    }
                    
                    savedFiles.add(savedPath);
                    NeoDiscover.LOGGER.info("✓ Archivo guardado: {} (tipo: {})", savedPath, type);
                    
                } catch (Exception e) {
                    String error = String.format("Error al procesar archivo en índice %d: %s", i, e.getMessage());
                    NeoDiscover.LOGGER.error(error, e);
                    errors.add(error);
                }
            }
            
            // Paso 4: Actualizar perfil en configuración
            try {
                // Remover api_key si existe en profile_json
                JsonObject profileToSave = profileJson.deepCopy();
                profileToSave.remove("api_key");
                
                NeoDiscover.LOGGER.info("Actualizando configuración con profile_json...");
                configManager.updateConfig(profileToSave);
                NeoDiscover.LOGGER.info("✓ Configuración actualizada correctamente");
            } catch (Exception e) {
                NeoDiscover.LOGGER.error("Error al actualizar configuración", e);
                errors.add("Error al actualizar configuración: " + e.getMessage());
            }
            
            // Respuesta
            com.google.gson.JsonObject response = new com.google.gson.JsonObject();
            response.addProperty("success", errors.isEmpty());
            response.addProperty("message", String.format("Perfil actualizado. %d archivo(s) subido(s).", savedFiles.size()));
            
            com.google.gson.JsonArray filesArray = new com.google.gson.JsonArray();
            for (String savedPath : savedFiles) {
                com.google.gson.JsonObject fileInfo = new com.google.gson.JsonObject();
                String[] parts = savedPath.split("/", 2);
                String fileName = parts.length > 1 ? parts[1] : savedPath;
                fileInfo.addProperty("name", fileName);
                
                // Generar URL según tipo
                if (savedPath.startsWith("mods/")) {
                    fileInfo.addProperty("url", fileManager.getModUrl(fileName));
                } else if (savedPath.startsWith("shaders/")) {
                    fileInfo.addProperty("url", fileManager.getShaderUrl(fileName));
                } else if (savedPath.startsWith("resourcepacks/")) {
                    fileInfo.addProperty("url", fileManager.getResourcePackUrl(fileName));
                } else {
                    fileInfo.addProperty("url", fileManager.getFileUrl(fileName));
                }
                filesArray.add(fileInfo);
            }
            response.add("files", filesArray);
            
            if (!errors.isEmpty()) {
                com.google.gson.JsonArray errorsArray = new com.google.gson.JsonArray();
                for (String error : errors) {
                    errorsArray.add(error);
                }
                response.add("errors", errorsArray);
            }
            
            int statusCode = errors.isEmpty() ? 200 : 207; // 207 Multi-Status si hay errores parciales
            sendResponse(exchange, statusCode, response.toString(), "application/json");
            
            NeoDiscover.LOGGER.info("✓ Procesamiento completado: {} archivo(s) guardado(s), {} error(es)", 
                savedFiles.size(), errors.size());
            if (!savedFiles.isEmpty()) {
                NeoDiscover.LOGGER.info("Archivos guardados: {}", savedFiles);
            }
        }
        
        private String extractBoundary(String contentType) {
            if (contentType == null) return null;
            String[] parts = contentType.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("boundary=")) {
                    return part.substring("boundary=".length()).replace("\"", "");
                }
            }
            return null;
        }
        
        private void parseMultipart(byte[] data, String boundary, java.util.Map<String, String> formFields, 
                                   java.util.Map<String, java.util.Map<String, Object>> files) throws IOException {
            String boundaryMarker = "--" + boundary;
            String endBoundary = boundaryMarker + "--";
            
            int pos = 0;
            while (pos < data.length) {
                // Buscar el siguiente boundary
                int boundaryPos = indexOf(data, boundaryMarker.getBytes(StandardCharsets.UTF_8), pos);
                if (boundaryPos == -1) break;
                
                int partStart = boundaryPos + boundaryMarker.length();
                if (partStart >= data.length) break;
                
                // Saltar CRLF
                if (partStart < data.length - 1 && data[partStart] == '\r' && data[partStart + 1] == '\n') {
                    partStart += 2;
                } else if (partStart < data.length && data[partStart] == '\n') {
                    partStart += 1;
                }
                
                // Buscar el siguiente boundary o fin
                int nextBoundary = indexOf(data, boundaryMarker.getBytes(StandardCharsets.UTF_8), partStart);
                int partEnd = nextBoundary != -1 ? nextBoundary : data.length;
                
                // Parsear esta parte
                parseMultipartPart(data, partStart, partEnd, formFields, files);
                
                pos = partEnd;
            }
        }
        
        private void parseMultipartPart(byte[] data, int start, int end, 
                                       java.util.Map<String, String> formFields,
                                       java.util.Map<String, java.util.Map<String, Object>> files) throws IOException {
            // Buscar el header separator (CRLFCRLF o LFCRLF)
            int headerEnd = -1;
            for (int i = start; i < end - 3; i++) {
                if ((data[i] == '\r' && data[i+1] == '\n' && data[i+2] == '\r' && data[i+3] == '\n') ||
                    (data[i] == '\n' && data[i+1] == '\r' && data[i+2] == '\n')) {
                    headerEnd = i + (data[i] == '\r' ? 4 : 3);
                    break;
                }
            }
            
            if (headerEnd == -1) return;
            
            // Parsear headers
            String headers = new String(data, start, headerEnd - start, StandardCharsets.UTF_8);
            String fieldName = null;
            String fileName = null;
            boolean isFile = false;
            
            for (String line : headers.split("\r?\n")) {
                if (line.toLowerCase().startsWith("content-disposition:")) {
                    // Extraer name
                    int nameIdx = line.indexOf("name=\"");
                    if (nameIdx != -1) {
                        int nameEnd = line.indexOf("\"", nameIdx + 6);
                        if (nameEnd != -1) {
                            fieldName = line.substring(nameIdx + 6, nameEnd);
                        }
                    }
                    // Extraer filename
                    int filenameIdx = line.indexOf("filename=\"");
                    if (filenameIdx != -1) {
                        int filenameEnd = line.indexOf("\"", filenameIdx + 10);
                        if (filenameEnd != -1) {
                            fileName = line.substring(filenameIdx + 10, filenameEnd);
                            isFile = true;
                        }
                    }
                }
            }
            
            if (fieldName == null) return;
            
            // Leer el contenido (saltar CRLF final si existe)
            int contentStart = headerEnd;
            int contentEnd = end;
            if (contentEnd > contentStart + 2 && 
                data[contentEnd - 2] == '\r' && data[contentEnd - 1] == '\n') {
                contentEnd -= 2;
            } else if (contentEnd > contentStart + 1 && data[contentEnd - 1] == '\n') {
                contentEnd -= 1;
            }
            
            byte[] content = new byte[contentEnd - contentStart];
            System.arraycopy(data, contentStart, content, 0, content.length);
            
            if (isFile && fileName != null) {
                java.util.Map<String, Object> fileData = new java.util.HashMap<>();
                fileData.put("filename", fileName);
                fileData.put("content", content);
                files.put(fieldName, fileData);
            } else {
                String value = new String(content, StandardCharsets.UTF_8);
                formFields.put(fieldName, value);
            }
        }
        
        private int indexOf(byte[] array, byte[] pattern, int fromIndex) {
            for (int i = fromIndex; i <= array.length - pattern.length; i++) {
                boolean match = true;
                for (int j = 0; j < pattern.length; j++) {
                    if (array[i + j] != pattern[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) return i;
            }
            return -1;
        }
    }
    
    private class DownloadsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }
            
            try {
                String path = exchange.getRequestURI().getPath();
                // Remover /downloads del path
                String fileName = path.substring("/downloads".length());
                if (fileName.startsWith("/")) {
                    fileName = fileName.substring(1);
                }
                
                // Prevenir path traversal
                if (fileName.contains("..") || fileName.contains("\\")) {
                    sendResponse(exchange, 400, "Invalid file path", "text/plain");
                    return;
                }
                
                Path filePath = null;
                // Si el path empieza con "mods/", "shaders/", o "resourcepacks/", buscar en la carpeta correspondiente
                if (fileName.startsWith("mods/")) {
                    String modName = fileName.substring("mods/".length());
                    Path modsPath = fileManager.getDownloadsPath().resolve("mods").resolve(modName);
                    if (Files.exists(modsPath)) {
                        filePath = modsPath;
                    }
                } else if (fileName.startsWith("shaders/")) {
                    String shaderName = fileName.substring("shaders/".length());
                    Path shadersPath = fileManager.getDownloadsPath().resolve("shaders").resolve(shaderName);
                    if (Files.exists(shadersPath)) {
                        filePath = shadersPath;
                    }
                } else if (fileName.startsWith("resourcepacks/")) {
                    String rpName = fileName.substring("resourcepacks/".length());
                    Path rpPath = fileManager.getDownloadsPath().resolve("resourcepacks").resolve(rpName);
                    if (Files.exists(rpPath)) {
                        filePath = rpPath;
                    }
                } else {
                    filePath = fileManager.getFile(fileName);
                }
                
                if (filePath == null || !Files.exists(filePath)) {
                    sendResponse(exchange, 404, "File not found", "text/plain");
                    return;
                }
                
                // Determinar content type
                String contentType = "application/octet-stream";
                String lowerFileName = fileName.toLowerCase();
                if (lowerFileName.endsWith(".jar")) {
                    contentType = "application/java-archive";
                } else if (lowerFileName.endsWith(".zip")) {
                    contentType = "application/zip";
                } else if (lowerFileName.endsWith(".json")) {
                    contentType = "application/json";
                } else if (lowerFileName.endsWith(".txt")) {
                    contentType = "text/plain";
                }
                
                // Enviar archivo
                byte[] fileData = Files.readAllBytes(filePath);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, fileData.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileData);
                }
                
                NeoDiscover.LOGGER.debug("Archivo servido: {}", fileName);
            } catch (Exception e) {
                NeoDiscover.LOGGER.error("Error al servir archivo", e);
                sendResponse(exchange, 500, "Internal Server Error", "text/plain");
            }
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }
}

