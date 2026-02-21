package com.example.intentoappdatosmusica;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PopupBusquedaCanciones extends Dialog {

    public interface OnEmocionesSeleccionadasListener {
        void onEmocionesSeleccionadas(List<String> emocionesSeleccionadas);
    }

    private final OnEmocionesSeleccionadasListener listener;
    private final Set<String> emocionesSeleccionadas = new HashSet<>();

    private FrameLayout contenedorChecks;
    private ImageView imagenRueda;

    private final List<String> emocionesIniciales;

    public PopupBusquedaCanciones(@NonNull Context context,
                                  List<String> emocionesIniciales,
                                  OnEmocionesSeleccionadasListener listener) {
        super(context);
        this.listener = listener;
        this.emocionesIniciales = emocionesIniciales;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.popup_busqueda_canciones);

        imagenRueda = findViewById(R.id.imagen_rueda_busqueda);
        contenedorChecks = findViewById(R.id.contenedor_checks_busqueda);
        Button btnAceptar = findViewById(R.id.btnAceptarBusqueda);
        Button btnCancelar = findViewById(R.id.btnCancelarBusqueda);

        // 🔥 Restaurar las emociones iniciales (mantener selección previa)
        if (emocionesIniciales != null) {
            emocionesSeleccionadas.addAll(emocionesIniciales);
            imagenRueda.post(this::actualizarChecks);
        }

        // Cargar la imagen de la rueda
        imagenRueda.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                imagenRueda.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (imagenRueda.getDrawable() == null) return;
            }
        });

        imagenRueda.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.performClick();

                // Detectar emoción según el ángulo tocado
                String emocion = detectarEmocion(event.getX(), event.getY(), imagenRueda);
                if (emocion != null) {
                    if (emocionesSeleccionadas.contains(emocion))
                        emocionesSeleccionadas.remove(emocion);
                    else
                        emocionesSeleccionadas.add(emocion);

                    actualizarChecks();
                }
            }
            return true;
        });

        btnAceptar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEmocionesSeleccionadas(new ArrayList<>(emocionesSeleccionadas));
            }
            dismiss();
        });

        btnCancelar.setOnClickListener(v -> dismiss());
    }

    private void actualizarChecks() {
        contenedorChecks.removeAllViews();
        if (imagenRueda.getDrawable() == null) return;

        if (!(imagenRueda.getDrawable() instanceof BitmapDrawable)) return;
        Bitmap bitmap = ((BitmapDrawable) imagenRueda.getDrawable()).getBitmap();
        if (bitmap == null) return;

        int bitmapW = bitmap.getWidth();
        int bitmapH = bitmap.getHeight();

        float cx = imagenRueda.getWidth() / 2f;
        float cy = imagenRueda.getHeight() / 2f;
        float radio = Math.min(imagenRueda.getWidth(), imagenRueda.getHeight()) * 0.35f;

        for (String emocion : emocionesSeleccionadas) {
            double angulo = obtenerAngulo(emocion);
            float x = cx + (float) (radio * Math.sin(Math.toRadians(angulo)));
            float y = cy - (float) (radio * Math.cos(Math.toRadians(angulo)));

            ImageView iv = new ImageView(getContext());
            iv.setImageResource(R.drawable.iconocheck);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(60, 60);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.leftMargin = (int) (x - 30);
            params.topMargin = (int) (y - 30);
            contenedorChecks.addView(iv, params);
        }
    }

    private String detectarEmocion(float x, float y, ImageView imageView) {
        Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scale = Math.min(imageViewWidth / bitmapWidth, imageViewHeight / bitmapHeight);
        float scaledBitmapWidth = bitmapWidth * scale;
        float scaledBitmapHeight = bitmapHeight * scale;

        float left = (imageViewWidth - scaledBitmapWidth) / 2f;
        float top = (imageViewHeight - scaledBitmapHeight) / 2f;

        float imageX = (x - left) / scale;
        float imageY = (y - top) / scale;

        float cx = bitmapWidth / 2f;
        float cy = bitmapHeight / 2f;

        float dx = imageX - cx;
        float dy = imageY - cy;

        double angulo = Math.toDegrees(Math.atan2(dx, -dy));
        if (angulo < 0) angulo += 360;

        // 8 sectores, sentido horario desde arriba (alegría)
        if (angulo < 22.5 || angulo >= 337.5) return "joy";
        else if (angulo < 67.5) return "trust";
        else if (angulo < 112.5) return "fear";
        else if (angulo < 157.5) return "surprise";
        else if (angulo < 202.5) return "sadness";
        else if (angulo < 247.5) return "disgust";
        else if (angulo < 292.5) return "anger";
        else if (angulo < 337.5) return "anticipation";
        else return null;
    }

    private double obtenerAngulo(String emocion) {
        switch (emocion) {
            case "joy": return 0;
            case "trust": return 45;
            case "fear": return 90;
            case "surprise": return 135;
            case "sadness": return 180;
            case "disgust": return 225;
            case "anger": return 270;
            case "anticipation": return 315;
            default: return 0;
        }
    }
}
