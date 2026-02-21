package com.example.intentoappdatosmusica;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RuedaEmocionalDialogFragment extends DialogFragment {

    private Seccion seccionActual;
    private List<String> palabrasSeleccionadas; // Lista de palabras ya seleccionadas en la sección

    // Mapa para palabras que pertenecen a una única emoción
    private Map<String, String> palabraEmocionUnica;
    // Conjunto de palabras que aparecen en múltiples emociones
    private Set<String> palabrasRepetidas;

    // Ángulos (grados) para cada emoción (mismo mapeo que en la otra clase)
    private static final Map<String, Double> EMOCION_ANGULO;
    static {
        EMOCION_ANGULO = new HashMap<>();
        EMOCION_ANGULO.put("joy", 0.0);
        EMOCION_ANGULO.put("trust", 45.0);
        EMOCION_ANGULO.put("fear", 90.0);
        EMOCION_ANGULO.put("surprise", 135.0);
        EMOCION_ANGULO.put("sadness", 180.0);
        EMOCION_ANGULO.put("disgust", 225.0);
        EMOCION_ANGULO.put("anger", 270.0);
        EMOCION_ANGULO.put("anticipation", 315.0);
    }

    public RuedaEmocionalDialogFragment(Seccion seccionActual) {
        this.seccionActual = seccionActual;
        // Obtener las palabras seleccionadas de la sección
        this.palabrasSeleccionadas = obtenerPalabrasDeSeccion(seccionActual);
    }

    // Método para extraer las palabras de las emociones seleccionadas en la sección
    private List<String> obtenerPalabrasDeSeccion(Seccion seccion) {
        List<String> palabras = new ArrayList<>();
        if (seccion != null && seccion.getEmociones() != null) {
            for (EmocionSeleccionada emocion : seccion.getEmociones()) {
                if (emocion != null && emocion.getPalabra() != null) {
                    palabras.add(emocion.getPalabra());
                }
            }
        }
        return palabras;
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        analizarDiccionarioEmociones(); // Analizar el CSV para identificar palabras únicas y repetidas

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_rueda_emocional, null);

        ImageView imagenRueda = view.findViewById(R.id.imagen_rueda);
        FrameLayout contenedorChecks = view.findViewById(R.id.contenedor_checks);
        Button btnBuscar = view.findViewById(R.id.btn_buscar_palabras);

        btnBuscar.setOnClickListener(v -> mostrarBusquedaGlobal());

        // Forzar al frente
        contenedorChecks.bringToFront();
        contenedorChecks.setElevation(1000f);

        // Esperar a que la imageView tenga tamaño y drawable para mostrar los checks
        imagenRueda.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                imagenRueda.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Si no hay drawable -> nada
                if (imagenRueda.getDrawable() == null) return;

                // bitmap dimension
                Bitmap bitmap = ((BitmapDrawable) imagenRueda.getDrawable()).getBitmap();
                if (bitmap == null) return;
                int bitmapW = bitmap.getWidth();
                int bitmapH = bitmap.getHeight();

                // Calcular y añadir checks para todas las combinaciones emoción/intensidad
                for (Map.Entry<String, Double> entry : EMOCION_ANGULO.entrySet()) {
                    String emocion = entry.getKey();
                    double anguloDeg = entry.getValue();

                    // intensidades: 3 (alto) = más interno, 2 = medio, 1 = más externo
                    for (int intensidad = 1; intensidad <= 3; intensidad++) {
                        // si hay palabras seleccionadas para esta emoción+intensidad -> dibujar check
                        if (contieneZona(emocion, intensidadToNivel(intensidad), palabrasSeleccionadas)) {
                            // calcular punto en coordenadas del bitmap
                            PointF ptBitmap = puntoEnBitmapPara(anguloDeg, intensidad, bitmapW, bitmapH);

                            // convertir ese punto a coordenadas en la ImageView (view)
                            PointF ptView = bitmapToImageViewCoords(ptBitmap.x, ptBitmap.y, imagenRueda, bitmap);

                            // añadir el check en el contenedor usando márgenes
                            agregarCheckEn(contenedorChecks, ptView.x, ptView.y);
                        }
                    }
                }
            }
        });

        // Touch para detectar selección por parte del usuario
        imagenRueda.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();

                Pair<String, Integer> resultado = detectarZona(event.getX(), event.getY(), imagenRueda);
                String emocion = resultado.first;
                int intensidad = resultado.second;

                if (emocion != null) {
                    mostrarPopupPalabras(emocion, intensidad);
                    dismiss();
                }
            }
            return true;
        });

        builder.setView(view);
        return builder.create();
    }

    private void mostrarBusquedaGlobal() {
        List<PalabraEmocionalUnica> listaGlobal = new ArrayList<>();
        File archivo = new File(requireContext().getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");

        Map<String, Integer> conteoPalabras = new HashMap<>();

        // Primera pasada: contar ocurrencias
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            reader.readLine(); // Omitir encabezado
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.trim().split(",");
                if (partes.length >= 6) {
                    String term = partes[0].trim().toLowerCase();
                    conteoPalabras.put(term, conteoPalabras.getOrDefault(term, 0) + 1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Segunda pasada: construir la lista con palabras únicas
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            reader.readLine(); // Omitir encabezado
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.trim().split(",");
                if (partes.length >= 6) {
                    String term = partes[0].trim();
                    if (conteoPalabras.getOrDefault(term.toLowerCase(), 0) == 1) {
                        String emocion = partes[1].trim();
                        String nivel = partes[5].trim();
                        int bgColor = ((DatosMusicalesActivity) getActivity()).getSeccionesController().getColorForEmotion(emocion, nivel);
                        int textColor = "bajo".equalsIgnoreCase(nivel) ? Color.BLACK : Color.WHITE;
                        listaGlobal.add(new PalabraEmocionalUnica(term, emocion, nivel, ContextCompat.getColor(getContext(), bgColor), textColor));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Collections.sort(listaGlobal, (o1, o2) -> o1.getPalabra().compareToIgnoreCase(o2.getPalabra()));

        ((DatosMusicalesActivity) getActivity()).getSeccionesController().mostrarPopupEmocionesGlobal(seccionActual, listaGlobal);
        dismiss();
    }

    // Analiza el archivo CSV para clasificar palabras como únicas o repetidas
    private void analizarDiccionarioEmociones() {
        palabraEmocionUnica = new HashMap<>();
        palabrasRepetidas = new HashSet<>();
        Map<String, Set<String>> palabraAEmociones = new HashMap<>();
        File archivo = new File(requireContext().getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");

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

    // convierte: intensidad int -> string "alto"/"medio"/"bajo"
    private String intensidadToNivel(int intensidad) {
        if (intensidad == 3) return "alto";
        if (intensidad == 2) return "medio";
        return "bajo";
    }

    // devuelve un punto en coordenadas del bitmap original (px) para un ángulo e intensidad
    private PointF puntoEnBitmapPara(double anguloDeg, int intensidad, int bitmapW, int bitmapH) {
        // centro:
        float cx = bitmapW / 2f;
        float cy = bitmapH / 2f;

        // radios relativos (mismos factores que en la otra clase)
        float maxRadio = Math.min(bitmapW, bitmapH) * 0.45f;
        float r3 = maxRadio * 0.25f; // intens 3 -> 25% del maxRadio
        float r2 = maxRadio * 0.55f; // intens 2 -> 55%
        float r1 = maxRadio * 0.85f; // intens 1 -> 85%

        float r;
        if (intensidad == 3) r = r3;
        else if (intensidad == 2) r = r2;
        else r = r1;

        // transformar angulo deg (0 = arriba) a radian
        double rad = Math.toRadians(anguloDeg);
        // con 0° arriba:
        float x = cx + (float) (r * Math.sin(rad));
        float y = cy - (float) (r * Math.cos(rad));

        return new PointF(x, y);
    }

    // Mapea un punto en coords del bitmap (px) a coords en la ImageView (px)
    private PointF bitmapToImageViewCoords(float imageX, float imageY, ImageView imageView, Bitmap bitmap) {
        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scale = Math.min(imageViewWidth / bitmapWidth, imageViewHeight / bitmapHeight);
        float scaledBitmapWidth = bitmapWidth * scale;
        float scaledBitmapHeight = bitmapHeight * scale;

        float left = (imageViewWidth - scaledBitmapWidth) / 2f;
        float top = (imageViewHeight - scaledBitmapHeight) / 2f;

        // imageX/imageY están en coordenadas del bitmap original -> convertir a view:
        float viewX = left + (imageX * scale);
        float viewY = top + (imageY * scale);

        return new PointF(viewX, viewY);
    }

    // añade un ImageView (check) en el contenedor FrameLayout
    private void agregarCheckEn(FrameLayout contenedor, float viewX, float viewY) {
        int sizePx = dpToPx(requireContext(), 28);
        ImageView iv = new ImageView(requireContext());
        iv.setImageResource(R.drawable.iconocheck);
        iv.setLayoutParams(new FrameLayout.LayoutParams(sizePx, sizePx));

        // left/top como integer
        int left = Math.round(viewX - sizePx / 2f); // centrar el check en el punto
        int top = Math.round(viewY - sizePx / 2f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx);
        params.leftMargin = left;
        params.topMargin = top;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        iv.setLayoutParams(params);

        contenedor.addView(iv);
    }

    private int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // verifica si cualquiera de las palabras seleccionadas pertenece a la lista de la emoción+nivel
    private boolean contieneZona(String emocion, String nivel, List<String> seleccionadas) {
        if (seleccionadas == null || seleccionadas.isEmpty()) return false;

        List<EmocionSeleccionada> listaZona = cargarPalabrasDesdeArchivo(emocion, nivel);
        if (listaZona == null || listaZona.isEmpty()) return false;

        // comparar ignorando mayúsculas y espacios
        Set<String> zonaSet = new HashSet<>();
        for (EmocionSeleccionada e : listaZona) zonaSet.add(e.getPalabra().trim().toLowerCase());

        for (String palabra : seleccionadas) {
            if (palabra == null) continue;
            if (zonaSet.contains(palabra.trim().toLowerCase())) return true;
        }
        return false;
    }

    // carga desde archivo CSV, excluyendo las palabras que se repiten en múltiples emociones
    private List<EmocionSeleccionada> cargarPalabrasDesdeArchivo(String emocion, String nivelArousal) {
        List<EmocionSeleccionada> lista = new ArrayList<>();
        File archivo = new File(requireContext().getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");

        if (palabrasRepetidas == null) {
            analizarDiccionarioEmociones(); // Asegurarse de que el análisis se ha hecho
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

    // Tu método detectarZona existente (sin cambios)
    private Pair<String, Integer> detectarZona(float x, float y, ImageView imageView) {
        Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scale = Math.min(imageViewWidth / bitmapWidth, imageViewHeight / bitmapHeight);
        float scaledBitmapWidth = bitmapWidth * scale;
        float scaledBitmapHeight = bitmapHeight * scale;

        float left = (imageViewWidth - scaledBitmapWidth) / 2;
        float top = (imageViewHeight - scaledBitmapHeight) / 2;

        float imageX = (x - left) / scale;
        float imageY = (y - top) / scale;

        if (imageX < 0 || imageX >= bitmapWidth || imageY < 0 || imageY >= bitmapHeight) {
            return new Pair<>(null, 0);
        }

        float cx = bitmapWidth / 2f;
        float cy = bitmapHeight / 2f;

        float dx = imageX - cx;
        float dy = imageY - cy;

        double distancia = Math.sqrt(dx * dx + dy * dy);
        double angulo = Math.toDegrees(Math.atan2(-dy, -dx));
        if (angulo < 0) angulo += 360;

        String emocion;
        if ((angulo >= 337.5 && angulo < 360) || (angulo >= 0 && angulo < 22.5)) emocion = "anger";
        else if (angulo >= 22.5 && angulo < 67.5) emocion = "anticipation";
        else if (angulo >= 67.5 && angulo < 112.5) emocion = "joy";
        else if (angulo >= 112.5 && angulo < 157.5) emocion = "trust";
        else if (angulo >= 157.5 && angulo < 202.5) emocion = "fear";
        else if (angulo >= 202.5 && angulo < 247.5) emocion = "surprise";
        else if (angulo >= 247.5 && angulo < 292.5) emocion = "sadness";
        else if (angulo >= 292.5 && angulo < 337.5) emocion = "disgust";
        else emocion = null;

        int intensidad;
        if (distancia < 400) intensidad = 3;
        else if (distancia < 800) intensidad = 2;
        else if (distancia < 1200) intensidad = 1;
        else return new Pair<>(null, 0);

        return new Pair<>(emocion, intensidad);
    }

    private void mostrarPopupPalabras(String emocion, int intensidad) {
        String nivelArousal;
        switch (intensidad) {
            case 3:
                nivelArousal = "alto";
                break;
            case 2:
                nivelArousal = "medio";
                break;
            case 1:
                nivelArousal = "bajo";
                break;
            default:
                nivelArousal = "";
                break;
        }

        if (getActivity() instanceof DatosMusicalesActivity) {
            DatosMusicalesActivity activity = (DatosMusicalesActivity) getActivity();
            DatosSeccionesController controller = activity.getSeccionesController();
            controller.mostrarPopupEmociones(emocion, nivelArousal, seccionActual.getId());
        }
    }
}
