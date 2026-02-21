package com.example.intentoappdatosmusica;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NuevaCancionActivity extends AppCompatActivity {

    private EditText editTextLink;
    private ImageButton btnConfirmar;
    private ImageView btnBack;
    private ImageView btnSeleccionarArchivo; // Botón circular
    private TextView textInstrucciones;

    private String nombreArchivoSeleccionado = null;

    private static final int PICK_AUDIO_REQUEST = 1;
    private Uri audioFileUri = null; // Para guardar temporalmente el archivo seleccionado

    private FrameLayout pantallaCarga;
    private ProgressBar progresoCarga;
    private TextView textoPorcentaje;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nueva_cancion);

        editTextLink = findViewById(R.id.editTextEnlace);
        btnConfirmar = findViewById(R.id.btnConfirmar);
        btnBack = findViewById(R.id.btnBack);
        btnSeleccionarArchivo = findViewById(R.id.btnSeleccionarArchivo);
        textInstrucciones = findViewById(R.id.textFormatos);

        btnBack.setOnClickListener(v -> finish()); // Cierra esta actividad y vuelve a MenuPrincipal

        btnSeleccionarArchivo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*"); // Solo archivos de audio
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_AUDIO_REQUEST);
        });

        pantallaCarga = findViewById(R.id.pantallaCarga);
        progresoCarga = findViewById(R.id.progresoCarga);
        textoPorcentaje = findViewById(R.id.textoPorcentaje);

        btnConfirmar.setOnClickListener(v -> {
            String enlace = editTextLink.getText().toString().trim();

            if (!enlace.isEmpty() && audioFileUri == null) {
                // Subir enlace de YouTube
                mostrarPantallaCarga();
                subirEnlace(enlace);
            } else if (enlace.isEmpty() && audioFileUri != null) {
                Log.e("NUEVACANCIONACTIVITY", "Nombre del archivo seleccionado: " + nombreArchivoSeleccionado); //nombre archivo para ejemplo: Windows96 - 101Fitness (Cut-up Sections).wav

                // Obtener extensión del archivo
                String extension = "";
                if (nombreArchivoSeleccionado != null && nombreArchivoSeleccionado.lastIndexOf('.') > 0) {
                    extension = nombreArchivoSeleccionado.substring(nombreArchivoSeleccionado.lastIndexOf('.') + 1).toLowerCase();
                }

                // Validar formatos permitidos
                if (!extension.isEmpty() && (extension.equals("mp3") || extension.equals("wav") || extension.equals("ogg"))) {
                    mostrarPantallaCarga();
                    subirArchivo(audioFileUri);
                } else {
                    Toast.makeText(this, "Formato de archivo no permitido. Use MP3, WAV u OGG", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No se permiten ambos al mismo tiempo, por favor seleccionar correctamente.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mostrarPantallaCarga() {
        pantallaCarga.setVisibility(View.VISIBLE);
        progresoCarga.setProgress(0);
        textoPorcentaje.setText("Cargando...");
    }

    private void ocultarPantallaCarga() {
        pantallaCarga.setVisibility(View.GONE);
    }

    private void subirEnlace(String enlace) {
        long inicio = System.currentTimeMillis();
        btnBack.setEnabled(false);
        btnSeleccionarArchivo.setEnabled(false);
        btnConfirmar.setEnabled(false);
        if (enlace == null || !esEnlaceYoutubeValido(enlace)) {
            Toast.makeText(this, "Enlace no válido", Toast.LENGTH_SHORT).show();
            btnBack.setEnabled(true);
            btnSeleccionarArchivo.setEnabled(true);
            btnConfirmar.setEnabled(true);
            ocultarPantallaCarga();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);
        if (usuarioId == -1) {
            Toast.makeText(this, "Usuario no identificado", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody usuarioIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(usuarioId));
        RequestBody enlaceBody = RequestBody.create(MediaType.parse("text/plain"), enlace);

        ApiService audioService = ApiClient.getRetrofitForLargeTransfers().create(ApiService.class);
        Call<EnlaceUploadResponse> call = audioService.subirEnlace(usuarioIdBody, enlaceBody);

        call.enqueue(new Callback<EnlaceUploadResponse>() {
            @Override
            public void onResponse(Call<EnlaceUploadResponse> call, Response<EnlaceUploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    EnlaceUploadResponse res = response.body();
                    int songId = res.getId();
                    String nombre = res.getNombre();
                    String autor = res.getAutor();
                    String album = res.getAlbum();
                    String duracion = res.getDuracion();

                    List<Seccion> prediccionesSecciones = res.getSecciones();
                    for (Seccion sec : prediccionesSecciones) {
                        Log.d("API", "Sección: " + sec.getTiempoInicio() + "s - " + sec.getTiempoFinal() +
                                " | V=" + sec.getValence() + ", A=" + sec.getArousal());
                    }

                    Toast.makeText(NuevaCancionActivity.this, "Enlace procesado exitosamente", Toast.LENGTH_SHORT).show();

                    // ✅ Iniciar descarga del audio
                    AudioRequest request = new AudioRequest(enlace);
                    ApiService audioService = ApiClient.getRetrofitForLargeTransfers().create(ApiService.class);

                    audioService.getAudio(request).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> audioResponse) {
                            if (audioResponse.isSuccessful() && audioResponse.body() != null) {
                                File mediaDir = new File(getExternalFilesDir(null), "media");
                                if (!mediaDir.exists()) mediaDir.mkdirs();

                                InputStream inputStream = audioResponse.body().byteStream();
                                File audioFile = new File(mediaDir, getYoutubeVideoId(enlace) + ".mp3");

                                Runnable onSuccess = () -> {
                                    Log.e("NUEVACANCION", "Descarga de enlace completada");

                                    // Pequeña pausa para asegurar que el archivo esté completamente liberado
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }

                                    Intent intent = new Intent(NuevaCancionActivity.this, DatosPrediccionActivity.class);
                                    intent.putExtra("song_id", songId);
                                    intent.putExtra("link", enlace);
                                    intent.putExtra("name", nombre);
                                    intent.putExtra("author", autor);
                                    intent.putExtra("album", album);
                                    intent.putExtra("duracion", duracion);
                                    intent.putExtra("ruta_audio", audioFile.getAbsolutePath());
                                    intent.putExtra("tipo_origen", "youtube");

                                    intent.putExtra("secciones", (Serializable) prediccionesSecciones);

                                    intent.putExtra("NC", true);

                                    MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();
                                    mediaPlayerList.removeMediaPlayer(songId);
                                    mediaPlayerList.resetMediaPlayer(songId, audioFile.getAbsolutePath());

                                    btnBack.setEnabled(true);
                                    btnConfirmar.setEnabled(true);
                                    btnSeleccionarArchivo.setEnabled(true);
                                    ocultarPantallaCarga();
                                    long fin = System.currentTimeMillis();
                                    Log.d("ISO 25010", "Tiempo transcurrido: " + (fin - inicio) + " ms");
                                    startActivityForResult(intent, 200);
                                };

                                Runnable onFailure = () -> {
                                    Log.e("NUEVACANCION", "Error al guardar el archivo de audio");
                                    Toast.makeText(NuevaCancionActivity.this, "Error al guardar el audio", Toast.LENGTH_SHORT).show();
                                    ocultarPantallaCarga();
                                    btnBack.setEnabled(true);
                                    btnConfirmar.setEnabled(true);
                                    btnSeleccionarArchivo.setEnabled(true);
                                };

                                new DescargarAudioDesdeEnlaceTask(inputStream, audioFile, onSuccess, onFailure).execute();

                            } else {
                                btnBack.setEnabled(true);
                                btnConfirmar.setEnabled(true);
                                btnSeleccionarArchivo.setEnabled(true);
                                ocultarPantallaCarga();
                                new AlertDialog.Builder(NuevaCancionActivity.this)
                                        .setTitle("Sin conexión")
                                        .setMessage("Para agregar una canción desde YouTube necesitas estar conectado a internet.")
                                        .setPositiveButton("Aceptar", null)
                                        .show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            btnBack.setEnabled(true);
                            btnConfirmar.setEnabled(true);
                            btnSeleccionarArchivo.setEnabled(true);
                            ocultarPantallaCarga();
                            Log.e("NUEVACANCION", "Falló la descarga de audio", t);
                            Toast.makeText(NuevaCancionActivity.this, "Error de red al descargar audio", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(NuevaCancionActivity.this, "Error: el enlace no es válido (no existe o tiene restricción de edad), por favor reintentar.", Toast.LENGTH_SHORT).show();
                    btnBack.setEnabled(true);
                    btnConfirmar.setEnabled(true);
                    btnSeleccionarArchivo.setEnabled(true);
                    ocultarPantallaCarga();
                }
            }

            @Override
            public void onFailure(Call<EnlaceUploadResponse> call, Throwable t) {
                btnBack.setEnabled(true);
                btnConfirmar.setEnabled(true);
                btnSeleccionarArchivo.setEnabled(true);
                ocultarPantallaCarga();
                Toast.makeText(NuevaCancionActivity.this, "Fallo de conexión", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class DescargarAudioDesdeEnlaceTask extends AsyncTask<Void, Integer, Boolean> {
        private InputStream inputStream;
        private File outputFile;
        private Runnable onSuccess;
        private Runnable onFailure;

        public DescargarAudioDesdeEnlaceTask(InputStream inputStream, File outputFile, Runnable onSuccess, Runnable onFailure) {
            this.inputStream = inputStream;
            this.outputFile = outputFile;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                inputStream.close();
                return true;
            } catch (IOException e) {
                Log.e("DESCARGA_ASYNC", "Error escribiendo el archivo", e);
                return false;
            }
        }

        @Override
        protected void onPreExecute() {
            progresoCarga.setIndeterminate(true); // siempre animado
            textoPorcentaje.setText("Cargando...");
        }

        @Override
        protected void onPostExecute(Boolean success) {
            ocultarPantallaCarga();
            if (success) {
                onSuccess.run();
            } else {
                onFailure.run();
            }
        }
    }

    private void subirArchivo(Uri fileUri) {
        long inicio = System.currentTimeMillis();
        String rutaCopia = copyFileToAppMediaFolder(fileUri);
        btnBack.setEnabled(false);
        btnSeleccionarArchivo.setEnabled(false);
        btnConfirmar.setEnabled(false);
        if (rutaCopia == null) {
            Toast.makeText(this, "Error al copiar el archivo", Toast.LENGTH_SHORT).show();
            btnBack.setEnabled(true);
            btnSeleccionarArchivo.setEnabled(true);
            btnConfirmar.setEnabled(true);
            ocultarPantallaCarga();
            return;
        }

        File file = new File(rutaCopia);

        MediaPlayer mediaPlayer = new MediaPlayer();
        String formattedTime;
        try {
            mediaPlayer.setDataSource(String.valueOf(file));
            mediaPlayer.prepare();
            int durationInMillis = mediaPlayer.getDuration();

            int hours = (durationInMillis / (1000 * 60 * 60)) % 24;
            int minutes = (durationInMillis / (1000 * 60)) % 60;
            int seconds = (durationInMillis / 1000) % 60;
            int milliseconds = durationInMillis % 1000;

            formattedTime = String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds);
            Log.e("NUEVA CANCION", "la canción nueva dura: " + formattedTime);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            mediaPlayer.release();
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse("audio/*"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("archivo", file.getName(), requestFile);

        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);
        if (usuarioId == -1) {
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody usuarioIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(usuarioId));
        RequestBody nombreCancionBody = RequestBody.create(MediaType.parse("text/plain"), nombreArchivoSeleccionado);
        RequestBody finSeccionBody = RequestBody.create(MediaType.parse("text/plain"), formattedTime);

        String nombreSinExtension = nombreArchivoSeleccionado.replaceFirst("[.][^.]+$", "");

        ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);
        Call<AudioUploadResponse> call = apiService.subirArchivoAudio(body, usuarioIdBody, nombreCancionBody, finSeccionBody);

        call.enqueue(new Callback<AudioUploadResponse>() {
            @Override
            public void onResponse(Call<AudioUploadResponse> call, Response<AudioUploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AudioUploadResponse res = response.body();
                    int songId = res.getId();

                    // 🔹 NUEVOS CAMPOS - Predicciones de la red neuronal
                    String nombre = res.getNombre();
                    String duracion = res.getDuracion();
                    double valence = res.getValence();
                    double arousal = res.getArousal();
                    boolean esTemporal = res.isTemporal();

                    Toast.makeText(NuevaCancionActivity.this, "Archivo procesado exitosamente", Toast.LENGTH_SHORT).show();

                    // Pequeña pausa para asegurar que el archivo esté completamente liberado
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // 🔹 Convertir las secciones predichas a objetos Seccion
                    List<Seccion> seccionesPredichas = new ArrayList<>();
                    if (res.getSecciones() != null && !res.getSecciones().isEmpty()) {
                        for (Seccion s : res.getSecciones()) {
                            // convertir tiempos de double a String si vienen así del servidor
                            String inicioStr, finStr;

                            try {
                                // Si el servidor envía "tiempo_inicio": 12.34 (double)
                                double inicioDouble = Double.parseDouble(s.getTiempoInicio());
                                double finDouble = Double.parseDouble(s.getTiempoFinal());
                                inicioStr = String.valueOf(s.getTiempoInicio());
                                finStr = String.valueOf(s.getTiempoFinal());

                            } catch (NumberFormatException e) {
                                // Si ya vienen como texto (por ejemplo "00:00:12.340")
                                inicioStr = s.getTiempoInicio();
                                finStr = s.getTiempoFinal();
                            }

                            Seccion nuevaSeccion = new Seccion(
                                    -1,
                                    inicioStr,
                                    finStr,
                                    "", ""
                            );
                            nuevaSeccion.setValence(s.getValence());
                            nuevaSeccion.setArousal(s.getArousal());
                            seccionesPredichas.add(nuevaSeccion);
                        }

                    } else {
                        // Fallback si no hay secciones
                        seccionesPredichas.add(new Seccion(-1, "00:00.000", duracion, "", ""));
                    }

                    MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();
                    mediaPlayerList.resetMediaPlayer(songId, rutaCopia);

                    Intent intent = new Intent(NuevaCancionActivity.this, DatosPrediccionActivity.class);
                    intent.putExtra("song_id", songId);
                    intent.putExtra("link", nombreArchivoSeleccionado);
                    intent.putExtra("name", nombre != null ? nombre : nombreSinExtension);
                    intent.putExtra("ruta_audio", rutaCopia);
                    intent.putExtra("duracion", duracion != null ? duracion : formattedTime);
                    intent.putExtra("tipo_origen", "archivo");
                    intent.putExtra("author", "(Sin autor)");
                    intent.putExtra("album", "(Sin álbum)");

                    // 🔹 Agregar las secciones predichas al intent
                    intent.putExtra("secciones", (Serializable) seccionesPredichas);
                    intent.putExtra("es_temporal", esTemporal);

                    intent.putExtra("NC", true);

                    Log.e("NUEVA CANCION ACT", "canción procesada: " + true);
                    ocultarPantallaCarga();
                    btnBack.setEnabled(true);
                    btnConfirmar.setEnabled(true);
                    btnSeleccionarArchivo.setEnabled(true);
                    long fin = System.currentTimeMillis();
                    Log.d("ISO 25010", "Tiempo transcurrido: " + (fin - inicio) + " ms");
                    startActivityForResult(intent, 200);
                } else {
                    ocultarPantallaCarga();
                    btnBack.setEnabled(true);
                    btnConfirmar.setEnabled(true);
                    btnSeleccionarArchivo.setEnabled(true);
                    Toast.makeText(NuevaCancionActivity.this, "Error al procesar el archivo", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AudioUploadResponse> call, Throwable t) {
                Toast.makeText(NuevaCancionActivity.this, "Sin conexión: procesando localmente", Toast.LENGTH_LONG).show();
                Log.e("onFailure", t.getMessage());

                SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
                int usuarioId = prefs.getInt("usuario_id", -1);
                if (usuarioId == -1) {
                    Toast.makeText(NuevaCancionActivity.this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show();
                    return;
                }

                int nuevoId = obtenerNuevoIdLocal();

                MediaPlayerList.getInstance().resetMediaPlayer(nuevoId, rutaCopia);

                // Modo offline - usar valores por defecto para predicciones
                Intent intent = new Intent(NuevaCancionActivity.this, DatosPrediccionActivity.class);
                intent.putExtra("song_id", nuevoId);
                intent.putExtra("link", nombreArchivoSeleccionado);
                intent.putExtra("name", nombreSinExtension);
                intent.putExtra("ruta_audio", rutaCopia);
                intent.putExtra("duracion", formattedTime);
                intent.putExtra("tipo_origen", "archivo_local");
                intent.putExtra("offline", true);

                // 🔹 VALORES POR DEFECTO PARA MODO OFFLINE
                intent.putExtra("valence", 0); // Valor neutral
                intent.putExtra("arousal", 0); // Valor neutral
                intent.putExtra("es_temporal", true);

                ocultarPantallaCarga();
                btnBack.setEnabled(true);
                btnConfirmar.setEnabled(true);
                btnSeleccionarArchivo.setEnabled(true);
                startActivityForResult(intent, 200);
            }
        });
    }

    private String convertirSegundosATiempo(double segundos) {
        int millis = (int) (segundos * 1000);
        int hours = (millis / (1000 * 60 * 60)) % 24;
        int minutes = (millis / (1000 * 60)) % 60;
        int seconds = (millis / 1000) % 60;
        int ms = millis % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }

    private void guardarCancionLocalmente(int nuevo_Id, String nombreArchivoOriginal, String nombreSinExtension,
                                          String duracion, String f_creacion, String f_ultimaedicion,
                                          int nuevo_Id_seccion, String f_creacionseccion, String f_ultimaedicionseccion) {
        try {
            File directorio = new File(getExternalFilesDir(null), "songdata");
            if (!directorio.exists()) directorio.mkdirs();

            // 🔹 Obtener usuario_id desde SharedPreferences
            SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
            int usuarioId = prefs.getInt("usuario_id", -1);
            if (usuarioId == -1) {
                Log.e("LOCAL_SAVE", "❌ No se encontró usuario_id en SharedPreferences. No se puede guardar canción.");
                return;
            }

            // 🔹 Archivo único por usuario
            File archivo = new File(directorio, "songdata_" + usuarioId + ".txt");

            // Preparar campos
            String nombre = nombreSinExtension;
            String autor = "(Sin autor)";
            String album = "(Sin álbum)";
            String enlace = nombreArchivoOriginal; // incluye .mp3, .wav, etc.
            String comentario = "(Sin comentario)";
            String estadoComentario = "false";
            String estadoPublicado = "false";
            String fecha_creacion = f_creacion;
            String fecha_ultimaedicion = f_ultimaedicion;
            String secciones = "00:00.000-" + duracion.substring(3); // quitar HH: y dejar MM:ss.dcm
            String fecha_creacion_seccion = f_creacionseccion;
            String fecha_ultimaedicion_seccion = f_ultimaedicionseccion;

            // Armar la línea
            String nuevaLinea = nuevo_Id + ";" + nombre + ";" + autor + ";" + album + ";" +
                    enlace + ";" + comentario + ";" + estadoComentario + ";" + estadoPublicado + ";" +
                    fecha_creacion + ";" + fecha_ultimaedicion + ";" +
                    nuevo_Id_seccion + "/" + secciones + "//" + fecha_creacion_seccion + "//" + fecha_ultimaedicion_seccion;

            // Escribir (agregar al final)
            FileWriter fw = new FileWriter(archivo, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(nuevaLinea);
            bw.newLine();
            bw.close();

            Toast.makeText(this, "Archivo 'songdata_" + usuarioId + ".txt' actualizado.", Toast.LENGTH_SHORT).show();
            Log.e("NUEVALINEA", nuevaLinea);

        } catch (IOException e) {
            Log.e("LOCAL_SAVE", "❌ Error al guardar canción localmente", e);
            Toast.makeText(this, "Error al guardar localmente", Toast.LENGTH_SHORT).show();
        }
    }

    private int obtenerNuevoIdLocal() {
        File directorio = new File(getExternalFilesDir(null), "songdata");

        // 🔹 Obtener usuario_id
        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);
        if (usuarioId == -1) {
            Log.e("LOCAL_ID", "❌ No se encontró usuario_id en SharedPreferences.");
            return 1; // valor por defecto
        }

        File archivo = new File(directorio, "songdata_" + usuarioId + ".txt");

        int nuevoId = 1;
        if (archivo.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] partes = linea.split(";");
                    if (partes.length > 0) {
                        try {
                            int id = Integer.parseInt(partes[0]);
                            if (id >= nuevoId) nuevoId = id + 1;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException e) {
                Log.e("LOCAL_ID", "❌ Error leyendo archivo para ID local", e);
            }
        }
        return nuevoId;
    }

    private int obtenerNuevoIdSeccionLocal() {
        File directorio = new File(getExternalFilesDir(null), "songdata");

        // 🔹 Obtener usuario_id
        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);
        if (usuarioId == -1) {
            Log.e("LOCAL_ID_SECCION", "❌ No se encontró usuario_id en SharedPreferences.");
            return 1; // valor por defecto
        }

        File archivo = new File(directorio, "songdata_" + usuarioId + ".txt");

        int nuevoId = 1;
        if (archivo.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] partesCancion = linea.split(";");
                    if (partesCancion.length >= 11) {
                        String secciones = partesCancion[10];  // Campo 11: secciones
                        String[] seccionesArray = secciones.split("\\|");

                        for (String seccion : seccionesArray) {
                            String[] partesSeccion = seccion.split("/");
                            if (partesSeccion.length > 0) {
                                try {
                                    int idSeccion = Integer.parseInt(partesSeccion[0]);
                                    if (idSeccion >= nuevoId) {
                                        nuevoId = idSeccion + 1;
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("LOCAL_ID_SECCION", "❌ Error leyendo archivo para ID de sección local", e);
            }
        }
        return nuevoId;
    }

    private String copyFileToAppMediaFolder(Uri uri) {
        String nombreArchivo = getFileNameFromUri(uri);
        File mediaDir = new File(getExternalFilesDir("media"), ""); // Ruta: /storage/emulated/0/Android/data/tu.app/files/media

        if (!mediaDir.exists()) {
            mediaDir.mkdirs();
        }

        File destino = new File(mediaDir, nombreArchivo);

        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(destino)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return destino.getAbsolutePath();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ✅ Caso 1: selección de archivo de audio
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            audioFileUri = data.getData(); // Guardamos la URI del archivo de audio

            // Obtener información del archivo
            nombreArchivoSeleccionado = getFileNameFromUri(audioFileUri);
            String rutaReal = getRealPathFromUri(audioFileUri);

            Log.d("NUEVACANCIONACTIVITY", "Archivo seleccionado - Nombre: " + nombreArchivoSeleccionado + ", Ruta: " + rutaReal);

            if (nombreArchivoSeleccionado != null && !nombreArchivoSeleccionado.isEmpty()) {
                textInstrucciones.setText(nombreArchivoSeleccionado);
            } else {
                textInstrucciones.setText("Archivo seleccionado");
            }

            btnSeleccionarArchivo.setBackgroundResource(R.drawable.nota_musical);
            Toast.makeText(this, "Archivo de audio seleccionado", Toast.LENGTH_SHORT).show();
        }

        // ✅ Caso 2: resultado desde DatosMusicalesActivity
        else if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            boolean cancionNuevaAgregada = data.getBooleanExtra("NC", false);
            Log.e("NUEVA CANCION", "Resultado recibido de DatosMusicalesActivity: NC = " + cancionNuevaAgregada);
            setResult(RESULT_OK, data); // Reenviar resultado a MenuPrincipalActivity
            finish(); // Cierra NuevaCancionActivity
        }

        else if (requestCode == 200) {
            if (resultCode == RESULT_OK) {
                // El usuario confirmó en DatosPrediccionActivity
                boolean cancionConfirmada = data.getBooleanExtra("cancion_confirmada", false);
                if (cancionConfirmada) {
                    // Reenviar a MenuPrincipal con éxito
                    setResult(RESULT_OK, data);
                } else {
                    // El usuario canceló
                    setResult(RESULT_CANCELED);
                }
                finish();
            } else if (resultCode == RESULT_CANCELED) {
                // El usuario canceló desde DatosPrediccionActivity
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String nombre = null;

        // Obtener nombre del archivo
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        nombre = cursor.getString(nameIndex);
                    }
                }
            }
        }

        if (nombre == null) {
            nombre = uri.getLastPathSegment();
        }

        return nombre;
    }

    private boolean esEnlaceYoutubeValido(String enlace) {
        return getYoutubeVideoId(enlace) != null;
    }

    private String getYoutubeVideoId(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.isEmpty()) return null;

        Pattern pattern = Pattern.compile("v=([a-zA-Z0-9_-]{11})|/videos/([a-zA-Z0-9_-]{11})|embed/([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})|/v/([a-zA-Z0-9_-]{11})|/e/([a-zA-Z0-9_-]{11})|watch\\?v=([a-zA-Z0-9_-]{11})|/shorts/([a-zA-Z0-9_-]{11})|/live/([a-zA-Z0-9_-]{11})");
        Matcher matcher = pattern.matcher(youtubeUrl);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i);
                }
            }
        }

        return null;
    }

    private String getRealPathFromUri(Uri uri) {
        String realPath = null;

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {MediaStore.Audio.Media.DATA};
            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                    realPath = cursor.getString(columnIndex);
                    if (realPath != null && new File(realPath).exists()) {
                        return realPath;
                    }
                }
            } catch (Exception e) {
                Log.e("NUEVACANCIONACTIVITY", "Error al obtener ruta desde MediaStore", e);
            }
        }

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            realPath = uri.getPath();
            if (realPath != null && new File(realPath).exists()) {
                return realPath;
            }
        }

        return null; // No copiar aquí
    }
}