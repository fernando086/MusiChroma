package com.example.intentoappdatosmusica;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PopupDefinirSecciones implements SeccionesAdapter.SeccionEditTextListener {
    private static final int MAX_SECCIONES = 10;
    private Context context;
    private Dialog dialog;
    private List<Seccion> seccionesSinInternet;

    private int usuario_id_usado, song_id_usado; //USADO PARA ENVIAR SECCIONES NUEVAS HACIA LA BASE DE DATOS

    private List<Seccion> seccionesStrCache; // Nueva copia interna

    public SeekBar getSeekBarPopup() {
        return seekBarPopup;
    }

    private int duracionCrudaDeMediaPlayer;

    private SeekBar seekBarPopup;
    private TextView tvProgress, tvDuration;
    private Button btnCancelar, btnAgregarSeccion, btnConfirmar;

    public RecyclerView getRecyclerViewSecciones() {
        return recyclerViewSecciones;
    }

    private RecyclerView recyclerViewSecciones;
    private SeccionesAdapter seccionesAdapter;
    private List<Seccion> listaSecciones = new ArrayList<>();

    private List<Seccion> seccionesOriginales = null; // guardada al abrir el popup, sirve para comparar si hubo cambios

    private Handler handler = new Handler(); // 🔹 Agregado para actualización en tiempo real
    private Runnable updateSeekBarRunnable;

    private RelativeLayout thumbsContainer;  // Contenedor donde estarán los thumbs adicionales
    private List<ImageView> thumbsList = new ArrayList<>(); // Lista de thumbs adicionales
    private int seccionSeleccionada = -1; // Índice de la sección seleccionada (para mover thumb)
    private boolean actualizandoDesdeThumb = false;

    private OnSeccionesConfirmadasListener listener;

    public boolean isActualizandoDesdeThumb() {
        return actualizandoDesdeThumb;
    }

    public void setActualizandoDesdeThumb(boolean actualizandoDesdeThumb) {
        this.actualizandoDesdeThumb = actualizandoDesdeThumb;
    }

    public PopupDefinirSecciones(Context context, List<Seccion> seccionesSinInternet, List<Seccion> seccionesStrCache) {
        this.context = context;
        this.seccionesSinInternet = seccionesSinInternet;
        this.seccionesStrCache = seccionesStrCache;
    }

    // Agrega este getter en PopupDefinirSecciones
    public TextView getTvDuration() {
        return tvDuration;
    }

    public int getSeccionSeleccionada() {
        return seccionSeleccionada;
    }

    public void setSeccionSeleccionada(int seccionSeleccionada) {
        this.seccionSeleccionada = seccionSeleccionada;

        for (int i = 0; i < thumbsList.size(); i++) {
            thumbsList.get(i).setEnabled(i == seccionSeleccionada);
        }

        seekBarPopup.setEnabled(seccionSeleccionada == -1);

        // 🔹 SINCRONIZAR con el adapter
        if (seccionesAdapter != null) {
            seccionesAdapter.sincronizarSeleccion(seccionSeleccionada);
        }
    }

    public void setOnSeccionesConfirmadasListener(OnSeccionesConfirmadasListener listener) {
        this.listener = listener;
    }

    public void mostrarDialogo(int songId, int usuario_id) {
        dialog = new Dialog(context);
        dialog.setContentView(R.layout.popup_definir_secciones);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true); // Permite que el usuario cierre el diálogo tocando fuera de él

        song_id_usado = songId;

        usuario_id_usado = usuario_id;

        // Inicializar vistas
        seekBarPopup = dialog.findViewById(R.id.seekBar_popup);
        tvProgress = dialog.findViewById(R.id.tv_progress_popup);
        tvDuration = dialog.findViewById(R.id.tv_duration_popup);
        recyclerViewSecciones = dialog.findViewById(R.id.rv_secciones);
        btnCancelar = dialog.findViewById(R.id.btn_cancelar);
        btnAgregarSeccion = dialog.findViewById(R.id.btn_agregar_seccion);
        btnConfirmar = dialog.findViewById(R.id.btn_confirmar);

        thumbsContainer = dialog.findViewById(R.id.thumbs_container);

        // Configurar el botón de play/pause
        ImageButton btnPlayPause = dialog.findViewById(R.id.btn_play_pause_popup);

        Switch switchEditar = dialog.findViewById(R.id.switch_editar_inicio_o_fin);
        switchEditar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cerrarFocoDeEditTexts(); // <- ⬅️ Agregado aquí
            seccionesAdapter.setModoEditarFin(isChecked);
        });

        recyclerViewSecciones.setLayoutManager(new LinearLayoutManager(context));
        seccionesAdapter = new SeccionesAdapter(listaSecciones, this, true);
        recyclerViewSecciones.setAdapter(seccionesAdapter);

        // Obtener instancia del reproductor
        MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();
        // 🔹 Asegurar que la información de la canción actual se use siempre
        mediaPlayerList.notifySongStateChanged(songId);
        mediaPlayerList.notifyProgressChanged(songId, mediaPlayerList.getCurrentPosition(songId));
        // Si las 2 líneas anteriores no están, el popup muestra datos de otra canción que no corresponde

        Log.e("PopupDefinirSecciones", "Recibiendo songId = " + songId);  // 🔹 Debug para verificar el ID de la canción

        // Obtener el progreso actual y la duración
        int currentPosition = mediaPlayerList.getCurrentPosition(songId);
        int duration = mediaPlayerList.getDuration(songId);
        duracionCrudaDeMediaPlayer = duration;

        // 🔹 Verificar si los valores son correctos
        Log.e("PopupDefinirSecciones", "Progreso inicial: " + currentPosition + " / " + duration);

        // Configurar el SeekBar y los TextView
        seekBarPopup.setMax(duration);
        seekBarPopup.setProgress(0); // 🔹 Reiniciar el SeekBar antes de actualizar
        seekBarPopup.post(() -> seekBarPopup.setProgress(currentPosition));
        tvProgress.setText(convertirMilisegundosAString(currentPosition));
        tvDuration.setText(convertirMilisegundosAString(MediaPlayerList.getInstance().getDuration(songId)));

        MediaPlayerList.getInstance().getSongProgressLiveData(songId).observe((LifecycleOwner) context, progress -> {
            if (seekBarPopup != null) {
                seekBarPopup.setProgress(progress);
                tvProgress.setText(convertirMilisegundosAString(progress));
            }
        });

        // Para que al abrir el dialog el botón de play/pause aparezca según el estado de canción
        if (mediaPlayerList.isPlaying(songId)) {
            btnPlayPause.setImageResource(R.drawable.iconopause);
        } else {
            btnPlayPause.setImageResource(R.drawable.iconoplay);
        }

        // Listener para el botón de play/pause
        btnPlayPause.setOnClickListener(v -> {
            if (mediaPlayerList.isPlaying(songId)) {
                mediaPlayerList.pause(songId);
                btnPlayPause.setImageResource(R.drawable.iconoplay);
            } else {
                mediaPlayerList.pauseAllExcept(songId);
                mediaPlayerList.play(songId);
                btnPlayPause.setImageResource(R.drawable.iconopause);
            }

            mediaPlayerList.notifySongStateChanged(songId);
            mediaPlayerList.notifyProgressChanged(songId, mediaPlayerList.getCurrentPosition(songId));
        });

        // Listener para el SeekBar
        seekBarPopup.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaPlayerList.seekTo(songId, progress);
                    tvProgress.setText(convertirMilisegundosAString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        if (updateSeekBarRunnable != null) {
            handler.removeCallbacks(updateSeekBarRunnable); // 🔹 Detener el Runnable anterior antes de iniciarlo nuevamente
        }

        // 🔹 Actualizar el SeekBar y contador en tiempo real con Handler
        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                int newProgress = mediaPlayerList.getCurrentPosition(songId);
                seekBarPopup.post(() -> {
                    seekBarPopup.setProgress(newProgress);
                    tvProgress.setText(convertirMilisegundosAString(newProgress));
                });
                handler.postDelayed(this, 100); // 🔹 Actualiza cada 500ms
            }
        };
        handler.post(updateSeekBarRunnable);

        // Agregar la primera sección (toda la canción)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String fechaActual = sdf.format(new Date());
        listaSecciones.add(new Seccion(0, "00:00.000", convertirMilisegundosAString(duration), fechaActual, fechaActual));
        seccionesAdapter.notifyDataSetChanged();

        // Configurar botones
        btnCancelar.setOnClickListener(v -> cerrarDialogo());
        btnAgregarSeccion.setOnClickListener(v -> {
            cerrarFocoDeEditTexts();
            agregarNuevaSeccion();
        });

        btnConfirmar.setOnClickListener(v -> {
            cerrarFocoDeEditTexts();
            confirmarSecciones();
        });

        // Configurar botón de retroceder
        ImageButton btnRewind = dialog.findViewById(R.id.btn_rewind_popup);
        btnRewind.setOnClickListener(v -> {
            MediaPlayerList.getInstance().rewind(songId, 5); // Retrocede 5 segundos
        });

        // Configurar botón de adelantar
        ImageButton btnForward = dialog.findViewById(R.id.btn_forward_popup);
        btnForward.setOnClickListener(v -> {
            MediaPlayerList.getInstance().forward(songId, 5); // Adelanta 5 segundos
        });

        cargarSeccionesDesdeServidor(songId);
        dialog.show();
    }

    private void cargarSeccionesDesdeServidor(int songId) {
        if (seccionesStrCache != null && !seccionesStrCache.isEmpty()) {
            Log.e("PopupDefinirSecciones", "Cargando secciones desde cache");
            cargarSeccionesDesdeCache();
            return;
        }

        Log.e("PopupDefinirSecciones", "seccionesStrCache vacío, llamando a BD");

        ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);
        Call<SeccionesResponse> call = apiService.obtenerSecciones(songId);

        call.enqueue(new Callback<SeccionesResponse>() {
            @Override
            public void onResponse(Call<SeccionesResponse> call, Response<SeccionesResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listaSecciones.clear();  // 🔹 Limpiar lista antes de agregar nuevas secciones
                    if (seccionesOriginales == null) {
                        seccionesOriginales = new ArrayList<>();
                    } else {
                        seccionesOriginales.clear();
                    }

                    List<Seccion> secciones = response.body().getSecciones();
                    listaSecciones.addAll(secciones);
                    // Copiar datos extra desde seccionesSinInternet
                    for (Seccion seccionNueva : listaSecciones) {
                        for (Seccion seccionPrev : seccionesSinInternet) {
                            boolean mismaSeccion = seccionNueva.getTiempoInicio().equals(seccionPrev.getTiempoInicio())
                                    && seccionNueva.getTiempoFinal().equals(seccionPrev.getTiempoFinal());

                            if (mismaSeccion) {
                                seccionNueva.setNombre(seccionPrev.getNombre());
                                seccionNueva.setComentario(seccionPrev.getComentario());
                                seccionNueva.setPublicado(seccionPrev.isPublicado());
                                seccionNueva.setEmociones(seccionPrev.getEmociones()); // si tienes getter/setter
                                seccionNueva.setGeneros(seccionPrev.getGeneros());     // si tienes getter/setter
                                break;
                            }
                        }
                    }

                    for (Seccion s : secciones) {
                        seccionesOriginales.add(new Seccion(s.getId(), s.getTiempoInicio(), s.getTiempoFinal(), s.getFecha_creacion(), s.getFecha_ultima_edicion()));
                    }

                    seccionesStrCache = new ArrayList<>(listaSecciones); // ✅ Copia defensiva
                    Log.e("seccionesStrCache", "Secciones cacheadas: " + seccionesStrCache.size() + " -> " + seccionesStrCache);

                    cargarThumbsDesdeSecciones();
                    seccionesAdapter.notifyDataSetChanged(); // 🔹 Actualizar RecyclerView
                    Log.e("PopupDefinirSecciones", "✅ Secciones cargadas desde servidor");

                    if (listener != null) {
                        listener.onSeccionesConfirmadas(listaSecciones.size(), seccionesStrCache, false); //seccionesStrCache es para evitar que la app llame a cargarSeccionesServidor a cada rato
                    }
                } else {
                    Log.e("PopupDefinirSecciones", "❌ Error en la respuesta de la API");
                    cargarSeccionesOffline();
                }
            }

            @Override
            public void onFailure(Call<SeccionesResponse> call, Throwable t) {
                Log.e("PopupDefinirSecciones", "⚠️ Error en la solicitud: " + t.getMessage());
                if (seccionesSinInternet != null && !seccionesSinInternet.isEmpty()) {
                    Log.e("PopupDefinirSecciones", "Cargando secciones desde archivo local");
                    cargarSeccionesOffline();
                } else {
                    Log.e("PopupDefinirSecciones", "No hay secciones locales para mostrar");
                }
            }
        });
    }

    private void cargarSeccionesOffline() {
        if (seccionesSinInternet != null && !seccionesSinInternet.isEmpty()) {
            listaSecciones.clear();
            if (seccionesOriginales == null) {
                seccionesOriginales = new ArrayList<>();
            } else {
                seccionesOriginales.clear();
            }

            // Ya no se hace split. Se copia directamente
            for (Seccion s : seccionesSinInternet) {
                listaSecciones.add(s);
                seccionesOriginales.add(new Seccion(
                        s.getId(), s.getTiempoInicio(), s.getTiempoFinal(),
                        s.getFecha_creacion(), s.getFecha_ultima_edicion()
                ));
            }

            cargarThumbsDesdeSecciones();
            seccionesAdapter.notifyDataSetChanged();

            // 🔁 Asignar el cache para evitar recarga
            seccionesStrCache = new ArrayList<>(listaSecciones); // ✅ Copia defensiva
            Log.e("seccionesStrCache", "Secciones cacheadas: " + seccionesStrCache.size() + " -> " + seccionesStrCache);

            Log.e("PopupDefinirSecciones", "✅ Secciones cargadas desde archivo local");
            Toast.makeText(context, "Sin conexión. Se cargaron los datos locales.", Toast.LENGTH_SHORT).show();

            if (listener != null) {
                listener.onSeccionesConfirmadas(listaSecciones.size(), seccionesStrCache, false); //seccionesStrCache es para evitar que la app llame a cargarSeccionesServidor a cada rato
            }
        } else {
            Log.e("PopupDefinirSecciones", "🚫 No hay secciones locales para mostrar");
        }
    }

    private void cargarSeccionesDesdeCache() {
        listaSecciones.clear();
        if (seccionesOriginales == null) {
            seccionesOriginales = new ArrayList<>();
        } else {
            seccionesOriginales.clear();
        }

        if (seccionesStrCache != null) {
            for (Seccion s : seccionesStrCache) {
                listaSecciones.add(s);
                seccionesOriginales.add(new Seccion(
                        s.getId(), s.getTiempoInicio(), s.getTiempoFinal(),
                        s.getFecha_creacion(), s.getFecha_ultima_edicion()
                ));
            }
        }

        cargarThumbsDesdeSecciones();
        seccionesAdapter.notifyDataSetChanged();
        Log.e("PopupDefinirSecciones", "Secciones cargadas desde cache (ya confirmadas)");
    }

    public void cargarThumbsDesdeSecciones() {
        // Guardar la selección actual antes de recrear
        int seleccionPrevia = seccionSeleccionada;

        // 1. Limpiar thumbs existentes
        limpiarThumbs();

        // 2. Verificar dimensiones del SeekBar
        if (seekBarPopup.getWidth() == 0) {
            seekBarPopup.post(this::cargarThumbsDesdeSecciones);
            return;
        }

        seekBarPopup.post(() -> {
            int seekBarWidth = seekBarPopup.getWidth();
            int seekBarPaddingLeft = seekBarPopup.getPaddingLeft();
            int seekBarPaddingRight = seekBarPopup.getPaddingRight();
            int seekBarTrackWidth = seekBarWidth - seekBarPaddingLeft - seekBarPaddingRight;

            for (int i = 0; i < listaSecciones.size() - 1; i++) {
                Seccion seccion = listaSecciones.get(i);
                int tiempoFinMs = convertirStringAMilisegundos(seccion.getTiempoFinal());
                float porcentaje = (float) tiempoFinMs / seekBarPopup.getMax();

                // Calcular posición exacta
                int thumbPosition = seekBarPaddingLeft + (int)(porcentaje * seekBarTrackWidth) - 20;

                ImageView thumb = new ImageView(context);
                thumb.setImageResource(R.drawable.seekbar_thumb_datosmusic);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(40, 60);
                params.leftMargin = thumbPosition;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    thumb.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_color)));
                }

                thumb.setLayoutParams(params);
                thumbsContainer.addView(thumb);
                thumbsList.add(thumb);

                // 🔹 RESTAURAR la selección después de recrear
                if (i == seleccionPrevia) {
                    setSeccionSeleccionada(seleccionPrevia);
                }

                final int thumbIndex = i;
                thumb.setOnClickListener(v -> setSeccionSeleccionada(thumbIndex));
                configurarListenerTouch(thumb, seekBarWidth, seekBarPaddingLeft, seekBarPaddingRight, seekBarTrackWidth);
            }

            // 🔹 Asegurar que el estado final sea correcto
            if (seccionesAdapter != null) {
                seccionesAdapter.setSeccionSeleccionada(seccionSeleccionada);
            }
        });
    }

    private int generarNuevoSeccionId() {
        int maxId = 0;
        for (Seccion s : listaSecciones) {
            if (s.getId() > maxId) {
                maxId = s.getId();
            }
        }
        return maxId + 1;
    }

    private void agregarNuevaSeccion() {
        //GENERAR NUEVO SECCION_ID UTILIZANDO EL SECCION_ID MÁS ALTO DE ESTA CANCIÓN DESPUÉS DE USAR ALGUNO DE LOS 3 CARGARSECCIONES
        //ESTE NUEVO SECCION_ID GENERADO NO SE ENVÍA HACIA LA BASE DE DATOS, SOLO MANEJAR EN ANDROID STUDIO
        String fechaActual = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());

        if (listaSecciones.isEmpty()) {
            Log.e("agregarNuevaSeccion", "LISTA VACIA");
            return;
        } else if (listaSecciones.size() >= MAX_SECCIONES) {
            Toast.makeText(context, "Limite máximo de secciones alcanzado: " + MAX_SECCIONES, Toast.LENGTH_SHORT).show();
            return;
        }

        int posicionActual = seekBarPopup.getProgress();
        String tiempoFormateado = convertirMilisegundosAString(posicionActual);

        if (!esTiempoValido(tiempoFormateado)) {
            Log.e("estiempovalido", "false");
            return;
        }

        // 🔹 CASO ESPECIAL: solo una sección
        if (listaSecciones.size() == 1) {
            Seccion unica = listaSecciones.get(0);
            int inicioMs = convertirStringAMilisegundos(unica.getTiempoInicio());
            int finMs = convertirStringAMilisegundos(unica.getTiempoFinal());

            if (posicionActual > inicioMs && posicionActual < finMs) {
                int nuevoId = generarNuevoSeccionId();
                Seccion nueva = new Seccion(nuevoId, tiempoFormateado, unica.getTiempoFinal(), fechaActual, fechaActual);
                //Como es sección nueva, debería tomarse el máximo getId de listaSecciones y aumentar en 1
                //posiblemente sea necesario hacer que el mét0do getSecciones del servidor retorne el máximo id de seccion que dicho usuario haya definido para que no haya cruce de seccion id con otras canciones del mismo usuario
                //si por alguna razón los songId quedan desordenados tras agregar o eliminar secciones, no debería ser preocupación porque muchos usuarios están ingresando id en seccion al mismo tiempo.
                unica.setTiempoFinal(tiempoFormateado);
                listaSecciones.add(nueva);

                agregarThumb((float) posicionActual / seekBarPopup.getMax());
                seccionesAdapter.notifyDataSetChanged();
                return;
            }
        }

        // Buscar en qué parte de la lista de secciones insertar la nueva sección
        for (int i = 0; i < listaSecciones.size() - 1; i++) {
            int tiempoInicio = convertirStringAMilisegundos(listaSecciones.get(i).getTiempoInicio());
            int tiempoFinal = convertirStringAMilisegundos(listaSecciones.get(i + 1).getTiempoFinal());

            if (posicionActual > tiempoInicio && posicionActual < tiempoFinal) {
                // Insertar nueva sección en la posición correcta
                int nuevoId = generarNuevoSeccionId();
                Seccion nuevaSeccion = new Seccion(nuevoId, tiempoFormateado, listaSecciones.get(i + 1).getTiempoInicio(), fechaActual, fechaActual);
                //Como es sección nueva, debería tomarse el máximo getId de listaSecciones y aumentar en 1
                //posiblemente sea necesario hacer que el mét0do getSecciones del servidor retorne el máximo id de seccion que dicho usuario haya definido para que no haya cruce de seccion id con otras canciones del mismo usuario
                //si por alguna razón los songId quedan desordenados tras agregar o eliminar secciones, no debería ser preocupación porque muchos usuarios están ingresando id en seccion al mismo tiempo.
                listaSecciones.get(i).setTiempoFinal(tiempoFormateado);
                listaSecciones.add(i + 1, nuevaSeccion);

                // 🔹 Reordenar la lista para evitar desorden en los tiempos
                Collections.sort(listaSecciones, Comparator.comparing(s -> convertirStringAMilisegundos(s.getTiempoInicio())));

                // 🔹 Asegurar que cada sección tenga el tiempo de fin correcto
                for (int j = 0; j < listaSecciones.size() - 1; j++) {
                    listaSecciones.get(j).setTiempoFinal(listaSecciones.get(j + 1).getTiempoInicio());
                }
                // 🔹 Asegurar que la última sección siempre termine en la duración total de la canción
                listaSecciones.get(listaSecciones.size() - 1).setTiempoFinal(tvDuration.getText().toString());

                // Agregar thumb en la posición correcta
                float porcentaje = (float) posicionActual / seekBarPopup.getMax();
                Log.e("Popup_agregarNueva", "porcentaje = " + porcentaje);
                agregarThumb(porcentaje);

                seccionesAdapter.notifyDataSetChanged();
                return;
            }
        }
    }

    private void agregarThumb(float porcentaje) {
        ImageView thumb = new ImageView(context);
        thumb.setImageResource(R.drawable.seekbar_thumb_datosmusic); // 🔹 Usa un drawable similar al thumb original
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(40, 60); // 🔹 Tamaño del thumb

        // 🔹 Obtener ancho del seekbar completo
        seekBarPopup.post(() -> {
            // Convertir las variables en arrays finales de un elemento
            final int[] seekBarDimensions = new int[4]; // [0]=width, [1]=paddingLeft, [2]=paddingRight, [3]=trackWidth
            seekBarDimensions[0] = seekBarPopup.getWidth();
            seekBarDimensions[1] = seekBarPopup.getPaddingLeft();
            seekBarDimensions[2] = seekBarPopup.getPaddingRight();
            seekBarDimensions[3] = seekBarDimensions[0] - seekBarDimensions[1] - seekBarDimensions[2];

            // Calcular posición exacta considerando el padding
            int thumbPosition = seekBarDimensions[1] + (int)(porcentaje * seekBarDimensions[3]) - (20); // 20 = mitad de 40 (ancho del thumb)

            // Asegurarnos que no salga de los límites
            thumbPosition = Math.max(seekBarDimensions[1] - 20, Math.min(thumbPosition, seekBarDimensions[0] - seekBarDimensions[2] - 20));

            params.leftMargin = thumbPosition;
            thumb.setLayoutParams(params);

            thumbsContainer.addView(thumb);
            thumbsList.add(thumb);

            // Configurar el listener de touch con las dimensiones correctas
            configurarListenerTouch(thumb, seekBarDimensions[0], seekBarDimensions[1], seekBarDimensions[2], seekBarDimensions[3]);
            cargarThumbsDesdeSecciones();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void configurarListenerTouch(ImageView thumb, int seekBarWidth, int seekBarPaddingLeft, int seekBarPaddingRight, int seekBarTrackWidth) {
        final float[] initialTouchX = new float[1];
        final float[] initialThumbX = new float[1];

        thumb.setOnTouchListener((v, event) -> {
            int index = thumbsList.indexOf(thumb);
            if (seccionSeleccionada != index) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    bloquearControlesSeccion(false);
                    initialTouchX[0] = event.getRawX();
                    initialThumbX[0] = thumb.getX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    bloquearControlesSeccion(true);

                    float deltaX = event.getRawX() - initialTouchX[0];
                    float newX = initialThumbX[0] + deltaX;

                    // Límites absolutos (el track visible completo)
                    float minAbsoluteX = seekBarPaddingLeft - 20f;
                    float maxAbsoluteX = (seekBarWidth - seekBarPaddingRight) - 20f;

                    // Límites relativos (entre thumbs adyacentes)
                    float minX = minAbsoluteX;
                    float maxX = maxAbsoluteX;

                    if (index > 0) {
                        View prevThumb = thumbsList.get(index - 1);
                        minX = prevThumb.getX() + thumb.getWidth();
                    }
                    if (index < thumbsList.size() - 1) {
                        View nextThumb = thumbsList.get(index + 1);
                        maxX = nextThumb.getX() - thumb.getWidth();
                    }

                    newX = Math.max(minX, Math.min(newX, maxX));
                    thumb.setX(newX);

                    // Convertir posición X a milisegundos
                    float relativePos = (newX - minAbsoluteX) / (maxAbsoluteX - minAbsoluteX);
                    int nuevaPosicion = (int)(relativePos * seekBarPopup.getMax());

                    actualizarTiemposSeccion(index, nuevaPosicion);
                    return true;

                case MotionEvent.ACTION_UP:
                    // 🔹 CORREGIDO: No reconstruir todos los thumbs, solo actualizar posición
                    // Mantener la selección actual del RadioButton
                    int seleccionPrevia = seccionSeleccionada;

                    // Forzar reconstrucción completa al soltar
                    cargarThumbsDesdeSecciones();

                    // Restaurar la selección
                    setSeccionSeleccionada(seleccionPrevia);
                    bloquearControlesSeccion(false); // 🔓 Desbloquear todos los controles al soltar el thumb
                    return true;
            }
            return false;
        });
    }

    private void actualizarTiemposSeccion(int index, int nuevaPosicion) {
        setActualizandoDesdeThumb(true); // 🔹 Inicia protección contra ciclo
        cerrarFocoDeEditTexts(); // ✅ Evita conflictos si hay un EditText activo

        String fechaActual = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());

        if (index >= 0 && index < listaSecciones.size() - 1) {
            String nuevoTiempo = convertirMilisegundosAString(nuevaPosicion);
            // 🔹 Asegurar que no sobrepase los límites de las otras secciones
            Seccion seccionActual = listaSecciones.get(index);
            Seccion seccionSiguiente = listaSecciones.get(index + 1);

            int tiempoInicioAnterior = convertirStringAMilisegundos(seccionActual.getTiempoInicio());
            int tiempoFinalSiguiente = convertirStringAMilisegundos(seccionSiguiente.getTiempoFinal());

            if (nuevaPosicion > tiempoInicioAnterior && nuevaPosicion < tiempoFinalSiguiente) {
                seccionActual.setTiempoFinal(nuevoTiempo);
                seccionSiguiente.setTiempoInicio(nuevoTiempo);

                seccionActual.setFecha_ultima_edicion(fechaActual);
                seccionSiguiente.setFecha_ultima_edicion(fechaActual);

                // 🔹 USANDO POST (SI NO SE USA POST ENTONCES HAY CRASH)
                recyclerViewSecciones.post(() -> {
                    seccionesAdapter.notifyItemChanged(index);
                    seccionesAdapter.notifyItemChanged(index + 1);
                });
            }
        }

        setActualizandoDesdeThumb(false); // 🔹 Finaliza protección
    }

    public void eliminarThumb(int posicion) {
        if (posicion >= 0 && posicion < thumbsList.size()) {
            ImageView thumbAEliminar = thumbsList.get(posicion);
            thumbsContainer.removeView(thumbAEliminar);
            thumbsList.remove(posicion);

            // Recrear todos los thumbs para asegurar consistencia
            cargarThumbsDesdeSecciones();
        }
    }

    public int obtenerCantidadThumbs() {
        return thumbsList.size();
    }

    private boolean hayCambiosEnSecciones() {
        if (seccionesOriginales.size() != listaSecciones.size()) {
            return true;
        }

        for (int i = 0; i < listaSecciones.size(); i++) {
            Seccion nueva = listaSecciones.get(i);
            Seccion original = seccionesOriginales.get(i);

            if (nueva.getId() != original.getId()
                    || !nueva.getTiempoInicio().equals(original.getTiempoInicio())
                    || !nueva.getTiempoFinal().equals(original.getTiempoFinal())) {
                return true;
            }
        }
        return false;
    }

    private void confirmarSecciones() {
        if (!hayCambiosEnSecciones()) {
            // No hubo cambios → cerrar sin hacer nada
            Log.e("confirmarSecciones", "SIN CAMBIOS");
            Log.e("seccionesOriginales: ", seccionesOriginales.toString());
            Log.e("listaSecciones: ", listaSecciones.toString());
            dialog.dismiss();
            return;
        }

        // Sí hubo cambios → mostrar diálogo de confirmación
        new AlertDialog.Builder(context)
                .setTitle("Confirmar cambios")
                .setMessage("¿Deseas guardar los cambios en las secciones?")
                .setPositiveButton("Sí", (dialogInterface, which) -> {
                    // Construir seccionesStr y notificar al listener
                    if (listener != null) {
                        // Crear JSON para enviar
                        JsonArray nuevasSeccionesJson = new JsonArray();
                        for (Seccion s : listaSecciones) {
                            JsonObject seccionJson = new JsonObject();
                            seccionJson.addProperty("id", s.getId());  // id de la sección (si es nueva, puede ser -1)
                            seccionJson.addProperty("tiempo_inicio", s.getTiempoInicio());
                            seccionJson.addProperty("tiempo_final", s.getTiempoFinal());
                            nuevasSeccionesJson.add(seccionJson);
                        }

                        JsonObject payload = new JsonObject();
                        payload.addProperty("cancion_id", song_id_usado); // ⚠️ Asegúrate de que esta variable esté inicializada
                        payload.addProperty("usuario_id", usuario_id_usado); // ⚠️ Asegúrate de que esta variable esté inicializada
                        payload.add("secciones", nuevasSeccionesJson);

                        //Llamada Retrofit
                        ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);
                        Call<JsonObject> call = apiService.actualizarSecciones(payload);

                        call.enqueue(new Callback<JsonObject>() {
                            @Override
                            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {

                                boolean guardarLocal = false;      // ← ¿es necesario escribir solo en songdata.txt?
                                String  status       = "";         // ← por si quieres loggear
                                JsonObject body      = response.body();

                                /* ─────────────────────────────── 1. CASO 404 ─────────────────────────────── */
                                if (response.code() == 404) {                  // la canción aún no existe en BD
                                    guardarLocal = true;
                                    status = "song_not_found (404)";

                                    /* ─────────────────────────────── 2. CASO 200 OK ──────────────────────────── */
                                } else if (response.isSuccessful() && body != null) {

                                    status = body.has("status") ? body.get("status").getAsString() : "";

                                    if ("song_not_found".equals(status)) {     // el servidor lo dijo explícito
                                        guardarLocal = true;

                                    } else {                                   // éxito real
                                        /* 2‑A) Sincroniza IDs/fechas recibidos */
                                        Log.e("popup logger", "ejecutando bloque 2A");
                                        JsonArray seccionesAct = body.has("new_ids")
                                                ? body.getAsJsonArray("new_ids")            // según servidor
                                                : body.getAsJsonArray("secciones");         // compatibilidad

                                        if (seccionesAct != null) {
                                            for (JsonElement el : seccionesAct) {
                                                JsonObject obj = el.getAsJsonObject();
                                                int id = obj.get("id").getAsInt();
                                                String tIni = obj.get("tiempo_inicio").getAsString();
                                                String tFin = obj.get("tiempo_final").getAsString();
                                                String fCre = obj.get("fecha_creacion").getAsString();
                                                String fUlt = obj.get("fecha_ultima_edicion").getAsString();
                                                String nombre = obj.has("nombre") && !obj.get("nombre").isJsonNull() ? obj.get("nombre").getAsString() : "";
                                                String comentario = obj.has("comentario") && !obj.get("comentario").isJsonNull() ? obj.get("comentario").getAsString() : "";
                                                boolean estadoComentario = obj.has("estado_comentario") ? obj.get("estado_comentario").getAsBoolean() : false;

                                                // Leer emociones
                                                List<EmocionSeleccionada> emociones = new ArrayList<>();
                                                if (obj.has("emociones") && obj.get("emociones").isJsonArray()) {
                                                    for (JsonElement em : obj.getAsJsonArray("emociones")) {
                                                        emociones.add(new EmocionSeleccionada(em.getAsString()));
                                                    }
                                                }

                                                // Leer géneros
                                                List<GeneroSeleccionado> generos = new ArrayList<>();
                                                if (obj.has("generos") && obj.get("generos").isJsonArray()) {
                                                    for (JsonElement genEl : obj.getAsJsonArray("generos")) {
                                                        JsonObject genObj = genEl.getAsJsonObject();
                                                        int id_seccion = genObj.get("id").getAsInt();
                                                        String nombre_seccion = genObj.get("nombre_genero").getAsString();
                                                        generos.add(new GeneroSeleccionado(id_seccion, nombre_seccion));
                                                    }
                                                }

                                                Log.e("cambios popup onresponse", id + tIni + tFin + nombre + comentario + estadoComentario);

                                                for (Seccion s : listaSecciones) {
                                                    if (s.getId() == id) {
                                                        s.setFecha_creacion(fCre);
                                                        s.setFecha_ultima_edicion(fUlt);
                                                        s.setNombre(nombre);
                                                        s.setComentario(comentario);
                                                        s.setPublicado(estadoComentario);
                                                        s.setEmociones(emociones);
                                                        s.setGeneros(generos);
                                                        break;
                                                    }
                                                }
                                            }

                                            listener.onSeccionesConfirmadas(listaSecciones.size(), listaSecciones, true); //se envía la nueva lista hacia DatosMusicalesActivity para el submenú donde se podrá escuchar y modificar info de cada sección
                                            seccionesOriginales.clear();
                                        }

                                        /* 2‑B) Escribir a songdata.txt y salir */
                                        actualizarArchivoLocal(song_id_usado, listaSecciones);
                                        Toast.makeText(context, "Secciones actualizadas", Toast.LENGTH_SHORT).show();
                                    }

                                    /* ───────────────────────────── 3. OTRO 4xx / 5xx ─────────────────────────── */
                                } else {
                                    guardarLocal = true;
                                    status = "HTTP " + response.code();
                                }

                                /* ─────────── 4. CUANDO guardarLocal = true  (404 o error) ─────────── */
                                if (guardarLocal) {
                                    Log.e("PopupConfirmar", "Guardando localmente por: " + status);

                                    // asigna IDs temporales a secciones nuevas
                                    for (Seccion s : listaSecciones) {
                                        if (s.getId() == -1) {
                                            s.setId(generarNuevoSeccionId());   // tu helper
                                        }
                                    }
                                    actualizarArchivoLocal(song_id_usado, listaSecciones);
                                    Toast.makeText(context,
                                            "Cambios guardados localmente (pendiente de sincronizar)",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<JsonObject> call, Throwable t) {
                                Log.e("PopupConfirmar", "⚠️ Fallo de red al actualizar secciones", t);
                                Toast.makeText(context, "No hay conexión. Cambios guardados localmente", Toast.LENGTH_SHORT).show();

                                // Establecer IDs temporales si es necesario
                                for (Seccion s : listaSecciones) {
                                    if (s.getId() == -1) {
                                        s.setId(generarNuevoSeccionId()); // Por ejemplo: System.currentTimeMillis()
                                    }
                                }

                                // Guardar los cambios en el archivo local
                                actualizarArchivoLocal(song_id_usado, listaSecciones);
                            }
                        });

                        //LOG DE VERIFICACIÓN
                        for (Seccion s : listaSecciones) {
                            Log.e("guardar cambios popup", s.getId() + s.getTiempoInicio() + s.getTiempoFinal() + s.getNombre() + s.getComentario() + s.isPublicado());
                        }
                    }
                    dialog.dismiss();
                })
                .setNeutralButton("Cancelar", (dialog, which) -> {})
                .setNegativeButton("No", null)
                .setCancelable(false)
                .show();
    }

    // 🔹 IMPLEMENTAR MÉTODOS DE LA INTERFAZ
    @Override
    public void actualizarTiempoDesdeEditText(int indexSeccion, String tiempo, boolean esTiempoInicio) {
        if (actualizandoDesdeThumb) return;

        if (!esTiempoValido(tiempo)) {
            Toast.makeText(context, "Formato de tiempo inválido", Toast.LENGTH_SHORT).show();
            return;
        }

        int tiempoMs = convertirStringAMilisegundos(tiempo);
        float porcentaje = (float) tiempoMs / duracionCrudaDeMediaPlayer;

        if (esTiempoInicio) {
            // Actualizar thumb correspondiente
            if (indexSeccion < thumbsList.size()) {
                ImageView thumb = thumbsList.get(indexSeccion);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) thumb.getLayoutParams();

                seekBarPopup.post(() -> {
                    int seekBarWidth = seekBarPopup.getWidth();
                    int seekBarPaddingLeft = seekBarPopup.getPaddingLeft();
                    int seekBarPaddingRight = seekBarPopup.getPaddingRight();
                    int seekBarTrackWidth = seekBarWidth - seekBarPaddingLeft - seekBarPaddingRight;

                    int thumbPosition = seekBarPaddingLeft + (int)(porcentaje * seekBarTrackWidth) - 20;
                    thumbPosition = Math.max(seekBarPaddingLeft - 20, Math.min(thumbPosition, seekBarWidth - seekBarPaddingRight - 20));

                    params.leftMargin = thumbPosition;
                    thumb.setLayoutParams(params);
                });
            }
        }
    }

    @Override
    public void eliminarSeccion(int posicion) {
        // Mover la lógica de eliminación aquí desde el adapter
        if (posicion < 0 || posicion >= listaSecciones.size() - 1) return;

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

        // Reconstruir los tiempos
        reconstruirTiemposSecciones();

        // Verificar si estamos eliminando la sección seleccionada
        if (getSeccionSeleccionada() == posicion) {
            setSeccionSeleccionada(-1);
        }

        // Eliminar el thumb CORRESPONDIENTE
        if (posicion < obtenerCantidadThumbs()) {
            eliminarThumb(posicion);
        }

        // Actualizar la UI
        seccionesAdapter.notifyDataSetChanged();

        // Forzar recarga de todos los thumbs
        cargarThumbsDesdeSecciones();
    }

    private void actualizarArchivoLocal(int cancionId, List<Seccion> nuevasSecciones) {
        // 🔹 Recuperar el usuario_id desde SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("UsuarioPrefs", Context.MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);

        if (usuarioId == -1) {
            Log.e("ArchivoLocal", "❌ No se encontró usuario_id en SharedPreferences. No se puede actualizar el archivo.");
            return;
        }

        // 🔹 Usar el archivo único de este usuario
        File archivo = new File(context.getExternalFilesDir(null), "songdata/songdata_" + usuarioId + ".txt");
        if (!archivo.exists()) {
            Log.e("ArchivoLocal", "❌ El archivo no existe: " + archivo.getAbsolutePath());
            return;
        }

        try {
            // Leer todas las líneas del archivo
            List<String> lineas = new ArrayList<>();
            BufferedReader lector = new BufferedReader(new FileReader(archivo));
            String linea;
            while ((linea = lector.readLine()) != null) {
                lineas.add(linea);
            }
            lector.close();

            // Modificar la línea correspondiente a la canción
            for (int i = 0; i < lineas.size(); i++) {
                String[] partes = lineas.get(i).split(";", -1);
                if (partes.length < 11) continue; // Asegurar que hay al menos 11 campos

                int idCancion = Integer.parseInt(partes[0]);
                if (idCancion == cancionId) {
                    // Construir la nueva cadena de secciones con fechas
                    StringBuilder seccionesStr = new StringBuilder();
                    for (Seccion s : nuevasSecciones) {
                        seccionesStr.append(s.getId()).append("/")
                                .append(s.getTiempoInicio()).append("-")
                                .append(s.getTiempoFinal()).append("//")
                                .append(s.getFecha_creacion()).append("//")
                                .append(s.getFecha_ultima_edicion()).append("|");
                        // TODO: agregar más campos si es necesario en el futuro
                    }
                    if (seccionesStr.length() > 0) {
                        seccionesStr.setLength(seccionesStr.length() - 1); // Eliminar el último "|"
                    }

                    // Reemplazar el campo de datos de secciones
                    partes[10] = seccionesStr.toString();  // índice 10 = campo de secciones
                    lineas.set(i, String.join(";", partes));
                    break;
                }
            }

            // Escribir las líneas modificadas de vuelta al archivo
            BufferedWriter escritor = new BufferedWriter(new FileWriter(archivo, false));
            for (String l : lineas) {
                escritor.write(l);
                escritor.newLine();
            }
            escritor.close();

            Log.d("ArchivoLocal", "✅ Archivo actualizado correctamente: " + archivo.getAbsolutePath());

        } catch (IOException e) {
            Log.e("ArchivoLocal", "❌ Error al actualizar el archivo: " + e.getMessage(), e);
        }
    }

    public void reconstruirTiemposSecciones() {
        // Asegurar que la primera sección comience en 00:00.000
        if (listaSecciones.isEmpty()) return;

        // Primera sección siempre comienza en 00:00.000
        listaSecciones.get(0).setTiempoInicio("00:00.000");

        // Asegurar continuidad entre secciones
        for (int i = 0; i < listaSecciones.size(); i++) {
            // Para todas las secciones excepto la última
            if (i < listaSecciones.size() - 1) {
                // El final de la sección actual debe coincidir con el inicio de la siguiente
                listaSecciones.get(i).setTiempoFinal(listaSecciones.get(i + 1).getTiempoInicio());
            } else {
                // Última sección termina con la duración total
                listaSecciones.get(i).setTiempoFinal(tvDuration.getText().toString());
            }
        }
    }

    private boolean esTiempoValido(String actual) {
        int tiempoActual = convertirStringAMilisegundos(actual);
        int duracionCancion = seekBarPopup.getMax();

        // No permitir insertar fuera del rango de la canción
        if (tiempoActual <= 0 || tiempoActual >= duracionCancion) {
            return false;
        }

        // ✅ Si hay solo UNA sección, permitir si el tiempo está dentro del rango de la canción
        if (listaSecciones.size() == 1) {
            return tiempoActual > 0 && tiempoActual < duracionCancion;
        }

        // Verificar si el tiempo está dentro de los límites adecuados
        for (int i = 0; i < listaSecciones.size() - 1; i++) {
            int tiempoInicio = convertirStringAMilisegundos(listaSecciones.get(i).getTiempoInicio());
            int tiempoFinal = convertirStringAMilisegundos(listaSecciones.get(i + 1).getTiempoFinal());

            if (tiempoActual > tiempoInicio && tiempoActual < tiempoFinal) {
                return true;
            }
        }
        return false;
    }

    private String convertirMilisegundosAString(int milisegundos) {
        int minutos = (milisegundos / 60000) % 60;
        int segundos = (milisegundos / 1000) % 60;
        int milis = milisegundos % 1000;

        return String.format("%02d:%02d.%03d", minutos, segundos, milis);
    }

    public int convertirStringAMilisegundos(String tiempo) {
        String[] partes = tiempo.split("[:.]"); // Divide en minutos, segundos, milisegundos
        int minutos = Integer.parseInt(partes[0]);
        int segundos = Integer.parseInt(partes[1]);
        int milisegundos = Integer.parseInt(partes[2]);

        return (minutos * 60 + segundos) * 1000 + milisegundos;
    }

    public void bloquearControlesSeccion(boolean bloquear) {
        if (recyclerViewSecciones == null) return;

        for (int i = 0; i < recyclerViewSecciones.getChildCount(); i++) {
            View item = recyclerViewSecciones.getChildAt(i);

            EditText etInicio = item.findViewById(R.id.et_inicio_seccion);
            EditText etFin = item.findViewById(R.id.et_fin_seccion);
            RadioButton rb = item.findViewById(R.id.rb_seccion);
            ImageButton btnEliminar = item.findViewById(R.id.btn_eliminar_seccion);

            if (etInicio != null) etInicio.setEnabled(!bloquear);
            if (etFin != null) etFin.setEnabled(!bloquear);
            if (rb != null) rb.setEnabled(!bloquear);
            if (btnEliminar != null) btnEliminar.setEnabled(!bloquear);
        }

        // Switch
        Switch sw = dialog.findViewById(R.id.switch_editar_inicio_o_fin);
        if (sw != null) sw.setEnabled(!bloquear);

        if (btnAgregarSeccion != null) btnAgregarSeccion.setEnabled(!bloquear);
        if (btnConfirmar != null) btnConfirmar.setEnabled(!bloquear);
    }

    public void cerrarFocoDeEditTexts() {
        if (dialog != null) {
            View currentFocus = dialog.getCurrentFocus();
            if (currentFocus instanceof EditText) {
                currentFocus.clearFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
            }
        }
    }

    private void cerrarDialogo() {
        if (dialog != null && dialog.isShowing()) {
            handler.removeCallbacks(updateSeekBarRunnable); // 🔹 Detener actualización
            dialog.dismiss();
        }
    }

    public void limpiarThumbs() {
        // Limpiar completamente todos los thumbs
        thumbsContainer.removeAllViews();
        thumbsList.clear();
    }

    public interface OnSeccionesConfirmadasListener {
        void onSeccionesConfirmadas(int cantidadSecciones, List<Seccion> seccionesConfirmadasStr, Boolean seccioncambiada);
    }
}