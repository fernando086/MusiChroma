package com.example.intentoappdatosmusica;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

//TODO IMPORTANTE:
//PARA PRUEBAS CON SERVIDOR LOCAL: agregar / antes de cada ruta en este archivo
//PARA PRUEBAS CON SERVIDOR REAL (RENDER): quitar / antes de cada ruta en este archivo
public interface ApiService {
    // Este método hace una solicitud HTTP POST a la URL '/api/token'
    @POST("api/token")
    Call<Void> sendToken(@Body TokenRequest tokenRequest);

    @POST("api/verify_token")
    Call<Void> verifyToken(@Body TokenRequest tokenRequest);

    @POST("api/obtener_datos_usuario")
    Call<UserResponse> obtenerUsuario(@Body TokenRequest tokenRequest);

    @POST("api/verificar_o_guardar_usuario")
    Call<UserResponse> verificarOGuardarUsuario(@Body TokenRequest tokenRequest);

    // Cambia @Field por @Body para enviar el objeto UserNameUpdateRequest
    @PUT("api/update_username") // Asumiendo que tienes una ruta para actualizar el nombre
    Call<Void> updateUserName(
            // Token de autenticación
            @Header("Authorization") String token,
            @Body UserNameUpdateRequest request // El objeto que contiene el nuevo nombre
    );

    @GET("api/obtener_canciones")
    Call<JsonObject> obtenerCanciones(@Query("usuario_id") String firebaseUid);

    @Streaming
    @POST("api/get_audio")
    Call<ResponseBody> getAudio(@Body AudioRequest request);

    @POST("api/get_archivo")
    Call<ResponseBody> getArchivo(@Body ArchivoRequest request);

    @Multipart
    @POST("api/subir_audio")
    Call<AudioUploadResponse> subirArchivoAudio(
            @Part MultipartBody.Part archivo,
            @Part("usuario_id") RequestBody usuarioId,
            @Part("nombre") RequestBody nombre,
            @Part("tiempo_fin") RequestBody tiempoFin);

    @Multipart
    @POST("api/subir_enlace")
    Call<EnlaceUploadResponse> subirEnlace(
            @Part("usuario_id") RequestBody usuarioId,
            @Part("enlace") RequestBody enlace);

    @GET("api/get_secciones")
    Call<SeccionesResponse> obtenerSecciones(@Query("cancion_id") int cancionId);

    @POST("api/actualizar_cancion")
    Call<JsonObject> actualizarCancion(@Body JsonObject body);

    @POST("api/actualizar_secciones")
    Call<JsonObject> actualizarSecciones(@Body JsonObject datos);

    @POST("/api/sincronizar_canciones")
    Call<ResponseBody> sincronizarCanciones(
            @Query("usuario_id") int usuarioId,
            @Body List<CancionConSecciones> listaCanciones);

    // 🔹 NUEVO ENDPOINT para guardar canción definitivamente
    // 🔹 Caso enlace (YouTube)
    @POST("api/guardar_cancion_definitiva")
    Call<GuardarCancionResponse> guardarCancionDefinitivaJSON(@Body GuardarCancionRequest request);

    // 🔹 Caso archivo local
    @Multipart
    @POST("api/guardar_cancion_definitiva")
    Call<GuardarCancionResponse> guardarCancionDefinitivaArchivo(
            @Part MultipartBody.Part archivo,
            @Part("usuario_id") RequestBody usuario_id,
            @Part("nombre") RequestBody nombre,
            @Part("autor") RequestBody autor,
            @Part("album") RequestBody album,
            @Part("tipo_origen") RequestBody tipo_origen,
            @Part("duracion") RequestBody duracion,
            @Part("secciones") RequestBody secciones);

    @POST("api/cancion/delete")
    Call<Void> deleteSong(@Body DeleteSongRequest request);

    @GET("api/obtener_sesiones")
    Call<JsonObject> obtenerSesiones(@Query("usuario_id") String firebaseUid);

    @POST("api/guardar_sesion")
    Call<JsonObject> guardarSesion(@Body SesionGuardarRequest request);

    @PUT("api/actualizar_sesion")
    Call<JsonObject> actualizarSesion(@Body SesionGuardarRequest request);

    @PUT("api/sesion/color")
    Call<JsonObject> actualizarColorSesion(@Body JsonObject body);

    @POST("api/sesion/delete")
    Call<Void> deleteSesion(@Body DeleteSesionRequest request);
}

class DeleteSesionRequest {
    private int id;

    public DeleteSesionRequest(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

class DeleteSongRequest {
    private int id;

    public DeleteSongRequest(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

class SesionGuardarRequest {
    int sesion_id;
    int usuario_id;
    String nombre;
    int numero_sesion;
    String institucion_educativa;
    String grado_seccion;
    String facilitador;
    int numero_estudiantes;
    boolean tipo;
    boolean modo;
    String fecha_hora_inicio;
    String fecha_hora_final;

    // Desarrollo
    String inicio;
    String actividad_central;
    String cierre;

    // Observaciones
    String descripcion_clima;
    String observaciones; // Comentarios adicionales

    boolean favorito;
    float cantidad_estrellas;
    int color;

    List<Integer> canciones_ids;
    List<String> palabras;
    String dificultades;
    String recomendaciones;

    // Grid Arrays & Custom
    List<Integer> objetivos_ids;
    String objetivos_custom;

    List<Integer> tecnicas_ids;
    String tecnicas_custom;

    List<Integer> materiales_ids;
    String materiales_custom;

    List<Integer> logros_ids;
    String logros_custom;

    List<Integer> clima_grupal_ids;
    String clima_grupal_custom;

    // Constructor completo
    public SesionGuardarRequest(int sesion_id, int usuario_id, String nombre, int numero_sesion,
            String institucion_educativa, String grado_seccion, String facilitador, int numero_estudiantes,
            boolean tipo, boolean modo, String fecha_hora_inicio, String fecha_hora_final,
            String inicio, String actividad_central, String cierre,
            String descripcion_clima, String observaciones,
            boolean favorito, float cantidad_estrellas, int color,
            List<Integer> canciones_ids, List<String> palabras,
            String dificultades, String recomendaciones,
            List<Integer> objetivos_ids, String objetivos_custom,
            List<Integer> tecnicas_ids, String tecnicas_custom,
            List<Integer> materiales_ids, String materiales_custom,
            List<Integer> logros_ids, String logros_custom,
            List<Integer> clima_grupal_ids, String clima_grupal_custom) {
        this.sesion_id = sesion_id;
        this.usuario_id = usuario_id;
        this.nombre = nombre;
        this.numero_sesion = numero_sesion;
        this.institucion_educativa = institucion_educativa;
        this.grado_seccion = grado_seccion;
        this.facilitador = facilitador;
        this.numero_estudiantes = numero_estudiantes;
        this.tipo = tipo;
        this.modo = modo;
        this.fecha_hora_inicio = fecha_hora_inicio;
        this.fecha_hora_final = fecha_hora_final;
        this.inicio = inicio;
        this.actividad_central = actividad_central;
        this.cierre = cierre;
        this.descripcion_clima = descripcion_clima;
        this.observaciones = observaciones;
        this.favorito = favorito;
        this.cantidad_estrellas = cantidad_estrellas;
        this.color = color;
        this.canciones_ids = canciones_ids;
        this.palabras = palabras;
        this.dificultades = dificultades;
        this.recomendaciones = recomendaciones;

        this.objetivos_ids = objetivos_ids;
        this.objetivos_custom = objetivos_custom;
        this.tecnicas_ids = tecnicas_ids;
        this.tecnicas_custom = tecnicas_custom;
        this.materiales_ids = materiales_ids;
        this.materiales_custom = materiales_custom;
        this.logros_ids = logros_ids;
        this.logros_custom = logros_custom;
        this.clima_grupal_ids = clima_grupal_ids;
        this.clima_grupal_custom = clima_grupal_custom;
    }
}

// 🔹 CLASE de respuesta para guardar definitivamente
class GuardarCancionResponse {
    private int id;
    private String mensaje;
    private String nombre;
    private String autor;
    private String album;
    private String duracion;

    public int getId() {
        return id;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getNombre() {
        return nombre;
    }

    public String getAutor() {
        return autor;
    }

    public String getAlbum() {
        return album;
    }

    public String getDuracion() {
        return duracion;
    }
}

class ArchivoRequest {
    private int cancion_id;

    public ArchivoRequest(int cancion_id) {
        this.cancion_id = cancion_id;
    }

    public int getCancion_id() {
        return cancion_id;
    }

    public void setCancion_id(int cancion_id) {
        this.cancion_id = cancion_id;
    }
}

class EnlaceUploadResponse {
    private int id;

    @SerializedName("id_seccion")
    private int id_seccion;

    private String nombre;
    private String autor;
    private String album;
    private String duracion;
    private boolean temporal;

    @SerializedName("secciones")
    private List<Seccion> secciones;

    @SerializedName("fecha_creacion")
    private String fechaCreacion;

    @SerializedName("fecha_ultima_edicion")
    private String fechaUltimaEdicion;

    @SerializedName("fecha_creacion_seccion")
    private String fechaCreacionSeccion;

    @SerializedName("fecha_ultima_edicion_seccion")
    private String fechaUltimaEdicionSeccion;

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getAutor() {
        return autor;
    }

    public String getAlbum() {
        return album;
    }

    public String getDuracion() {
        return duracion;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public String getFechaUltimaEdicion() {
        return fechaUltimaEdicion;
    }

    public int getId_seccion() {
        return id_seccion;
    }

    public String getFechaCreacionSeccion() {
        return fechaCreacionSeccion;
    }

    public String getFechaUltimaEdicionSeccion() {
        return fechaUltimaEdicionSeccion;
    }

    public List<Seccion> getSecciones() {
        return secciones;
    }
}

class AudioUploadResponse {

    private int id;

    @SerializedName("id_seccion")
    private int id_seccion;

    @SerializedName("fecha_creacion")
    private String fechaCreacion;

    @SerializedName("fecha_ultima_edicion")
    private String fechaUltimaEdicion;

    @SerializedName("fecha_creacion_seccion")
    private String fechaCreacionSeccion;

    @SerializedName("fecha_ultima_edicion_seccion")
    private String fechaUltimaEdicionSeccion;

    // 🔹 Ahora usamos directamente la clase Seccion existente
    @SerializedName("secciones")
    private List<Seccion> secciones;

    private String nombre;
    private String duracion;
    private double valence;
    private double arousal;
    private boolean temporal;

    // --- Getters ---
    public int getId() {
        return id;
    }

    public int getId_seccion() {
        return id_seccion;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public String getFechaUltimaEdicion() {
        return fechaUltimaEdicion;
    }

    public String getFechaCreacionSeccion() {
        return fechaCreacionSeccion;
    }

    public String getFechaUltimaEdicionSeccion() {
        return fechaUltimaEdicionSeccion;
    }

    public List<Seccion> getSecciones() {
        return secciones;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDuracion() {
        return duracion;
    }

    public double getValence() {
        return valence;
    }

    public double getArousal() {
        return arousal;
    }

    public boolean isTemporal() {
        return temporal;
    }

    // --- Setters ---
    public void setSecciones(List<Seccion> secciones) {
        this.secciones = secciones;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setDuracion(String duracion) {
        this.duracion = duracion;
    }

    public void setValence(double valence) {
        this.valence = valence;
    }

    public void setArousal(double arousal) {
        this.arousal = arousal;
    }

    public void setTemporal(boolean temporal) {
        this.temporal = temporal;
    }
}

// 🔹 NUEVA CLASE para guardar canción definitivamente
class GuardarCancionRequest implements Serializable {
    private int usuario_id;
    private String nombre;
    private String autor;
    private String album;
    private String enlace; // o ruta local
    private String tipo_origen; // "youtube" o "archivo"
    private int duracion; // en milisegundos
    private List<Seccion> secciones;

    public GuardarCancionRequest(int usuario_id, String nombre, String autor, String album, String enlace,
            String tipo_origen, int duracion, List<Seccion> secciones) {
        this.usuario_id = usuario_id;
        this.nombre = nombre;
        this.autor = autor;
        this.album = album;
        this.enlace = enlace;
        this.tipo_origen = tipo_origen;
        this.duracion = duracion;
        this.secciones = secciones;
    }
}

// 🔹 CLASE para representar secciones en el guardado definitivo
class SeccionGuardado {
    private String inicio;
    private String fin;
    private double valence;
    private double arousal;

    public SeccionGuardado(String inicio, String fin, double valence, double arousal) {
        this.inicio = inicio;
        this.fin = fin;
        this.valence = valence;
        this.arousal = arousal;
    }

    // Getters
    public String getInicio() {
        return inicio;
    }

    public String getFin() {
        return fin;
    }

    public double getValence() {
        return valence;
    }

    public double getArousal() {
        return arousal;
    }
}

class SeccionesResponse {
    @SerializedName("secciones")
    private List<Seccion> secciones;

    public List<Seccion> getSecciones() {
        return secciones;
    }
}

class AudioRequest {
    private String songEnlace;

    public AudioRequest(String songEnlace) {
        this.songEnlace = songEnlace;
    }

    public String getSongEnlace() {
        return songEnlace;
    }

    public void setSongEnlace(String songEnlace) {
        this.songEnlace = songEnlace;
    }
}

class TokenRequest {
    private String token;
    private String nombre; // Para el nombre de usuario
    private String imagen; // Puede ser una URL o un string codificado en Base64
    private String firebaseUid; // Firebase UID

    public TokenRequest(String token) {
        this.token = token;
    }

    // Constructor
    public TokenRequest(String token, String nombre, String imagen, String firebaseUid) {
        this.token = token;
        this.nombre = nombre;
        this.imagen = imagen;
        this.firebaseUid = firebaseUid;
    }

    // Getters y setters si es necesario
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getImagen() {
        return imagen;
    }

    public void setImagen(String imagen) {
        this.imagen = imagen;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }
}

class UserResponse {
    private int id;
    private String nombre;
    private String imagen;
    private String firebaseUid;

    // Getters
    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getImagen() {
        return imagen;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setImagen(String imagen) {
        this.imagen = imagen;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }
}

class UserNameUpdateRequest {
    private String firebaseUid;
    private String newName;

    public UserNameUpdateRequest(String firebaseUid, String newName) {
        this.firebaseUid = firebaseUid;
        this.newName = newName;
    }

    // Getters y Setters
    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }
}
