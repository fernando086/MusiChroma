# MusiChroma

**Status:** Usable Prototype / Work in Progress

## Overview
MusiChroma is an innovative application designed for music therapy and educational interventions. It enables facilitators to manage psychological and developmental sessions, seamlessly process audio directly from YouTube, and analyze the emotional impact of music using advanced machine learning algorithms (Valence and Arousal models).

## Tech Stack

### 📱 Frontend (Mobile App - Android Studio)
* **Language & UI:** Java, XML, Jetpack Compose (enabled), Material Design, ConstraintLayout, ViewPager2, Car UI Library.
* **Authentication:** Firebase Auth, Google Play Services Auth.
* **Network & Data:** Retrofit (with Gson converter) for API requests, PostgreSQL JDBC Driver for direct DB operations.
* **Media & ML:** Picasso (image loading), TensorFlow Lite (on-device inference).

### 🐍 Backend (Python / Flask - `app_musica.py`)
* **Framework:** Flask (with Flask-Compress for optimized responses).
* **Database & Auth:** `psycopg2` (PostgreSQL connection), `firebase_admin` (token verification).
* **Audio Processing:** `yt-dlp` & FFmpeg (for downloading and extracting YouTube audio), `pydub`.
* **Machine Learning Inference:** TensorFlow, Keras, Librosa, NumPy, Joblib.

### 🧠 Machine Learning & Data Science (`pipeline_russell.ipynb`)
* **Core Libraries:** TensorFlow 2.10.0 (CNN + GRU architectures), Keras, NumPy, Pandas.
* **Audio Feature Extraction:** Librosa (extracting 102 features including Mel spectrograms, MFCC, Chroma CQT, and Spectral data).
* **Data Pipeline:** TFRecords (for efficient, compressed storage of audio windows and continuous valence/arousal labels).


This project was developed relying heavily on a **Vibe Coding** methodology, utilizing powerful Large Language Models (LLMs), specifically cutting-edge AI coding agents, especially Gemini (in Antigravity IDE), ChatGPT and Deepseek, to accelerate both architectural design and complex logic implementation. This AI-augmented workflow was instrumental in establishing the full-stack infrastructure, rapidly prototyping neural network integrations for audio analysis, and ensuring an agile, highly iterative development process from conception to a usable prototype.

## 🚀 Getting Started
If you want to run this prototype locally, follow these steps to set up the backend, tunneling, and the Android client.

### 1. Prerequisites
- **Python 3.x**
- **Android Studio**
- **PostgreSQL**
- **FFmpeg** (added to your system's PATH)
- **ngrok** (for tunneling the local backend to the internet)

### 2. Backend Setup
1. Clone this repository to your local machine.
2. Navigate to the `flask-backend` directory and install the required dependencies (e.g., `pip install -r requirements.txt` or install manually).
3. Create a PostgreSQL database named `intento_aplicacionmovil_android` and configure the user/password to match the `app_musica.py` connection string.
4. Place your Firebase Admin SDK JSON credentials file in the root directory and update the path in the Flask app.
5. Run the server:
   ```bash
   python app_musica.py
   ```

### 3. Exposing the API (ngrok)
To allow the Android emulator or physical device to communicate with your local Flask server over the internet, run ngrok on the port your Flask app uses (typically 5000):
```bash
ngrok http 5000
```
*Note the generated forwarding URL (e.g., `https://<random-id>.ngrok.app`).*

### 4. Frontend Setup (Android)
1. Open the project folder `intentoAppDatosMusica` in Android Studio.
2. Update the base URL in your Retrofit client or network configuration to point to the newly generated **ngrok URL**.
3. Sync Gradle and run the app on an Android Emulator (API 28+) or a physical device.
