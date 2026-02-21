package com.example.intentoappdatosmusica;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.viewpager2.widget.ViewPager2;

import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;

import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DatosSesionesActivity extends AppCompatActivity {

    private EditText etNombreSesion, etObservaciones, etDificultades, etRecomendaciones; // etObjetivoSesion eliminado
    private EditText etNombreIE, etNombreFacilitador;
    private TextView tvDuracionSesion;
    private Spinner spinnerGrado, spinnerSeccion;

    private NumberPicker npNumeroEstudiantes;
    private Switch swTipoSesion, swModoSesion;
    private Button btnFechaInicio, btnFechaFinal, btnCanciones, btnEmociones;
    private ImageView btnBack, btnInfo;
    private TextView title;

    private RatingBar ratingBar;

    private Sesion sesionActual;
    private Sesion copiaInicial;

    private int usuario_id;

    private final SimpleDateFormat bdFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.ENGLISH);

    List<Song> canciones;

    // Variables para el filtrado de emociones
    private Map<String, String> palabraEmocionUnica;
    private Set<String> palabrasRepetidas;

    public static int getColorForEmotion(String emocion, String nivelArousal) {
        switch (emocion.toLowerCase()) {
            case "anger":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.rojo_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.rojo_normal;
                return R.color.rojo_claro;
            case "fear":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.turquesa_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.turquesa_normal;
                return R.color.turquesa_claro;
            case "joy":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.amarillo_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.amarillo_normal;
                return R.color.amarillo_claro;
            case "sadness":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.morado_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.morado_normal;
                return R.color.morado_claro;
            case "anticipation":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.naranja_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.naranja_normal;
                return R.color.naranja_claro;
            case "surprise":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.azul_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.azul_normal;
                return R.color.azul_claro;
            case "trust":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.verde_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.verde_normal;
                return R.color.verde_claro;
            case "disgust":
                if ("alto".equalsIgnoreCase(nivelArousal))
                    return R.color.rosado_oscuro;
                if ("medio".equalsIgnoreCase(nivelArousal))
                    return R.color.rosado_normal;
                return R.color.rosado_claro;
            default:
                return R.color.popup_background;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datos_sesiones);

        analizarDiccionarioEmociones(); // Analizar el CSV al crear la actividad

        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        usuario_id = prefs.getInt("usuario_id", -1);
        if (usuario_id == -1) {
            Toast.makeText(this, "Usuario no identificado", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ 1. Recuperar la sesión recibida
        sesionActual = (Sesion) getIntent().getSerializableExtra("sesion");
        canciones = (List<Song>) getIntent().getSerializableExtra("lista_canciones");

        // ✅ 2. Enlazar componentes de la barra superior
        btnBack = findViewById(R.id.btnBack);
        btnInfo = findViewById(R.id.btnInfo);
        title = findViewById(R.id.title);

        // ✅ 3. Configurar título
        if (sesionActual != null) {
            title.setText(
                    "DATOS DE SESIÓN - " + (sesionActual.getId() != 0 ? sesionActual.getNumeroSesion() : "NUEVA"));

            // Allow editing number by clicking the title
            title.setOnClickListener(v -> {
                final EditText input = new EditText(this);
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                input.setText(String.valueOf(sesionActual.getNumeroSesion()));

                new AlertDialog.Builder(this)
                        .setTitle("Editar Número de Sesión")
                        .setMessage("Ingrese el nuevo número de sesión:")
                        .setView(input)
                        .setPositiveButton("Aceptar", (dialog, which) -> {
                            String val = input.getText().toString();
                            if (!val.isEmpty()) {
                                try {
                                    int newNum = Integer.parseInt(val);
                                    sesionActual.setNumeroSesion(newNum);
                                    title.setText("DATOS DE SESIÓN - " + newNum);
                                } catch (NumberFormatException e) {
                                    Toast.makeText(this, "Número inválido", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }

        // ✅ 4. Botón de regreso
        btnBack.setOnClickListener(v -> {
            if (!hayCambios()) {
                finish(); // Nada que guardar
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("¿Deseas guardar los cambios?")
                    .setMessage("Has modificado datos en la sesión.")
                    .setPositiveButton("Guardar", (dialog, which) -> {
                        if (sesionActual.getId() == 0) {
                            if (validarCampos())
                                guardarSesionEnServidor();
                        } else {
                            actualizarSesionEnServidor();
                        }
                    })
                    .setNegativeButton("Salir sin guardar", (dialog, which) -> finish())
                    .setNeutralButton("Seguir editando", null)
                    .show();
        });

        btnInfo.setOnClickListener(v -> Toast.makeText(this, "Información de la sesión", Toast.LENGTH_SHORT).show());

        crearCopiaEstadoInicial();

        // Configurar menú de pestañas
        configurarMenuPestanas();

        // Inicializar listas de opciones para las grids
        inicializarOpcionesGrids();
    }

    // Listas de opciones para las cuadrículas
    private List<OpcionSesion> opcionesObjetivos;
    private List<OpcionSesion> opcionesTecnicas;
    private List<OpcionSesion> opcionesMateriales;
    private List<OpcionSesion> opcionesLogros;

    private List<OpcionSesion> opcionesClimaGrupal;

    private void inicializarOpcionesGrids() {
        opcionesObjetivos = crearOpcionesPorDefecto("Objetivo", 9);
        opcionesTecnicas = crearOpcionesPorDefecto("Técnica", 9);
        opcionesMateriales = crearOpcionesPorDefecto("Material", 9);
        opcionesLogros = crearOpcionesPorDefecto("Logro", 9);
        opcionesClimaGrupal = crearOpcionesPorDefecto("Clima", 6); // 6 items (3x2)
    }

    private List<OpcionSesion> crearOpcionesPorDefecto(String prefijo, int totalItems) {
        List<OpcionSesion> lista = new ArrayList<>();
        // items genéricos hasta totalItems - 1
        for (int i = 1; i < totalItems; i++) {
            lista.add(new OpcionSesion(i, prefijo + " " + i, android.R.drawable.ic_menu_help, false));
        }
        // Opción final: Otro (editable) con ID = totalItems
        lista.add(new OpcionSesion(totalItems, "Otro", android.R.drawable.ic_menu_edit, true));
        return lista;
    }

    private ViewPager2 viewPager;
    private HorizontalScrollView scrollPestanas;
    private LinearLayout menuPestanas;
    private Button[] tabButtons;
    private int currentTab = 0;
    private static final int NUM_TABS = 8;

    private void configurarMenuPestanas() {
        viewPager = findViewById(R.id.viewPager);
        scrollPestanas = findViewById(R.id.scrollPestanas);
        menuPestanas = findViewById(R.id.menuPestanas);

        // Crear array de botones
        tabButtons = new Button[] {
                findViewById(R.id.tabGenerales),
                findViewById(R.id.tabObjetivos),
                findViewById(R.id.tabTecnicas),
                findViewById(R.id.tabMateriales),
                findViewById(R.id.tabDesarrollo),
                findViewById(R.id.tabObservaciones),
                findViewById(R.id.tabLogros),
                findViewById(R.id.tabExtras)
        };

        // Configurar ViewPager2 con adapter
        SesionPagerAdapter adapter = new SesionPagerAdapter();
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2); // Mantener 2 páginas en memoria

        // Establecer la primera pestaña como activa por defecto (sin animación)
        viewPager.setCurrentItem(0, false);

        // Listener para cambios de página (deslizamiento)
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                cambiarPestaña(position);
            }
        });

        // Configurar listeners para cada botón de pestaña
        for (int i = 0; i < tabButtons.length; i++) {
            final int index = i;
            tabButtons[i].setOnClickListener(v -> {
                viewPager.setCurrentItem(index, true); // Cambiar con animación
            });
        }

        // Configurar estado inicial: primera pestaña activa
        // Usar post para asegurar que se ejecute después de que la vista esté
        // completamente renderizada
        viewPager.post(() -> {
            cambiarPestaña(0);
        });
    }

    private void cambiarPestaña(int position) {
        currentTab = position;

        // Actualizar colores de todos los botones
        for (int i = 0; i < tabButtons.length; i++) {
            if (i == position) {
                // Pestaña activa
                tabButtons[i].setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.teal_700));
                tabButtons[i].setEnabled(false);
            } else {
                // Pestañas inactivas
                tabButtons[i].setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorPrimary));
                tabButtons[i].setEnabled(true);
            }
        }

        // Desplazar el scroll horizontal para mostrar la pestaña activa
        tabButtons[position].post(() -> {
            int scrollX = tabButtons[position].getLeft() - (scrollPestanas.getWidth() / 2)
                    + (tabButtons[position].getWidth() / 2);
            scrollPestanas.smoothScrollTo(scrollX, 0);
        });
    }

    private void configurarRecyclerViewGrid(RecyclerView rv, List<OpcionSesion> opciones, Runnable onUpdate) {
        if (rv == null)
            return;

        // 1. Configurar LayoutManager (3 columnas)
        rv.setLayoutManager(new GridLayoutManager(this, 3));

        // 2. Configurar Adapter (Wrap Runnable to Interface)
        GridOpcionesAdapter adapter = new GridOpcionesAdapter(opciones, () -> {
            if (onUpdate != null)
                onUpdate.run();
        });
        rv.setAdapter(adapter);
    }

    // Convierte "Opción 1, Opción 2" -> Marca los items en la lista
    private void sincronizarListaConIDs(List<OpcionSesion> lista, List<Integer> ids, String customText) {
        if (lista == null)
            return;
        for (OpcionSesion op : lista) {
            op.setSeleccionado(false);
            op.setTextoPersonalizado("");
            // Si es editable, le cargamos el customText (si existe)
            if (op.isEditable() && customText != null) {
                op.setTextoPersonalizado(customText);
            }
        }
        if (ids != null) {
            for (Integer id : ids) {
                for (OpcionSesion op : lista) {
                    if (op.getId() == id) {
                        op.setSeleccionado(true);
                        break;
                    }
                }
            }
        }
    }

    private List<Integer> obtenerIDsSeleccionados(List<OpcionSesion> lista) {
        List<Integer> ids = new ArrayList<>();
        if (lista == null)
            return ids;
        for (OpcionSesion op : lista) {
            if (op.isSeleccionado()) {
                ids.add(op.getId());
            }
        }
        return ids;
    }

    private String obtenerTextoCustom(List<OpcionSesion> lista) {
        if (lista == null)
            return "";
        for (OpcionSesion op : lista) {
            if (op.isEditable()) {
                return op.getTextoPersonalizado() != null ? op.getTextoPersonalizado() : "";
            }
        }
        return "";
    }

    private void analizarDiccionarioEmociones() {

        palabraEmocionUnica = new HashMap<>();
        palabrasRepetidas = new HashSet<>();
        Map<String, Set<String>> palabraAEmociones = new HashMap<>();
        File archivo = new File(this.getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");

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
        File archivo = new File(this.getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");

        if (palabrasRepetidas == null) { // Comprobación por si acaso
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
                    if (!palabrasRepetidas.contains(term.toLowerCase()) && emotionArchivo.equalsIgnoreCase(emocion)
                            && nivel.equalsIgnoreCase(nivelArousal)) {
                        lista.add(new EmocionSeleccionada(term));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lista;
    }

    private void crearCopiaEstadoInicial() {
        copiaInicial = new Sesion();
        copiaInicial.setNombre(sesionActual.getNombre());
        copiaInicial.setObjetivosIds(new ArrayList<>(sesionActual.getObjetivosIds()));
        copiaInicial.setObjetivosCustom(sesionActual.getObjetivosCustom());
        copiaInicial.setTecnicasIds(new ArrayList<>(sesionActual.getTecnicasIds()));
        copiaInicial.setTecnicasCustom(sesionActual.getTecnicasCustom());
        copiaInicial.setMaterialesIds(new ArrayList<>(sesionActual.getMaterialesIds()));
        copiaInicial.setMaterialesCustom(sesionActual.getMaterialesCustom());
        copiaInicial.setLogrosIds(new ArrayList<>(sesionActual.getLogrosIds()));
        copiaInicial.setLogrosCustom(sesionActual.getLogrosCustom());

        copiaInicial.setClimaGrupalIds(new ArrayList<>(sesionActual.getClimaGrupalIds()));
        copiaInicial.setClimaGrupalCustom(sesionActual.getClimaGrupalCustom());

        copiaInicial.setObservaciones(sesionActual.getObservaciones());
        copiaInicial.setDescripcionClima(sesionActual.getDescripcionClima());

        copiaInicial.setInicio(sesionActual.getInicio());
        copiaInicial.setActividadCentral(sesionActual.getActividadCentral());
        copiaInicial.setCierre(sesionActual.getCierre());

        copiaInicial.setInstitucionEducativa(sesionActual.getInstitucionEducativa());
        copiaInicial.setGradoSeccion(sesionActual.getGradoSeccion());
        copiaInicial.setFacilitador(sesionActual.getFacilitador());
        copiaInicial.setNumeroEstudiantes(sesionActual.getNumeroEstudiantes());
        copiaInicial.setNumeroSesion(sesionActual.getNumeroSesion());

        copiaInicial.setTipo(sesionActual.isTipo());
        copiaInicial.setModo(sesionActual.isModo());
        copiaInicial.setFechaHoraInicio(sesionActual.getFechaHoraInicio());
        copiaInicial.setFechaHoraFinal(sesionActual.getFechaHoraFinal());
        copiaInicial.setCancionesIds(new ArrayList<>(sesionActual.getCancionesIds()));
        copiaInicial.setPalabras(new ArrayList<>(sesionActual.getPalabras()));
        copiaInicial.setEstrellas(sesionActual.getEstrellas());
        copiaInicial.setDificultad(sesionActual.getDificultades()); // fixed getter usage
        copiaInicial.setRecomendaciones(sesionActual.getRecomendaciones());
    }

    private boolean hayCambios() {
        if (etNombreSesion != null && !copiaInicial.getNombre().equals(etNombreSesion.getText().toString()))
            return true;

        // Comparar listas y custom
        if (!new HashSet<>(sesionActual.getObjetivosIds()).equals(new HashSet<>(copiaInicial.getObjetivosIds())))
            return true;
        if (!esIgual(sesionActual.getObjetivosCustom(), copiaInicial.getObjetivosCustom()))
            return true;

        if (!new HashSet<>(sesionActual.getTecnicasIds()).equals(new HashSet<>(copiaInicial.getTecnicasIds())))
            return true;
        if (!esIgual(sesionActual.getTecnicasCustom(), copiaInicial.getTecnicasCustom()))
            return true;

        if (!new HashSet<>(sesionActual.getMaterialesIds()).equals(new HashSet<>(copiaInicial.getMaterialesIds())))
            return true;
        if (!esIgual(sesionActual.getMaterialesCustom(), copiaInicial.getMaterialesCustom()))
            return true;

        if (!new HashSet<>(sesionActual.getLogrosIds()).equals(new HashSet<>(copiaInicial.getLogrosIds())))
            return true;
        if (!esIgual(sesionActual.getLogrosCustom(), copiaInicial.getLogrosCustom()))
            return true;

        if (!new HashSet<>(sesionActual.getClimaGrupalIds()).equals(new HashSet<>(copiaInicial.getClimaGrupalIds())))
            return true;
        if (!esIgual(sesionActual.getClimaGrupalCustom(), copiaInicial.getClimaGrupalCustom()))
            return true;

        // Desarrollo
        if (!esIgual(sesionActual.getInicio(), copiaInicial.getInicio()))
            return true;
        if (!esIgual(sesionActual.getActividadCentral(), copiaInicial.getActividadCentral()))
            return true;
        if (!esIgual(sesionActual.getCierre(), copiaInicial.getCierre()))
            return true;

        // Observaciones / Clima Text
        if (!esIgual(sesionActual.getDescripcionClima(), copiaInicial.getDescripcionClima()))
            return true;
        if (!esIgual(sesionActual.getObservaciones(), copiaInicial.getObservaciones()))
            return true;

        // Metadatos nuevos
        if (!esIgual(sesionActual.getInstitucionEducativa(), copiaInicial.getInstitucionEducativa()))
            return true;
        if (!esIgual(sesionActual.getGradoSeccion(), copiaInicial.getGradoSeccion()))
            return true;
        if (!esIgual(sesionActual.getFacilitador(), copiaInicial.getFacilitador()))
            return true;
        if (sesionActual.getNumeroSesion() != copiaInicial.getNumeroSesion())
            return true;
        if (sesionActual.getNumeroEstudiantes() != copiaInicial.getNumeroEstudiantes())
            return true;

        // Extras
        if (!esIgual(sesionActual.getDificultades(), copiaInicial.getDificultades()))
            return true;
        if (!esIgual(sesionActual.getRecomendaciones(), copiaInicial.getRecomendaciones()))
            return true;

        // Switches
        if (swTipoSesion != null && copiaInicial.isTipo() != swTipoSesion.isChecked())
            return true;
        if (swModoSesion != null && copiaInicial.isModo() != swModoSesion.isChecked())
            return true;

        // Fechas
        if (btnFechaInicio != null && !copiaInicial.getFechaHoraInicio().equals(btnFechaInicio.getText().toString()))
            return true;
        if (btnFechaFinal != null && !copiaInicial.getFechaHoraFinal().equals(btnFechaFinal.getText().toString()))
            return true;

        // Canciones
        if (!new HashSet<>(copiaInicial.getCancionesIds()).equals(new HashSet<>(sesionActual.getCancionesIds())))
            return true;

        // Palabras
        if (!new HashSet<>(copiaInicial.getPalabras()).equals(new HashSet<>(sesionActual.getPalabras())))
            return true;

        // Rating
        if (ratingBar != null && copiaInicial.getEstrellas() != ratingBar.getRating())
            return true;

        return false;
    }

    private boolean esIgual(String s1, String s2) {
        if (s1 == null && s2 == null)
            return true;
        if (s1 == null)
            return s2.isEmpty();
        if (s2 == null)
            return s1.isEmpty();
        return s1.equals(s2);
    }

    @SuppressLint("DefaultLocale")
    private void mostrarDateTimePicker(String fechaActualStr, final OnFechaSeleccionada listener) {
        final Calendar calendario = Calendar.getInstance();

        if (fechaActualStr != null && !fechaActualStr.isEmpty() && !fechaActualStr.equals("Seleccionar fecha y hora")) {
            try {
                // Usar el formato que maneja GMT
                SimpleDateFormat parseFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
                Date fecha = parseFormat.parse(fechaActualStr);
                if (fecha != null)
                    calendario.setTime(fecha);
            } catch (ParseException e) {
                Log.e("DateTimePicker", "Error parseando fecha: " + fechaActualStr, e);
            }
        }

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendario.set(Calendar.YEAR, year);
            calendario.set(Calendar.MONTH, month);
            calendario.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendario.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendario.set(Calendar.MINUTE, minute);
                calendario.set(Calendar.SECOND, 0);

                // Formatear para el servidor (con GMT)
                SimpleDateFormat displayFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                        Locale.ENGLISH);
                String fechaFormateada = displayFormat.format(calendario.getTime());

                listener.onFechaSeleccionada(fechaFormateada);

            }, calendario.get(Calendar.HOUR_OF_DAY), calendario.get(Calendar.MINUTE), true).show();

        }, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).show();
    }

    private boolean validarFechas(String fechaInicio, String fechaFinal, boolean esInicio) {
        if (fechaInicio == null || fechaFinal == null || fechaInicio.isEmpty() || fechaFinal.isEmpty() ||
                fechaInicio.equals("Seleccionar fecha y hora") || fechaFinal.equals("Seleccionar fecha y hora")) {
            return true; // No se puede validar si una no está seteada
        }
        try {
            Date inicio = bdFormat.parse(limpiarGMT(fechaInicio));
            Date fin = bdFormat.parse(limpiarGMT(fechaFinal));

            if (inicio != null && fin != null) {
                if (esInicio) {
                    // Al cambiar la fecha de inicio, no debe ser posterior a la final
                    return !inicio.after(fin);
                } else {
                    // Al cambiar la fecha final, no debe ser anterior a la de inicio
                    return !fin.before(inicio);
                }
            }
        } catch (ParseException e) {
            Log.e("ValidarFechas", "Error al parsear fechas", e);
        }
        return true; // Si hay error, no bloquear al usuario
    }

    private String limpiarGMT(String fecha) {
        return fecha.replace(" GMT", ""); // Quita el " GMT" para parsear
    }

    private void guardarSesionEnServidor() {
        // --- INICIO DE VALIDACIÓN DE FECHAS ---
        String fechaInicioEnvio = btnFechaInicio.getText().toString();
        String fechaFinalEnvio = btnFechaFinal.getText().toString();

        // Formato para enviar al servidor (debe coincidir con el que usas en el Picker)
        SimpleDateFormat serverFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance(); // Captura la hora actual del sistema

        // 1. Validar Fecha de Inicio
        if (fechaInicioEnvio.equals("Seleccionar fecha y hora") || fechaInicioEnvio.isEmpty()) {
            // Si no se eligió nada, usamos la hora actual
            fechaInicioEnvio = serverFormat.format(calendar.getTime());
        } else {
            // Si el usuario SÍ eligió una fecha de inicio, actualizamos el objeto
            // 'calendar'
            // con esa fecha para que el cálculo de la fecha final (si es necesaria) sea
            // coherente.
            try {
                // Parseamos la fecha que eligió el usuario (el formato en el botón incluye '
                // GMT')
                Date fechaElegida = serverFormat.parse(fechaInicioEnvio);
                if (fechaElegida != null) {
                    calendar.setTime(fechaElegida);
                }
            } catch (ParseException e) {
                e.printStackTrace();
                // Si falla el parseo, el calendario se queda con la hora actual por seguridad
            }
        }

        // 2. Validar Fecha Final
        if (fechaFinalEnvio.equals("Seleccionar fecha y hora") || fechaFinalEnvio.isEmpty()) {
            // Sumamos 1 hora a la fecha de referencia (que es la de inicio o la actual)
            calendar.add(Calendar.HOUR_OF_DAY, 1);
            fechaFinalEnvio = serverFormat.format(calendar.getTime());
        }
        // --- FIN DE VALIDACIÓN DE FECHAS ---

        SesionGuardarRequest request = new SesionGuardarRequest(
                0, // sesion_id ignorado o 0
                usuario_id,
                etNombreSesion != null ? etNombreSesion.getText().toString() : "",
                sesionActual.getNumeroSesion(),
                sesionActual.getInstitucionEducativa(),
                sesionActual.getGradoSeccion(),
                sesionActual.getFacilitador(),
                sesionActual.getNumeroEstudiantes(),
                swTipoSesion != null ? swTipoSesion.isChecked() : false,
                swModoSesion != null ? swModoSesion.isChecked() : false,
                fechaInicioEnvio,
                fechaFinalEnvio,
                sesionActual.getInicio(),
                sesionActual.getActividadCentral(),
                sesionActual.getCierre(),
                sesionActual.getDescripcionClima(), // descripcion_clima
                sesionActual.getObservaciones(), // observaciones (comentarios)
                false, // favorito
                ratingBar != null ? ratingBar.getRating() : 0,
                0, // color
                sesionActual.getCancionesIds(),
                sesionActual.getPalabras(),
                etDificultades != null ? etDificultades.getText().toString()
                        : (sesionActual.getDificultades() != null ? sesionActual.getDificultades() : ""),
                etRecomendaciones != null ? etRecomendaciones.getText().toString()
                        : (sesionActual.getRecomendaciones() != null ? sesionActual.getRecomendaciones() : ""),
                sesionActual.getObjetivosIds(), sesionActual.getObjetivosCustom(),
                sesionActual.getTecnicasIds(), sesionActual.getTecnicasCustom(),
                sesionActual.getMaterialesIds(), sesionActual.getMaterialesCustom(),
                sesionActual.getLogrosIds(), sesionActual.getLogrosCustom(),
                sesionActual.getClimaGrupalIds(), sesionActual.getClimaGrupalCustom());

        ApiService api = ApiClient.getRetrofitInstance().create(ApiService.class);
        api.guardarSesion(request).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(DatosSesionesActivity.this, "Sesión guardada con éxito", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent();
                    intent.putExtra("sesion_actualizada", true);
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    Toast.makeText(DatosSesionesActivity.this, "Error guardando sesión", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(DatosSesionesActivity.this, "Fallo en la conexión", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void actualizarSesionEnServidor() {
        // --- INICIO DE VALIDACIÓN DE FECHAS (Copia exacta de la lógica de guardar) ---
        String fechaInicioEnvio = btnFechaInicio.getText().toString();
        String fechaFinalEnvio = btnFechaFinal.getText().toString();

        SimpleDateFormat serverFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance();

        // 1. Validar Fecha de Inicio
        if (fechaInicioEnvio.equals("Seleccionar fecha y hora") || fechaInicioEnvio.isEmpty()) {
            fechaInicioEnvio = serverFormat.format(calendar.getTime());
        } else {
            try {
                Date fechaElegida = serverFormat.parse(fechaInicioEnvio);
                if (fechaElegida != null) {
                    calendar.setTime(fechaElegida);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        // 2. Validar Fecha Final
        if (fechaFinalEnvio.equals("Seleccionar fecha y hora") || fechaFinalEnvio.isEmpty()) {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
            fechaFinalEnvio = serverFormat.format(calendar.getTime());
        }
        // --- FIN DE VALIDACIÓN ---

        // Nota: En actualizar pasamos el ID de la sesión como primer parámetro
        SesionGuardarRequest request = new SesionGuardarRequest(
                sesionActual.getId(),
                usuario_id,
                etNombreSesion != null ? etNombreSesion.getText().toString() : "",
                sesionActual.getNumeroSesion(),
                sesionActual.getInstitucionEducativa(),
                sesionActual.getGradoSeccion(),
                sesionActual.getFacilitador(),
                sesionActual.getNumeroEstudiantes(),
                swTipoSesion != null ? swTipoSesion.isChecked() : false,
                swModoSesion != null ? swModoSesion.isChecked() : false,
                fechaInicioEnvio,
                fechaFinalEnvio,
                sesionActual.getInicio(),
                sesionActual.getActividadCentral(),
                sesionActual.getCierre(),
                sesionActual.getDescripcionClima(),
                sesionActual.getObservaciones(),
                false, // favorito
                ratingBar != null ? ratingBar.getRating() : sesionActual.getEstrellas(),
                0, // color
                sesionActual.getCancionesIds(),
                sesionActual.getPalabras(),
                etDificultades != null ? etDificultades.getText().toString()
                        : (sesionActual.getDificultades() != null ? sesionActual.getDificultades() : ""),
                etRecomendaciones != null ? etRecomendaciones.getText().toString()
                        : (sesionActual.getRecomendaciones() != null ? sesionActual.getRecomendaciones() : ""),
                sesionActual.getObjetivosIds(), sesionActual.getObjetivosCustom(),
                sesionActual.getTecnicasIds(), sesionActual.getTecnicasCustom(),
                sesionActual.getMaterialesIds(), sesionActual.getMaterialesCustom(),
                sesionActual.getLogrosIds(), sesionActual.getLogrosCustom(),
                sesionActual.getClimaGrupalIds(), sesionActual.getClimaGrupalCustom());

        ApiService api = ApiClient.getRetrofitInstance().create(ApiService.class);
        api.actualizarSesion(request).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(DatosSesionesActivity.this, "Sesión actualizada", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent();
                    intent.putExtra("sesion_actualizada", true);
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    Toast.makeText(DatosSesionesActivity.this, "Error al actualizar", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(DatosSesionesActivity.this, "Fallo en conexión", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validarCampos() {
        if (etNombreSesion == null || etNombreSesion.getText().toString().trim().isEmpty()) {
            if (etNombreSesion != null) {
                etNombreSesion.setError("El nombre es obligatorio");
            }
            return false;
        }
        return true;
    }

    // Adapter interno para ViewPager2
    private class SesionPagerAdapter extends RecyclerView.Adapter<SesionPagerAdapter.ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            switch (viewType) {
                case 0: // Generales
                    view = inflater.inflate(R.layout.tab_generales_sesion, parent, false);
                    break;
                case 1: // Objetivos
                    view = inflater.inflate(R.layout.tab_objetivos_sesion, parent, false);
                    break;
                case 2: // Técnicas
                    view = inflater.inflate(R.layout.tab_tecnicas_sesion, parent, false);
                    break;
                case 3: // Materiales
                    view = inflater.inflate(R.layout.tab_materiales_sesion, parent, false);
                    break;
                case 4: // Desarrollo
                    view = inflater.inflate(R.layout.tab_desarrollo_sesion, parent, false);
                    break;
                case 5: // Observaciones
                    view = inflater.inflate(R.layout.tab_observaciones_sesion, parent, false);
                    break;
                case 6: // Logros
                    view = inflater.inflate(R.layout.tab_logros_sesion, parent, false);
                    break;
                case 7: // Extras
                    view = inflater.inflate(R.layout.tab_extras_sesion, parent, false);
                    break;
                default:
                    view = inflater.inflate(R.layout.item_tab_sesion, parent, false);
                    break;
            }
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            View rootView = holder.itemView;

            switch (position) {
                case 0: // Generales
                    etNombreSesion = rootView.findViewById(R.id.etNombreSesion);
                    swTipoSesion = rootView.findViewById(R.id.swTipoSesion);
                    swModoSesion = rootView.findViewById(R.id.swModoSesion);
                    btnFechaInicio = rootView.findViewById(R.id.btnFechaInicio);
                    btnFechaFinal = rootView.findViewById(R.id.btnFechaFinal);
                    btnCanciones = rootView.findViewById(R.id.btnCanciones);
                    btnEmociones = rootView.findViewById(R.id.btnEmociones);

                    // Inicializar nuevos componentes (Spinner, NumberPicker, etc.)
                    inicializarComponentesGenerales(rootView);

                    // Configurar los listeners y datos iniciales
                    configurarComponentesGenerales();
                    break;

                case 1: // Objetivos
                    RecyclerView rvObjetivos = rootView.findViewById(R.id.rvGridObjetivos);
                    sincronizarListaConIDs(opcionesObjetivos, sesionActual.getObjetivosIds(),
                            sesionActual.getObjetivosCustom());
                    configurarRecyclerViewGrid(rvObjetivos, opcionesObjetivos, () -> {
                        sesionActual.setObjetivosIds(obtenerIDsSeleccionados(opcionesObjetivos));
                        sesionActual.setObjetivosCustom(obtenerTextoCustom(opcionesObjetivos));
                    });
                    break;

                case 2: // Técnicas
                    RecyclerView rvTecnicas = rootView.findViewById(R.id.rvGridTecnicas);
                    sincronizarListaConIDs(opcionesTecnicas, sesionActual.getTecnicasIds(),
                            sesionActual.getTecnicasCustom());
                    configurarRecyclerViewGrid(rvTecnicas, opcionesTecnicas, () -> {
                        sesionActual.setTecnicasIds(obtenerIDsSeleccionados(opcionesTecnicas));
                        sesionActual.setTecnicasCustom(obtenerTextoCustom(opcionesTecnicas));
                    });
                    break;

                case 3: // Materiales
                    RecyclerView rvMateriales = rootView.findViewById(R.id.rvGridMateriales);
                    sincronizarListaConIDs(opcionesMateriales, sesionActual.getMaterialesIds(),
                            sesionActual.getMaterialesCustom());
                    configurarRecyclerViewGrid(rvMateriales, opcionesMateriales, () -> {
                        sesionActual.setMaterialesIds(obtenerIDsSeleccionados(opcionesMateriales));
                        sesionActual.setMaterialesCustom(obtenerTextoCustom(opcionesMateriales));
                    });
                    break;

                case 4: // Desarrollo
                    EditText etInicio = rootView.findViewById(R.id.etDesarrolloInicio);
                    EditText etCentral = rootView.findViewById(R.id.etDesarrolloCentral);
                    EditText etCierre = rootView.findViewById(R.id.etDesarrolloCierre);

                    if (etInicio != null) {
                        etInicio.setText(sesionActual.getInicio());
                        etInicio.addTextChangedListener(new TextWatcher() {
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            public void afterTextChanged(Editable s) {
                                sesionActual.setInicio(s.toString());
                            }
                        });
                    }
                    if (etCentral != null) {
                        etCentral.setText(sesionActual.getActividadCentral());
                        etCentral.addTextChangedListener(new TextWatcher() {
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            public void afterTextChanged(Editable s) {
                                sesionActual.setActividadCentral(s.toString());
                            }
                        });
                    }
                    if (etCierre != null) {
                        etCierre.setText(sesionActual.getCierre());
                        etCierre.addTextChangedListener(new TextWatcher() {
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            public void afterTextChanged(Editable s) {
                                sesionActual.setCierre(s.toString());
                            }
                        });
                    }
                    break;

                case 5: // Observaciones
                    // 1. Clima Grupal (Grid)
                    RecyclerView rvClima = rootView.findViewById(R.id.rvGridClimaGrupal);
                    sincronizarListaConIDs(opcionesClimaGrupal, sesionActual.getClimaGrupalIds(),
                            sesionActual.getClimaGrupalCustom());
                    configurarRecyclerViewGrid(rvClima, opcionesClimaGrupal, () -> {
                        sesionActual.setClimaGrupalIds(obtenerIDsSeleccionados(opcionesClimaGrupal));
                        sesionActual.setClimaGrupalCustom(obtenerTextoCustom(opcionesClimaGrupal));
                    });

                    // 2. Descripción (Observaciones generales - Clima)
                    etObservaciones = rootView.findViewById(R.id.etObservacionesDescripcion);
                    if (etObservaciones != null) {
                        etObservaciones.setText(
                                sesionActual.getDescripcionClima() != null ? sesionActual.getDescripcionClima() : "");
                        etObservaciones.addTextChangedListener(new TextWatcher() {
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            public void afterTextChanged(Editable s) {
                                sesionActual.setDescripcionClima(s.toString());
                            }
                        });
                    }

                    // 3. Calificación de la Sesión (Rating)
                    ratingBar = rootView.findViewById(R.id.ratingSesion);
                    if (ratingBar != null) {
                        ratingBar.setRating(sesionActual.getEstrellas());
                        ratingBar.setOnRatingBarChangeListener((rb, rating, fromUser) -> {
                            sesionActual.setEstrellas(rating);
                        });
                    }

                    // 4. Comentarios Adicionales (antes Participación)
                    EditText etComentariosPart = rootView.findViewById(R.id.etComentariosParticipacion);
                    if (etComentariosPart != null) {
                        etComentariosPart.setText(sesionActual.getObservaciones()); // Maps to 'observaciones' DB
                        etComentariosPart.addTextChangedListener(new TextWatcher() {
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            public void afterTextChanged(Editable s) {
                                sesionActual.setObservaciones(s.toString());
                            }
                        });
                    }
                    break;

                case 6: // Logros
                    RecyclerView rvLogros = rootView.findViewById(R.id.rvGridLogros);
                    sincronizarListaConIDs(opcionesLogros, sesionActual.getLogrosIds(), sesionActual.getLogrosCustom());
                    configurarRecyclerViewGrid(rvLogros, opcionesLogros, () -> {
                        sesionActual.setLogrosIds(obtenerIDsSeleccionados(opcionesLogros));
                        sesionActual.setLogrosCustom(obtenerTextoCustom(opcionesLogros));
                    });
                    break;

                case 7: // Extras
                    etDificultades = rootView.findViewById(R.id.etDificultades);
                    etRecomendaciones = rootView.findViewById(R.id.etRecomendaciones);
                    if (etDificultades != null) {
                        etDificultades
                                .setText(sesionActual.getDificultades() != null ? sesionActual.getDificultades() : "");
                        etDificultades.addTextChangedListener(new TextWatcher() {
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            public void afterTextChanged(Editable s) {
                                sesionActual.setDificultad(s.toString());
                            }
                        });
                    }
                    if (etRecomendaciones != null) {
                        etRecomendaciones.setText(
                                sesionActual.getRecomendaciones() != null ? sesionActual.getRecomendaciones() : "");
                        etRecomendaciones.addTextChangedListener(new TextWatcher() {
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            public void afterTextChanged(Editable s) {
                                sesionActual.setRecomendaciones(s.toString());
                            }
                        });
                    }
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return NUM_TABS;
        }

        @Override
        public int getItemViewType(int position) {
            return position; // Usar la posición como tipo de vista
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView, int viewType) {
                super(itemView);
            }
        }
    }

    private void inicializarComponentesGenerales(View rootView) {

        // Inicializar nuevos componentes
        etNombreIE = rootView.findViewById(R.id.etNombreIE);
        etNombreFacilitador = rootView.findViewById(R.id.etNombreFacilitador);
        spinnerGrado = rootView.findViewById(R.id.spinnerGrado);
        spinnerSeccion = rootView.findViewById(R.id.spinnerSeccion);
        npNumeroEstudiantes = rootView.findViewById(R.id.npNumeroEstudiantes);
        tvDuracionSesion = rootView.findViewById(R.id.etDuracionSesion);

        // Configurar Spinner de Grado (1-6)
        ArrayAdapter<String> adapterGrado = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[] { "1", "2", "3", "4", "5", "6" });
        adapterGrado.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerGrado != null) {
            spinnerGrado.setAdapter(adapterGrado);
        }

        // Configurar Spinner de Sección (A-D)
        ArrayAdapter<String> adapterSeccion = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[] { "A", "B", "C", "D" });
        adapterSeccion.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerSeccion != null) {
            spinnerSeccion.setAdapter(adapterSeccion);
        }

        // NumberPicker ya está configurado en el XML (min=1, max=50)
    }

    private void configurarComponentesGenerales() {
        if (sesionActual == null)
            return;

        if (etNombreSesion != null)
            etNombreSesion.setText(sesionActual.getNombre());
        if (etNombreIE != null)
            etNombreIE.setText(sesionActual.getInstitucionEducativa());
        if (etNombreFacilitador != null)
            etNombreFacilitador.setText(sesionActual.getFacilitador());

        if (spinnerGrado != null && spinnerSeccion != null && sesionActual.getGradoSeccion() != null) {
            String[] parts = sesionActual.getGradoSeccion().split(" ");
            if (parts.length > 0) {
                @SuppressWarnings("unchecked")
                ArrayAdapter<String> adapterG = (ArrayAdapter<String>) spinnerGrado.getAdapter();
                int pos = adapterG.getPosition(parts[0]);
                if (pos >= 0)
                    spinnerGrado.setSelection(pos);
            }
            if (parts.length > 1) {
                @SuppressWarnings("unchecked")
                ArrayAdapter<String> adapterS = (ArrayAdapter<String>) spinnerSeccion.getAdapter();
                int pos = adapterS.getPosition(parts[1]);
                if (pos >= 0)
                    spinnerSeccion.setSelection(pos);
            }
        }

        if (npNumeroEstudiantes != null) {
            // Asegurarse de que esté en rango (1-50 según XML anterior o lógica Default)
            int num = sesionActual.getNumeroEstudiantes();
            if (num < 1)
                num = 1;
            npNumeroEstudiantes.setValue(num);
        }

        if (swTipoSesion != null)
            swTipoSesion.setChecked(sesionActual.isTipo());
        if (swModoSesion != null)
            swModoSesion.setChecked(sesionActual.isModo());

        if (btnFechaInicio != null) {
            btnFechaInicio
                    .setText(sesionActual.getFechaHoraInicio() != null && !sesionActual.getFechaHoraInicio().isEmpty()
                            ? limpiarGMT(sesionActual.getFechaHoraInicio())
                            : "Seleccionar fecha y hora");
        }
        if (btnFechaFinal != null) {
            btnFechaFinal
                    .setText(sesionActual.getFechaHoraFinal() != null && !sesionActual.getFechaHoraFinal().isEmpty()
                            ? limpiarGMT(sesionActual.getFechaHoraFinal())
                            : "Seleccionar fecha y hora");
        }

        actualizarDuracionSesion();

        if (btnCanciones != null) {
            btnCanciones.setText(sesionActual.getCantidadCanciones() + " Canciones Seleccionadas");
        }

        configurarListenersGenerales();
    }

    private void configurarListenersGenerales() {
        if (etNombreIE != null) {
            etNombreIE.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                public void afterTextChanged(Editable s) {
                    sesionActual.setInstitucionEducativa(s.toString());
                }
            });
        }
        if (etNombreFacilitador != null) {
            etNombreFacilitador.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                public void afterTextChanged(Editable s) {
                    sesionActual.setFacilitador(s.toString());
                }
            });
        }
        if (npNumeroEstudiantes != null) {
            npNumeroEstudiantes.setOnValueChangedListener((picker, oldVal, newVal) -> {
                sesionActual.setNumeroEstudiantes(newVal);
            });
        }

        // Listeners para Grado y Sección
        android.widget.AdapterView.OnItemSelectedListener spinnerListener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (spinnerGrado != null && spinnerSeccion != null) {
                    Object gradoObj = spinnerGrado.getSelectedItem();
                    Object seccionObj = spinnerSeccion.getSelectedItem();
                    String grado = gradoObj != null ? gradoObj.toString() : "1";
                    String seccion = seccionObj != null ? seccionObj.toString() : "A";
                    sesionActual.setGradoSeccion(grado + " " + seccion);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        if (spinnerGrado != null)
            spinnerGrado.setOnItemSelectedListener(spinnerListener);
        if (spinnerSeccion != null)
            spinnerSeccion.setOnItemSelectedListener(spinnerListener);

        // 👉 Abrir popup de selección de canciones
        btnCanciones.setOnClickListener(v -> {
            List<Integer> idsPreseleccionados = new ArrayList<>(sesionActual.getCancionesIds());
            PopupSeleccionCanciones popup = new PopupSeleccionCanciones(
                    DatosSesionesActivity.this,
                    canciones,
                    idsPreseleccionados,
                    idsSeleccionados -> {
                        sesionActual.setCancionesIds(idsSeleccionados);
                        sesionActual.setCantidadCanciones(idsSeleccionados.size());
                        btnCanciones.setText(idsSeleccionados.size() + " Canciones Seleccionadas");
                    });
            popup.show();
        });

        btnFechaInicio.setOnClickListener(v -> mostrarDateTimePicker(sesionActual.getFechaHoraInicio(), fecha -> {
            if (!validarFechas(fecha, sesionActual.getFechaHoraFinal(), true)) {
                Toast.makeText(this, "La fecha de inicio no puede ser posterior a la fecha final", Toast.LENGTH_LONG)
                        .show();
                return;
            }
            btnFechaInicio.setText(fecha);
            sesionActual.setFechaHoraInicio(fecha);
            actualizarDuracionSesion();
        }));

        btnFechaFinal.setOnClickListener(v -> mostrarDateTimePicker(sesionActual.getFechaHoraFinal(), fecha -> {
            if (!validarFechas(sesionActual.getFechaHoraInicio(), fecha, false)) {
                Toast.makeText(this, "La fecha final no puede ser anterior a la fecha de inicio", Toast.LENGTH_LONG)
                        .show();
                return;
            }
            btnFechaFinal.setText(fecha);
            sesionActual.setFechaHoraFinal(fecha);
            actualizarDuracionSesion();
        }));

        btnEmociones.setOnClickListener(v -> {
            RuedaEmocionalDialogFragmentSesion dialog = new RuedaEmocionalDialogFragmentSesion(
                    sesionActual.getPalabras(),
                    (emocion, intensidad) -> {
                        String nivelArousal = (intensidad == 3) ? "alto" : (intensidad == 2) ? "medio" : "bajo";
                        List<EmocionSeleccionada> listaEmociones = cargarPalabrasDesdeArchivo(emocion, nivelArousal);
                        int backgroundColor = getColorForEmotion(emocion, nivelArousal);
                        int textColor = "bajo".equalsIgnoreCase(nivelArousal) ? Color.BLACK : Color.WHITE;

                        Set<EmocionSeleccionada> seleccionInicial = new HashSet<>();
                        for (EmocionSeleccionada item : listaEmociones) {
                            if (sesionActual.getPalabras().contains(item.getPalabra())) {
                                seleccionInicial.add(item);
                            }
                        }

                        mostrarPopupSeleccionMultiple(
                                listaEmociones,
                                seleccionInicial,
                                emocion + " (" + nivelArousal + ")",
                                backgroundColor,
                                textColor,
                                false);
                    });
            dialog.show(getSupportFragmentManager(), "popup_rueda");
        });
    }

    public void mostrarPopupEmocionesGlobal(List<PalabraEmocionalUnica> listaGlobal) {
        Set<String> palabrasSeleccionadas = new HashSet<>(sesionActual.getPalabras());

        Set<PalabraEmocionalUnica> seleccionInicial = new HashSet<>();
        for (PalabraEmocionalUnica palabraUnica : listaGlobal) {
            if (palabrasSeleccionadas.contains(palabraUnica.getPalabra())) {
                seleccionInicial.add(palabraUnica);
            }
        }

        int backgroundColor = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                        ? R.color.background_dark
                        : R.color.background_light;
        int textColor = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                        ? Color.WHITE
                        : Color.BLACK;

        mostrarPopupSeleccionMultiple(listaGlobal, seleccionInicial, "Búsqueda Global de Emociones", backgroundColor,
                textColor, true);
    }

    private <T extends Seleccionable> void mostrarPopupSeleccionMultiple(List<T> listaCompleta, Set<T> seleccionInicial,
            String titulo, int backgroundColorRes, int textColor, boolean isGlobalSearch) {
        LayoutInflater inflater = getLayoutInflater();
        View popupView = inflater.inflate(R.layout.popup_lista, null);

        if (backgroundColorRes != 0) {
            popupView.setBackgroundColor(ContextCompat.getColor(this, backgroundColorRes));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
        tvSubtitulo.setText("Selecciona emociones");
        recycler.setLayoutManager(new LinearLayoutManager(this));

        if (textColor != 0) {
            tvTitulo.setTextColor(textColor);
            tvSubtitulo.setTextColor(textColor);
            tvContadorGlobal.setTextColor(textColor);
            tvContadorLocal.setTextColor(textColor);
            cbFiltrar.setTextColor(textColor);
            btnAceptar.setTextColor(textColor);
            btnCancelar.setTextColor(textColor);
        }

        int seleccionGlobalTotal = sesionActual.getPalabras() != null ? sesionActual.getPalabras().size() : 0;

        PopupListaAdapter<T> adapter = // "Cocinero, aquí tienes tu orden:"
                new PopupListaAdapter<>(
                        listaCompleta, // 1. "Usa esta lista completa de ingredientes."
                        seleccionInicial, // 2. "De esa lista, estos ya deben estar marcados."
                        tvContadorGlobal, // 3. "Este es el marcador de 'Total' que debes actualizar."
                        tvContadorLocal, // 4. "Este es el marcador para 'En esta lista'."
                        textColor, // 5. "Usa este color para el texto."
                        seleccionGlobalTotal // 6. "Importante: El número total de ingredientes seleccionados en TODO el
                                             // restaurante es este. No te pases de 10."
                );
        recycler.setAdapter(adapter);

        buscador.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setTextoBusqueda(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        cbFiltrar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            adapter.setMostrarSoloSeleccionados(isChecked);
        });

        btnAceptar.setOnClickListener(v -> {
            Set<T> seleccionFinalPopup = adapter.getSeleccionados();
            Set<String> nuevasPalabras = new HashSet<>();
            for (T item : seleccionFinalPopup) {
                if (item instanceof EmocionSeleccionada) {
                    nuevasPalabras.add(((EmocionSeleccionada) item).getPalabra());
                } else if (item instanceof PalabraEmocionalUnica) {
                    nuevasPalabras.add(((PalabraEmocionalUnica) item).getPalabra());
                }
            }

            Set<String> palabrasFinales = new HashSet<>(sesionActual.getPalabras());

            if (isGlobalSearch) {
                palabrasFinales = nuevasPalabras;
            } else {
                Set<String> palabrasDeCategoriaActual = listaCompleta.stream()
                        .map(item -> ((EmocionSeleccionada) item).getPalabra())
                        .collect(Collectors.toSet());
                palabrasFinales.removeAll(palabrasDeCategoriaActual);
                palabrasFinales.addAll(nuevasPalabras);
            }

            sesionActual.setPalabras(new ArrayList<>(palabrasFinales));
            Toast.makeText(this, palabrasFinales.size() + " emociones guardadas", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void actualizarDuracionSesion() {
        if (tvDuracionSesion == null || sesionActual == null)
            return;

        String inicioStr = sesionActual.getFechaHoraInicio();
        String finalStr = sesionActual.getFechaHoraFinal();

        if (inicioStr == null || inicioStr.isEmpty() || finalStr == null || finalStr.isEmpty() ||
                inicioStr.equals("Seleccionar fecha y hora") || finalStr.equals("Seleccionar fecha y hora")) {
            tvDuracionSesion.setText("-");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        try {
            Date inicio = sdf.parse(inicioStr);
            Date fin = sdf.parse(finalStr);

            if (inicio != null && fin != null) {
                long diff = fin.getTime() - inicio.getTime();
                if (diff < 0) {
                    tvDuracionSesion.setText("Error"); // Fecha final antes que inicio
                } else {
                    long minutos = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff);
                    tvDuracionSesion.setText(minutos + " min");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            tvDuracionSesion.setText("-");
        }
    }

}

interface OnStringChangeListener {
    void onStringChanged(String newValue);
}

interface OnFechaSeleccionada {
    void onFechaSeleccionada(String fecha);
}
