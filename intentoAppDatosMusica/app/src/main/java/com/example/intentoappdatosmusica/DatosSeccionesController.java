package com.example.intentoappdatosmusica;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DatosSeccionesController {
    private final Activity activity;
    private final LinearLayout layoutSubmenuSecciones;
    private final View layoutContenidoSeccion;
    private final int songId;
    private final int usuarioId;
    private Context context;

    private boolean seccionMostrada = false;
    private final List<Button> botonesSecciones = new ArrayList<>();

    private List<Seccion> listaSecciones;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBarRunnable;

    private Integer indiceUltimaSeccionMostrada = null;

    private boolean seccionesModificadas = false;

    // Mapa para palabras que pertenecen a una única emoción
    private Map<String, String> palabraEmocionUnica;
    // Conjunto de palabras que aparecen en múltiples emociones
    private Set<String> palabrasRepetidas;

    public boolean hayCambiosEnSecciones() {
        return seccionesModificadas;
    }

    public void marcarCambio() {
        seccionesModificadas = true;
    }

    public Integer getIndiceUltimaSeccionMostrada() {
        return indiceUltimaSeccionMostrada;
    }

    public List<Seccion> getListaSecciones() {
        return listaSecciones;
    }

    public DatosSeccionesController(Activity activity, int songId, int usuarioId, Context context) {
        this.activity = activity;
        this.songId = songId;
        this.usuarioId = usuarioId;
        this.context = context;

        layoutSubmenuSecciones = activity.findViewById(R.id.layoutSubmenuSecciones);
        layoutContenidoSeccion = activity.findViewById(R.id.layoutContenidoSeccion); // asegúrate de tenerlo en el XML
        analizarDiccionarioEmociones();
    }

    // Constructor alternativo para ViewPager2 que acepta las vistas directamente
    public DatosSeccionesController(Activity activity, int songId, int usuarioId, Context context, 
                                    LinearLayout layoutSubmenuSecciones, View layoutContenidoSeccion) {
        this.activity = activity;
        this.songId = songId;
        this.usuarioId = usuarioId;
        this.context = context;
        this.layoutSubmenuSecciones = layoutSubmenuSecciones;
        this.layoutContenidoSeccion = layoutContenidoSeccion;
        analizarDiccionarioEmociones();
    }

    public void mostrarSecciones(List<Seccion> listaSecciones) {
        if (listaSecciones == null || listaSecciones.isEmpty()) {
            layoutSubmenuSecciones.removeAllViews();
            botonesSecciones.clear(); // 🧹 limpia botones anteriores
            return;
        }

        layoutSubmenuSecciones.removeAllViews();
        botonesSecciones.clear(); // 🧹 limpia botones anteriores

        for (int i = 0; i < listaSecciones.size(); i++) {
            Seccion seccion = listaSecciones.get(i);
            String nombreSeccion = seccion.getNombre();

            // Limitar a 50 caracteres como máximo (excluyendo "Sección X: ")
            if (nombreSeccion != null && nombreSeccion.length() > 50) {
                nombreSeccion = nombreSeccion.substring(0, 47) + "...";
            }

            Button btnSeccion = new Button(activity);
            btnSeccion.setText(nombreSeccion != null
                    ? "Sección " + (i + 1) + ": " + nombreSeccion
                    : "Sección " + (i + 1));

            btnSeccion.setPadding(20, 10, 20, 10);
            btnSeccion.setBackgroundResource(R.drawable.tab_selector);
            btnSeccion.setTextColor(ContextCompat.getColor(activity, R.color.white));

            final int index = i;
            btnSeccion.setOnClickListener(v -> mostrarContenidoDeSeccion(index));
            btnSeccion.setOnLongClickListener(v -> {
                mostrarDialogoCambiarNombredeSeccion(index);
                return true;
            });

            layoutSubmenuSecciones.addView(btnSeccion);
            botonesSecciones.add(btnSeccion); // ✅ agrega a la lista
        }

        // Guarda internamente la lista si lo necesitas para otros métodos
        this.listaSecciones = listaSecciones;
    }

    public void mostrarContenidoDeSeccion(int index) {
        indiceUltimaSeccionMostrada = index;
        seccionMostrada = true;

        // 🔁 Restaurar estilos de todos los botones
        for (int i = 0; i < botonesSecciones.size(); i++) {
            Button btn = botonesSecciones.get(i);
            btn.setBackgroundResource(R.drawable.tab_selector); // estilo normal
            btn.setTextColor(ContextCompat.getColor(activity, R.color.white));
        }

        // ✅ Resaltar el botón seleccionado
        if (index >= 0 && index < botonesSecciones.size()) {
            Button btnSeleccionado = botonesSecciones.get(index);
            btnSeleccionado.setBackgroundResource(R.drawable.tab_selector_selected); // estilo destacado
            btnSeleccionado.setTextColor(ContextCompat.getColor(activity, R.color.teal_200)); // ejemplo
        }

        layoutContenidoSeccion.setVisibility(View.VISIBLE);
        Seccion seccion = listaSecciones.get(index);

        EditText etComentarioSeccion = layoutContenidoSeccion.findViewById(R.id.etComentarioSeccion);

        // 1. Eliminar cualquier TextWatcher anterior que se haya guardado como tag
        TextWatcher watcherAnterior = (TextWatcher) etComentarioSeccion.getTag();
        if (watcherAnterior != null) {
            etComentarioSeccion.removeTextChangedListener(watcherAnterior);
        }

        // 2. Establecer el nuevo texto ANTES de agregar el listener
        etComentarioSeccion.setText(seccion.getComentario() != null ? seccion.getComentario() : "");

        // 3. Crear el nuevo TextWatcher
        TextWatcher watcherComentario = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String nuevoComentario = s.toString();
                if (!nuevoComentario.equals(seccion.getComentario())) {
                    seccion.setComentario(nuevoComentario);
                    marcarCambio();
                    Log.e("marcar Cambio", "comentario");
                }
            }
        };

        // 4. Guardar el nuevo watcher como tag y asignarlo
        etComentarioSeccion.setTag(watcherComentario);
        etComentarioSeccion.addTextChangedListener(watcherComentario);

        CheckBox checkRevisar = layoutContenidoSeccion.findViewById(R.id.checkboxSeccion);
        checkRevisar.setOnCheckedChangeListener(null);  // elimina posibles antiguos
        checkRevisar.setChecked(seccion.isPublicado());  // ajusta según tu getter
        checkRevisar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            seccion.setPublicado(isChecked); // actualiza el modelo
            marcarCambio();                // marca como modificado
            Log.e("marcar Cambio", "checkbox");
        });

        SeekBar sbSeccion = layoutContenidoSeccion.findViewById(R.id.seekBarSeccion);

        ImageButton btnAdelantar = layoutContenidoSeccion.findViewById(R.id.btnAdelantar5Seccion);
        ImageButton btnPlayPause = layoutContenidoSeccion.findViewById(R.id.btnPlayPauseSeccion);
        ImageButton btnRetroceder = layoutContenidoSeccion.findViewById(R.id.btnRetroceder5Seccion);

        TextView tvInicioSeccion = layoutContenidoSeccion.findViewById(R.id.tvInicioSeccion);
        TextView tvProgreso = layoutContenidoSeccion.findViewById(R.id.tvProgresoSeccion);
        TextView tvFinalSeccion = layoutContenidoSeccion.findViewById(R.id.tvFinSeccion);

        // Botón para emociones
        LinearLayout btnEmociones = layoutContenidoSeccion.findViewById(R.id.btnDefinirEmociones);
        btnEmociones.setOnClickListener(v -> {
            //int idSeccion = seccion.getId(); // o como guardes el ID
            RuedaEmocionalDialogFragment dialog = new RuedaEmocionalDialogFragment(seccion);
            dialog.show(((AppCompatActivity) context).getSupportFragmentManager(), "popup_rueda");
        });

        // Botón para géneros
        Button btnGeneros = layoutContenidoSeccion.findViewById(R.id.btnSeleccionarGeneros);
        btnGeneros.setOnClickListener(v -> mostrarPopupGeneros(seccion));

        // Configurar SeekBars si es posible obtener duración
        int inicioMs = convertirTiempoAMilisegundos(seccion.getTiempoInicio());
        int finMs = convertirTiempoAMilisegundos(seccion.getTiempoFinal());
        int duracionSeccionMs = finMs - inicioMs;

        MediaPlayerList playerList = MediaPlayerList.getInstance();

        boolean estabaReproduciendo = playerList.isPlaying(songId);

        // Siempre reposicionar al inicio de la sección
        playerList.seekTo(songId, inicioMs);

        // Reanudar solo si estaba sonando
        if (estabaReproduciendo) {
            playerList.play(songId);
            btnPlayPause.setImageResource(R.drawable.iconopausacancion);
        } else {
            btnPlayPause.setImageResource(R.drawable.iconoplay);
        }

        sbSeccion.setMax(duracionSeccionMs);
        sbSeccion.setProgress(0);
        tvInicioSeccion.setText(seccion.getTiempoInicio());
        tvFinalSeccion.setText(seccion.getTiempoFinal());
        tvProgreso.setText(formatearMilisegundos(inicioMs));

        // Establecer valores
        etComentarioSeccion.setText(seccion.getComentario());

        // Listener SeekBar manual
        sbSeccion.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int nuevaPos = inicioMs + progress;
                    playerList.seekTo(songId, nuevaPos);
                    tvProgreso.setText(formatearMilisegundos(nuevaPos)); // ✅ actualiza contador
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Botón Play/Pause
        btnPlayPause.setOnClickListener(v -> {
            if (playerList.isPlaying(songId)) {
                playerList.pause(songId);
                btnPlayPause.setImageResource(R.drawable.iconoplay);
                handler.removeCallbacks(updateSeekBarRunnable); // ⛔ detener actualizaciones
            } else {
                playerList.pauseAllExcept(songId); // 🔥 AÑADE ESTA LÍNEA
                playerList.seekTo(songId, inicioMs + sbSeccion.getProgress());
                playerList.play(songId);
                btnPlayPause.setImageResource(R.drawable.iconopausacancion);
                handler.post(updateSeekBarRunnable); // ✅ reanudar actualizaciones
            }
        });

        // Actualización del SeekBar respecto a la sección
        handler.removeCallbacks(updateSeekBarRunnable);
        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                int position = playerList.getCurrentPosition(songId);
                if (position >= inicioMs && position <= finMs) {
                    sbSeccion.setProgress(position - inicioMs);
                    tvProgreso.setText(formatearMilisegundos(position));
                    handler.postDelayed(this, 100);
                } else {
                    sbSeccion.setProgress(0);
                    tvProgreso.setText(formatearMilisegundos(inicioMs));
                    btnPlayPause.setImageResource(R.drawable.iconoplay);
                    playerList.pause(songId);
                }
            }
        };
        handler.post(updateSeekBarRunnable);

        // Adelantar/Retroceder
        btnAdelantar.setOnClickListener(v -> {
            int nuevaPos = Math.min(finMs, playerList.getCurrentPosition(songId) + 5000);
            playerList.seekTo(songId, nuevaPos);
        });

        btnRetroceder.setOnClickListener(v -> {
            int nuevaPos = Math.max(inicioMs, playerList.getCurrentPosition(songId) - 5000);
            playerList.seekTo(songId, nuevaPos);
        });

        Log.d("DatosSeccionesCtrl", "Sección mostrada: ID=" + seccion.getId());
    }

    public void cancelarSeccionActiva() {
        seccionMostrada = false;

        // Detener cualquier actualización automática del SeekBar
        if (handler != null && updateSeekBarRunnable != null) {
            handler.removeCallbacks(updateSeekBarRunnable);
        }

        // Ocultar contenido visual de sección (opcional)
        layoutContenidoSeccion.setVisibility(View.GONE);
    }

    private void mostrarPopupGeneros(Seccion seccion) {
        List<GeneroDisponible> disponibles = obtenerTodosLosGeneros();

        // Convertimos disponibles en GeneroSeleccionado
        List<GeneroSeleccionado> listaSeleccionable = new ArrayList<>();
        for (GeneroDisponible g : disponibles) {
            listaSeleccionable.add(new GeneroSeleccionado(g.getId(), g.getNombre()));
        }

        Set<GeneroSeleccionado> seleccionInicial = new HashSet<>(
                seccion.getGeneros() != null ? seccion.getGeneros() : new ArrayList<>()
        );

        int textColor = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;

        mostrarPopupSeleccionMultiple(seccion, listaSeleccionable, seleccionInicial,"Selecciona géneros","Máximo 10 géneros",false, R.color.popup_background, textColor);
    }

    public void mostrarPopupEmociones(String emocion, String nivelArousal, int seccionId) {
        Seccion seccion = null;
        for (Seccion s : listaSecciones) {
            if (s.getId() == seccionId) {
                seccion = s;
                break;
            }
        }
        if (seccion == null) return;

        List<EmocionSeleccionada> listaEmociones = cargarPalabrasDesdeArchivo(emocion, nivelArousal);

        Set<EmocionSeleccionada> seleccionInicial = new HashSet<>(
                seccion.getEmociones() != null ? seccion.getEmociones() : new ArrayList<>()
        );

        int backgroundColor = getColorForEmotion(emocion, nivelArousal);
        int textColor = "bajo".equalsIgnoreCase(nivelArousal) ? Color.BLACK : Color.WHITE;

        mostrarPopupSeleccionMultiple(
                seccion,
                listaEmociones,
                seleccionInicial,
                "Selecciona palabras: " + emocion + " (" + nivelArousal + ")",
                "Máximo 10 emociones",
                true,
                backgroundColor,
                textColor
        );
    }

    public void mostrarPopupEmocionesGlobal(Seccion seccion, List<PalabraEmocionalUnica> listaGlobal) {
        Set<String> palabrasSeleccionadas = seccion.getEmociones().stream()
                .map(EmocionSeleccionada::getPalabra)
                .collect(Collectors.toSet());

        Set<PalabraEmocionalUnica> seleccionInicial = new HashSet<>();
        for (PalabraEmocionalUnica palabraUnica : listaGlobal) {
            if (palabrasSeleccionadas.contains(palabraUnica.getPalabra())) {
                seleccionInicial.add(palabraUnica);
            }
        }

        // Usar un color de fondo neutro para la búsqueda global
        int backgroundColor = R.color.popup_background;
        int textColor = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;

        mostrarPopupSeleccionMultiple(seccion, listaGlobal, seleccionInicial, "Búsqueda Global de Emociones", "Máximo 10 emociones", true, backgroundColor, textColor);
    }

    private void mostrarDialogoCambiarNombredeSeccion(int index) {
        Seccion seccion = listaSecciones.get(index);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Cambiar nombre de la sección " + (index + 1));

        // Crear EditText prellenado con el nombre actual
        final EditText input = new EditText(activity);
        if (seccion.getNombre() != null) {
            input.setText(seccion.getNombre());
            input.setSelection(seccion.getNombre().length()); // Mover cursor al final
        }

        input.setInputType(InputType.TYPE_CLASS_TEXT);

        builder.setView(input);

        builder.setPositiveButton("Aceptar", (dialog, which) -> {
            String nuevoNombre = input.getText().toString().trim();

            if (!nuevoNombre.isEmpty()) {
                // Limitar a 50 caracteres
                if (nuevoNombre.length() > 50) {
                    nuevoNombre = nuevoNombre.substring(0, 50);
                }

                seccion.setNombre(nuevoNombre);
                marcarCambio();
                Log.e("marcar Cambio", "nombre seccion");
                mostrarSecciones(listaSecciones); // Redibuja botones
            }
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private <T extends Seleccionable> void mostrarPopupSeleccionMultiple(Seccion seccion, List<T> listaCompleta, Set<T> seleccionInicial, String titulo, String subtitulo, boolean esEmocion, int backgroundColorRes, int textColor) {
        LayoutInflater inflater = activity.getLayoutInflater();
        View popupView = inflater.inflate(R.layout.popup_lista, null);

        if (backgroundColorRes != 0) {
            popupView.setBackgroundColor(ContextCompat.getColor(context, backgroundColorRes));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(popupView);
        final AlertDialog dialog = builder.create();

        TextView tvTitulo = popupView.findViewById(R.id.tv_titulo_popup);
        TextView tvSubtitulo = popupView.findViewById(R.id.tv_subtitulo_popup);
        EditText buscador = popupView.findViewById(R.id.et_buscador_popup);
        CheckBox cbFiltrar = popupView.findViewById(R.id.cb_filtrar_seleccionados);
        RecyclerView recycler = popupView.findViewById(R.id.recycler_lista_popup);
        TextView tvContadorGlobal = popupView.findViewById(R.id.tv_contador_seleccionados);
        TextView tvContadorLocal = popupView.findViewById(R.id.tv_contador_local_popup);
        Button btnAceptar = popupView.findViewById(R.id.btn_aceptar_popup);
        Button btnCancelar = popupView.findViewById(R.id.btn_cancelar_popup);

        tvTitulo.setText(titulo);
        tvSubtitulo.setText(subtitulo);
        recycler.setLayoutManager(new LinearLayoutManager(activity));

        if (textColor != 0) {
            tvTitulo.setTextColor(textColor);
            tvSubtitulo.setTextColor(textColor);
            tvContadorGlobal.setTextColor(textColor);
            tvContadorLocal.setTextColor(textColor);
            cbFiltrar.setTextColor(textColor);
            btnAceptar.setTextColor(textColor);
            btnCancelar.setTextColor(textColor);
        }

        int seleccionGlobalTotal;
        if (esEmocion) {
            seleccionGlobalTotal = seccion.getEmociones() != null ? seccion.getEmociones().size() : 0;
        } else {
            seleccionGlobalTotal = seccion.getGeneros() != null ? seccion.getGeneros().size() : 0;
        }

        PopupListaAdapter<T> adapter = new PopupListaAdapter<>(listaCompleta, seleccionInicial, tvContadorGlobal, tvContadorLocal, textColor, seleccionGlobalTotal);
        recycler.setAdapter(adapter);

        buscador.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setTextoBusqueda(s.toString());
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        cbFiltrar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            adapter.setMostrarSoloSeleccionados(isChecked);
        });

        btnAceptar.setOnClickListener(v -> {
            Set<T> seleccionFinalPopup = adapter.getSeleccionados();
            if (esEmocion) {
                Set<String> nuevasPalabras = new HashSet<>();
                for (T item : seleccionFinalPopup) {
                    if (item instanceof EmocionSeleccionada) {
                        nuevasPalabras.add(((EmocionSeleccionada) item).getPalabra());
                    } else if (item instanceof PalabraEmocionalUnica) {
                        nuevasPalabras.add(((PalabraEmocionalUnica) item).getPalabra());
                    }
                }

                boolean isGlobalSearch = !listaCompleta.isEmpty() && listaCompleta.get(0) instanceof PalabraEmocionalUnica;

                if (isGlobalSearch) {
                    // Global Search: The new selection replaces the old one entirely.
                    List<EmocionSeleccionada> emocionesFinales = new ArrayList<>();
                    for (String palabra : nuevasPalabras) {
                        emocionesFinales.add(new EmocionSeleccionada(palabra));
                    }
                    seccion.setEmociones(emocionesFinales);
                    Toast.makeText(activity, emocionesFinales.size() + " emociones guardadas", Toast.LENGTH_SHORT).show();
                } else {
                    // Category-specific Search: Merge with existing selections.
                    Set<String> palabrasDeCategoriaActual = listaCompleta.stream()
                            .map(item -> ((EmocionSeleccionada) item).getPalabra())
                            .collect(Collectors.toSet());

                    Set<EmocionSeleccionada> emocionesActuales = new HashSet<>(seccion.getEmociones() != null ? seccion.getEmociones() : new ArrayList<>());

                    emocionesActuales.removeIf(emocion -> palabrasDeCategoriaActual.contains(emocion.getPalabra()));

                    for (String palabra : nuevasPalabras) {
                        emocionesActuales.add(new EmocionSeleccionada(palabra));
                    }
                    seccion.setEmociones(new ArrayList<>(emocionesActuales));
                    Toast.makeText(activity, emocionesActuales.size() + " emociones guardadas", Toast.LENGTH_SHORT).show();
                }
            } else {
                seccion.setGeneros(new ArrayList<>((Collection<? extends GeneroSeleccionado>) seleccionFinalPopup));
                Toast.makeText(activity, seleccionFinalPopup.size() + " géneros guardados", Toast.LENGTH_SHORT).show();
            }
            marcarCambio();
            Log.e("marcar Cambio", "lista emociones / genero");
            dialog.dismiss();
        });

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private List<GeneroDisponible> obtenerTodosLosGeneros() {
        List<GeneroDisponible> lista = new ArrayList<>();
        File archivo = new File(context.getExternalFilesDir(null), "songdata/generos/generos.csv");

        if (!archivo.exists()) {
            Log.e("Géneros", "Archivo no encontrado: " + archivo.getAbsolutePath());
            return lista;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;

            // Saltar encabezado si existe
            if ((linea = br.readLine()) != null && linea.startsWith("id")) {
                // encabezado leído y descartado
            }

            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length >= 2) {
                    try {
                        int id = Integer.parseInt(partes[0].trim());
                        String nombre = partes[1].trim();
                        lista.add(new GeneroDisponible(id, nombre));
                    } catch (NumberFormatException e) {
                        Log.e("Géneros", "Error al convertir ID: " + partes[0], e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e("Géneros", "Error leyendo archivo", e);
        }

        return lista;
    }

    private void analizarDiccionarioEmociones() {
        palabraEmocionUnica = new HashMap<>();
        palabrasRepetidas = new HashSet<>();
        Map<String, Set<String>> palabraAEmociones = new HashMap<>();
        File archivo = new File(context.getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");

        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            reader.readLine(); // Omitir el encabezado
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.trim().split(",");
                if (partes.length >= 2) {
                    String term = partes[0].trim().toLowerCase();
                    String emotion = partes[1].trim().toLowerCase();

                    palabraAEmociones.putIfAbsent(term, new HashSet<>());
                    palabraAEmociones.get(term).add(emotion);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (Map.Entry<String, Set<String>> entry : palabraAEmociones.entrySet()) {
            if (entry.getValue().size() == 1) {
                palabraEmocionUnica.put(entry.getKey(), entry.getValue().iterator().next());
            } else {
                palabrasRepetidas.add(entry.getKey());
            }
        }
    }

    private List<EmocionSeleccionada> cargarPalabrasDesdeArchivo(String emocion, String nivelArousal) {
        List<EmocionSeleccionada> lista = new ArrayList<>();
        File archivo = new File(context.getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");

        if (palabrasRepetidas == null) { // Comprobación defensiva
            analizarDiccionarioEmociones();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            reader.readLine(); // Omitir encabezado
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.trim().split(",");
                if (partes.length >= 6) {
                    String term = partes[0].trim();
                    String emotionArchivo = partes[1].trim();
                    String nivel = partes[5].trim();

                    // Solo agregar si la palabra no está en la lista de repetidas
                    if (!palabrasRepetidas.contains(term.toLowerCase()) && emotionArchivo.equalsIgnoreCase(emocion) && nivel.equalsIgnoreCase(nivelArousal)) {
                        lista.add(new EmocionSeleccionada(term));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lista;
    }

    public int getColorForEmotion(String emocion, String nivelArousal) {
        switch (emocion.toLowerCase()) {
            case "anger":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.rojo_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.rojo_normal;
                return R.color.rojo_claro;
            case "fear":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.turquesa_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.turquesa_normal;
                return R.color.turquesa_claro;
            case "joy":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.amarillo_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.amarillo_normal;
                return R.color.amarillo_claro;
            case "sadness":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.morado_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.morado_normal;
                return R.color.morado_claro;
            case "anticipation":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.naranja_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.naranja_normal;
                return R.color.naranja_claro;
            case "surprise":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.azul_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.azul_normal;
                return R.color.azul_claro;
            case "trust":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.verde_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.verde_normal;
                return R.color.verde_claro;
            case "disgust":
                if ("alto".equalsIgnoreCase(nivelArousal)) return R.color.rosado_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal)) return R.color.rosado_normal;
                return R.color.rosado_claro;
            default:
                return R.color.popup_background;
        }
    }

    private int convertirTiempoAMilisegundos(String tiempo) {
        try {
            String[] partes = tiempo.split("[:.]");
            int minutos = Integer.parseInt(partes[0]);
            int segundos = Integer.parseInt(partes[1]);
            int milisegundos = Integer.parseInt(partes[2]);
            return (minutos * 60 + segundos) * 1000 + milisegundos;
        } catch (Exception e) {
            Log.e("ParseTiempo", "Error al convertir tiempo: " + tiempo);
            return 0;
        }
    }

    private String formatearMilisegundos(int ms) {
        int horas = ms / (3600 * 1000);
        int minutos = (ms % (3600 * 1000)) / (60 * 1000);
        int segundos = (ms % (60 * 1000)) / 1000;
        int milisegundos = ms % 1000;

        return String.format("%02d:%02d:%02d.%03d", horas, minutos, segundos, milisegundos);
    }
}
