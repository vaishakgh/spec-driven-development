# YouTube Playlist API — Mule 4

A Mule 4 application that connects to the YouTube Data API v3 to retrieve songs from a YouTube playlist.

## 📁 Project Structure

```
youtube-playlist-api/
├── src/main/mule/
│   └── youtube-playlist.xml          # Mule flows
├── src/main/resources/
│   ├── config.yaml                   # Your local config (NOT committed)
│   └── config.yaml.template          # Safe template to share
├── .github/workflows/
│   └── deploy-to-cloudhub.yml        # CI/CD pipeline
├── .gitignore
├── pom.xml
├── settings.xml
└── README.md
```

## 🚀 Setup Instructions

### 1. Get a YouTube API Key
- Go to [Google Cloud Console](https://console.cloud.google.com/)
- Enable **YouTube Data API v3**
- Create credentials → API Key

### 2. Get Your Playlist ID
- Open your YouTube playlist
- Copy the ID from the URL: `...list=PLxxxxxxxx`

### 3. Configure the App
- Copy `src/main/resources/config.yaml.template` → `config.yaml`
- Fill in your API Key and Playlist ID

### 4. Run in Anypoint Studio
- File → Import → Anypoint Studio Project
- Right-click project → Run As → Mule Application

## 🌐 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/youtube/playlists` | Get all songs in playlist |
| GET | `/youtube/song/{videoId}` | Get details of a specific song |

## 🧪 Test the API

```bash
# Get all playlist songs
curl http://localhost:8081/youtube/playlists

# Get specific song
curl http://localhost:8081/youtube/song/dQw4w9WgXcQ
```

## ☁️ Deploy to CloudHub

Add these GitHub Secrets in your repo settings:
- `ANYPOINT_USERNAME`
- `ANYPOINT_PASSWORD`
- `ANYPOINT_ENVIRONMENT`
- `YOUTUBE_API_KEY`
- `YOUTUBE_PLAYLIST_ID`

Then push to `main` — GitHub Actions will auto-deploy to CloudHub!
