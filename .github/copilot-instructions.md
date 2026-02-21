# Copilot Instructions - Music Emotion Analysis Backend

## Project Overview
This is a Flask-based backend for a music emotion analysis application. It integrates:
- **Audio Processing**: Downloads music from YouTube (yt-dlp) and uploads local files
- **ML/AI**: TensorFlow/Keras CNN model predicting music valence/arousal by time segments
- **Database**: PostgreSQL storing users, songs, sessions, emotions, and relationships
- **Auth**: Firebase Admin SDK for user authentication and verification
- **Client**: Android app (intentoAppDatosMusica) communicating via REST API

## Architecture & Data Flow

### Core Components
1. **`app_musica.py`** - Main Flask application (~1000 lines)
   - Single-file monolithic backend (no blueprints/modules yet)
   - All routes handle user management, audio processing, and database synchronization

2. **Audio Processing Pipeline**
   - YouTube → yt-dlp downloads best audio → librosa processes mel-spectrograms
   - Local uploads sent as multipart/form-data with base64 encoding
   - Audio split into sections using automatic librosa beat detection

3. **ML Model**
   - Keras model: `best_model_intento8_tfrecord_menoscapas_traindesde20.keras`
   - Custom layers: AttentionBlock, CCC loss metric for valence/arousal prediction
   - Input: mel-spectrogram (128 mels, 87 frames fixed)
   - Output: [valence, arousal] per audio section (-1.0 to +1.0 range)

4. **Database Schema** (PostgreSQL, port 5433)
   - Users ↔ Firebase UID mapping
   - Songs: stored locally or as YouTube link
   - Sections: time-indexed song segments with predicted emotions
   - Sessions: user listening sessions grouping songs + emotional words

### Key Data Flow
```
User (Android) → Firebase Auth Token → Flask API
   ↓
/verificar_o_guardar_usuario: Create/find user by Firebase UID
   ↓
/subir_audio or /subir_enlace: Extract audio, predict emotions per section
   ↓
/guardar_cancion_definitiva: Persist to PostgreSQL with section metadata
   ↓
/guardar_sesion: Create listening session linking songs + emotion words
```

## Critical Developer Patterns

### Database Connection Pattern
```python
def get_db_connection():
    return psycopg2.connect(
        host="localhost", database="intento_aplicacionmovil_android",
        user="admin_fernando", password="191VP90957QX2685", port="5433"
    )
# CRITICAL: Always close cursor AND connection in finally blocks
cursor.close()
conn.close()
```

### Audio Prediction Pattern
- **Per-section predictions** (not whole-file) - use `predecir_audio_por_secciones(audio_bytes)`
- Sections auto-detected using `librosa.segment.agglomerative()` on MFCC
- Each section returns: `{tiempo_inicio, tiempo_final, valence, arousal}`
- Keras model expects: (1, 128, 87, 1) shaped mel-spectrograms

### Authentication Pattern
- All protected routes require `Authorization: Bearer {idToken}` header
- Verify with `auth.verify_id_token(token)` → returns `uid`
- Match `uid` to `usuario.firebase_uid` in database

### Response Format
- Success: `jsonify({...}), 200`
- Errors: `jsonify({"error": "message"}), 4xx`
- When returning section predictions: include list with all metadata, not just scores

## Key Workflows

### 1. Add Music to Library (Local File)
1. POST `/api/subir_audio`: multipart with file + usuario_id
2. Predicts emotions per section (temporary ID, doesn't save yet)
3. User edits sections in frontend
4. POST `/api/guardar_cancion_definitiva`: persists to DB

### 2. Add Music from YouTube
1. POST `/api/subir_enlace`: YouTube URL + usuario_id
2. yt-dlp downloads audio → predicts sections
3. Returns title, author extracted via yt-dlp metadata
4. User confirms → `/api/guardar_cancion_definitiva` saves

### 3. Sync Canciones (Mobile Sync)
- POST `/api/sincronizar_canciones`: Bidirectional mobile ↔ server sync
- Compares `fecha_ultima_edicion` timestamps
- Handles insert/update/delete based on local vs server state

### 4. Create Session
- POST `/api/guardar_sesion`: Links songs + emotional words from NRC lexicon
- Joins with `nrc_vad` and `nrc_emotion` tables for word emotion mapping

## Critical Integration Points

### PostgreSQL Tables Referenced
- `usuario`: firebase_uid, nombre, imagen, last_name_change
- `cancion`: user_id, nombre, enlace, archivo (binary), estado_publicado
- `seccion`: cancion_id, tiempo_inicio, tiempo_final, promedio_valence, arousal_real
- `emocion_seleccionada`: seccion_id, palabra
- `sesion`, `sesion_cancion`, `sesion_palabra`: session grouping
- `nrc_vad`, `nrc_emotion`: external lexicon for word-emotion mapping

### External Dependencies
- **Firebase**: `CREDENCIAL_PATH` hardcoded as JSON path in file
- **FFmpeg**: Required for audio conversion, searched in standard Windows paths
- **yt-dlp**: Cookies file at `www.youtube.com_cookies (6).txt` for restricted videos

## Project-Specific Conventions

### Time Formats
- Database: `time` type (HH:MM:SS)
- JSON responses: `"MM:SS.mmm"` format (e.g., "01:56.810")
- Conversion helper: `convertir_a_time()` handles multiple input formats

### Temporary IDs
- Local sections use negative Unix timestamps as temp IDs: `temp_id = int(time.time() * -1)`
- Server-assigned IDs returned in sync response for client mapping

### Error Handling
- Heavy use of try/except with `traceback.print_exc()` for debugging
- Rollback on exception: `conn.rollback()`
- Console logging: `print()` statements throughout (no structured logging yet)

## Testing & Running

### Start Server
```powershell
cd flask-backend
python app_musica.py
# Runs on http://localhost:5000
```

### Key Endpoints to Test
- `GET /`: Health check
- `POST /api/token`: Receive Firebase token
- `POST /api/verify_token`: Verify token
- `POST /api/verificar_o_guardar_usuario`: Create/get user
- `POST /api/subir_audio`: Upload and predict
- `POST /api/guardar_cancion_definitiva`: Save to DB
- `POST /api/guardar_sesion`: Create session

### Debug Model Loading
- Keras model loaded once at startup
- If missing, endpoint returns 0-valued predictions as fallback
- Check console for: `✅ Modelo de Keras cargado exitosamente` or `❌ Error cargando modelo`

## Notes for AI Agents

1. **Credentials are hardcoded** - Never commit changes exposing passwords/API keys
2. **Single-file monolith** - Refactoring into modules recommended for scalability
3. **No async/background tasks** - Long audio processing blocks requests
4. **Timestamp precision** - Microsecond precision in datetime fields; serialize to ISO format
5. **Audio byte handling** - Use `psycopg2.Binary()` for binary data, base64 for JSON transfer
6. **Section auto-detection** - Not always precise; users must edit in frontend before save
7. **Firebase initialization** - Happens at import time; restart required to reload credentials
