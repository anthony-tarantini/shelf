# User Manual 📖

Welcome to Shelf! This manual explains how to use the core features of the system to manage your digital library.

## 📥 Importing Books

Shelf uses a **watch-folder** approach to importing books.

1. **Upload files:** Place your EPUB or Audiobook (MP3/M4B) files into your configured storage directory.
2. **Scan:** Shelf will automatically scan the directory recursively.
3. **Metadata Extraction:** Shelf attempts to extract metadata (Title, Author, Cover) directly from the files.

## 🏷️ Managing Metadata

Once books are imported, you can view and edit their metadata through the web UI.

- **Metadata Providers:** Shelf can fetch missing details from external services like Hardcover.
- **Series & Collections:** You can group books into series and specify their reading order.
- **Tags:** Add custom tags to organize your library by genre, mood, or status.

## 📱 Connecting Devices

Shelf provides an **OPDS Feed** that is compatible with most modern e-readers and mobile apps.

### Supported Apps
- **Koreader:** Full integration support (including sync).
- **KyBook 3 / MapleRead:** (iOS)
- **Moon+ Reader:** (Android)

### Connecting via OPDS
Point your app to your Shelf URL followed by `/opds`. 
Example: `https://shelf.example.com/opds`

## 🎧 Audiobooks

Shelf includes a built-in web-based audio player for audiobooks. It supports:
- Track navigation.
- Playback speed control.
- Resume from where you left off.
