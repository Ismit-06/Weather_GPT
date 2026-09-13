# <p align="center"><img src="assets/logo.png" width="120" alt="WeatherGPT Logo" /><br/>WeatherGPT</p>

<p align="center">
  <strong>Next-Generation Autonomous Atmospheric Intelligence, Multimodal Sky Vision & Flood Defense System</strong>
</p>

<p align="center">
  <a href="https://github.com/Ismit-06/Weather_GPT/releases"><img src="https://img.shields.io/github/v/release/Ismit-06/Weather_GPT?color=00D2FF&label=Release&style=for-the-badge&logo=android" alt="Latest Release" /></a>
  <a href="https://weather-gpt-ymze.onrender.com"><img src="https://img.shields.io/badge/API_Status-Online-00E676?style=for-the-badge&logo=fastapi" alt="API Status" /></a>
  <a href="#-installation--downloads"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android" alt="Platform" /></a>
  <a href="#-license"><img src="https://img.shields.io/badge/License-MIT-38BDF8?style=for-the-badge" alt="License" /></a>
</p>

---

## 🚀 Instant Download & Installation

Anyone can directly download and install WeatherGPT on their Android device:

<p align="center">
  <a href="https://github.com/Ismit-06/Weather_GPT/releases/latest/download/app-release.apk">
    <img src="https://img.shields.io/badge/⚡_DOWNLOAD_LATEST_RELEASE_APK-Direct_Download-0284C7?style=for-the-badge&logo=android&logoColor=white" height="52" alt="Download Latest Release APK" />
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/Ismit-06/Weather_GPT/releases">
    <img src="https://img.shields.io/badge/📦_ALL_RELEASES-GitHub_Releases-1E293B?style=for-the-badge&logo=github&logoColor=white" height="52" alt="All Releases" />
  </a>
</p>

<details open>
<summary><strong>📱 Installation Options & Direct Links</strong></summary>

| Package Variant | Architecture / Platform | Direct Download Link | Details |
| :--- | :--- | :--- | :--- |
| **Production Release APK** | Universal (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | [**📥 Download `app-release.apk`**](https://github.com/Ismit-06/Weather_GPT/releases/latest/download/app-release.apk) | Optimized, ZipAligned, V2/V3 Signed |
| **Developer Debug APK** | Universal with telemetry diagnostics | [**🛠️ Download `app-debug.apk`**](https://github.com/Ismit-06/Weather_GPT/releases/latest/download/app-debug.apk) | Debug signature enabled |
| **Cloud Microservice** | REST & WebSockets | [**🌐 Open Live Render Backend**](https://weather-gpt-ymze.onrender.com) | FastAPI + Uvicorn Production Host |

> [!TIP]
> **Android Installation Quick Guide:**
> 1. Download **`app-release.apk`** using the button above directly onto your phone.
> 2. Open the downloaded file from your browser or file manager.
> 3. If prompted with *"Install unknown apps"*, toggle **Allow from this source**.
> 4. Tap **Install** and launch WeatherGPT!

</details>

---

## ⚡ Architectural Overview

WeatherGPT bridges high-throughput edge sensors, computer vision, and national hydrological telemetry with low-latency LLM orchestration:

```mermaid
graph TD
    subgraph Client [Android Jetpack Compose Client]
        UI[Glassmorphic Dynamic Theme]
        Cam[CameraX & Sky Vision Pipeline]
        Audio[Voice Engine & Audio Recorder]
        Nav[Navigation & Offline SQLite Cache]
    end

    subgraph Network [API & Telemetry Gateway]
        FastAPI[FastAPI Production Gateway - Render Cloud]
        WS[Bi-directional WebSocket Audio Stream]
    end

    subgraph Intelligence [Atmospheric & LLM Intelligence]
        Gemma[Google Gemma 4 Vision / LoRA-002]
        Sarvam[Sarvam Indic Speech ASR & TTS]
        Fusion[Sky-Weather Optical & Sensor Fusion]
        CWC[CWC Telemetry - 124 All-India Reservoirs]
        Doppler[Doppler Radar & Global Met Services]
    end

    UI -->|HTTPS / REST| FastAPI
    Cam -->|Multipart Image| FastAPI
    Audio -->|Audio Stream| WS
    FastAPI --> Gemma
    FastAPI --> Sarvam
    FastAPI --> Fusion
    FastAPI --> CWC
    FastAPI --> Doppler
```

---

## ✨ Cutting-Edge Capabilities

### 👁️ 1. Sky-AI: Visual Cloud Perception & Confounder Rejection
- **Gemma 4 Fine-Tuned Model**: Utilizes **Google Gemma 4 26B A4B Instruct** specialized via LoRA (**`SKY-LORA-002`**) for atmospheric cloud dynamics.
- **Confounder Rejection Engine**: Actively eliminates non-sky indoor false positives (e.g. blue bedsheets, curtains, tinted glass, ceiling textures, computer displays) via spectral analysis and edge-density heuristics.
- **Multimodal Atmospheric Fusion**: Fuses live CameraX RGB telemetry, ambient brightness, and orientation metadata with meteorological radar models to classify cloud altitude, density, and immediate rain risk.

### 🌊 2. All-India Dam Intelligence & Flood Early Warning (CWC)
- **124 Monitored Dams Across 31 States & UTs**: Full telemetry synchronization from the Central Water Commission (CWC).
- **Comprehensive River Basins**: Krishna, Godavari, Ganga, Indus, Narmada, Cauvery, Mahanadi, Brahmaputra, Tapi, Sabarmati, and more.
- **Real-Time Hydrological Metrics**: Full Reservoir Level (FRL in meters), current water level, live storage vs total capacity in BCM (Billion Cubic Meters), and storage percentage.
- **Automated Risk Stratification**:
  - `CRITICAL SPILL RISK` ($\ge 90\%$)
  - `HIGH FLOOD WATCH` ($\ge 85\%$)
  - `MODERATE STORAGE` ($\ge 70\%$)
  - `NORMAL CAPACITY` ($< 70\%$)
- **Interactive Exploration Suite**: Real-time multi-attribute search (dam name, district, river basin, state) with regional filter chips (`Southern`, `Northern`, `Western`, `Eastern`, `Central`).

### 🎙️ 3. Multilingual Indic Voice Assistant (Sarvam AI)
- **12+ Indian Languages Supported**: Hindi, English, Hinglish, Odia, Telugu, Tamil, Kannada, Bengali, Marathi, Gujarati, Malayalam, and Punjabi.
- **Streaming WebSocket Speech**: Bidirectional voice pipeline for natural audio query transcription and low-latency neural speech synthesis.
- **Sub-50ms FastPath Engine**: Offline-resilient rule-based meteorological engine answering high-frequency weather and outdoor activity queries instantly.

### 🗺️ 4. Turbo Vector Map & Doppler Radar Layers
- **OSMDroid Turbo Engine**: 12 parallel raster rendering threads, 250 in-memory tile cache with 60-day persistent disk cache.
- **Dynamic Layering**: Switch effortlessly between live precipitation radar, thermal contour maps, wind vector particle fields, and satellite imagery.

---

## 🛠️ Technology Stack

| Domain | Frameworks, Tools & Protocols |
| :--- | :--- |
| **Mobile Client** | Kotlin 2.0, Jetpack Compose, Material 3, Coroutines, StateFlow, Navigation Compose, CameraX |
| **Mapping & Radar** | OSMDroid, MapTiler Vector Tiles, OpenWeather Doppler Layers, Leaflet / TileStream |
| **Vision & AI** | Google Gemma 4 (26B A4B LoRA), PyTorch, Hugging Face Transformers, OpenCV, Scikit-Learn |
| **Speech & Multilingual** | Sarvam AI Speech API, Android AudioRecord, Neural TTS, OkHttp WebSocket Streaming |
| **Cloud & Backend** | Python 3.12, FastAPI, Uvicorn, SQLAlchemy, PostgreSQL, Render Cloud Platform |
| **Data & Storage** | Retrofit 2, OkHttp 4, Gson, Room Database, EncryptedSharedPreferences |

---

## 📦 Project Layout

```text
WeatherGPT/
├── Weather_GPT/
│   ├── android/                  # Android Studio project (Kotlin + Jetpack Compose)
│   │   ├── app/src/main/java/    # Screens, ViewModels, AI fusion, Audio engine
│   │   └── app/build.gradle.kts  # Gradle dependencies & signing configuration
│   ├── app/                      # FastAPI cloud service
│   │   ├── routers/              # Dams, Alerts, Visual Cloud, Chat, Hazards, Speech
│   │   ├── services/             # Fusion engines, CWC bulletin parser, Met clients
│   │   └── models/               # SQLAlchemy database schemas
│   ├── sky-ai/                   # LoRA fine-tuning scripts & inference microservice
│   └── requirements.txt          # Python cloud service dependencies
├── assets/                       # Brand assets, high-res logos & audio samples
└── README.md                     # Documentation & direct download gateway
```

---

## 💻 Local Development Setup

<details>
<summary><strong>📱 Android Studio Setup</strong></summary>

1. **Clone repository:**
   ```bash
   git clone https://github.com/Ismit-06/Weather_GPT.git
   cd Weather_GPT/Weather_GPT/android
   ```
2. **Setup SDK & MapTiler Key:**
   Create a `local.properties` file in `Weather_GPT/android/`:
   ```properties
   sdk.dir=C:\Users\<USER>\AppData\Local\Android\Sdk
   MAPTILER_API_KEY=your_maptiler_api_key
   ```
3. **Build APKs:**
   ```bash
   # Build Release APK
   ./gradlew assembleRelease

   # Or compile and install directly to connected device
   ./gradlew installDebug
   ```

</details>

<details>
<summary><strong>🐍 Python Backend & Cloud Service</strong></summary>

1. **Navigate to the backend directory and configure venv:**
   ```bash
   cd Weather_GPT
   python -m venv venv
   source venv/bin/activate  # On Windows: .\venv\Scripts\activate
   pip install -r requirements.txt
   ```
2. **Run the development server:**
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```
3. **Interactive Documentation:**
   Navigate to `http://localhost:8000/docs` to test all OpenAPI endpoints.

</details>

---

## 🤝 Contributing

Contributions, feedback, and pull requests are welcomed!
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AtmosphericFeature`)
3. Commit your Changes (`git commit -m 'Add AtmosphericFeature'`)
4. Push to the Branch (`git push origin feature/AtmosphericFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.

<p align="center">
  <sub>Built with ❤️ by the WeatherGPT Team • Advancing Autonomous Atmospheric Science</sub>
</p>
