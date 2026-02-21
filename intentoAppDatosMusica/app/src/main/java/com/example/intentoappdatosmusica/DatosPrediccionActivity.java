package com.example.intentoappdatosmusica;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DatosPrediccionActivity extends AppCompatActivity
        implements SeccionesAdapter.SeccionEditTextListener {

    // Variables existentes
    private MediaPlayer mediaPlayer;
    private String rutaAudio;
    private int songId;
    private boolean esOffline;
    private String nombreCancion;
    private String artista;
    private String album;
    private String duracion;
    private String tipoOrigen;
    private String link;

    // Variables para la funcionalidad de secciones (adaptadas de PopupDefinirSecciones)
    private SeekBar seekBar;
    private TextView tvProgresoDuracion, tvEmocionActual;
    private ImageButton btnPlayPause;
    private ImageButton btnRewind;
    private ImageButton btnForward;
    private Handler handler = new Handler();
    private boolean isPlaying = false;
    private int duracionTotalMs = 0;

    // Componentes para la gestión de secciones
    private RecyclerView recyclerViewSecciones;
    private SeccionesAdapter seccionesAdapter;
    private List<Seccion> listaSecciones = new ArrayList<>();
    private RelativeLayout thumbsContainer;
    private List<ImageView> thumbsList = new ArrayList<>();
    private int seccionSeleccionada = -1;
    private Button btnConfirmarSecciones;

    // Variables para el estado de las secciones
    private boolean modoEditarFin = false;
    private boolean actualizandoDesdeThumb = false;
    private List<Seccion> seccionesOriginales = null;
    private int usuarioId;

    // En las variables de clase, agregar:
    private boolean moviendoThumb = false;

    // AHORA:
    private Map<String, List<String>> mapaPalabrasPorVA; // igual que antes
    private Map<String, List<EmocionSeleccionada>> palabrasRepresentativasPorVA; // lista de palabras únicas válidas

    private Map<String, List<String>> palabrasPorEmocionUnica = new HashMap<>();

    private void cargarPalabrasDesdeCSV() {
        palabrasPorEmocionUnica.clear();

        File csvFile = new File(getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");
        if (!csvFile.exists()) {
            Log.e("Predicciones", "El archivo CSV no existe: " + csvFile.getAbsolutePath());
            return;
        }

        // TEMP: Mapa para contar cuántas emociones tiene cada palabra
        Map<String, Set<String>> emocionesPorPalabra = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }

                String[] cols = line.split(",");
                if (cols.length < 2) continue;

                String palabra = cols[0].trim();
                String emocion = cols[1].trim().toLowerCase();

                emocionesPorPalabra.putIfAbsent(palabra, new HashSet<>());
                emocionesPorPalabra.get(palabra).add(emocion);
            }

            // Ahora llenamos palabrasPorEmocionUnica SOLO con palabras que tengan 1 categoría
            for (Map.Entry<String, Set<String>> entry : emocionesPorPalabra.entrySet()) {
                String palabra = entry.getKey();
                Set<String> emociones = entry.getValue();

                if (emociones.size() == 1) {  // ✅ Solo palabras con UNA emoción
                    String emocion = emociones.iterator().next(); // la única
                    palabrasPorEmocionUnica.putIfAbsent(emocion, new ArrayList<>());
                    palabrasPorEmocionUnica.get(emocion).add(palabra);
                }
            }

            Log.d("Predicciones", "Palabras únicas por emoción:" + palabrasPorEmocionUnica);

        } catch (Exception e) {
            Log.e("Predicciones", "Error leyendo CSV", e);
        }
    }

    private EmocionSeleccionada obtenerPalabraPorCategoria(float val, float aro) {
        String emocion = convertirValenceArousalAEmocion(val, aro).toLowerCase();

        List<String> palabras = palabrasPorEmocionUnica.get(emocion);
        if (palabras != null && !palabras.isEmpty()) {
            // Puedes elegir aleatoria o la primera
            String palabraElegida = palabras.get(new Random().nextInt(palabras.size()));
            return new EmocionSeleccionada(0, 0, palabraElegida);
        }
        return null;
    }

    private void asignarEmocionesASecciones() {
        if (listaSecciones == null) return;

        for (Seccion s : listaSecciones) {
            float val = (float) s.getValence();
            float aro = (float) s.getArousal();

            // 🔹 Guardar los valores predichos reales
            s.setValenceReal(val);
            s.setArousalReal(aro);

            // 🔹 Buscar palabra representativa según emoción
            EmocionSeleccionada palabra = obtenerPalabraPorCategoria(val, aro);

            if (palabra != null) {
                List<EmocionSeleccionada> lista = new ArrayList<>();
                lista.add(palabra);
                s.setEmociones(lista);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datos_prediccion);

        Log.e("proceso prediccion", "Obteniendo datos de Intent");
        // Obtener datos del intent
        obtenerDatosIntent();
        cargarPalabrasDesdeCSV();

        // Obtener usuario ID
        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", Context.MODE_PRIVATE);
        usuarioId = prefs.getInt("usuario_id", -1);

        // Inicializar vistas para la funcionalidad de secciones
        Log.e("proceso prediccion", "Inicializando vistas");
        inicializarVistasSecciones();

        // Configurar interfaz
        mostrarMetadatos();
        configurarBotonesAccion();
        cargarPredicciones();

        // 🔹 INICIALIZAR SECCIÓN POR DEFECTO (SOLO SI ES NECESARIO)
        // Esto asegura que haya al menos una sección mientras se cargan las demás
        if (listaSecciones.isEmpty()) inicializarSeccionesPorDefecto();

        cargarThumbsIniciales();
    }

    // 🔹 NUEVO MÉTODO: Cargar los thumbs iniciales desde las secciones
    private void cargarThumbsIniciales() {
        if (listaSecciones != null && !listaSecciones.isEmpty()) {
            // Esperar a que la UI esté completamente cargada
            seekBar.postDelayed(() -> {
                cargarThumbsDesdeSecciones();
                Log.d("ThumbsIniciales", "Thumbs cargados: " + thumbsList.size());
            }, 300);
        }
    }

    private void inicializarVistasSecciones() {
        // Configurar SeekBar y controles de reproducción
        seekBar = findViewById(R.id.seekBar_prediccion);
        tvProgresoDuracion = findViewById(R.id.tvProgresoDuracion);
        btnPlayPause = findViewById(R.id.btn_play_pause_prediccion);
        btnRewind = findViewById(R.id.btn_rewind_prediccion);
        btnForward = findViewById(R.id.btn_forward_prediccion);

        tvEmocionActual = findViewById(R.id.tvEmocionActual);

        // Configurar vistas para la gestión de secciones
        recyclerViewSecciones = findViewById(R.id.rv_secciones_prediccion);
        thumbsContainer = findViewById(R.id.thumbs_container);
        btnConfirmarSecciones = findViewById(R.id.btn_confirmar_prediccion);

        // Configurar RecyclerView
        recyclerViewSecciones.setLayoutManager(new LinearLayoutManager(this));
        seccionesAdapter = new SeccionesAdapter(listaSecciones, this, false);
        recyclerViewSecciones.setAdapter(seccionesAdapter);

        // Configurar botón de confirmar secciones
        btnConfirmarSecciones.setOnClickListener(v -> {
            cerrarFocoDeEditTexts();
            guardarCancionEnBaseDeDatos();
        });

        // Configurar controles de reproducción
        configurarControlesReproduccion();
    }

    private void configurarControlesReproduccion() {
        // Inicializar MediaPlayer
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(rutaAudio);
            mediaPlayer.prepare();

            duracionTotalMs = mediaPlayer.getDuration();
            seekBar.setMax(duracionTotalMs);
            actualizarTextoProgreso(0);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al cargar el audio", Toast.LENGTH_SHORT).show();
        }

        // Configurar botones de control
        btnPlayPause.setOnClickListener(v -> toggleReproduccion());
        btnRewind.setOnClickListener(v -> cambiarPosicion(-5000)); // Retrocede 5 segundos
        btnForward.setOnClickListener(v -> cambiarPosicion(5000)); // Adelanta 5 segundos

        // Configurar SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && seekBar.isEnabled()) {
                    mediaPlayer.seekTo(progress);
                    actualizarTextoProgreso(progress);
                    float segundos = (float) (progress / 1000.0);
                    actualizarEmocionSegunProgreso(segundos);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (seekBar.isEnabled()) {
                    handler.removeCallbacks(updateSeekBar);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar.isEnabled()) {
                    handler.postDelayed(updateSeekBar, 100);
                }
            }
        });

        mediaPlayer.setOnCompletionListener(mp -> detenerReproduccion());
    }

    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(currentPosition);
                actualizarTextoProgreso(currentPosition);

                // 🔹 Convertir a segundos y actualizar emoción actual
                float segundos = currentPosition / 1000f;
                actualizarEmocionSegunProgreso(segundos);

                handler.postDelayed(this, 100);
            }
        }
    };

    private void toggleReproduccion() {
        if (isPlaying) {
            pausarReproduccion();
        } else {
            iniciarReproduccion();
        }
    }

    private void iniciarReproduccion() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            btnPlayPause.setImageResource(R.drawable.iconopause);
            handler.postDelayed(updateSeekBar, 100);
        }
    }

    private void pausarReproduccion() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlayPause.setImageResource(R.drawable.iconoplay);
            handler.removeCallbacks(updateSeekBar);
        }
    }

    private void detenerReproduccion() {
        isPlaying = false;
        btnPlayPause.setImageResource(R.drawable.iconoplay);
        handler.removeCallbacks(updateSeekBar);
    }

    private void cambiarPosicion(int cambioMs) {
        if (mediaPlayer != null) {
            int nuevaPosicion = mediaPlayer.getCurrentPosition() + cambioMs;
            nuevaPosicion = Math.max(0, Math.min(nuevaPosicion, duracionTotalMs));

            mediaPlayer.seekTo(nuevaPosicion);
            seekBar.setProgress(nuevaPosicion);
            actualizarTextoProgreso(nuevaPosicion);

            // 🔹 Forzar actualización de emoción
            float segundos = nuevaPosicion / 1000f;
            actualizarEmocionSegunProgreso(segundos);
        }
    }

    private void actualizarTextoProgreso(int currentPositionMs) {
        String progresoActual = convertirMilisegundosAString(currentPositionMs);
        String duracionTotal = convertirMilisegundosAString(duracionTotalMs);
        tvProgresoDuracion.setText(progresoActual + " / " + duracionTotal);
    }

    private String convertirMilisegundosAString(int milisegundos) {
        int minutos = (milisegundos / 60000) % 60;
        int segundos = (milisegundos / 1000) % 60;
        int milis = milisegundos % 1000;

        return String.format("%02d:%02d.%03d", minutos, segundos, milis);
    }

    public int convertirStringAMilisegundos(String tiempo) {
        String[] partes = tiempo.split("[:.]");
        int minutos = Integer.parseInt(partes[0]);
        int segundos = Integer.parseInt(partes[1]);
        int milisegundos = Integer.parseInt(partes[2]);

        return (minutos * 60 + segundos) * 1000 + milisegundos;
    }

    private void obtenerDatosIntent() {
        Intent intent = getIntent();
        songId = intent.getIntExtra("song_id", -1);
        esOffline = intent.getBooleanExtra("offline", false);
        nombreCancion = intent.getStringExtra("name");
        artista = intent.getStringExtra("author");
        album = intent.getStringExtra("album");
        tipoOrigen = intent.getStringExtra("tipo_origen");
        link = intent.getStringExtra("link");
        rutaAudio = intent.getStringExtra("ruta_audio");

        // 🔹 CAPTURAR DURACIÓN SI VIENE EN EL INTENT
        if (intent.hasExtra("duracion")) {
            duracionTotalMs = intent.getIntExtra("duracion", 0);
        }

        // 🔹 CARGAR LAS SECCIONES CON LAS PREDICCIONES
        listaSecciones = (List<Seccion>) getIntent().getSerializableExtra("secciones");

        if (listaSecciones != null) {
            Log.d("Predicciones", "Secciones recibidas: " + listaSecciones.size());
            for (Seccion s : listaSecciones) {
                Log.d("Predicciones", "Sección original: " + s.getTiempoInicio() + " - " +
                        s.getTiempoFinal() + " | V=" + s.getValence() + ", A=" + s.getArousal());

                // Convertir a formato legible si es necesario, pero mantener los valores numéricos
                // Solo convertir a String si la interfaz lo requiere para mostrar
                String inicioFormateado = convertirSegundosAFormato(s.getTiempoInicio());
                String finFormateado = convertirSegundosAFormato(s.getTiempoFinal());

                s.setTiempoInicio(inicioFormateado);
                s.setTiempoFinal(finFormateado);

                Log.d("Predicciones", "Sección formateada: " + inicioFormateado + " - " +
                        finFormateado + " | V=" + s.getValence() + ", A=" + s.getArousal());
            }
        } else {
            Log.e("Predicciones", "listaSecciones es NULL - no se recibieron predicciones");
        }
    }

    private String convertirSegundosAFormato(String segundos) {
        double totalSegundos = Double.parseDouble(segundos);
        int minutos = (int) (totalSegundos / 60);
        double restoSegundos = totalSegundos % 60;
        return String.format(java.util.Locale.US, "%02d:%06.3f", minutos, restoSegundos);
    }

    private void mostrarMetadatos() {
        TextView txtTitulo = findViewById(R.id.tvNombreCancion);
        TextView txtArtista = findViewById(R.id.tvAutorCancion);
        TextView txtAlbum = findViewById(R.id.tvAlbumCancion);

        txtTitulo.setText(nombreCancion != null ? nombreCancion : "Sin título");
        txtArtista.setText(artista != null && !artista.isEmpty() ? artista : "Artista desconocido");
        txtAlbum.setText(album != null && !album.isEmpty() ? album : "Álbum desconocido");
    }

    private void configurarBotonesAccion() {
        Button btnConfirmar = findViewById(R.id.btn_confirmar_prediccion);
        Button btnCancelar = findViewById(R.id.btn_cancelar_prediccion);

        btnConfirmar.setOnClickListener(v -> guardarCancionEnBaseDeDatos());
        btnCancelar.setOnClickListener(v -> cancelarYCerrar());
    }

    private void cargarPredicciones() {
        // Verificar que las secciones se cargaron correctamente
        if (listaSecciones != null && !listaSecciones.isEmpty()) {
            Log.d("Predicciones", "Secciones cargadas: " + listaSecciones.size());
            for (Seccion seccion : listaSecciones) {
                Log.d("Predicciones", "Sección: " + seccion.getTiempoInicio() + " - " +
                        seccion.getTiempoFinal() + " | V=" + seccion.getValence() +
                        ", A=" + seccion.getArousal());
            }
        } else {
            Log.d("Predicciones", "No hay secciones cargadas");
            // Mostrar un mensaje al usuario
            tvEmocionActual.setText("No hay datos de emociones disponibles");
        }
    }

    private void guardarCancionEnBaseDeDatos() {
        if (esOffline) {
            guardarCancionLocalmente();
        } else {
            guardarCancionEnServidor();
        }
    }

    private void cancelarYCerrar() {
        if (rutaAudio != null) {
            File archivo = new File(rutaAudio);
            if (archivo.exists() && archivo.delete()) {
                Toast.makeText(this, "Canción cancelada", Toast.LENGTH_SHORT).show();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void guardarCancionLocalmente() {
        // Implementar guardado local
        Toast.makeText(this, "Canción guardada localmente", Toast.LENGTH_SHORT).show();
    }

    private void guardarCancionEnServidor() {
        asignarEmocionesASecciones();

        if (listaSecciones == null || listaSecciones.isEmpty()) {
            Toast.makeText(this, "No hay secciones para guardar", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);

        if (tipoOrigen.equals("youtube")) {
            // 🔹 Caso YouTube: se envía JSON normal
            GuardarCancionRequest request = new GuardarCancionRequest(
                    usuarioId,
                    nombreCancion,
                    artista,
                    album,
                    link,
                    tipoOrigen,
                    duracionTotalMs,
                    listaSecciones
            );

            Call<GuardarCancionResponse> call = apiService.guardarCancionDefinitivaJSON(request);

            call.enqueue(new Callback<GuardarCancionResponse>() {
                @Override
                public void onResponse(Call<GuardarCancionResponse> call, Response<GuardarCancionResponse> response) {
                    manejarRespuesta(response);
                }

                @Override
                public void onFailure(Call<GuardarCancionResponse> call, Throwable t) {
                    Toast.makeText(DatosPrediccionActivity.this, "❌ Fallo en la conexión: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else if (tipoOrigen.equals("archivo")) {
            // 🔹 Caso archivo: se envía como multipart
            File file = new File(rutaAudio);
            if (!file.exists()) {
                Toast.makeText(this, "El archivo no existe", Toast.LENGTH_SHORT).show();
                return;
            }

            RequestBody requestFile = RequestBody.create(MediaType.parse("audio/*"), file);
            MultipartBody.Part archivoPart = MultipartBody.Part.createFormData("archivo", file.getName(), requestFile);

            RequestBody usuarioIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(usuarioId));
            RequestBody nombreBody = RequestBody.create(MediaType.parse("text/plain"), nombreCancion.isEmpty() ? "Sin título" : nombreCancion);
            RequestBody autorBody = RequestBody.create(MediaType.parse("text/plain"), artista.isEmpty() ? "Artista desconocido" : artista);
            RequestBody albumBody = RequestBody.create(MediaType.parse("text/plain"), album.isEmpty() ? "Álbum desconocido" : album);
            RequestBody tipoBody = RequestBody.create(MediaType.parse("text/plain"), tipoOrigen);
            RequestBody duracionBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(duracionTotalMs));

            Gson gson = new Gson();
            String seccionesJson = gson.toJson(listaSecciones);
            RequestBody seccionesBody = RequestBody.create(MediaType.parse("application/json"), seccionesJson);

            Call<GuardarCancionResponse> call = apiService.guardarCancionDefinitivaArchivo(
                    archivoPart,
                    usuarioIdBody,
                    nombreBody,
                    autorBody,
                    albumBody,
                    tipoBody,
                    duracionBody,
                    seccionesBody
            );

            call.enqueue(new Callback<GuardarCancionResponse>() {
                @Override
                public void onResponse(Call<GuardarCancionResponse> call, Response<GuardarCancionResponse> response) {
                    manejarRespuesta(response);
                }

                @Override
                public void onFailure(Call<GuardarCancionResponse> call, Throwable t) {
                    Toast.makeText(DatosPrediccionActivity.this, "❌ Fallo en la conexión: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void manejarRespuesta(Response<GuardarCancionResponse> response) {
        if (response.isSuccessful() && response.body() != null) {
            Toast.makeText(this, "✅ " + response.body().getMensaje(), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(DatosPrediccionActivity.this, MenuPrincipalActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "⚠️ Error al guardar: " + response.code(), Toast.LENGTH_SHORT).show();
        }
    }

    private void inicializarSeccionesPorDefecto() {
        // 🔹 SOLO INICIALIZAR SI NO HAY SECCIONES
        if (listaSecciones.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String fechaActual = sdf.format(new Date());

            Log.e("seccionespordefecto", "duracionTotalMs" + duracionTotalMs);
            String tiempoFinal = convertirMilisegundosAString(duracionTotalMs);
            Log.e("seccionespordefecto", "tiempoFinal" + tiempoFinal);
            listaSecciones.add(new Seccion(0, "00:00.000", tiempoFinal, fechaActual, fechaActual));

            Log.d("DatosPrediccion", "Sección por defecto inicializada");
        }
    }

    private void ordenarSeccionesPorTiempo() {
        // 🔹 ORDENAR LAS SECCIONES POR TIEMPO DE INICIO
        Collections.sort(listaSecciones, (s1, s2) -> {
            int tiempo1 = convertirStringAMilisegundos(s1.getTiempoInicio());
            int tiempo2 = convertirStringAMilisegundos(s2.getTiempoInicio());
            return Integer.compare(tiempo1, tiempo2);
        });

        // 🔹 RECONSTRUIR LOS TIEMPOS PARA ASEGURAR CONTINUIDAD
        //reconstruirTiemposSecciones();

        Log.d("Ordenamiento", "Secciones ordenadas por tiempo de inicio");
        for (int i = 0; i < listaSecciones.size(); i++) {
            Seccion s = listaSecciones.get(i);
            Log.d("Ordenamiento", "Sección " + i + ": " + s.getTiempoInicio() + " - " + s.getTiempoFinal());
        }
    }

    @Override
    public void eliminarSeccion(int posicion) {
        /*if (posicion < 0 || posicion >= listaSecciones.size() - 1) return;

        // Guardar los tiempos de la sección que se eliminará
        String tiempoInicioEliminado = listaSecciones.get(posicion).getTiempoInicio();
        String tiempoFinalEliminado = listaSecciones.get(posicion).getTiempoFinal();

        // 1. Limpiar completamente los thumbs existentes
        limpiarThumbs();

        // Eliminar la sección
        listaSecciones.remove(posicion);

        // Actualizar la sección anterior (si existe)
        if (posicion > 0) {
            listaSecciones.get(posicion - 1).setTiempoFinal(tiempoFinalEliminado);
        }

        // Actualizar la sección siguiente (si existe y no es la última)
        if (posicion < listaSecciones.size() && posicion > 0) {
            listaSecciones.get(posicion).setTiempoInicio(tiempoInicioEliminado);
        }

        // 🔹 ORDENAR LAS SECCIONES DESPUÉS DE ELIMINAR
        ordenarSeccionesPorTiempo();

        // Verificar si estamos eliminando la sección seleccionada
        if (seccionSeleccionada == posicion) {
            setSeccionSeleccionada(-1);
        }

        setSeccionSeleccionada(-1);

        // Actualizar la UI
        seccionesAdapter.notifyDataSetChanged();

        // Forzar recarga de todos los thumbs
        cargarThumbsDesdeSecciones();*/
    }

    public void limpiarThumbs() {
        thumbsContainer.removeAllViews();
        thumbsList.clear();
    }

    public void cargarThumbsDesdeSecciones() {
        // 🔹 PRIMERO ORDENAR LAS SECCIONES
        /*ordenarSeccionesPorTiempo();

        // Guardar el índice seleccionado actual
        int seleccionPrevia = seccionSeleccionada;*/

        // 1. Limpiar thumbs existentes
        limpiarThumbs();

        // 2. Verificar dimensiones del SeekBar
        if (seekBar.getWidth() == 0) {
            seekBar.post(this::cargarThumbsDesdeSecciones);
            return;
        }

        seekBar.post(() -> {
            int seekBarWidth = seekBar.getWidth();
            int seekBarPaddingLeft = seekBar.getPaddingLeft();
            int seekBarPaddingRight = seekBar.getPaddingRight();
            int seekBarTrackWidth = seekBarWidth - seekBarPaddingLeft - seekBarPaddingRight;

            // 🔹 CORREGIDO: Asegurar que los thumbs se creen en orden correcto
            for (int i = 0; i < listaSecciones.size() - 1; i++) {
                Seccion seccion = listaSecciones.get(i);
                int tiempoFinMs = convertirStringAMilisegundos(seccion.getTiempoFinal());

                // 🔹 VERIFICAR QUE EL TIEMPO SEA VÁLIDO
                if (tiempoFinMs <= 0 || tiempoFinMs >= duracionTotalMs) {
                    Log.e("ThumbDebug", "Tiempo inválido para thumb " + i + ": " + tiempoFinMs);
                    continue;
                }

                float porcentaje = (float) tiempoFinMs / duracionTotalMs;

                // Calcular posición exacta
                int thumbPosition = seekBarPaddingLeft + (int)(porcentaje * seekBarTrackWidth) - 20;

                ImageView thumb = new ImageView(this);
                thumb.setImageResource(R.drawable.seekbar_thumb_datosmusic);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(40, 60);
                params.leftMargin = thumbPosition;

                // 🔹 CORREGIDO: Configurar color inicial
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    thumb.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_color)));
                }

                thumb.setLayoutParams(params);
                thumbsContainer.addView(thumb);
                thumbsList.add(thumb);

                Log.d("ThumbDebug", "Thumb " + i + " creado en posición: " + thumbPosition +
                        " (tiempo: " + seccion.getTiempoFinal() + ")");

                // Configurar listeners
                final int thumbIndex = i;
                thumb.setOnClickListener(v -> {
                    Log.d("ThumbClick", "Thumb " + thumbIndex + " clickeado");
                    setSeccionSeleccionada(thumbIndex);
                });
            }

            debugThumbPositions();
        });
    }

    public void setActualizandoDesdeThumb(boolean actualizando) {
        this.actualizandoDesdeThumb = actualizando;
        this.moviendoThumb = actualizando; // 🔹 Controlar también el movimiento
    }

    public void reconstruirTiemposSecciones() {
        // Asegurar que la primera sección comience en 00:00.000
        /*if (listaSecciones.isEmpty()) return;

        // Primera sección siempre comienza en 00:00.000
        listaSecciones.get(0).setTiempoInicio("00:00.000");

        // 🔹 ASEGURAR CONTINUIDAD ENTRE SECCIONES
        for (int i = 0; i < listaSecciones.size(); i++) {
            if (i < listaSecciones.size() - 1) {
                // El final de la sección actual debe coincidir con el inicio de la siguiente
                Seccion actual = listaSecciones.get(i);
                Seccion siguiente = listaSecciones.get(i + 1);

                // Solo actualizar si es necesario
                if (!actual.getTiempoFinal().equals(siguiente.getTiempoInicio())) {
                    actual.setTiempoFinal(siguiente.getTiempoInicio());
                }
            } else {
                // Última sección termina con la duración total
                String duracionTotal = convertirMilisegundosAString(duracionTotalMs);
                if (!listaSecciones.get(i).getTiempoFinal().equals(duracionTotal)) {
                    listaSecciones.get(i).setTiempoFinal(duracionTotal);
                }
            }
        }*/
    }

    @Override
    public int obtenerCantidadThumbs() {
        return thumbsList.size();
    }

    @Override
    public void actualizarTiempoDesdeEditText(int indexSeccion, String tiempo, boolean esTiempoInicio) {
        /*if (actualizandoDesdeThumb) return;

        if (!esTiempoValido(tiempo)) {
            Toast.makeText(this, "Formato de tiempo inválido", Toast.LENGTH_SHORT).show();
            return;
        }

        int tiempoMs = convertirStringAMilisegundos(tiempo);
        float porcentaje = (float) tiempoMs / duracionTotalMs;

        if (esTiempoInicio) {
            // Actualizar thumb correspondiente
            if (indexSeccion < thumbsList.size()) {
                ImageView thumb = thumbsList.get(indexSeccion);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) thumb.getLayoutParams();

                seekBar.post(() -> {
                    int seekBarWidth = seekBar.getWidth();
                    int seekBarPaddingLeft = seekBar.getPaddingLeft();
                    int seekBarPaddingRight = seekBar.getPaddingRight();
                    int seekBarTrackWidth = seekBarWidth - seekBarPaddingLeft - seekBarPaddingRight;

                    int thumbPosition = seekBarPaddingLeft + (int)(porcentaje * seekBarTrackWidth) - 20;
                    thumbPosition = Math.max(seekBarPaddingLeft - 20, Math.min(thumbPosition, seekBarWidth - seekBarPaddingRight - 20));

                    params.leftMargin = thumbPosition;
                    thumb.setLayoutParams(params);
                });
            }
        }*/
        // Forzar recarga para mantener los valores originales
        if (seccionesAdapter != null) {
            seccionesAdapter.notifyDataSetChanged();
        }
    }

    public void setSeccionSeleccionada(int index) {
        /*Log.d("ThumbDebug", "setSeccionSeleccionada llamado con índice: " + index);

        // 🔹 CORREGIDO: Sincronizar correctamente con el adapter
        this.seccionSeleccionada = index;

        // 🔹 ACTUALIZAR ESTADO VISUAL DE TODOS LOS THUMBS
        for (int i = 0; i < thumbsList.size(); i++) {
            ImageView thumb = thumbsList.get(i);
            boolean estaSeleccionado = (i == seccionSeleccionada);

            Log.d("ThumbDebug", "Thumb " + i + " - Seleccionado: " + estaSeleccionado);

            // 🔹 CAMBIAR COLOR PARA INDICAR SELECCIÓN (igual que en PopupDefinirSecciones)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int color = estaSeleccionado ?
                        ContextCompat.getColor(this, R.color.black) : // Color cuando está seleccionado
                        ContextCompat.getColor(this, R.color.icon_color); // Color normal

                thumb.setImageTintList(ColorStateList.valueOf(color));
            }

            // 🔹 CORREGIDO: Habilitar solo el thumb seleccionado para movimiento
            thumb.setEnabled(estaSeleccionado);
        }

        // 🔹 CORREGIDO: Habilitar SeekBar solo si no hay sección seleccionada
        seekBar.setEnabled(seccionSeleccionada == -1);

        // 🔹 CORREGIDO: Sincronizar con el adapter usando el método correcto
        if (seccionesAdapter != null) {
            seccionesAdapter.sincronizarSeleccion(seccionSeleccionada);
        }

        Log.d("ThumbDebug", "Sección seleccionada: " + seccionSeleccionada);*/
    }

    private void cerrarFocoDeEditTexts() {
        View currentFocus = getCurrentFocus();
        if (currentFocus instanceof EditText) {
            currentFocus.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacks(updateSeekBar);
    }

    private void debugThumbPositions() {
        Log.d("ThumbDebug", "=== POSICIONES DE THUMBS ===");
        for (int i = 0; i < thumbsList.size(); i++) {
            ImageView thumb = thumbsList.get(i);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) thumb.getLayoutParams();
            Log.d("ThumbDebug", "Thumb " + i + ": leftMargin = " + params.leftMargin +
                    ", X = " + thumb.getX() + ", Enabled = " + thumb.isEnabled());
        }

        Log.d("ThumbDebug", "=== TIEMPOS DE SECCIONES ===");
        for (int i = 0; i < listaSecciones.size(); i++) {
            Seccion s = listaSecciones.get(i);
            Log.d("ThumbDebug", "Sección " + i + ": " + s.getTiempoInicio() + " - " + s.getTiempoFinal());
        }
    }

    // Método para convertir V/A a emociones de Plutchik
    private String convertirValenceArousalAEmocion(float valence, float arousal) {
        // Calcular ángulo en el espacio circunflejo (en radianes)
        double angle = Math.atan2(arousal, valence);

        // Convertir a grados [0, 360)
        double angleDeg = (Math.toDegrees(angle) + 360) % 360;

        // Calcular intensidad (distancia desde el origen)
        double intensity = Math.sqrt(valence * valence + arousal * arousal);

        // Si la intensidad es muy baja, considerar como Trust
        if (intensity < 0.2) {
            return "trust";
        }

        if (angleDeg >= 330.0 || angleDeg < 60.0) {
            return "joy";
        } else if (angleDeg >= 60.0 && angleDeg < 90.0) {
            return "trust";
        } else if (angleDeg >= 90.0 && angleDeg < 120.0) {
            return "fear";
        } else if (angleDeg >= 120.0 && angleDeg < 150.0) {
            return "surprise";
        } else if (angleDeg >= 150.0 && angleDeg < 210.0) {
            return "sadness";
        } else if (angleDeg >= 210.0 && angleDeg < 240.0) {
            return "disgust";
        } else if (angleDeg >= 240.0 && angleDeg < 270.0) {
            return "anger";
        } else if (angleDeg >= 270.0 && angleDeg < 330.0) {
            return "anticipation";
        }

        return "neutral";
    }

    private void actualizarEmocionSegunProgreso(float segundosActuales) {
        Log.d("Predicciones", "Buscando emoción para segundo: " + segundosActuales);

        if (listaSecciones == null) {
            Log.e("Predicciones", "listaSecciones es NULL en actualizarEmocionSegunProgreso");
            tvEmocionActual.setText("Error: No hay datos de emociones");
            return;
        }

        Log.d("Predicciones", "Total de secciones disponibles: " + listaSecciones.size());

        for (Seccion s : listaSecciones) {
            // Convertir los tiempos de String a segundos float
            float inicioSeg = convertirFormatoATiempoEnSegundos(s.getTiempoInicio());
            float finSeg = convertirFormatoATiempoEnSegundos(s.getTiempoFinal());

            Log.d("Predicciones", "Comparando: " + segundosActuales + " vs [" +
                    inicioSeg + " - " + finSeg + "]");

            if (segundosActuales >= inicioSeg && segundosActuales < finSeg) {
                float val = (float) s.getValence();
                float aro = (float) s.getArousal();

                Log.d("Predicciones", "Sección encontrada! V=" + val + ", A=" + aro);

                // Usar el nuevo método basado en reglas
                String emocion = convertirValenceArousalAEmocion(val, aro);
                String emocionEspanol = traducirEmocion(emocion);
                List<EmocionSeleccionada> listaPalabras = Collections.singletonList(obtenerPalabraPorCategoria(val, aro));

                if (listaPalabras != null) {
                    Log.d("Predicciones", "Palabras representativas encontradas: " + listaPalabras.size());
                    s.setEmociones(listaPalabras); // ✅ ahora sí es List<EmocionSeleccionada>
                } else {
                    Log.d("Predicciones", "No hay palabra única representativa para V=" + val + ", A=" + aro);
                }

                String texto = String.format("Posible emoción detectada: " + emocionEspanol + "\nPositividad = " + val + "\nIntensidad = " + aro);

                tvEmocionActual.setText(texto);

                // 🟢 Aplica color y borde según la emoción detectada
                aplicarColorYBordeATexto(tvEmocionActual, emocion);
                Log.d("Predicciones", "Emoción mostrada: " + texto);
                return;
            }
        }

        // Si no se encontró ninguna sección
        Log.d("Predicciones", "No se encontró sección para el segundo: " + segundosActuales);
        tvEmocionActual.setText("Posible emoción detectada: No disponible");
        tvEmocionActual.setTextColor(Color.GRAY);
    }

    private float convertirFormatoATiempoEnSegundos(String tiempo) {
        try {
            String[] partes = tiempo.split(":");
            int minutos = Integer.parseInt(partes[0]);
            float segundos = Float.parseFloat(partes[1]);
            return minutos * 60 + segundos;
        } catch (Exception e) {
            e.printStackTrace();
            return 0f;
        }
    }

    private String traducirEmocion(String emocionIngles) {
        switch (emocionIngles.toLowerCase()) {
            case "joy": return "alegría\nSugerencia: Útil para comenzar o cerrar sesiones con energía positiva, o reforzar logros del estudiante.";
            case "sadness": return "tristeza\nSugerencia: Recomendable para sesiones introspectivas o de expresión emocional contenida.";
            case "trust": return "confianza\nSugerencia: Favorece sensación de seguridad y conexión: ideal para dinámicas grupales o de autoestima.";
            case "disgust": return "disgusto\nSugerencia: Útil en actividades de reconocimiento o liberación de emociones negativas.";
            case "fear": return "miedo\nSugerencia: Puede utilizarse para explorar emociones difíciles en contextos controlados o de consciencia de sensaciones.";
            case "anger": return "ira\nSugerencia: Adecuada para trabajar el manejo de frustración o la canalización de energía intensa.";
            case "surprise": return "sorpresa\nSugerencia: Apropiada para actividades creativas o cuando se busca estimular la curiosidad.";
            case "anticipation": return "anticipación\nSugerencia: Ideal para preparar al estudiante antes de actividades importantes o cambios de rutina.";
            case "neutral": return "neutral";
            default: return emocionIngles;
        }
    }

    private void aplicarColorYBordeATexto(TextView textView, String emocionIngles) {
        int colorTexto;
        switch (emocionIngles.toLowerCase()) {
            case "anger": colorTexto = Color.parseColor("#d40001"); break;      // Rojo
            case "fear": colorTexto = Color.parseColor("#12936c"); break;       // Turquesa
            case "joy": colorTexto = Color.parseColor("#fed401"); break;        // Amarillo
            case "sadness": colorTexto = Color.parseColor("#7b06ea"); break;    // Morado
            case "anticipation": colorTexto = Color.parseColor("#ff7901"); break; // Naranja
            case "surprise": colorTexto = Color.parseColor("#2b7ffe"); break;   // Azul
            case "trust": colorTexto = Color.parseColor("#28a000"); break;      // Verde
            case "disgust": colorTexto = Color.parseColor("#ae0be5"); break;    // Rosado púrpura
            default: colorTexto = Color.WHITE; break;
        }

        // 🌈 Color principal
        textView.setTextColor(colorTexto);

        // 🖤 Borde negro (simulado con sombra centrada)
        textView.setShadowLayer(5f, 0f, 0f, Color.BLACK);
    }
}