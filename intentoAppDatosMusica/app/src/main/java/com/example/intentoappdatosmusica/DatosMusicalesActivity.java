package com.example.intentoappdatosmusica;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DatosMusicalesActivity extends AppCompatActivity {
    private MediaPlayerList mediaPlayerList;
    private SeekBar seekBar;
    private TextView tvProgress, tvDuration;
    private EditText etCg;
    private Handler handler;
    private Runnable updateSeekBar;
    private ImageButton playPauseButton;
    private CheckBox estado_cg, estado_cancion;
    private boolean seccionesStrYaDefinidas; // Sirve para que la aplicación decida si bloquear o desbloquear la pestaña Secciones

    private List<Seccion> seccionesConfirmadasStrCache = null; // Sirve para evitar que PopupDefinirSecciones llame a cada rato al archivo o a la base de datos
    private boolean seccionesModificadas = false; // Nueva bandera

    private int songId;
    private String name, link;
    private String author, album, cg;
    private boolean es_cg, es_cancion;

    private int usuario_id;

    private boolean cancionNueva = false;

    private DatosSeccionesController seccionesController;
    private List<Seccion> seccionesStr;
    private View vistaGeneralesRoot; // Guardar referencia a la vista raíz de Generales

    ApiService audioService = ApiClient.getRetrofitForLargeTransfers().create(ApiService.class);

    //PARA VERIFICAR CAMBIOS:
    private String originalName, originalAuthor, originalAlbum, originalLink, originalCg;
    private boolean originalEstadoCg, originalEstadoCancion;

    public DatosSeccionesController getSeccionesController() {
        return seccionesController;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datos_musicales); // Cambia al layout correspondiente

        // Inicializar MediaPlayerList
        mediaPlayerList = MediaPlayerList.getInstance();

        // Recibir el ID de la canción
        Intent intent = getIntent();

        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        usuario_id = prefs.getInt("usuario_id", -1);
        if (usuario_id == -1) {
            Toast.makeText(this, "Usuario no identificado", Toast.LENGTH_SHORT).show();
            return;
        }

        songId = intent.getIntExtra("song_id", -1);
        Log.e("DatosM", "song_id = " + songId);
        name = intent.getStringExtra("name");
        String author = intent.getStringExtra("author");
        String album = intent.getStringExtra("album");
        link = intent.getStringExtra("link");
        String cg = intent.getStringExtra("cg");
        boolean es_cg = intent.getBooleanExtra("estado_cg", false);
        boolean es_cancion = intent.getBooleanExtra("estado_cancion", false);

        //tablaCancionCambiada aquí SOLAMENTE para información en pruebas
        cancionNueva = intent.getBooleanExtra("NC", false);
        Log.e("DATOS MUSICALES", "ONCREATE: canción nueva agregada: " + cancionNueva);

        List<Seccion> seccionesStr = (ArrayList<Seccion>) getIntent().getSerializableExtra("secciones");
        // Datos obtenidos desde el archivo de texto (desde MenuPrincipalActivity hacia SongAdapter y luego hacia DatosMusicalesActivity)
        //Antes seccionesStr solo contenía SECCION ID Y TIEMPOS INICIO Y FINAL Y FECHA CREACIÓN Y FECHA ULTIMA EDICIÓN
        //Ahora tiene todos los datos
        // Log y verificación
        if (seccionesStr != null && !seccionesStr.isEmpty()) {
            Log.e("DatosMusicales", "Cantidad de secciones recibidas: " + seccionesStr.size());
            for (Seccion s : seccionesStr) {
                Log.e("DatosMusicales", "Sección: ID=" + s.getId() + ", Inicio=" + s.getTiempoInicio() + ", Fin=" + s.getTiempoFinal());
            }
        } else {
            seccionesStr = new ArrayList<>(); // Lista vacía por defecto
        }

        // Los componentes de la pestaña Generales se inicializarán cuando se cree la vista
        // a través del adapter del ViewPager2

        //PESTAÑAS - Configurar ViewPager2 con animaciones
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        Button tabGenerales = findViewById(R.id.tabGenerales);
        Button tabSecciones = findViewById(R.id.tabSecciones);

        // Configurar ViewPager2 con adapter
        MusicalPagerAdapter adapter = new MusicalPagerAdapter();
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(1); // Mantener 1 página en memoria

        // Establecer la primera pestaña como activa por defecto (sin animación)
        viewPager.setCurrentItem(0, false);

        // Listener para cambios de página (deslizamiento con animación)
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                cambiarPestañaMusical(position, tabGenerales, tabSecciones);
            }
        });

        // Configurar listeners para cada botón de pestaña
        tabGenerales.setOnClickListener(v -> {
            viewPager.setCurrentItem(0, true); // Cambiar con animación
        });

        tabSecciones.setOnClickListener(v -> {
            viewPager.setCurrentItem(1, true); // Cambiar con animación
        });

        // Configurar estado inicial: primera pestaña activa
        viewPager.post(() -> {
            cambiarPestañaMusical(0, tabGenerales, tabSecciones);
        });

        seccionesStrYaDefinidas = seccionesStr != null && !seccionesStr.isEmpty(); //si la canción es nueva entonces será false

        // Guardar datos para cuando se inicialicen los componentes
        this.name = name;
        this.author = author;
        this.album = album;
        this.link = link;
        this.cg = cg;
        this.es_cg = es_cg;
        this.es_cancion = es_cancion;
        
        // Guardar seccionesStr para uso posterior
        this.seccionesStr = seccionesStr;

        // seccionesController se inicializará cuando se cree la vista de Secciones en el adapter

        // Guardar valores originales
        originalName = name;
        originalAuthor = author;
        originalAlbum = album;
        originalLink = link;
        originalCg = cg;
        originalEstadoCg = es_cg;
        originalEstadoCancion = es_cancion;

        // Configurar el botón de retroceso (ESTE BOTÓN ES PARA ACTUALIZAR DATOS DE CANCIÓN)
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> manejarRetrocesoPersonalizado());
    }

    private void cambiarPestañaMusical(int position, Button tabGenerales, Button tabSecciones) {
        if (position == 0) {
            // Pestaña Generales
            tabGenerales.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.teal_700));
            tabGenerales.setEnabled(false);
            tabSecciones.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorPrimary));
            tabSecciones.setEnabled(true);
            
            // ❌ Cancelar sección activa
            if (seccionesController != null) {
                seccionesController.cancelarSeccionActiva();
            }
        } else {
            // Pestaña Secciones
            tabGenerales.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorPrimary));
            tabGenerales.setEnabled(true);
            tabSecciones.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.teal_700));
            tabSecciones.setEnabled(false);

            // Inicializar seccionesController si aún no existe
            ViewPager2 viewPager = findViewById(R.id.viewPager);
            if (viewPager != null && viewPager.getChildCount() > 1) {
                View seccionesView = ((RecyclerView) viewPager.getChildAt(0)).getChildAt(1);
                if (seccionesView != null && seccionesController == null) {
                    // Las vistas ya están creadas, pero seccionesController necesita las referencias correctas
                    // Como los campos son final, necesitamos recrear el controller
                    // Por ahora, simplemente intentamos usar el controller existente
                }
            }

            // ✅ Seleccionar primera sección automáticamente
            if (seccionesController != null) {
                Integer indice = seccionesController.getIndiceUltimaSeccionMostrada();
                if (indice != null) {
                    seccionesController.mostrarContenidoDeSeccion(indice);
                } else {
                    seccionesController.mostrarContenidoDeSeccion(0); // primera vez
                }
            }
        }
    }

    // Adapter interno para ViewPager2
    private class MusicalPagerAdapter extends RecyclerView.Adapter<MusicalPagerAdapter.ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                // Pestaña Generales
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tab_generales_musicales, parent, false);
            } else {
                // Pestaña Secciones
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tab_secciones_musicales, parent, false);
            }
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (position == 0) {
                // Pestaña Generales - inicializar referencias a los componentes
                View rootView = holder.itemView;
                vistaGeneralesRoot = rootView; // Guardar referencia para uso posterior
                seekBar = rootView.findViewById(R.id.seekBar_progress);
                tvProgress = rootView.findViewById(R.id.tv_progress);
                tvDuration = rootView.findViewById(R.id.tv_duration);
                etCg = rootView.findViewById(R.id.et_comment);
                estado_cg = rootView.findViewById(R.id.cb_publicar_cancion);
                estado_cancion = rootView.findViewById(R.id.cb_publicar_comentario);
                playPauseButton = rootView.findViewById(R.id.btn_play_pause);
                
                // Configurar los componentes de la pestaña Generales pasando la vista raíz
                configurarComponentesGenerales(rootView);
            } else {
                // Pestaña Secciones - inicializar seccionesController con las vistas correctas
                View rootView = holder.itemView;
                LinearLayout layoutSubmenu = rootView.findViewById(R.id.layoutSubmenuSecciones);
                View layoutContenido = rootView.findViewById(R.id.layoutContenidoSeccion);
                
                // Crear seccionesController con las vistas del ViewPager2
                if (layoutSubmenu != null && layoutContenido != null) {
                    seccionesController = new DatosSeccionesController(
                        DatosMusicalesActivity.this, 
                        songId, 
                        usuario_id, 
                        DatosMusicalesActivity.this,
                        layoutSubmenu,
                        layoutContenido
                    );
                    
                    // Mostrar secciones si existen
                    if (seccionesStr != null) {
                        seccionesController.mostrarSecciones(seccionesStr);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Dos pestañas: Generales y Secciones
        }

        @Override
        public int getItemViewType(int position) {
            return position; // 0 para Generales, 1 para Secciones
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView, int viewType) {
                super(itemView);
            }
        }
    }

    private void configurarComponentesGenerales(View rootView) {
        // Esta función se llama cuando se crea la vista de Generales
        if (seekBar != null) {
            seekBar.getThumb().setAlpha(0); // Ocultar el thumb por defecto
        }
        
        // Asignar valores a los TextView y componentes usando la vista raíz
        if (name != null) {
            TextView textViewName = rootView.findViewById(R.id.inputNombre);
            TextView textViewAuthor = rootView.findViewById(R.id.inputAutor);
            TextView textViewAlbum = rootView.findViewById(R.id.inputAlbum);
            TextView textViewLink = rootView.findViewById(R.id.inputEnlace);
            
            if (textViewName != null) textViewName.setText(name);
            if (textViewAuthor != null) textViewAuthor.setText(author);
            if (textViewAlbum != null) textViewAlbum.setText(album);
            if (textViewLink != null) textViewLink.setText(link);
        }
        
        if (etCg != null && cg != null) {
            etCg.setText(cg);
        }
        if (estado_cg != null) {
            estado_cg.setChecked(es_cg);
        }
        if (estado_cancion != null) {
            estado_cancion.setChecked(es_cancion);
        }
        
        if (playPauseButton != null && MediaPlayerList.getInstance().isDownloading(songId)) {
            playPauseButton.setEnabled(false);
        }
        
        // Configurar reproductor y botón de definir secciones
        String fileName = getFilePath(link);
        String filePath;
        if (fileName != null) {
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName + ".mp3";
        } else {
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + link;
        }
        File audioFile = new File(filePath);
        boolean fileExists = audioFile.exists();
        Log.e("datosMusicales","el archivo existe = " + fileExists);
        
        // Buscar el botón dentro del include usando la vista raíz
        View btnDefineSections = rootView.findViewById(R.id.btn_define_sections);
        
        //Si se abre la interfaz y el archivo no existe
        if (!fileExists) {
            if (seekBar != null) {
                seekBar.setEnabled(false);
                seekBar.getThumb().setAlpha(0);
            }
            if (playPauseButton != null) {
                playPauseButton.setOnClickListener(v -> descargarCancion(link, name));
            }

            // Se actualiza la información en vivo para actualizar interfaz
            MediaPlayerList.getInstance().getDownloadingStateLiveData().observe(this, downloadingMap -> actualizarEstadoBotonPlayPause());

            MediaPlayerList.getInstance().getSongProgressLiveData(songId).observe(this, progress -> {
                if (mediaPlayerList.isPlaying(songId)) {
                    startSeekBarUpdate();
                }
            });

            MediaPlayerList.getInstance().getCurrentSongIdLiveData().observe(this, songId -> {
                if (songId == this.songId) {
                    int currentPosition = mediaPlayerList.getCurrentPosition(songId);
                    if (seekBar != null) seekBar.setProgress(currentPosition);
                    if (tvProgress != null) tvProgress.setText(formatTime(currentPosition));
                    if (tvDuration != null) tvDuration.setText(formatTime(mediaPlayerList.getDuration(songId)));
                    actualizarEstadoBotonPlayPause();

                    String newFileName = getFilePath(link);
                    String newFilePath;
                    if (newFileName != null) {
                        newFilePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + newFileName + ".mp3";
                    } else {
                        newFilePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + link;
                    }

                    File newAudioFile = new File(newFilePath);
                    if (newAudioFile.exists()) {
                        if (!mediaPlayerList.isSongLoaded(songId)) {
                            Toast.makeText(this, name + " descargado. Actualizando interfaz...", Toast.LENGTH_SHORT).show();
                            mediaPlayerList.resetMediaPlayer(songId, newFilePath);
                            if (playPauseButton != null) playPauseButton.setImageResource(R.drawable.iconoplay);
                            if (seekBar != null) seekBar.setEnabled(true);
                            if (tvDuration != null) tvDuration.setText(formatTime(mediaPlayerList.getDuration(songId)));
                            configurarBotonesReproduccion(rootView);
                            startSeekBarUpdate();
                        }
                    }
                    inicializarReproductor();
                }
            });
        } else {
            if (seekBar != null) {
                seekBar.getThumb().setAlpha(255);
            }
            inicializarReproductor();
            configurarBotonesReproduccion(rootView);
        }

        // Configurar botón de definir secciones
        if (btnDefineSections != null) {
            List<Seccion> seccionesActuales = seccionesStr;
            btnDefineSections.setOnClickListener(v -> {
                String fileNameCheck = getFilePath(link);
                String filePathCheck;
                if (fileNameCheck != null) {
                    filePathCheck = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileNameCheck + ".mp3";
                } else {
                    filePathCheck = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + link;
                }
                File audioFileCheck = new File(filePathCheck);
                boolean fileExistsNow = audioFileCheck.exists();

                if (!fileExistsNow) {
                    Toast.makeText(this, "Por favor descarga la canción antes de definir secciones", Toast.LENGTH_SHORT).show();
                } else {
                    PopupDefinirSecciones popup = new PopupDefinirSecciones(DatosMusicalesActivity.this, seccionesActuales, seccionesConfirmadasStrCache);
                    popup.setOnSeccionesConfirmadasListener((cantidadSecciones, seccionesConfirmadasStr, seccionesCambiadas) -> {
                        seccionesModificadas = seccionesCambiadas;
                        Button tabSecciones = findViewById(R.id.tabSecciones);
                        if (tabSecciones != null) tabSecciones.setEnabled(true);
                        seccionesConfirmadasStrCache = seccionesConfirmadasStr;
                        LinearLayout layoutSubmenuSecciones = findViewById(R.id.layoutSubmenuSecciones);
                        if (layoutSubmenuSecciones != null) layoutSubmenuSecciones.removeAllViews();
                        seccionesController = new DatosSeccionesController(this, songId, usuario_id, this);
                        seccionesController.mostrarSecciones(seccionesConfirmadasStrCache);
                        Toast.makeText(this, "Secciones confirmadas: " + cantidadSecciones, Toast.LENGTH_SHORT).show();
                    });
                    popup.mostrarDialogo(songId, usuario_id);
                }
            });
        }
    }

    private void finishWithResult() {
        Intent intent = new Intent();
        if (seccionesModificadas) cancionNueva = true;
        Log.e("CERRANDO seccionesModificadas", String.valueOf(seccionesModificadas));
        intent.putExtra("NC", cancionNueva); // Aquí se envía el valor correcto
        setResult(RESULT_OK, intent);
        finish();
    }

    /* ───────── helper local (ponlo justo encima de manejarRetrocesoPersonalizado) ───── */
    private JsonArray seccionesStrToJson(List<Seccion> seccionesStr) {
        JsonArray arr = new JsonArray();
        if (seccionesStr == null || seccionesStr.isEmpty()) return arr;

        for (Seccion s : seccionesStr) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.getId());
            o.addProperty("tiempo_inicio", s.getTiempoInicio());
            o.addProperty("tiempo_final", s.getTiempoFinal());
            o.addProperty("fecha_creacion", s.getFecha_creacion());
            o.addProperty("fecha_ultima_edicion", s.getFecha_ultima_edicion());
            o.addProperty("nombre_seccion", s.getNombre());
            o.addProperty("comentario_seccion", s.getComentario());
            o.addProperty("estado_cs_publicado", s.isPublicado());

            // Emociones
            JsonArray emocionesArray = new JsonArray();
            List<EmocionSeleccionada> emociones = s.getEmociones();
            if (emociones != null) {
                for (EmocionSeleccionada emocion : emociones) {
                    emocionesArray.add(emocion.getPalabra());
                }
            }
            o.add("emociones", emocionesArray);

            // Géneros
            JsonArray generosArray = new JsonArray();
            List<GeneroSeleccionado> generos = s.getGeneros();
            if (generos != null) {
                for (GeneroSeleccionado genero : generos) {
                    generosArray.add(genero.getId());
                }
            }
            o.add("generos", generosArray);

            arr.add(o);
        }

        return arr;
    }

    private void manejarRetrocesoPersonalizado() {
        Intent resultIntent = new Intent();
        int currentPosition = mediaPlayerList.getCurrentPosition(songId); // 🔹 Obtiene la posición exacta

        resultIntent.putExtra("songId", songId);
        resultIntent.putExtra("currentPosition", currentPosition);
        resultIntent.putExtra("isPlaying", mediaPlayerList.isPlaying(songId));

        // 🔹 Notificar el cambio de estado
        mediaPlayerList.notifySongStateChanged(songId);

        // Capturar valores actuales
        String currentName = ((TextView) findViewById(R.id.inputNombre)).getText().toString();
        String currentAuthor = ((TextView) findViewById(R.id.inputAutor)).getText().toString();
        String currentAlbum = ((TextView) findViewById(R.id.inputAlbum)).getText().toString();
        String currentLink = ((TextView) findViewById(R.id.inputEnlace)).getText().toString();
        String currentCg = etCg.getText().toString();
        boolean currentEstadoCg = estado_cg.isChecked();
        boolean currentEstadoCancion = estado_cancion.isChecked();

        boolean hayCambios = !currentName.equals(originalName) ||
                !currentAuthor.equals(originalAuthor) ||
                !currentAlbum.equals(originalAlbum) ||
                !currentLink.equals(originalLink) ||
                !currentCg.equals(originalCg) ||
                currentEstadoCg != originalEstadoCg ||
                currentEstadoCancion != originalEstadoCancion ||
                seccionesController.hayCambiosEnSecciones();

        if (hayCambios) {
            // Mostrar ventana de confirmación
            new AlertDialog.Builder(this)
                    .setTitle("Confirmar cambios")
                    .setMessage("¿Deseas guardar los cambios antes de salir?")
                    .setPositiveButton("Sí", (dialog, which) -> {
                        JsonObject json = new JsonObject();
                        json.addProperty("song_id", songId);
                        json.addProperty("nombre", currentName);
                        json.addProperty("autor", currentAuthor);
                        json.addProperty("album", currentAlbum);
                        json.addProperty("enlace", currentLink);
                        json.addProperty("comentario_general", currentCg);
                        json.addProperty("estado_cg_publicado", currentEstadoCg);
                        json.addProperty("estado_publicado", currentEstadoCancion);
                        json.addProperty("usuario_id", usuario_id);
                        List<Seccion> seccionesStrUsar;

                        if (seccionesConfirmadasStrCache != null) {
                            seccionesStrUsar = seccionesConfirmadasStrCache;
                        } else {
                            seccionesStrUsar = seccionesController.getListaSecciones();  // ✅ toma la versión dinámica actual
                        }

                        JsonArray seccionesJson = seccionesStrToJson(seccionesStrUsar);
                        json.add("secciones", seccionesJson);
                        //EL ADDPROPERTY ANTERIOR A ESTA LÍNEA COMENTADA SOLAMENTE SE USARÁ SI LA CANCIÓN NO EXISTÍA EN LA BASE DE DATOS (POR LO QUE DEBE SUCEDER INSERT EN VEZ DE UPDATE)
                        //ESTO SUCEDE PORQUE LAS SECCIONES YA SE GUARDAN EN EL POPUPDEFINIRSECCIONES.XML (SI LA CANCIÓN EXISTE EN BD),
                        //DATOSMUSICALESACTIVITY ES PARA GUARDAR DATOS DE CANCIÓN, NO DE SECCIÓN, PERO SE DEBE CUBRIR EL CASO DE UPDATE PARA UNA CANCIÓN QUE NO EXISTE EN BD
                        //Resulta que el atributo "seccionesConfirmadasStrCache" se llena al cerrar "popup_definir_secciones.xml", pero si este popup nunca fue abierto, entonces:
                        //Se toma el contenido de resultIntent.getStringExtra (se llena tras cerrar NuevaCancionActivity.java)

                        ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);
                        apiService.actualizarCancion(json).enqueue(new Callback<JsonObject>() {
                            @Override
                            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                                String fechaUltimaEd  = "";
                                int idRealServidor = songId;   // por defecto, el que ya tenías

                                if (response.isSuccessful() && response.body() != null) {
                                    JsonObject body = response.body();
                                    String status   = body.get("status").getAsString();

                                    if ("inserted_as_new".equals(status)) {
                                        idRealServidor = body.get("id_real").getAsInt();
                                        fechaUltimaEd  = body.get("fecha_ultima_edicion").getAsString();

                                        // 🔄 Actualiza `songId` in‑memory para el resto de la sesión
                                        songId = idRealServidor;
                                        Toast.makeText(DatosMusicalesActivity.this,
                                                "Canción enviada como nueva al servidor", Toast.LENGTH_SHORT).show();

                                    } else if ("updated".equals(status)) {
                                        if (!body.get("fecha_ultima_edicion").isJsonNull())
                                            fechaUltimaEd = body.get("fecha_ultima_edicion").getAsString();
                                    }
                                } else {   // 4xx ó 5xx distinto de 404
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                    fechaUltimaEd = sdf.format(new Date());
                                    Toast.makeText(DatosMusicalesActivity.this,
                                            "Cambios guardados localmente (pendiente de sincronizar)", Toast.LENGTH_SHORT).show();
                                }

                                guardarCancionEnArchivo(songId, currentName, currentAuthor, currentAlbum, currentLink,
                                        currentCg, currentEstadoCg, currentEstadoCancion, fechaUltimaEd);

                                cancionNueva = true;
                                finishWithResult();
                            }

                            @Override
                            public void onFailure(Call<JsonObject> call, Throwable t) {
                                Log.e("DatosMusicales", "Error al actualizar canción, no hay conexión", t);
                                Toast.makeText(DatosMusicalesActivity.this, "No hay conexión, actualizando localmente", Toast.LENGTH_SHORT).show();
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                String fechaActual = sdf.format(new Date());

                                guardarCancionEnArchivo(songId, currentName, currentAuthor, currentAlbum, currentLink, currentCg,
                                        currentEstadoCg, currentEstadoCancion, fechaActual);
                                cancionNueva = true;
                                seccionesConfirmadasStrCache = null; // 🔄 Limpiar cache
                                finishWithResult();
                            }
                        });
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        seccionesConfirmadasStrCache = null; // 🔄 Limpiar cache
                        finishWithResult();
                    })
                    .setNeutralButton("Cancelar", (dialog, which) -> {
                        // Nada más que cerrar el diálogo.
                        // Al no llamar a finishWithResult() la actividad sigue abierta.
                        // dialog.dismiss();   // implícito
                    })
                    .setCancelable(false)
                    .show();
        } else {
            seccionesConfirmadasStrCache = null; // 🔄 Limpiar cache
            finishWithResult(); // salir normalmente si no hubo cambios
        }
    }

    private void actualizarEstadoBotonPlayPause() {
        boolean isDownloading = MediaPlayerList.getInstance().isAnySongDownloading(); // 🔹 Detecta si hay alguna descarga en curso
        boolean isThisDownloading = MediaPlayerList.getInstance().isDownloading(songId); // 🔹 Detecta si esta canción se está descargando

        // Determinar si es un enlace de YouTube o un archivo local
        String fileName = getFilePath(link); // Devuelve null si no es un enlace de YouTube
        String filePath;
        if (fileName == null) {
            // Es un archivo local, usar directamente el nombre del archivo
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + link;
        } else {
            // Es un enlace de YouTube, agregar extensión .mp3
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName + ".mp3";
        }

        boolean fileExists = new File(filePath).exists();
        boolean isPlaying = mediaPlayerList.isPlaying(songId);

        Log.e("DatosMusicales", "Actualizando botón: isDownloading=" + isDownloading + ", isThisDownloading=" + isThisDownloading + ", fileExists=" + fileExists + ", isPlaying=" + isPlaying);

        if (isThisDownloading) {
            playPauseButton.setEnabled(false);
            playPauseButton.setImageResource(R.drawable.iconolocked);
            Log.e("DatosMusicales", "Estado: Descargando esta canción. Botón bloqueado.");
        } else if (isDownloading && !fileExists) {  // 🔹 Bloquear si cualquier otra canción se está descargando
            playPauseButton.setEnabled(false);
            playPauseButton.setImageResource(R.drawable.iconolocked);
            Log.e("DatosMusicales", "Estado: Otra canción está en descarga. Bloqueando botón.");
        } else if (fileExists) {
            playPauseButton.setEnabled(true);
            seekBar.getThumb().setAlpha(255); // 🔹 Mostrar el thumb
            tvDuration.setText(formatTime(mediaPlayerList.getDuration(songId))); // 🔹 Actualizar duración
            playPauseButton.setImageResource(isPlaying ? R.drawable.iconopause : R.drawable.iconoplay);
            Log.e("DatosMusicales", "Estado: Canción descargada. Botón actualizado a play/pause.");

            // Reconfigurar botones si la vista raíz está disponible
            if (vistaGeneralesRoot != null) {
                configurarBotonesReproduccion(vistaGeneralesRoot);
            }
        } else {
            playPauseButton.setEnabled(true);
            playPauseButton.setImageResource(R.drawable.iconodescargar);
            Log.e("DatosMusicales", "Estado: Canción no descargada. Botón actualizado a descargar.");
        }
    }

    private String convertirMilisegundosAString(int milisegundos) {
        int minutos = (milisegundos / 60000) % 60;
        int segundos = (milisegundos / 1000) % 60;
        int milis = milisegundos % 1000;

        return String.format("%02d:%02d.%03d", minutos, segundos, milis);
    }

    private String getFilePath(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.isEmpty()) return null;

        // Extraer los 11 caracteres del ID del video
        String videoId = "";
        Pattern pattern = Pattern.compile("v=([a-zA-Z0-9_-]{11})|/videos/([a-zA-Z0-9_-]{11})|embed/([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})|/v/([a-zA-Z0-9_-]{11})|/e/([a-zA-Z0-9_-]{11})|watch\\?v=([a-zA-Z0-9_-]{11})|/shorts/([a-zA-Z0-9_-]{11})|/live/([a-zA-Z0-9_-]{11})");
        Matcher matcher = pattern.matcher(youtubeUrl);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i); // Retorna el primer grupo no nulo (ID del video)
                }
            }
        }

        if (videoId.isEmpty()) return null;

        // Construir la ruta del archivo
        return videoId;
    }

    private void descargarCancion(String youtubeLink, String name) {
        Toast.makeText(this, "Descargando: " + name, Toast.LENGTH_SHORT).show();
        MediaPlayerList.getInstance().setDownloading(songId, true);
        MediaPlayerList.getInstance().updateButtonState(playPauseButton, songId);

        boolean isYoutubeLink = getFilePath(youtubeLink) != null;
        audioService = ApiClient.getRetrofitForLargeTransfers().create(ApiService.class);

        if (isYoutubeLink) {
            AudioRequest request = new AudioRequest(youtubeLink);
            audioService.getAudio(request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Log.e("AUDIO_STREAM", "Recibiendo datos de audio en streaming...");

                        try {
                            // Crear archivo local en la carpeta correcta
                            File directory = new File(getExternalFilesDir(null), "media");
                            if (!directory.exists()) {
                                directory.mkdirs();
                            }
                            File audioFile = new File(directory, getFilePath(youtubeLink) + ".mp3");

                            // **Iniciar la descarga usando DownloadAudioTask**
                            new DownloadAudioTask(response.body().byteStream(), audioFile, name, playPauseButton, seekBar, tvDuration).execute();

                        } catch (Exception e) {
                            Log.e("AUDIO_STREAM", "Error al iniciar la descarga", e);
                        }
                    } else {
                        Log.e("API_ERROR", "Error al obtener URL del audio: " + response.message());
                        MediaPlayerList.getInstance().setDownloading(songId, false);
                        MediaPlayerList.getInstance().updateButtonState(playPauseButton, songId);
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e("API_ERROR", "Falló la llamada a getAudio", t);
                    MediaPlayerList.getInstance().setDownloading(songId, false);
                    MediaPlayerList.getInstance().updateButtonState(playPauseButton, songId);
                }
            });
        } else {
            // 🧾 Archivo subido desde el dispositivo
            ArchivoRequest request = new ArchivoRequest(songId);
            audioService.getArchivo(request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    procesarRespuestaDeAudio(response, new File(getExternalFilesDir("media"), youtubeLink));
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    manejarErrorDescarga();
                }
            });
        }
    }

    private void procesarRespuestaDeAudio(Response<ResponseBody> response, File destino) {
        if (response.isSuccessful() && response.body() != null) {
            try {
                new DownloadAudioTask(
                        response.body().byteStream(),
                        destino,
                        name,
                        playPauseButton,
                        seekBar,
                        tvDuration
                ).execute();
            } catch (Exception e) {
                Log.e("AUDIO_STREAM", "Error guardando el archivo", e);
            }
        } else {
            Log.e("API_ERROR", "Respuesta inválida del servidor");
            manejarErrorDescarga();
        }
    }

    private void manejarErrorDescarga() {
        MediaPlayerList.getInstance().setDownloading(songId, false);
        MediaPlayerList.getInstance().updateButtonState(playPauseButton, songId);
        Toast.makeText(this, "Error al descargar el archivo", Toast.LENGTH_SHORT).show();
    }

    private class DownloadAudioTask extends AsyncTask<Void, Void, Boolean> {
        private InputStream inputStream;
        private File audioFile;
        private String name;
        private ImageButton playPauseButton;
        private SeekBar seekBar;
        private TextView tvDuration;

        public DownloadAudioTask(InputStream inputStream, File audioFile, String name, ImageButton playPauseButton, SeekBar seekBar, TextView tvDuration) {
            this.inputStream = inputStream;
            this.audioFile = audioFile;
            this.name = name;
            this.playPauseButton = playPauseButton;
            this.seekBar = seekBar;
            this.tvDuration = tvDuration;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                FileOutputStream outputStream = new FileOutputStream(audioFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                outputStream.close();
                inputStream.close();
                return true;
            } catch (IOException e) {
                Log.e("DownloadAudioTask", "Error al guardar el archivo de audio", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Log.e("datosMusicales","success = " + success);
            MediaPlayerList.getInstance().setDownloading(songId, false);
            MediaPlayerList.getInstance().updateButtonState(playPauseButton, songId);

            if (success && audioFile.exists() && audioFile.length() > 0) {
                Toast.makeText(DatosMusicalesActivity.this, "Descarga completa de " + name, Toast.LENGTH_SHORT).show();

                // Cambiar a ícono de reproducción
                playPauseButton.setImageResource(R.drawable.iconoplay); // Cambiar a icono de reproducción

                // 🔹 Reinicializar el MediaPlayer en MediaPlayerList con el archivo descargado
                mediaPlayerList.resetMediaPlayer(songId, String.valueOf(audioFile)); // ✅ Nueva función

                // 🔹 🔥 Reconfigurar el reproductor después de la descarga
                inicializarReproductor();

                // 🔹 🔥 Comenzar la actualización del SeekBar
                startSeekBarUpdate();

                playPauseButton.setEnabled(true);
            } else {
                Toast.makeText(DatosMusicalesActivity.this, "Error en la descarga de " + name, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void inicializarReproductor() {
        if (mediaPlayerList.isSongLoaded(songId)) {
            int currentPosition = mediaPlayerList.getCurrentPosition(songId);
            boolean isPlaying = mediaPlayerList.isPlaying(songId);

            seekBar.getThumb().setAlpha(255); // 🔹 Asegurar que se muestre
            seekBar.setMax(mediaPlayerList.getDuration(songId));
            seekBar.setProgress(currentPosition);
            tvDuration.setText(formatTime(mediaPlayerList.getDuration(songId)));
            tvProgress.setText(formatTime(currentPosition));

            // Actualizar botón de play/pausa
            playPauseButton.setImageResource(isPlaying ? R.drawable.iconopausacancion : R.drawable.iconoplay);

            observarCambiosReproductor(); // 🔹 Observa cambios en el LiveData solo una vez

            startSeekBarUpdate(); // 🔹 Iniciar actualización del SeekBar

            // Listener para mover manualmente el SeekBar
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        mediaPlayerList.seekTo(songId, progress);
                        tvProgress.setText(formatTime(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            configurarBotonPlayPause(); // Reconfigurar botón
        }
    }

    private void observarCambiosReproductor() {
        mediaPlayerList.getCurrentSongIdLiveData().observe(this, songId -> {
            if (songId == this.songId) {
                // Actualiza los controles de esta actividad basados en el estado actual del MediaPlayer
                boolean isPlaying = mediaPlayerList.isPlaying(songId);
                int currentPosition = mediaPlayerList.getCurrentPosition(songId);

                seekBar.setProgress(currentPosition);
                tvProgress.setText(formatTime(currentPosition));
                tvDuration.setText(formatTime(mediaPlayerList.getDuration(songId)));
                playPauseButton.setImageResource(isPlaying ? R.drawable.iconopausacancion : R.drawable.iconoplay);
            }
        });
    }

    private void configurarBotonPlayPause() {
        playPauseButton.setEnabled(true);

        seekBar.setEnabled(true);

        playPauseButton.setOnClickListener(v -> {
            if (mediaPlayerList.isPlaying(songId)) {
                mediaPlayerList.pause(songId);
            } else {
                mediaPlayerList.pauseAllExcept(songId);
                mediaPlayerList.play(songId);

                // 🔹 Asegurar que se inicie la actualización del SeekBar después de la reproducción
                actualizarEstadoBotonPlayPause();
                startSeekBarUpdate();
            }
            mediaPlayerList.notifySongStateChanged(songId);
        });
    }

    private void startSeekBarUpdate() {
        if (handler == null) {
            handler = new Handler();
        }
        if (updateSeekBar == null) {
            updateSeekBar = new Runnable() {
                @Override
                public void run() {
                    int currentPosition = mediaPlayerList.getCurrentPosition(songId);
                    seekBar.setProgress(currentPosition);
                    tvProgress.setText(formatTime(currentPosition));

                    // 🔹 Asegurar que el progreso se sincroniza con otras interfaces
                    mediaPlayerList.notifyProgressChanged(songId, currentPosition);

                    handler.postDelayed(this, 100);
                }
            };
        }
        handler.removeCallbacks(updateSeekBar); // 🔹 Asegurar que no se solapen múltiples hilos
        handler.post(updateSeekBar);
    }

    private void configurarBotonesReproduccion(View rootView) {
        // Configurar botón de retroceder usando la vista raíz
        ImageButton btnRewind = rootView.findViewById(R.id.btn_rewind);
        if (btnRewind != null) {
            btnRewind.setOnClickListener(v -> {
                mediaPlayerList.rewind(songId, 5); // Retrocede 5 segundos
            });
        }

        // Configurar botón de adelantar usando la vista raíz
        ImageButton btnForward = rootView.findViewById(R.id.btn_forward);
        if (btnForward != null) {
            btnForward.setOnClickListener(v -> {
                mediaPlayerList.forward(songId, 5); // Adelanta 5 segundos
            });
        }

        // Configurar botón de play/pause (ya existente)
        configurarBotonPlayPause();
    }

    private void guardarCancionEnArchivo(int songId, String nombre, String autor, String album, String enlace,
                                         String comentario, boolean estadoCg, boolean estadoPublicado, String f_ultimaedicion) {
        File dir = new File(getExternalFilesDir(null), "songdata");
        if (!dir.exists()) dir.mkdirs();

        // 🔹 Obtener el ID de usuario desde SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);

        // 🔹 Si no existe el id de usuario, evitamos guardar con un archivo incorrecto
        if (usuarioId == -1) {
            Log.e("DatosMusicales", "❌ No se encontró usuario_id en SharedPreferences. No se puede guardar la canción.");
            return;
        }

        // 🔹 Archivo único para cada usuario
        File archivo = new File(dir, "songdata_" + usuarioId + ".txt");

        String fechaCreacion = "desconocida";
        String secciones = "";

        try {
            List<String> lineasActuales = new ArrayList<>();
            if (archivo.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(archivo));
                String linea;
                while ((linea = reader.readLine()) != null) {
                    if (linea.startsWith(songId + ";")) {
                        // 🔍 Extraer fecha de creación y datos de secciones desde línea anterior
                        String[] partes = linea.split(";", -1);
                        if (partes.length >= 10) {
                            fechaCreacion = partes[8]; // Fecha de creación antigua
                            secciones = partes.length >= 11 ? partes[10] : "";
                        }
                        // No agregar esta línea vieja al listado
                    } else {
                        lineasActuales.add(linea);
                    }
                }
                reader.close();
            }

            String nuevaLinea = songId + ";" +
                    nombre + ";" +
                    autor + ";" +
                    album + ";" +
                    enlace + ";" +
                    comentario + ";" +
                    estadoCg + ";" +
                    estadoPublicado + ";" +
                    fechaCreacion + ";" +
                    f_ultimaedicion + ";" +
                    secciones;

            lineasActuales.add(nuevaLinea);

            BufferedWriter writer = new BufferedWriter(new FileWriter(archivo, false));
            for (String l : lineasActuales) {
                writer.write(l);
                writer.newLine();
            }
            writer.close();

            Log.e("DatosMusicales", "✅ Canción actualizada en archivo local: " + archivo.getAbsolutePath());
        } catch (IOException e) {
            Log.e("DatosMusicales", "❌ Error al escribir en archivo local songdata_" + usuarioId + ".txt", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && updateSeekBar != null) {
            handler.removeCallbacks(updateSeekBar); // Detener actualización al salir de la actividad
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayerList.isPlaying(songId)) {
            startSeekBarUpdate(); // Reanudar actualización si la canción sigue reproduciéndose
        }
    }

    private String formatTime(int milliseconds) {
        int minutes = (milliseconds / 60000) % 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /*
    @Override
    protected void onDestroy() {
        Log.e("DatosMusicales","ejecutando destroy");
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(updateSeekBar);
        }
        if (isFinishing()) { // Solo si la app realmente se cierra
            Log.e("DatosMusicales","ejecutando isfinishing");
        }
    }*/

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        manejarRetrocesoPersonalizado(); // Reutiliza la lógica
        //super.onBackPressed();
    }
}