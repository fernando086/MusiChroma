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
