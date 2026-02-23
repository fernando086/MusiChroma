import subprocess, base64
from flask import Flask, Response, request, jsonify
import flask
import json
from flask_compress import Compress
import psycopg2
from psycopg2 import Binary
from psycopg2.extras import RealDictCursor
import firebase_admin
from firebase_admin import credentials, auth
from datetime import datetime, time, timedelta
from pydub import AudioSegment

import tensorflow as tf
import numpy as np
import librosa
import io
import tempfile
import os
from flask import request, jsonify
import librosa
import numpy as np
import io
import joblib
import keras

import re
from dotenv import load_dotenv

load_dotenv()

active_downloads = {}  # Diccionario para rastrear descargas en curso

app = Flask(__name__)
Compress(app)
app.config['MAX_CONTENT_LENGTH'] = 100 * 1024 * 1024  # 100 MB

# Inicializar Firebase Admin SDK
cred = credentials.Certificate("C:/Users/thepe/AndroidStudioProjects/intentoAppDatosMusica/tesis-musica-20242-firebase-adminsdk-fl1hr-1aac8bd291.json")
firebase_admin.initialize_app(cred)

@app.route('/')
def index():
    return "Servidor Flask funcionando correctamente."

@app.route('/api/token', methods=['POST'])
def receive_token():
    # Aquí recibimos el token del cliente
    data = request.json
    if 'token' in data:
        token = data['token']
        # Aquí puedes visualizar el token en la consola del servidor
        print(f"Token recibido: {token}")
        # Procesar el token como desees
        return jsonify({"message": "Token recibido exitosamente"}), 200
    else:
        return jsonify({"error": "Token no encontrado"}), 400

@app.route('/api/verify_token', methods=['POST'])
def verify_token():
    token = request.json.get('token')

    try:
        # Verificar el token recibido
        decoded_token = auth.verify_id_token(token)
        uid = decoded_token['uid']
        print("Token válido.")
        return jsonify({"message": "Token válido", "user_id": uid}), 200
    except Exception as e:
        print(f"Error de verificación del token: {str(e)}")
        return jsonify({"message": "Token no válido", "error": str(e)}), 401

@app.route('/protected', methods=['POST'])
def protected_route():
    id_token = request.headers.get('Authorization')
    user_id = verify_token(id_token)
    if user_id:
        return jsonify({"message": "Token válido, acceso permitido"})
    else:
        return jsonify({"message": "Token inválido o ausente"}), 401

def get_db_connection():
    conn = psycopg2.connect(
        host=os.getenv("DB_HOST", "localhost"),
        database=os.getenv("DB_NAME", "intento_aplicacionmovil_android"),
        user=os.getenv("DB_USER", "admin_fernando"),
        password=os.getenv("DB_PASSWORD", "191VP90957QX2685"),
        port=os.getenv("DB_PORT", "5433")
    )
    return conn

@app.route('/usuarios')
def get_usuarios():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute('SELECT * FROM usuario')
    usuario = cursor.fetchall()
    cursor.close()
    conn.close()
    return jsonify(usuario)

# Función para agregar un usuario a PostgreSQL si no existe
def agregar_usuario_si_no_existe(nombre, uid):
    try:
        # Conexión a PostgreSQL
        conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "localhost"),
            database=os.getenv("DB_NAME", "intento_aplicacionmovil_android"),  # Cambia el nombre de tu base de datos
            user=os.getenv("DB_USER", "admin_fernando"),  # Cambia por tu usuario de PostgreSQL
            password=os.getenv("DB_PASSWORD", "191VP90957QX2685")  # Cambia por tu contraseña de PostgreSQL
        )
        cursor = conn.cursor()

        # Verificar si el usuario ya existe por el UID de Firebase
        cursor.execute("SELECT id FROM Usuario WHERE firebase_uid = %s", (uid,))
        resultado = cursor.fetchone()

        if resultado is None:
            # Si no existe, insertar un nuevo usuario
            cursor.execute(
                "INSERT INTO Usuario (nombre, firebase_uid) VALUES (%s, %s) RETURNING id",
                (nombre, uid)
            )
            usuario_id = cursor.fetchone()[0]
            conn.commit()
            print(f"Usuario nuevo agregado con ID {usuario_id}")
            return jsonify({"message": "Usuario nuevo agregado", "user_id": usuario_id}), 200
        else:
            print("El usuario ya existe en la base de datos: ", resultado)
            return jsonify({"message": "Usuario ya existente", "user_id": resultado[0]}), 200        

    except Exception as e:
        print(f"Error al agregar o verificar usuario: {e}")
    cursor.close()
    conn.close()

@app.route('/api/get_user', methods=['POST'])
def get_user():
    token = request.json.get('token')

    try:
        # Verificar el token
        decoded_token = auth.verify_id_token(token)
        uid = decoded_token['uid']

        # Conectar a PostgreSQL
        conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "localhost"),
            database=os.getenv("DB_NAME", "intento_aplicacionmovil_android"),  # Cambia el nombre de tu base de datos
            user=os.getenv("DB_USER", "admin_fernando"),  # Cambia por tu usuario de PostgreSQL
            password=os.getenv("DB_PASSWORD", "191VP90957QX2685")  # Cambia por tu contraseña de PostgreSQL
        )
        cursor = conn.cursor()

        # Buscar al usuario por su ID de Firebase
        cursor.execute("SELECT nombre, imagen FROM Usuario WHERE id_firebase = %s", (uid,))
        user = cursor.fetchone()

        if user:
            nombre, imagen = user
            return jsonify({
                "nombre": nombre,
                "imagen": imagen.decode('utf-8') if imagen else None
            }), 200
        else:
            return jsonify({"message": "Usuario no encontrado"}), 404
    except Exception as e:
        return jsonify({"message": "Error al obtener usuario", "error": str(e)}), 400
    finally:
        cursor.close()
        conn.close()

@app.route('/api/verificar_o_guardar_usuario', methods=['POST'])
def verificar_o_guardar_usuario():
    data = request.get_json()
    print("Datos recibidos:", data) # Recibidos desde Android Studio
    nombre = data.get('nombre')
    imagen = data.get('imagen')  # Aquí estará la URL o el byte array según cómo lo manejes
    firebase_uid = data.get('firebaseUid')

    conn = None
    cursor = None

    print(f"Firebase UID recibido: {firebase_uid}")  # Depuración
    print(f"Nombre recibido: {nombre}")
    print(f"Imagen recibida: {imagen}")

    if not firebase_uid:
        print("Error: Firebase UID no proporcionado.")
        return jsonify({"error": "Firebase UID no proporcionado"}), 400

    try:
        print("estableciendo conexión con BD PostgreSQL")
        conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "localhost"),
            database=os.getenv("DB_NAME", "intento_aplicacionmovil_android"),
            user=os.getenv("DB_USER", "admin_fernando"),
            password=os.getenv("DB_PASSWORD", "191VP90957QX2685"),
            port=os.getenv("DB_PORT", "5433")
        )
        print("el usuario que se conectará a la base de datos ha sido identificado")
        cursor = conn.cursor()
        print("cursor conectado")

        # Verificar si el usuario ya existe en la base de datos
        cursor.execute("SELECT * FROM usuario WHERE firebase_uid = %s", (firebase_uid,))
        print("cursor ejecutado")
        usuario = cursor.fetchone()
        print("buscando si existe el usuario")

        if usuario:
            print("Usuario ya existente: ", usuario[0]) # TODO ENVIAR ESTE NÚMERO HACIA ANDROID STUDIO, O ENVIAR FIREBASE UID DESDE MENUPRINCIPAL HACIA OTRA INTERFAZ
        else:
            # Insertar el nuevo usuario en la base de datos
            print("el usuario no existe, insertando")
            cursor.execute("""
                INSERT INTO Usuario (nombre, imagen, firebase_uid)
                VALUES (%s, %s, %s)
            """, (nombre, imagen, firebase_uid))
            conn.commit()
            print(f"Nuevo usuario insertado en PostgreSQL: {nombre}")

        return jsonify({"message": "Operación exitosa"}), 200

    except Exception as e:
        print(f"Error al agregar o verificar usuario: {str(e)}")
        return jsonify({"error": f"Error al agregar o verificar usuario: {str(e)}"}), 500

    finally:
        # Asegurarse de cerrar el cursor y la conexión
        if cursor:
            cursor.close()
        if conn:
            conn.close()

@app.route('/api/obtener_datos_usuario', methods=['POST'])
def obtener_datos_usuario():
    data = request.get_json()
    firebase_uid = data.get('firebaseUid')

    conn = None
    cursor = None

    if not firebase_uid:
        return jsonify({"error": "Firebase UID no proporcionado"}), 400

    try:
        conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "localhost"),
            database=os.getenv("DB_NAME", "intento_aplicacionmovil_android"),
            user=os.getenv("DB_USER", "admin_fernando"),
            password=os.getenv("DB_PASSWORD", "191VP90957QX2685"),
            port=os.getenv("DB_PORT", "5433")
        )
        cursor = conn.cursor()

        # Obtener los datos del usuario desde la base de datos
        cursor.execute("SELECT id, nombre, imagen, firebase_uid FROM usuario WHERE firebase_uid = %s", (firebase_uid,))
        usuario = cursor.fetchone()

        if usuario:
            user_id, user_name, user_image, user_firebase_uid = usuario
            return jsonify({
                "id": user_id,
                "nombre": user_name,
                "imagen": user_image,
                "firebaseUid": user_firebase_uid
            }), 200
        else:
            return jsonify({"error": "Usuario no encontrado"}), 404

    except Exception as e:
        return jsonify({"error": f"Error al obtener datos del usuario: {str(e)}"}), 500

    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

@app.route('/api/update_username', methods=['PUT'])
def update_username():
    print("consiguiendo data")
    data = request.json
    new_name = data.get('newName')
    user_token = request.headers.get('Authorization')
    print("newName = ", new_name, " Authorization = ", user_token)

    # Remover el prefijo 'Bearer ' si está presente
    if user_token.startswith("Bearer "):
        user_token = user_token.split(" ")[1]

    try:
        # Verificar el token y obtener el firebase_uid
        decoded_token = auth.verify_id_token(user_token)
        firebase_uid = decoded_token['uid']
        print("Firebase UID: ", firebase_uid)
    except Exception as e:
        print("Error al verificar el token: ", str(e))
        return jsonify({'error': 'Token inválido'}), 401

    # Obtener conexión y cursor
    conn = get_db_connection()
    print("conexión a postgresql conseguida")
    cur = conn.cursor()
    print("cursor para postgresql conectado")

    # Busca al usuario en la base de datos por el firebase_uid (que asumo se corresponde con el token)
    cur.execute("SELECT id, last_name_change FROM usuario WHERE firebase_uid = %s", (firebase_uid,))
    print("el select se ha ejecutado")
    user = cur.fetchone()
    print("se ejecutó fetchone")
    print("entrando a if else")

    if user:
        user_id, last_name_change = user

        # Verifica si ha pasado al menos 24 horas desde el último cambio de nombre
        now = datetime.now()
        if last_name_change and now - last_name_change < timedelta(hours=24):
            cur.close()
            conn.close()
            print("El usuario intentó cambiar su nombre antes del plazo de 24 horas.")
            return jsonify({'error': 'You can only change your name once every 24 hours'}), 403

        # Actualiza el nombre y el tiempo de cambio
        cur.execute("UPDATE usuario SET nombre = %s, last_name_change = %s WHERE id = %s",
                    (new_name, now, user_id))
        conn.commit()

        cur.close()
        conn.close()
        return jsonify({'success': True, 'name': new_name}), 200
    else:
        cur.close()
        conn.close()
        return jsonify({'error': 'User not found'}), 404
    
@app.route('/api/obtener_sesiones', methods=['GET'])
def get_sesiones():
    usuario_id = request.args.get('usuario_id')  # <-- Obtener desde la URL

    if not usuario_id:
        return jsonify({"error": "usuario_id es requerido"}), 400

    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)

    query = """
    SELECT
        s.id,
        s.numero_sesion,
        s.nombre,
        s.institucion_educativa,
        s.grado_seccion,
        s.facilitador,
        s.numero_estudiantes,
        s.tipo,
        s.modo,
        s.fecha_hora_inicio,
        s.fecha_hora_final,
        s.inicio,
        s.actividad_central,
        s.cierre,
        s.descripcion_clima,
        s.observaciones,
        s.favorito,
        s.cantidad_estrellas,
        s.color,
        s.dificultades,
        s.recomendaciones,
        s.objetivos_ids, s.objetivos_custom,
        s.tecnicas_ids, s.tecnicas_custom,
        s.materiales_ids, s.materiales_custom,
        s.logros_ids, s.logros_custom,
        s.clima_grupal_ids, s.clima_grupal_custom,
        COALESCE(ARRAY_AGG(DISTINCT sc.cancion_id) FILTER (WHERE sc.cancion_id IS NOT NULL), '{}') AS canciones_ids,
        COALESCE(ARRAY_AGG(DISTINCT sp.term) FILTER (WHERE sp.term IS NOT NULL), '{}') AS palabras
    FROM sesion s
    LEFT JOIN sesion_cancion sc ON s.id = sc.sesion_id
    LEFT JOIN sesion_palabra sp ON s.id = sp.sesion_id
    INNER JOIN usuario u ON s.usuario_id = u.id
    WHERE u.firebase_uid = %s
    GROUP BY s.id
    ORDER BY s.fecha_hora_inicio DESC;
    """

    cur.execute(query, (usuario_id,))
    sesiones = cur.fetchall()

    cur.close()
    conn.close()

    return jsonify({"sesiones": [dict(row) for row in sesiones]})

@app.route('/api/obtener_canciones', methods=['GET'])
def obtener_canciones():
    usuario_id = request.args.get('usuario_id')
    print("obtener_canciones: El valor de usuario_id conseguido es: ", usuario_id)

    if not usuario_id:
        return jsonify({"error": "Falta user_id"}), 400

    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        query = """
            SELECT c.id, c.nombre, c.autor, c.album, c.enlace,
                   c.comentario_general, c.estado_cg_publicado, c.estado_publicado,
                   c.fecha_creacion, c.fecha_ultima_edicion
            FROM cancion c
            INNER JOIN usuario u ON c.usuario_id = u.id
            WHERE u.firebase_uid = %s
        """
        cursor.execute(query, (usuario_id,))
        canciones = cursor.fetchall()

        canciones_list = []
        archivo_contenido = []

        for cancion in canciones:
            cancion_id = cancion[0]

            cursor.execute("""
                SELECT s.id, s.tiempo_inicio, s.tiempo_final, s.fecha_creacion, s.fecha_ultima_edicion,
                       s.nombre, s.comentario_seccion, s.estado_cs_publicado
                FROM seccion s
                WHERE s.cancion_id = %s
                ORDER BY s.tiempo_inicio
            """, (cancion_id,))
            secciones = cursor.fetchall()

            secciones_list = []
            secciones_str_partes = []

            for s in secciones:
                seccion_id = s[0]

                # Emociones
                cursor.execute("SELECT palabra FROM emocion_seleccionada WHERE seccion_id = %s", (seccion_id,))
                emociones = [row[0] for row in cursor.fetchall()]

                # Géneros
                cursor.execute("""
                    SELECT g.id, g.nombre
                    FROM seccion_genero sg
                    JOIN genero g ON sg.genero_id = g.id
                    WHERE sg.seccion_id = %s
                """, (seccion_id,))
                generos = [{"id": row[0], "nombre_genero": row[1]} for row in cursor.fetchall()]

                # Serializar para JSON principal
                tiempo_inicio_str = s[1].strftime("%M:%S.%f")[:-3]
                tiempo_fin_str = s[2].strftime("%M:%S.%f")[:-3]
                f_creacion = s[3].strftime("%Y-%m-%d %H:%M:%S.%f")
                f_ultima_edicion = s[4].strftime("%Y-%m-%d %H:%M:%S.%f")
                secciones_list.append({
                    "id": seccion_id,
                    "inicio": tiempo_inicio_str,
                    "fin": tiempo_fin_str,
                    "s_f_creacion": f_creacion,
                    "s_f_ultima_edicion": f_ultima_edicion,
                    "nombre_seccion": s[5],
                    "comentario": s[6],
                    "publicado": s[7],
                    "emociones": emociones,
                    "generos": generos
                })

                # Serializar para archivo local
                seccion_str = "{}-{}/{}//{}//{}//{}//{}//{}//{}".format(
                    seccion_id,
                    tiempo_inicio_str,
                    tiempo_fin_str,
                    f_creacion,
                    f_ultima_edicion,
                    s[5] or "",
                    s[6] or "",
                    s[7],
                    ",".join(emociones),
                    ",".join([g["nombre_genero"] for g in generos])
                )
                secciones_str_partes.append(seccion_str)

            canciones_list.append({
                "id": cancion_id,
                "nombre": cancion[1],
                "autor": cancion[2],
                "album": cancion[3],
                "enlace": cancion[4],
                "comentario_general": cancion[5],
                "estado_cg_publicado": cancion[6],
                "estado_publicado": cancion[7],
                "f_creacion": cancion[8].strftime("%Y-%m-%d %H:%M:%S.%f"),
                "f_ultima_edicion": cancion[9].strftime("%Y-%m-%d %H:%M:%S.%f"),
                "secciones": secciones_list
            })

            archivo_contenido.append(f"{cancion_id};{cancion[1]};{cancion[2]};{cancion[3]};{cancion[4]};{cancion[5]};{cancion[6]};{cancion[7]};{cancion[8]};{cancion[9]};{'|'.join(secciones_str_partes)}\n")

        cursor.close()
        conn.close()

        return jsonify({
            "canciones": canciones_list,
            "archivo_contenido": archivo_contenido
        }), 200

    except Exception as e:
        import traceback
        print("⚠️ Error en obtener_canciones:")
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/api/get_archivo', methods=['POST'])
def get_archivo():
    try:
        data = request.get_json()
        cancion_id = data.get('cancion_id')

        if not cancion_id:
            return jsonify({"error": "ID de canción no proporcionado"}), 400

        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT archivo FROM cancion WHERE id = %s", (cancion_id,))
        resultado = cursor.fetchone()

        cursor.close()
        conn.close()

        if resultado and resultado[0]:
            archivo_bytes = resultado[0]
            return Response(archivo_bytes, mimetype="audio/mpeg")
        else:
            return jsonify({"error": "Archivo no encontrado"}), 404

    except Exception as e:
        return jsonify({"error": str(e)}), 500

def descargar_audio_yield(enlace):
    """Descarga el audio desde YouTube y transmite directamente al usuario."""
    comando = [
        "yt-dlp", "-f", "bestaudio", "--extract-audio",
        "--audio-format", "mp3", "--output", "-", enlace
    ]

    proceso = subprocess.Popen(comando, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def generar():
        while True:
            chunk = proceso.stdout.read(8192)  # Lee en bloques de 8KB
            if not chunk:
                break
            yield chunk  # Se envía directamente al usuario
    
    return flask.stream_with_context(generar())

@app.route('/api/get_audio', methods=['POST'])
def get_audio():
    enlace = request.json.get('songEnlace')
    return Response(descargar_audio_yield(enlace), mimetype="audio/mpeg")

@app.route('/api/get_secciones', methods=['GET'])
def get_secciones():
    try:
        cancion_id = request.args.get('cancion_id')  # Obtener el ID de la canción desde la solicitud

        if not cancion_id:
            return jsonify({"error": "Se requiere el ID de la canción"}), 400

        conn = get_db_connection()
        cursor = conn.cursor()

        query = """
        SELECT id, tiempo_inicio, tiempo_final, fecha_creacion, fecha_ultima_edicion
        FROM seccion
        WHERE cancion_id = %s
        ORDER BY tiempo_inicio ASC;
        """
        print("get_secciones dice: query listo")
        cursor.execute(query, (cancion_id,))
        secciones = cursor.fetchall()
        print("get_secciones dice: fetch all listo")
        cursor.close()
        conn.close()

        # Convertir los resultados en una lista de diccionarios
        lista_secciones = []
        print("get_secciones dice: lista secciones [] listo")
        for seccion in secciones:
            lista_secciones.append({
                "id": seccion[0],
                #"cancion_id": seccion[1], # No es necesario este campo porque ya está entrando desde menu hacia datosM
                #"usuario_id": seccion[2], # De igual manera con el anterior campo
                "tiempo_inicio": seccion[1].strftime("%M:%S.%f")[:-3],  # 🔹 Convertir a String (ejemplo: "01:56.810")
                "tiempo_final": seccion[2].strftime("%M:%S.%f")[:-3],   # 🔹 Convertir a String (ejemplo: "02:09.850")
                "fecha_creacion": seccion[3].strftime("%Y-%m-%d %H:%M:%S.%f"),
                "fecha_ultima_edicion": seccion[4].strftime("%Y-%m-%d %H:%M:%S.%f")
                #"id_orden_seccion": seccion[3] # Se podría generar automáticamente en el código de Android Studio
            })

        print("get_secciones dice: método correcto")
        print(lista_secciones)
        return jsonify({"secciones": lista_secciones}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500
    
def procesar_audio_desde_enlace(enlace_youtube):
    """
    Procesa audio desde un enlace de YouTube y devuelve valence/arousal por secciones usando Keras
    """
    try:
        import yt_dlp
        
        # 🔹 ENCONTRAR LA RUTA DE FFMPEG
        ffmpeg_path = "C:/ffmpeg/bin/ffmpeg.exe"
        ffprobe_path = "C:/ffmpeg/bin/ffprobe.exe"
        
        # Verificar que los archivos existen
        if not os.path.exists(ffmpeg_path):
            # Intentar rutas alternativas comunes
            alternative_paths = [
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe",
                os.path.join(os.environ.get('PROGRAMFILES', ''), "ffmpeg", "bin", "ffmpeg.exe"),
                os.path.join(os.environ.get('PROGRAMFILES(X86)', ''), "ffmpeg", "bin", "ffmpeg.exe"),
            ]
            
            for path in alternative_paths:
                if os.path.exists(path):
                    ffmpeg_path = path
                    ffprobe_path = path.replace("ffmpeg.exe", "ffprobe.exe")
                    break
            else:
                print("❌ FFmpeg no encontrado. Usando método alternativo...")
                return procesar_audio_sin_ffmpeg(enlace_youtube)
        
        # 🔹 OPCIONES MEJORADAS para yt-dlp
        ydl_opts = {
            'format': 'bestaudio/best',
            'outtmpl': os.path.join(tempfile.gettempdir(), '%(id)s.%(ext)s'),
            'quiet': True,
            'extractor_args': {'youtube': ['player_client=android,web']}, # Bypasses 403 issue
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'wav',
                'preferredquality': '192',
            }],
            'ffmpeg_location': os.path.dirname(ffmpeg_path),
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(enlace_youtube, download=True)
            temp_audio_path = ydl.prepare_filename(info)
            
            # Obtener la ruta del archivo de audio convertido
            base_path = os.path.splitext(temp_audio_path)[0]
            temp_audio_path_wav = base_path + '.wav'
            
            if not os.path.exists(temp_audio_path_wav):
                print(f"⚠️ Archivo WAV no encontrado, usando el original: {temp_audio_path}")
                temp_audio_path_wav = temp_audio_path
        
        # Leer archivo y procesar
        with open(temp_audio_path_wav, 'rb') as f:
            audio_bytes = f.read()
        
        # 🔹 CAMBIAR: Usar predicción por secciones en lugar de completa
        resultados = predecir_audio_por_secciones(audio_bytes)
            
        # Limpiar archivos temporales
        try:
            for path in [temp_audio_path, temp_audio_path_wav]:
                if os.path.exists(path):
                    os.remove(path)
        except Exception as e:
            print(f"⚠️ Error limpiando archivos temporales: {e}")
        
        return resultados  # 🔹 Devolver lista de secciones con predicciones
        
    except Exception as e:
        print(f"❌ Error procesando audio desde enlace: {e}")
        import traceback
        traceback.print_exc()
        return [{"tiempo_inicio": 0.0, "tiempo_final": 10.0, "valence": 0.5, "arousal": 0.5}]
    
def procesar_audio_sin_ffmpeg(enlace_youtube):
    """
    Método alternativo cuando FFmpeg no está disponible
    """
    try:
        import yt_dlp
        
        # 🔹 Descargar solo el audio sin postprocesamiento
        ydl_opts = {
            'format': 'bestaudio[ext=m4a]/bestaudio',
            'outtmpl': os.path.join(tempfile.gettempdir(), '%(id)s.%(ext)s'),
            'extractor_args': {'youtube': ['player_client=android,web']},
            'quiet': True,
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(enlace_youtube, download=True)
            temp_audio_path = ydl.prepare_filename(info)
        
        # Leer archivo y procesar
        with open(temp_audio_path, 'rb') as f:
            audio_bytes = f.read()
        
        # 🔹 CAMBIAR: Usar predicción por secciones
        resultados = predecir_audio_por_secciones(audio_bytes)
                
        # Limpiar
        try:
            if os.path.exists(temp_audio_path):
                os.remove(temp_audio_path)
        except:
            pass
            
        return resultados  # 🔹 Devolver lista de secciones
        
    except Exception as e:
        print(f"❌ Error en método alternativo: {e}")
        return [{"tiempo_inicio": 0.0, "tiempo_final": 10.0, "valence": 0.5, "arousal": 0.5}]
    
@app.route('/api/subir_enlace', methods=['POST'])
def subir_enlace():
    enlace = request.form.get('enlace')
    usuario_id = request.form.get('usuario_id')

    if not enlace or not usuario_id:
        return jsonify({"error": "Faltan datos"}), 400

    try:
        import yt_dlp
        import datetime
        import os

        # 🔹 Ruta absoluta al archivo de cookies
        cookie_path = os.path.join(os.path.dirname(__file__), "www.youtube.com_cookies.txt")

        # 🔹 Verifica que el archivo exista antes de continuar
        if not os.path.exists(cookie_path):
            print("⚠️ Advertencia: No se encontró el archivo de cookies:", cookie_path)
            cookie_file_arg = None
        else:
            print("✅ Archivo de cookies detectado:", cookie_path)
            cookie_file_arg = cookie_path

        # Extraer información usando yt-dlp
        ydl_opts = {
            'quiet': True, # Evitar salida innecesaria
            'skip_download': True, # No descargar el video
            'forcejson': True, # Forzar salida en JSON
            'extract_flat': False, # Extraer información completa
            'extractor_args': {'youtube': ['player_client=android,web']}, # Evitar error 403
            'format': 'bestaudio/best', # Pedir directamente el mejor audio para evitar que busque formatos que requieren firmas complejas
            'ignoreerrors': True, # Continuar incluso si falla en obtener algún formato específico
        }
        
        # Agregamos cookies solo si existen para evitar crasheos si se borran
        if cookie_file_arg:
            ydl_opts['cookiefile'] = cookie_file_arg

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # Añadir process=False es la clave: extrae metadatos (título, autor)
            # sin intentar resolver las firmas de los formatos de video/audio.
            info = ydl.extract_info(enlace, download=False, process=False)
            
        if info is None:
            info = {}
            print("⚠️ yt-dlp devolvió None al extraer metadatos. Se usarán valores desconocidos temporales.")

        nombre = info.get('title', 'Unknown Title')
        print("subir_enlace: titulo de enlace youtube: ", nombre)
        autor = info.get('uploader', 'Unknown Author')
        album = info.get('album', None)
        duracion_segundos = info.get('duration', 0)
        duracion_time = str(datetime.timedelta(seconds=duracion_segundos))  # HH:MM:SS

        # 🔹 CAMBIAR: Obtener predicciones por secciones en lugar de valores únicos
        secciones_predicciones = procesar_audio_desde_enlace(enlace)

        print("ENLACE EXTRAÍDO:", nombre, autor, album, duracion_time)
        print("[Predicciones por Secciones]:")
        
        # Validación extra: si procesar_audio_desde_enlace falla, retorna una predicción default válida
        if not secciones_predicciones or len(secciones_predicciones) == 0:
            print("⚠️ Advertencia: No se obtuvieron predicciones válidas, utilizando valor por defecto.")
            secciones_predicciones = [{"tiempo_inicio": 0.0, "tiempo_final": max(10.0, duracion_segundos), "valence": 0.5, "arousal": 0.5}]
            
        for i, seccion in enumerate(secciones_predicciones):
            print(f"  Sección {i+1}: {seccion.get('tiempo_inicio', 0):.1f}s - {seccion.get('tiempo_final', 10):.1f}s | V={seccion.get('valence', 0.5):.4f}, A={seccion.get('arousal', 0.5):.4f}")

        # 🔹 NO guardar en base de datos - solo retornar metadatos y predicciones
        import time
        temp_id = int(time.time() * -1)  # ID temporal negativo

        return jsonify({
            "mensaje": "Enlace procesado exitosamente",
            "id": temp_id,
            "nombre": nombre,
            "autor": autor,
            "album": album,
            "duracion": duracion_time,
            "secciones": secciones_predicciones,  # 🔹 Devolver las secciones con predicciones
            "temporal": True
        }), 200

    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": f"Error descargando audio (403 Forbidden). Intenta actualizar cookies. Detalle: {str(e)}"}), 500

@app.route('/api/sesion/delete', methods=['POST'])
def eliminar_sesion():
    data = request.get_json()
    sesion_id = data.get('id')

    if not sesion_id:
        return jsonify({"error": "Falta sesion_id"}), 400

    try:
        conn = get_db_connection()
        cur = conn.cursor()

        # Eliminar asociaciones en sesion_cancion y sesion_palabra
        cur.execute("DELETE FROM sesion_cancion WHERE sesion_id = %s", (sesion_id,))
        cur.execute("DELETE FROM sesion_palabra WHERE sesion_id = %s", (sesion_id,))

        # Eliminar la sesión
        cur.execute("DELETE FROM sesion WHERE id = %s", (sesion_id,))

        conn.commit()
        cur.close()
        conn.close()

        return jsonify({"mensaje": "Sesión eliminada exitosamente"}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/cancion/delete', methods=['POST'])
def eliminar_cancion():
    data = request.get_json()
    cancion_id = data.get('id')

    if not cancion_id:
        return jsonify({"error": "Falta cancion_id"}), 400

    try:
        conn = get_db_connection()
        cur = conn.cursor()

        # Eliminar secciones asociadas
        cur.execute("DELETE FROM seccion WHERE cancion_id = %s", (cancion_id,))

        # Eliminar la canción
        cur.execute("DELETE FROM cancion WHERE id = %s", (cancion_id,))

        conn.commit()
        cur.close()
        conn.close()

        return jsonify({"mensaje": "Canción eliminada exitosamente"}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/subir_audio', methods=['POST'])
def subir_audio():
    if 'archivo' not in request.files:
        return jsonify({"error": "No se envió ningún archivo"}), 400

    archivo = request.files['archivo']
    usuario_id = request.form.get('usuario_id')
    nombre = request.form.get('nombre')
    nombre_sin_extension = re.sub(r'\.(mp3|wav|ogg)$', '', nombre, flags=re.IGNORECASE)
    duracion = request.form.get('tiempo_fin')  # ejemplo: "00:06:48"
    print("subir_audio: duracion = ", duracion)

    duracion_obj = validar_formato_tiempo(duracion)
    
    if not duracion_obj:
        print('DURACIÓN CON FORMATO INVÁLIDO')
        return jsonify({"error": "Formato de duración inválido. Debe ser HH:MM:SS"}), 400
    
    duracion_str = duracion_obj.strftime('%H:%M:%S.%f')[:-3]  # "00:01:58.299"

    if not usuario_id:
        return jsonify({"error": "No se envió usuario_id"}), 400

    if archivo.filename == '':
        return jsonify({"error": "Nombre de archivo inválido"}), 400

    if not allowed_file(archivo.filename):
        return jsonify({"error": "Formato de archivo no permitido"}), 400

    if not archivo.mimetype.startswith('audio/'):
        return jsonify({"error": "Solo se permiten archivos de audio"}), 400
    
    contenido_bytes = archivo.read()

    # 🔹 Reemplazar el procesamiento de Torch por Keras:
    # 🔹 CAMBIAR: Obtener predicciones por secciones
    secciones_predicciones = predecir_audio_por_secciones(contenido_bytes)

    print(f"[Predicción por Secciones]")
    for i, seccion in enumerate(secciones_predicciones):
        print(f"  Sección {i+1}: {seccion['tiempo_inicio']:.1f}s - {seccion['tiempo_final']:.1f}s | V={seccion['valence']:.4f}, A={seccion['arousal']:.4f}")

    # 🔹 NO guardar en PostgreSQL - solo retornar predicciones
    import time
    temp_id = int(time.time() * -1)  # ID temporal negativo

    return jsonify({
        "mensaje": "Archivo procesado exitosamente",
        "id": temp_id,
        "nombre": nombre_sin_extension,
        "duracion": duracion_str,
        "secciones": secciones_predicciones,  # 🔹 Devolver las secciones con predicciones
        "temporal": True
    }), 200

@app.route('/api/guardar_sesion', methods=['POST'])
def guardar_sesion():
    data = request.get_json()

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        # 1️⃣ Insertar en sesion
        cur.execute("""
            INSERT INTO sesion (
                nombre, numero_sesion, institucion_educativa, grado_seccion, facilitador, numero_estudiantes,
                tipo, modo, fecha_hora_inicio, fecha_hora_final, 
                inicio, actividad_central, cierre, descripcion_clima, observaciones,
                favorito, cantidad_estrellas, color, usuario_id, dificultades, recomendaciones,
                objetivos_ids, objetivos_custom, tecnicas_ids, tecnicas_custom,
                materiales_ids, materiales_custom, logros_ids, logros_custom,
                clima_grupal_ids, clima_grupal_custom
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            RETURNING id;
        """, (
            data.get('nombre', ''),
            data.get('numero_sesion', 1),
            data.get('institucion_educativa', ''),
            data.get('grado_seccion', ''),
            data.get('facilitador', ''),
            data.get('numero_estudiantes', 1),
            data.get('tipo', False),
            data.get('modo', False),
            data.get('fecha_hora_inicio'),
            data.get('fecha_hora_final'),
            data.get('inicio', ''),
            data.get('actividad_central', ''),
            data.get('cierre', ''),
            data.get('descripcion_clima', ''),
            data.get('observaciones', ''),
            data.get('favorito', False),
            data.get('cantidad_estrellas', 0),
            data.get('color', 0),
            data['usuario_id'],
            data.get('dificultades', ''),
            data.get('recomendaciones', ''),
            data.get('objetivos_ids', []), data.get('objetivos_custom', ''),
            data.get('tecnicas_ids', []), data.get('tecnicas_custom', ''),
            data.get('materiales_ids', []), data.get('materiales_custom', ''),
            data.get('logros_ids', []), data.get('logros_custom', ''),
            data.get('clima_grupal_ids', []), data.get('clima_grupal_custom', '')
        ))

        sesion_id = cur.fetchone()[0]

        # 2️⃣ Insertar canciones seleccionadas
        canciones = data.get('canciones_ids', [])
        for cid in canciones:
            cur.execute("INSERT INTO sesion_cancion (sesion_id, cancion_id) VALUES (%s, %s)", (sesion_id, cid))

        # 3️⃣ Insertar palabras emocionales (todas las emociones asociadas a cada palabra)
        palabras = data.get('palabras', [])
        for term in palabras:
            # Inserta múltiples emociones si la palabra tiene varias en el lexicón
            cur.execute("""
                INSERT INTO sesion_palabra (sesion_id, term, emotion, nivel_arousal)
                SELECT %s, v.term, em.name, 
                    CASE 
                        WHEN v.arousal >= 0.33 THEN 'alto'
                        WHEN v.arousal <= -0.33 THEN 'bajo'
                        ELSE 'medio'
                    END
                FROM nrc_vad v
                JOIN nrc_emotion e ON e.term = v.term
                JOIN emotion em ON e.emotion_id = em.emotion_id
                WHERE v.term = %s
                AND e.association = 1  -- ✅ Filtrar solo emociones reales
            """, (sesion_id, term))

            # Si no insertó nada, fallback genérico
            if cur.rowcount == 0:
                cur.execute("""
                    INSERT INTO sesion_palabra (sesion_id, term, emotion, nivel_arousal)
                    VALUES (%s, %s, %s, %s)
                """, (sesion_id, term, 'unknown', 'unknown'))

        conn.commit()

        return jsonify({"status": "ok", "id": sesion_id})

    except Exception as e:
        import traceback
        conn.rollback()
        traceback.print_exc()  # 👈 Esto mostrará el error detallado en la consola
        return jsonify({"status": "error", "msg": str(e)}), 500

    finally:
        cur.close()
        conn.close()

@app.route('/api/actualizar_sesion', methods=['PUT'])
def actualizar_sesion():
    data = request.get_json()
    sesion_id = data.get("sesion_id")

    if not sesion_id:
        return jsonify({"status": "error", "msg": "sesion_id faltante"}), 400

    conn = get_db_connection()
    cur = conn.cursor()

    print("Actualizando sesión: ", sesion_id)

    try:
        # 1️⃣ Actualizar tabla sesion
        cur.execute("""
            UPDATE sesion
            SET nombre=%s, numero_sesion=%s, institucion_educativa=%s, grado_seccion=%s, facilitador=%s, numero_estudiantes=%s,
                tipo=%s, modo=%s, fecha_hora_inicio=%s, fecha_hora_final=%s,
                inicio=%s, actividad_central=%s, cierre=%s, descripcion_clima=%s, observaciones=%s,
                favorito=%s, cantidad_estrellas=%s, color=%s, dificultades=%s, recomendaciones=%s,
                objetivos_ids=%s, objetivos_custom=%s, tecnicas_ids=%s, tecnicas_custom=%s,
                materiales_ids=%s, materiales_custom=%s, logros_ids=%s, logros_custom=%s,
                clima_grupal_ids=%s, clima_grupal_custom=%s
            WHERE id=%s
        """, (
            data.get('nombre', ''),
            data.get('numero_sesion', 1),
            data.get('institucion_educativa', ''),
            data.get('grado_seccion', ''),
            data.get('facilitador', ''),
            data.get('numero_estudiantes', 1),
            data.get('tipo', False),
            data.get('modo', False),
            data.get('fecha_hora_inicio'),
            data.get('fecha_hora_final'),
            data.get('inicio', ''),
            data.get('actividad_central', ''),
            data.get('cierre', ''),
            data.get('descripcion_clima', ''),
            data.get('observaciones', ''),
            data.get('favorito', False),
            data.get('cantidad_estrellas', 0),
            data.get('color', 0),
            data.get('dificultades', ''),
            data.get('recomendaciones', ''),
            data.get('objetivos_ids', []), data.get('objetivos_custom', ''),
            data.get('tecnicas_ids', []), data.get('tecnicas_custom', ''),
            data.get('materiales_ids', []), data.get('materiales_custom', ''),
            data.get('logros_ids', []), data.get('logros_custom', ''),
            data.get('clima_grupal_ids', []), data.get('clima_grupal_custom', ''),
            sesion_id            
        ))
        print("Sesión actualizada en tabla sesion.")

        # 2️⃣ Borrar canciones y palabras anteriores
        cur.execute("DELETE FROM sesion_cancion WHERE sesion_id = %s", (sesion_id,))
        cur.execute("DELETE FROM sesion_palabra WHERE sesion_id = %s", (sesion_id,))

        # 3️⃣ Insertar canciones nuevas
        for cid in data.get('canciones_ids', []):
            cur.execute("INSERT INTO sesion_cancion (sesion_id, cancion_id) VALUES (%s, %s)", (sesion_id, cid))

        # 4️⃣ Insertar palabras nuevas (mismo código que ya tenías)
        for term in data.get('palabras', []):
            cur.execute("""
                INSERT INTO sesion_palabra (sesion_id, term, emotion, nivel_arousal)
                SELECT %s, v.term, em.name, 
                    CASE 
                        WHEN v.arousal >= 0.33 THEN 'alto'
                        WHEN v.arousal <= -0.33 THEN 'bajo'
                        ELSE 'medio'
                    END
                FROM nrc_vad v
                JOIN nrc_emotion e ON e.term = v.term
                JOIN emotion em ON e.emotion_id = em.emotion_id
                WHERE v.term = %s
                AND e.association = 1
            """, (sesion_id, term))

            if cur.rowcount == 0:
                cur.execute("""
                    INSERT INTO sesion_palabra (sesion_id, term, emotion, nivel_arousal)
                    VALUES (%s, %s, %s, %s)
                """, (sesion_id, term, 'unknown', 'unknown'))

        conn.commit()
        return jsonify({"status": "ok", "id": sesion_id})

    except Exception as e:
        import traceback
        traceback.print_exc()
        if conn:
            conn.rollback()
        return jsonify({"status": "error", "msg": str(e)}), 500

@app.route('/api/sesion/color', methods=['PUT'])
def actualizar_color_sesion():
    data = request.get_json()
    sesion_id = data.get("id")          # <- ahora viene del body
    nuevo_color = data.get("color")

    if nuevo_color is None:
        return jsonify({"status": "error", "msg": "color faltante"}), 400

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        cur.execute("""
            UPDATE sesion
            SET color = %s
            WHERE id = %s
        """, (nuevo_color, sesion_id))

        conn.commit()
        return jsonify({"status": "ok", "id": sesion_id, "color": nuevo_color})

    except Exception as e:
        conn.rollback()
        return jsonify({"status": "error", "msg": str(e)}), 500

ALLOWED_EXTENSIONS = {'mp3', 'wav', 'ogg'}

@app.route('/api/guardar_cancion_definitiva', methods=['POST'])
def guardar_cancion_definitiva():
    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        if request.content_type.startswith('multipart/form-data'):
            # 🟢 Caso archivo local
            archivo = request.files.get('archivo')
            usuario_id = request.form.get('usuario_id')
            nombre = request.form.get('nombre')
            autor = request.form.get('autor')
            album = request.form.get('album')
            tipo_origen = request.form.get('tipo_origen')
            duracion = request.form.get('duracion')
            secciones_json = request.form.get('secciones')
            secciones = json.loads(secciones_json) if secciones_json else []

            archivo_bytes = archivo.read() if archivo else None

            cursor.execute("""
                INSERT INTO cancion (nombre, autor, album, enlace, archivo, usuario_id, estado_publicado)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                RETURNING id;
            """, (
                nombre, autor, album,
                archivo.filename if archivo else None,
                psycopg2.Binary(archivo_bytes) if archivo_bytes else None,
                int(usuario_id), True
            ))

        else:
            # 🟢 Caso JSON (YouTube)
            data = request.get_json()
            usuario_id = data.get("usuario_id")
            secciones = data.get("secciones", [])

            cursor.execute("""
                INSERT INTO cancion (nombre, autor, album, enlace, usuario_id, estado_publicado)
                VALUES (%s, %s, %s, %s, %s, %s)
                RETURNING id;
            """, (
                data.get("nombre"), data.get("autor"),
                data.get("album"), data.get("enlace"),
                int(usuario_id), True
            ))

        cancion_id = cursor.fetchone()[0]

        # 🔹 Insertar secciones y emociones
        for seccion in secciones:
            tiempo_inicio = convertir_a_time(seccion["tiempo_inicio"])
            tiempo_final = convertir_a_time(seccion["tiempo_final"])

            cursor.execute("""
                INSERT INTO seccion (nombre, cancion_id, usuario_id, tiempo_inicio, tiempo_final,
                                    promedio_valence, promedio_arousal,
                                    valence_real, arousal_real,
                                    comentario_seccion, estado_cs_publicado)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                RETURNING id;
            """, (
                seccion.get("nombre", "Sección"),
                cancion_id,
                int(usuario_id),
                tiempo_inicio,
                tiempo_final,
                seccion.get("valence"),        # valence de la palabra (visual)
                seccion.get("arousal"),        # arousal de la palabra (visual)
                seccion.get("valenceReal"),    # valence predicho real
                seccion.get("arousalReal"),    # arousal predicho real
                seccion.get("comentario"),
                True
            ))

            seccion_id = cursor.fetchone()[0]
            for emocion in seccion.get("emociones", []):
                if not emocion or not isinstance(emocion, dict):
                    continue  # Ignora valores None o inválidos

                palabra = emocion.get("palabra")
                if palabra:
                    cursor.execute("""
                        INSERT INTO emocion_seleccionada (seccion_id, palabra)
                        VALUES (%s, %s);
                    """, (seccion_id, palabra))

        conn.commit()
        cursor.close()
        conn.close()

        return jsonify({"mensaje": "Canción guardada correctamente", "id": cancion_id}), 200

    except Exception as e:
        import traceback
        traceback.print_exc()
        if conn:
            conn.rollback()
        return jsonify({"status": "error", "msg": str(e)}), 500
    
def convertir_a_time(valor):
    # Si ya viene con milisegundos "MM:SS.mmm", hay que convertirlo a "HH:MM:SS"
    if isinstance(valor, str):
        try:
            # Formato como "00:09.985" → convertirlo
            if "." in valor:
                minutos, resto = valor.split(":")
                segundos, _ = resto.split(".")  # ignoramos ms
                return f"00:{minutos.zfill(2)}:{segundos.zfill(2)}"  # → "00:00:09"
            # Si es formato "MM:SS"
            if len(valor) == 5 and ":" in valor:
                minutos, segundos = valor.split(":")
                return f"00:{minutos.zfill(2)}:{segundos.zfill(2)}"
            # Si es formato "HH:MM:SS"
            return valor
        except:
            return "00:00:00"  # fallback
    return "00:00:00"

def validar_formato_tiempo(tiempo_str):
    # Aceptar formatos como "00:06:48" o "00:06:48.299"
    match = re.match(r'^(\d{2}):(\d{2}):(\d{2})(\.\d{1,6})?$', tiempo_str)
    if not match:
        return None

    h, m, s = int(match.group(1)), int(match.group(2)), int(match.group(3))
    microsegundos = int(float(match.group(4) or 0) * 1_000_000)

    try:
        return time(hour=h, minute=m, second=s, microsecond=microsegundos)
    except:
        return None

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

# Definir objetos custom para cargar el modelo
def ccc_score_tf(y_true, y_pred, eps=1e-8):
    """
    Calcula el Concordance Correlation Coefficient (CCC).
    
    CCC mide acuerdo entre predicción y realidad (mejor que MSE para regresión ordinal).
    Rango: [-1, 1] donde 1 = acuerdo perfecto, 0 = sin acuerdo, -1 = desacuerdo perfecto.
    
    Fórmula: CCC = 2 * cov(y_true, y_pred) / (var(y_true) + var(y_pred) + (mean_true - mean_pred)^2)
    """
    # Asegurar que ambos sean float32 (para evitar problemas con mixed precision)
    y_true = tf.cast(y_true, tf.float32)
    y_pred = tf.cast(y_pred, tf.float32)

    mean_true = tf.reduce_mean(y_true, axis=0)
    mean_pred = tf.reduce_mean(y_pred, axis=0)
    var_true = tf.reduce_mean(tf.square(y_true - mean_true), axis=0)
    var_pred = tf.reduce_mean(tf.square(y_pred - mean_pred), axis=0)
    cov = tf.reduce_mean((y_true - mean_true) * (y_pred - mean_pred), axis=0)

    ccc = (2.0 * cov) / (var_true + var_pred + tf.square(mean_true - mean_pred) + eps)
    return ccc

@tf.function
def ccc_metric(y_true, y_pred):
    """
    Métrica CCC promediada: si hay múltiples dimensiones (valence y arousal),
    retorna el promedio de CCC para ambas.
    """
    c = ccc_score_tf(y_true, y_pred)
    return tf.reduce_mean(c)

@tf.function
def ccc_loss(y_true, y_pred):
    """
    Loss basado en CCC: 1 - CCC
    Esto convierte maximización de CCC a minimización de loss.
    """
    c = ccc_score_tf(y_true, y_pred)
    return 1.0 - tf.reduce_mean(c)

def combined_loss(alpha=0.5):
    """
    Loss combinado: alpha * MSE + (1-alpha) * CCC_loss
    
    - MSE proporciona estabilidad (penaliza errores grandes)
    - CCC refina la correlación (lo importante para emoción)
    - alpha=0.5: balance entre ambos
    """
    def loss(y_true, y_pred):
        y_true = tf.cast(y_true, tf.float32)
        y_pred = tf.cast(y_pred, tf.float32)
        
        # MSE: error cuadrático medio (penaliza desviaciones)
        mse = tf.reduce_mean(tf.square(y_true - y_pred))
        
        # CCC: correlación de concordancia (para cada dimensión: valence y arousal)
        ccc_val = 1.0 - ccc_valence(y_true, y_pred)
        ccc_aro = 1.0 - ccc_arousal(y_true, y_pred)
        avg_ccc_loss = (ccc_val + ccc_aro) / 2.0
        
        # Combinar: 50% MSE + 50% CCC
        return alpha * mse + (1.0 - alpha) * avg_ccc_loss
    return loss

@tf.function
def ccc_valence(y_true, y_pred):
    """CCC para dimensión 0 (valence solamente)"""
    return ccc_score_tf(y_true[:, 0], y_pred[:, 0])

@tf.function
def ccc_arousal(y_true, y_pred):
    """CCC para dimensión 1 (arousal solamente)"""
    return ccc_score_tf(y_true[:, 1], y_pred[:, 1])

class AttentionBlock(tf.keras.layers.Layer):
    def __init__(self, **kwargs):
        super(AttentionBlock, self).__init__(**kwargs)
        self.score_dense = tf.keras.layers.Dense(1, activation='tanh')
        self.softmax = tf.keras.layers.Softmax(axis=1)

    def call(self, inputs):
        scores = self.score_dense(inputs)
        weights = self.softmax(scores)
        context = tf.reduce_sum(inputs * weights, axis=1)
        return context
    
    def get_config(self):
        return super(AttentionBlock, self).get_config()

# Definir la ruta base del script de Flask
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# Usar el modelo de MEJOR rendimiento (Época 32)
MODEL_FILE_NAME = "best_mer_model.keras"
SCALER_FILE_NAME = "scaler_per_block_102.pkl"

MODEL_PATH = os.path.join(BASE_DIR, MODEL_FILE_NAME)
SCALER_PATH = os.path.join(BASE_DIR, SCALER_FILE_NAME)

# Cargar Modelo (Asegúrate que las custom_objects sean correctas)
try:
    # Agregamos la ruta de debug para verificar
    print(f"DEBUG: Cargando modelo desde: {MODEL_PATH}")
    model_keras = tf.keras.models.load_model(
        MODEL_PATH,
        custom_objects={
            "AttentionBlock": AttentionBlock,
            "ccc_metric": ccc_metric,
            "ccc_valence": ccc_valence,
            "ccc_arousal": ccc_arousal,
            "ccc_loss": ccc_loss,
            "combined_loss": combined_loss
        },
        compile=False
    )
    print(f"✅ Modelo {MODEL_FILE_NAME} cargado exitosamente")
except Exception as e:
    print(f"❌ Error cargando modelo: {e}")
    model_keras = None

# Cargar Scaler
try:
    scaler_info = joblib.load(SCALER_PATH)
    print(f"✅ Scaler cargado: {SCALER_PATH}")
except Exception as e:
    print(f"❌ Error cargando scaler: {e}")
    scaler_info = None

# Definición de Bloques (Debe coincidir con el entrenamiento)
FEATURE_BLOCKS_FIXED = [
    ("Mel", 64), ("MFCC", 13), ("Chroma_CQT", 12), ("Spectral_Centroid", 1),
    ("Spectral_Bandwidth", 1), ("Spectral_Contrast", 7), ("Spectral_Rolloff", 1),
    ("Spectral_Flatness", 1), ("RMS", 1), ("Zero_Crossing", 1),
]

# ====== Función para preprocesar audio ======
# ====== FUNCIONES DE PREPROCESAMIENTO PARA KERAS ======

def extract_features_102(y, sr=22050, hop_length=512):
    """Extrae los 102 features exactos que espera el modelo"""
    if y is None or len(y) == 0: return None
    
    features_list = []
    # 1. Mel (64)
    S = librosa.feature.melspectrogram(y=y, sr=sr, n_mels=64, hop_length=hop_length)
    S_db = librosa.power_to_db(S, ref=np.max)
    features_list.append(S_db)
    # 2. MFCC (13)
    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13, hop_length=hop_length)
    features_list.append(mfcc)
    # 3. Chroma (12)
    chroma = librosa.feature.chroma_cqt(y=y, sr=sr, hop_length=hop_length)
    features_list.append(chroma)
    # 4-10. Spectral
    features_list.append(librosa.feature.spectral_centroid(y=y, sr=sr, hop_length=hop_length))
    features_list.append(librosa.feature.spectral_bandwidth(y=y, sr=sr, hop_length=hop_length))
    features_list.append(librosa.feature.spectral_contrast(y=y, sr=sr, hop_length=hop_length))
    features_list.append(librosa.feature.spectral_rolloff(y=y, sr=sr, hop_length=hop_length))
    features_list.append(librosa.feature.spectral_flatness(y=y, hop_length=hop_length))
    features_list.append(librosa.feature.rms(y=y, hop_length=hop_length))
    features_list.append(librosa.feature.zero_crossing_rate(y, hop_length=hop_length))
    
    # Concatenar: (102, n_frames)
    return np.vstack(features_list)

def normalize_features(X, scaler_info):
    """Aplica normalización (x-mean)/std por bloque"""
    if X is None or scaler_info is None: return X
    X_norm = X.copy().astype(np.float32)
    block_start = 0
    
    for block_name, block_count in FEATURE_BLOCKS_FIXED:
        if block_name in scaler_info:
            mean = scaler_info[block_name]["mean"]
            std = scaler_info[block_name]["std"]
            std_safe = np.where(std > 0, std, 1.0)
            
            block_data = X_norm[block_start:block_start+block_count, :]
            for i in range(block_count):
                if i < len(mean):
                    block_data[i, :] = (block_data[i, :] - mean[i]) / std_safe[i]
        block_start += block_count
    return X_norm

def predecir_audio_por_secciones(audio_bytes):
    if model_keras is None or scaler_info is None:
        print("❌ Modelo o Scaler no cargados.")
        return []

    try:
        # Cargar audio completo
        y_full, sr = librosa.load(io.BytesIO(audio_bytes), sr=22050)
        duracion_total = librosa.get_duration(y=y_full, sr=sr)
        
        # SEGMENTACIÓN INTELIGENTE (Simplificada para velocidad)
        # Dividimos en segmentos de 10 segundos (o lo que detecte librosa)
        # Para mantener consistencia con tu lógica anterior, usamos librosa.segment
        
        # 1. Features rápidos para segmentación
        S = np.abs(librosa.stft(y_full))
        onset_env = librosa.onset.onset_strength(y=y_full, sr=sr)
        tempo, beats = librosa.beat.beat_track(onset_envelope=onset_env, sr=sr)
        
        # Intentar segmentación automática, si falla usar fija
        try:
            mfcc = librosa.feature.mfcc(y=y_full, sr=sr)
            bound_frames = librosa.segment.agglomerative(mfcc, k=10) # 10 secciones aprox
            bounds = librosa.frames_to_time(bound_frames, sr=sr)
            bounds = np.sort(np.unique(np.concatenate(([0, duracion_total], bounds))))
        except:
            # Fallback: cada 15 segundos
            bounds = np.arange(0, duracion_total, 15.0)
            if bounds[-1] != duracion_total:
                bounds = np.append(bounds, duracion_total)

        resultados = []
        
        # Procesar cada sección
        for i in range(len(bounds)-1):
            start_time = bounds[i]
            end_time = bounds[i+1]
            
            # Extraer centro de la sección para análisis (2 segundos)
            # Tu modelo fue entrenado con ventanas de 2s.
            # Tomamos una ventana de 2s en la mitad de la sección para representarla.
            center_time = (start_time + end_time) / 2
            
            # Cargar/Recortar segmento de 2s
            start_sample = int((center_time - 1.0) * sr)
            end_sample = int((center_time + 1.0) * sr)
            
            # Padding si es necesario
            if start_sample < 0: start_sample = 0
            if end_sample > len(y_full): end_sample = len(y_full)
            
            y_window = y_full[start_sample:end_sample]
            
            # Asegurar longitud exacta (2s = 44100 samples)
            target_len = int(2.0 * sr)
            if len(y_window) < target_len:
                y_window = np.pad(y_window, (0, target_len - len(y_window)))
            else:
                y_window = y_window[:target_len]

            # --- PIPELINE DE INFERENCIA ---
            # 1. Extraer 102 Features
            # --- PIPELINE DE INFERENCIA ---
            # 1. Extraer 102 Features
            # Esto devuelve (102, N_FRAMES), donde N puede ser 86, 87, etc.
            X = extract_features_102(y_window, sr=sr) 
            
            # --- CORRECCIÓN DE DIMENSIÓN (CRÍTICO) ---
            # El modelo espera 86 frames exactos en la dimensión de tiempo.
            # X tiene forma (features=102, time=N)
            
            TARGET_FRAMES = 86
            current_frames = X.shape[1]
            
            if current_frames > TARGET_FRAMES:
                # Si sobra, recortamos el final
                X = X[:, :TARGET_FRAMES]
            elif current_frames < TARGET_FRAMES:
                # Si falta, rellenamos con ceros al final
                pad_width = TARGET_FRAMES - current_frames
                X = np.pad(X, ((0,0), (0,pad_width)), mode='constant')
            
            # Ahora X tiene forma (102, 86) garantizada
            
            # 2. Normalizar (Tu función normalize_features ya maneja la forma correcta)
            X_norm = normalize_features(X, scaler_info)
            
            # 3. Transponer para Keras -> (86, 102)
            X_final = X_norm.T 
            
            # 4. Batch dimension -> (1, 86, 102)
            X_batch = np.expand_dims(X_final, axis=0)
            
            # 5. Predecir
            pred = model_keras.predict(X_batch, verbose=0)[0] # [valence, arousal]
            
            resultados.append({
                "tiempo_inicio": float(start_time),
                "tiempo_final": float(end_time),
                "valence": float(pred[0]),
                "arousal": float(pred[1])
            })
            
        return resultados

    except Exception as e:
        print(f"❌ Error en predicción: {e}")
        import traceback
        traceback.print_exc()
        return []

@app.route('/api/actualizar_cancion', methods=['POST'])
def actualizar_cancion():
    try:
        data = request.get_json()
        song_id = data.get('song_id')
        nombre = data.get('nombre')
        autor = data.get('autor')
        album = data.get('album')
        enlace = data.get('enlace')
        comentario = data.get('comentario_general')
        estado_cg = data.get('estado_cg_publicado')
        estado_cancion = data.get('estado_publicado')
        usuario_id = data.get('usuario_id')      # ← añade este campo en el JSON
        secciones = data.get("secciones", [])

        print(data)

        conn = get_db_connection()
        cur = conn.cursor()

        # ---------- INTENTAR UPDATE DE LA CANCIÓN ----------
        cur.execute("""
            UPDATE cancion
            SET nombre = %s,
                autor = %s,
                album = %s,
                enlace = %s,
                comentario_general = %s,
                estado_cg_publicado = %s,
                estado_publicado = %s,                    
                fecha_ultima_edicion = now()
            WHERE id = %s AND usuario_id = %s
        """, (nombre, autor, album, enlace, comentario, estado_cg, estado_cancion, song_id, usuario_id))

        # ---------- SI LA CANCIÓN NO EXISTE, INSERT ----------
        if cur.rowcount == 0:
            cur.execute("""
                INSERT INTO cancion (
                    usuario_id, nombre, autor, album, enlace, comentario_general,
                    estado_cg_publicado, estado_publicado,
                    fecha_creacion, fecha_ultima_edicion
                )
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s, now(), now())
                RETURNING id, fecha_creacion, fecha_ultima_edicion
            """, (usuario_id, nombre, autor, album, enlace,
                  comentario, estado_cg, estado_cancion))
            row = cur.fetchone()
            new_song_id = row[0]

            # ─── NUEVO ▸ insertar TODAS las secciones recibidas ─────────────
            for sec in secciones:
                t_ini = sec.get("tiempo_inicio", "00:00:00")
                t_fin = sec.get("tiempo_final",  "00:00:00")
                nombre_sec = sec.get("nombre_seccion")
                comentario_sec = sec.get("comentario_seccion")
                publicado = sec.get("estado_cs_publicado", False)
                emociones = sec.get("emociones", [])
                generos = sec.get("generos", [])

                cur.execute("""
                    INSERT INTO seccion (
                        nombre, cancion_id, usuario_id,
                        tiempo_inicio, tiempo_final, comentario_seccion,
                        estado_cs_publicado
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    RETURNING id
                """, (nombre_sec, new_song_id, usuario_id, t_ini, t_fin, comentario_sec,
                      publicado))
                sec_id = cur.fetchone()[0]
                sec["id"] = sec_id

                # EMOCIONES
                for emocion in emociones:
                    cur.execute("""
                        INSERT INTO emocion_seleccionada (seccion_id, palabra)
                        VALUES (%s, %s)
                    """, (sec_id, emocion))

                # GÉNEROS
                for genero_id in generos:
                    cur.execute("""
                        INSERT INTO seccion_genero (seccion_id, genero_id)
                        VALUES (%s, %s)
                    """, (sec_id, genero_id))
            # ────────────────────────────────────────────────────────────────

            conn.commit()
            return jsonify({
                "status":               "inserted_as_new",
                "id_real":              new_song_id,
                "fecha_creacion":       row[1].isoformat(),
                "fecha_ultima_edicion": row[2].isoformat(),
                "secciones":            secciones       # opcional
            }), 200
        
        else:
            # ---------- UPDATE EXISTENTE - SECCIONES TAMBIÉN ----------
            if secciones:
                # 1. Obtener IDs existentes
                cur.execute("""
                    SELECT id FROM seccion 
                    WHERE cancion_id = %s AND usuario_id = %s
                """, (song_id, usuario_id))
                ids_existentes = {r[0] for r in cur.fetchall()}

                nuevos_ids = {s.get("id", -1) for s in secciones}
                ids_a_eliminar = ids_existentes - nuevos_ids

                # 2. Eliminar secciones eliminadas y sus emociones/géneros
                for sec_id in ids_a_eliminar:
                    cur.execute("DELETE FROM emocion_seleccionada WHERE seccion_id = %s", (sec_id,))
                    cur.execute("DELETE FROM seccion_genero WHERE seccion_id = %s", (sec_id,))
                    cur.execute("DELETE FROM seccion WHERE id = %s", (sec_id,))

                # 3. Insertar o actualizar secciones nuevas
                for sec in secciones:
                    sec_id = sec.get("id", -1)
                    t_ini = sec.get("tiempo_inicio")
                    t_fin = sec.get("tiempo_final")
                    nombre_sec = sec.get("nombre_seccion")
                    comentario_sec = sec.get("comentario_seccion")
                    publicado = sec.get("estado_cs_publicado", False)
                    emociones = sec.get("emociones", [])
                    generos = sec.get("generos", [])

                    if sec_id in ids_existentes:
                        cur.execute("""
                            UPDATE seccion SET
                                tiempo_inicio = %s,
                                tiempo_final = %s,
                                nombre = %s,
                                comentario_seccion = %s,
                                estado_cs_publicado = %s,
                                fecha_ultima_edicion = now()
                            WHERE id = %s AND cancion_id = %s AND usuario_id = %s
                        """, (t_ini, t_fin, nombre_sec, comentario_sec, publicado, sec_id, song_id, usuario_id))

                        # Borrar emociones y géneros viejos
                        cur.execute("DELETE FROM emocion_seleccionada WHERE seccion_id = %s", (sec_id,))
                        cur.execute("DELETE FROM seccion_genero WHERE seccion_id = %s", (sec_id,))

                        # Insertar emociones nuevas
                        for emocion in emociones:
                            cur.execute("""
                                INSERT INTO emocion_seleccionada (seccion_id, palabra)
                                VALUES (%s, %s)
                            """, (sec_id, emocion))

                        # Insertar géneros nuevos
                        for genero_id in generos:
                            cur.execute("""
                                INSERT INTO seccion_genero (seccion_id, genero_id)
                                VALUES (%s, %s)
                            """, (sec_id, genero_id))
                    else:
                        cur.execute("""
                            INSERT INTO seccion (
                                nombre, cancion_id, usuario_id,
                                tiempo_inicio, tiempo_final, comentario_seccion,
                                estado_cs_publicado
                            )
                            VALUES (%s, %s, %s, %s, %s, %s, %s)
                            RETURNING id
                        """, (nombre_sec, song_id, usuario_id, t_ini, t_fin, comentario_sec, publicado))
                        sec_id = cur.fetchone()[0]
                        sec["id"] = sec_id

                        for emocion in emociones:
                            cur.execute("""
                                INSERT INTO emocion_seleccionada (seccion_id, palabra)
                                VALUES (%s, %s)
                            """, (sec_id, emocion))
                        for genero_id in generos:
                            cur.execute("""
                                INSERT INTO seccion_genero (seccion_id, genero_id)
                                VALUES (%s, %s)
                            """, (sec_id, genero_id))

            # 4. Obtener fecha actualizada desde base de datos (por trigger)
            cur.execute("""
                SELECT fecha_ultima_edicion
                FROM cancion
                WHERE id = %s
            """, (song_id,))
            result = cur.fetchone()
            fecha_ultima_edicion = result[0].isoformat() if result else None

            conn.commit()
            return jsonify({
                "status": "updated",
                "message": "Canción y secciones actualizadas",
                "fecha_ultima_edicion": fecha_ultima_edicion
            }), 200

    except Exception as e:
        import traceback
        traceback.print_exc()
        if 'conn' in locals():
            conn.rollback()
        return jsonify({"status": "error", "message": str(e)}), 500

    finally:
        if 'cur' in locals(): cur.close()
        if 'conn' in locals(): conn.close()
    
@app.route('/api/actualizar_secciones', methods=['POST'])
def actualizar_secciones():
    data = request.get_json()
    cancion_id = data.get("cancion_id")
    usuario_id = data.get("usuario_id")
    nuevas_secciones = data.get("secciones", [])

    if not cancion_id or not usuario_id or not nuevas_secciones:
        return jsonify({"error": "Datos incompletos"}), 400

    try:
        # ✅ Conectar antes de cualquier cursor.execute()
        conn = get_db_connection()
        cursor = conn.cursor()

        # 0. Verificar existencia de la canción
        cursor.execute("SELECT 1 FROM cancion WHERE id = %s AND usuario_id = %s",
                    (cancion_id, usuario_id))
        if cursor.fetchone() is None:
            return jsonify({
                "status":  "song_not_found",
                "message": "La canción aún no existe en el servidor"
            }), 404

        # 1. Obtener las secciones existentes
        cursor.execute("""
            SELECT id FROM seccion 
            WHERE cancion_id = %s AND usuario_id = %s
        """, (cancion_id, usuario_id))
        secciones_existentes = cursor.fetchall()
        ids_existentes_en_bd = {s[0] for s in secciones_existentes}

        # 2. Calcular los IDs que deben eliminarse
        nuevos_ids_recibidos = {s.get("id", -1) for s in nuevas_secciones}
        ids_a_eliminar = ids_existentes_en_bd - nuevos_ids_recibidos

        # 3. Eliminar primero las secciones que ya no existen
        for sec_id in ids_a_eliminar:
            cursor.execute("""
                DELETE FROM seccion 
                WHERE id = %s AND cancion_id = %s AND usuario_id = %s
            """, (sec_id, cancion_id, usuario_id))

        # 4. Luego actualizar o insertar secciones
        for seccion in nuevas_secciones:
            sec_id = seccion.get("id", -1)
            tiempo_inicio = seccion.get("tiempo_inicio")
            tiempo_final = seccion.get("tiempo_final")

            if sec_id in ids_existentes_en_bd:
                cursor.execute("""
                    UPDATE seccion SET 
                        tiempo_inicio = %s,
                        tiempo_final = %s
                    WHERE id = %s AND cancion_id = %s AND usuario_id = %s
                """, (tiempo_inicio, tiempo_final, sec_id, cancion_id, usuario_id))
            else:
                cursor.execute("""
                    INSERT INTO seccion (cancion_id, usuario_id, tiempo_inicio, tiempo_final, estado_cs_publicado)
                    VALUES (%s, %s, %s, %s, false)
                """, (cancion_id, usuario_id, tiempo_inicio, tiempo_final))

        conn.commit()

        # 5. Obtener TODAS las secciones actualizadas con sus fechas y datos asociados
        cursor.execute("""
            SELECT 
                id, tiempo_inicio, tiempo_final, fecha_creacion, fecha_ultima_edicion,
                nombre, comentario_seccion, estado_cs_publicado
            FROM seccion
            WHERE cancion_id = %s AND usuario_id = %s
            ORDER BY tiempo_inicio
        """, (cancion_id, usuario_id))
        secciones_actualizadas = cursor.fetchall()

        resultado = [serialize_seccion(fila) for fila in secciones_actualizadas]

        return jsonify({
            "status": "ok",
            "mensaje": "Secciones sincronizadas correctamente",
            "new_ids": resultado
        }), 200

    except Exception as e:
        print("Error al actualizar secciones:", str(e))
        return jsonify({"error": "Error interno"}), 500

    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()

def serialize_seccion(seccion):
    seccion_id = seccion[0]

    # Obtener emociones
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT palabra FROM emocion_seleccionada WHERE seccion_id = %s
    """, (seccion_id,))
    emociones = [row[0] for row in cursor.fetchall()]

    # ✅ Obtener géneros con nombre
    cursor.execute("""
        SELECT g.id, g.nombre 
        FROM seccion_genero sg
        JOIN genero g ON sg.genero_id = g.id
        WHERE sg.seccion_id = %s
    """, (seccion_id,))
    generos = [{"id": row[0], "nombre_genero": row[1]} for row in cursor.fetchall()]

    cursor.close()

    return {
        'id': seccion[0],
        'tiempo_inicio': str(seccion[1]),
        'tiempo_final': str(seccion[2]),
        'fecha_creacion': seccion[3].strftime('%Y-%m-%d %H:%M:%S.%f') if seccion[3] else None,
        'fecha_ultima_edicion': seccion[4].strftime('%Y-%m-%d %H:%M:%S.%f') if seccion[4] else None,
        'nombre': seccion[5],
        'comentario': seccion[6],
        'estado_comentario': seccion[7],
        'emociones': emociones,
        'generos': generos  # ← Ya no son enteros planos
    }

#TODO
@app.route('/api/sincronizar_canciones', methods=['POST'])
def sincronizar_canciones():
    data = request.get_json()
    usuario_id = request.args.get("usuario_id", type=int)

    print("[SYNC]  request.content_type =", request.content_type)
    print("[SYNC]  request.content_length =", request.content_length)
    print("[SYNC]  raw first 500 bytes:", request.get_data()[:500])
    data = request.get_json(silent=True)   # silent evita abort 400 interno
    print("[SYNC]  parsed =", type(data), "len=", (len(data) if isinstance(data, list) else "n/a"))

    if not isinstance(data, list):
        return jsonify({"error": "Formato incorrecto, se esperaba una lista"}), 400

    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=RealDictCursor)

    canciones_insertadas = []

    try:
        # Obtener IDs y fechas actuales en la base de datos
        cur.execute("""
            SELECT id, fecha_ultima_edicion FROM cancion
            WHERE usuario_id = %s
        """, (usuario_id,))
        canciones_bd = {row['id']: row['fecha_ultima_edicion'] for row in cur.fetchall()}

        ids_recibidos = set()

        for cancion in data:
            id_temporal = cancion['id']
            fecha_local = datetime.fromisoformat(cancion['fechaUltimaEdicion'])
            ids_recibidos.add(id_temporal)

            # 1) Obtener el binario (o None)
            audio_bin = _decode_audio_b64(cancion.get('archivoBase64'))

            if id_temporal not in canciones_bd:
                # 🔹 Insertar canción SIN ID (para usar serial)
                cur.execute("""
                    INSERT INTO cancion (usuario_id, nombre, autor, album, enlace, comentario_general,
                        estado_cg_publicado, estado_publicado, fecha_creacion, fecha_ultima_edicion, archivo)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    RETURNING id, fecha_creacion, fecha_ultima_edicion
                """, (
                    usuario_id, cancion['nombre'], cancion['autor'], cancion['album'],
                    cancion['enlaceRuta'],
                    cancion['comentario'], cancion['estadoComentario1'], cancion['publicado'],
                    cancion['fechaCreacion'], cancion['fechaUltimaEdicion'],
                    audio_bin
                ))
                row = cur.fetchone()
                nuevo_id_cancion = row['id']

                # Secciones insertadas
                secciones_insertadas = []
                for s in cancion.get('secciones', []):
                    cur.execute("""
                        INSERT INTO seccion (cancion_id, usuario_id, tiempo_inicio, tiempo_final,
                            fecha_creacion, fecha_ultima_edicion)
                        VALUES (%s, %s, %s, %s, %s, %s)
                        RETURNING id, fecha_creacion, fecha_ultima_edicion
                    """, (
                        nuevo_id_cancion, usuario_id,
                        s['tiempoInicio'], s['tiempoFinal'],
                        s['fechaCreacion'], s['fechaUltimaEdicion']
                    ))
                    seccion_info = cur.fetchone()
                    secciones_insertadas.append({
                        "idTemporal": s['id'],  # ID temporal desde Android
                        "idReal": seccion_info['id'],
                        "fechaCreacion": seccion_info['fecha_creacion'].isoformat(),
                        "fechaUltimaEdicion": seccion_info['fecha_ultima_edicion'].isoformat()
                    })
                
                # Guardar en lista de cambios
                canciones_insertadas.append({
                    "idTemporal": id_temporal,
                    "idReal": nuevo_id_cancion,
                    "fechaCreacion": row['fecha_creacion'].isoformat(),
                    "fechaUltimaEdicion": row['fecha_ultima_edicion'].isoformat(),
                    "secciones": secciones_insertadas
                })

            else:
                fecha_bd = canciones_bd[id_temporal]
                if fecha_local > fecha_bd:
                    # 🔄 Actualizar canción existente
                    cur.execute("""
                        UPDATE cancion SET nombre = %s, autor = %s, album = %s, enlace = %s,
                            comentario_general = %s, estado_cg_publicado = %s, estado_publicado = %s,
                            fecha_creacion = %s, fecha_ultima_edicion = %s
                        WHERE id = %s AND usuario_id = %s
                    """, (
                        cancion['nombre'], cancion['autor'], cancion['album'], cancion['enlaceRuta'],
                        cancion['comentario'], cancion['estadoComentario1'], cancion['publicado'],
                        cancion['fechaCreacion'], cancion['fechaUltimaEdicion'],
                        id_temporal, usuario_id
                    ))

                    # 🔄 Reemplazar secciones
                    cur.execute("DELETE FROM seccion WHERE cancion_id = %s AND usuario_id = %s", (id_temporal, usuario_id))
                    for s in cancion.get('secciones', []):
                        cur.execute("""
                            INSERT INTO seccion (cancion_id, usuario_id, tiempo_inicio, tiempo_final,
                                fecha_creacion, fecha_ultima_edicion)
                            VALUES (%s, %s, %s, %s, %s, %s)
                        """, (
                            id_temporal, usuario_id,
                            s['tiempoInicio'], s['tiempoFinal'],
                            s['fechaCreacion'], s['fechaUltimaEdicion']
                        ))

        # 🗑️ Eliminar canciones que ya no están localmente
        ids_bd = set(canciones_bd.keys())
        ids_a_eliminar = ids_bd - ids_recibidos
        for id_a_eliminar in ids_a_eliminar:
            cur.execute("DELETE FROM seccion WHERE cancion_id = %s AND usuario_id = %s", (id_a_eliminar, usuario_id))
            cur.execute("DELETE FROM cancion WHERE id = %s AND usuario_id = %s", (id_a_eliminar, usuario_id))

        conn.commit()
        return jsonify({
            "mensaje": "Sincronización completa",
            "cancionesNuevas": canciones_insertadas
        }), 200

    except Exception as e:
        import traceback
        traceback.print_exc()        # <── añade esto
        conn.rollback()
        return jsonify({"error": str(e)}), 500

    finally:
        cur.close()
        conn.close()

def es_archivo_audio(nombre):
    return nombre.lower().endswith(('.mp3', '.wav', '.ogg'))

def _decode_audio_b64(b64str):
    try:
        return Binary(base64.b64decode(b64str)) if b64str else None
    except Exception:
        return None       # opcional: loggear error

if __name__ == "__main__":
    app.run(debug=True)