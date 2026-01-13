# Project Documentation & Recovery Guide

## ⚠️ Recovery Mode Notice
If you are reading this, you might be in **Recovery Mode** or just finished an Environment Rebuild.
The `dev.nix` configuration has been optimized ("slimmed down") to prevent timeouts during environment creation.

**Key Changes:**
- Removed redundant packages (`dart` is included in `flutter`).
- **Faster Rebuilds:** `npm install` and `flutter pub get` are conditional or skipped in `onStart`.
- **Stable Previews:** Web preview now defaults to `release` mode for stability. Backend preview runs on port 8080.

## Web Preview Modes

The project supports two modes for the Web Preview, controlled by the `WEB_MODE` environment variable in `.idx/dev.nix`.

### 1. Release Mode (Default & Recommended)
- **Setting:** `WEB_MODE = "release";`
- **Behavior:** Runs `flutter build web --release` and serves static files via `tools/web_dev_proxy.js`.
- **Pros:** Fast load times, stable, accurately represents production build.
- **Cons:** No hot reload. Requires a manual "Refresh" (restart preview) to see changes.

### 2. Dev Mode
- **Setting:** `WEB_MODE = "dev";`
- **Behavior:** Runs `flutter run -d web-server`.
- **Pros:** Supports Hot Reload.
- **Cons:** Can be slower to start and heavier on memory.

**To switch modes:**
1. Edit `.idx/dev.nix`.
2. Change `WEB_MODE` value.
3. Run **"IDX: Rebuild Environment"** (Cmd/Ctrl + Shift + P).

## Backend Preview
The backend server runs automatically on port 8080.
- Source: `server/` directory.
- Command: `npm start` (runs `src/index.js`).

## Architecture Overview
See `docs/ARCHITECTURE.md` for detailed system design.
