# NeoDiscover - NeoForge Server Plugin

A server-side plugin for NeoForge that exposes server information via HTTP endpoints and manages downloadable content (mods, shaders, resource packs).

## Features

- **HTTP Server**: Opens a port (default 25080) to serve server information
- **Automatic Information Collection**: Automatically gathers server information (name, version, mods, etc.)
- **File Management**: Manages downloadable files (mods, shaders, resource packs) in organized folders
- **REST API**: POST endpoint to update configuration with API key validation
- **Multipart File Upload**: Accepts file uploads with structured metadata
- **Download URLs**: Generates download URLs for all managed files

## Requirements

- NeoForge 21.1.215 or higher
- Minecraft 1.21.1
- Java 21

## Installation

1. Download the latest release JAR from the [Releases](https://github.com/YOUR_USERNAME/Minecraft-Neo-Discover/releases) page
2. Copy the JAR file to the `mods` folder of your NeoForge server
3. Start the server. The plugin will initialize automatically.

## Usage

### Get Server Information

The plugin exposes a GET endpoint at `http://localhost:25080/profiles.json` that returns JSON with server information.

### Configuration

The plugin creates a `neodiscover_config.txt` file in the server directory. This file stores:

- `api_key`: Automatically generated API key (shown in logs on startup)
- `server_url`: Server URL (optional)
- `server_name`: Server name (optional, obtained from MOTD if not specified)
- `description`: Server description (optional)
- `name`: Profile name
- `id`: Profile ID
- `shaders`: JSON array with shader information
- `resourcepacks`: JSON array with resource pack information
- `options`: JSON object with additional game options
- `mods`: JSON array with mod information (automatically populated from `downloads/mods/`)

### Update Configuration via POST

You can update the configuration by sending a POST request to `http://localhost:25080/update` with:

**Option 1: JSON only**
```json
{
  "api_key": "your-api-key-here",
  "name": "My Server",
  "description": "Server description",
  ...
}
```

**Option 2: Multipart with files**
- `profile_json`: Complete profile JSON as string
- `files_metadata`: JSON array with file metadata
- `api_key`: API key (or use `X-API-Key` header)
- File fields: `mods_0`, `mods_1`, `shaders_0`, `resourcepacks_0`, etc.

**Headers:**
- `X-API-Key`: API key (optional, can also be in body)
- `Content-Type`: `application/json` or `multipart/form-data`

### File Structure

Files are organized in the `downloads` folder:
- `downloads/mods/` - Mod JAR files
- `downloads/shaders/` - Shader ZIP files
- `downloads/resourcepacks/` - Resource pack ZIP files

### Download Files

Files can be downloaded via:
- Mods: `http://localhost:25080/downloads/mods/filename.jar`
- Shaders: `http://localhost:25080/downloads/shaders/filename.zip`
- Resource Packs: `http://localhost:25080/downloads/resourcepacks/filename.zip`

## API Endpoints

### GET `/profiles.json`
Returns server information and profile data in JSON format.

### POST `/update`
Updates server configuration and/or uploads files.

**Headers:**
- `X-API-Key`: API key (optional, can be in body)
- `Content-Type`: `application/json` or `multipart/form-data`

**Body (JSON):**
```json
{
  "api_key": "your-api-key",
  "name": "Server Name",
  "description": "Description",
  ...
}
```

**Body (Multipart):**
- `profile_json`: JSON string with profile data
- `files_metadata`: JSON array with file metadata
- `api_key`: API key (if not in header)
- File fields: `mods_0`, `shaders_0`, `resourcepacks_0`, etc.

### GET `/downloads/{type}/{filename}`
Downloads a file from the downloads folder.

## Development

### Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

### Project Structure

```
src/
  main/
    java/
      com/neodiscover/
        config/        # Configuration management
        events/        # Server event handlers
        server/        # HTTP server and file management
    resources/
      META-INF/
        neoforge.mods.toml
```

## License

MIT License

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
