package com.example.intentoappdatosmusica;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.intentoappdatosmusica.R;
import com.example.intentoappdatosmusica.CancionSeleccionAdapter;
import com.example.intentoappdatosmusica.Song;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PopupSeleccionCanciones extends Dialog {

    private List<Song> listaCanciones;
    private List<Integer> idsPreseleccionados;
    private OnCancionesSeleccionadasListener listener;
    private List<String> emocionesSeleccionadasActuales = new ArrayList<>();

    private CancionSeleccionAdapter adapter;

    public interface OnCancionesSeleccionadasListener {
        void onCancionesSeleccionadas(List<Integer> idsSeleccionados);
    }

    public PopupSeleccionCanciones(@NonNull Context context, List<Song> canciones,
                                   List<Integer> idsPreseleccionados,
                                   OnCancionesSeleccionadasListener listener) {
        super(context);
        this.listaCanciones = canciones;
        this.idsPreseleccionados = idsPreseleccionados;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.popup_seleccionar_canciones);

        // ✅ Hacer que el popup ocupe el 90% del ancho de la pantalla
        Window window = getWindow();
        if (window != null) {
            window.setLayout((int)(getContext().getResources().getDisplayMetrics().widthPixels * 0.9),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Mantener bordes redondeados del fondo
        }

        RecyclerView recyclerView = findViewById(R.id.rvCanciones);
        Button btnAceptar = findViewById(R.id.btnConfirmar);
        Button btnCancelar = findViewById(R.id.btnCancelar);
        Button btnBuscar = findViewById(R.id.btnBuscar);
        CheckBox cbSoloSesion = findViewById(R.id.cbSoloSesion);

        cbSoloSesion.setOnCheckedChangeListener((buttonView, isChecked) -> {
            adapter.setSoloSeleccionados(isChecked);
        });

        File csvFile = new File(getContext().getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");
        Map<String, EmocionDisponible> emocionesMap = EmocionDisponible.cargarEmociones(csvFile);

        adapter = new CancionSeleccionAdapter(getContext(), listaCanciones, idsPreseleccionados, emocionesMap);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        btnAceptar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCancionesSeleccionadas(adapter.getIdsSeleccionados());
            }
            emocionesSeleccionadasActuales.clear();
            dismiss();
        });

        btnBuscar.setOnClickListener(v -> {
            PopupBusquedaCanciones popupBusqueda = new PopupBusquedaCanciones(
                    getContext(),
                    new ArrayList<>(emocionesSeleccionadasActuales), // pasar selección previa
                    emocionesSeleccionadas -> {
                        // 🔥 guardar selección actual
                        emocionesSeleccionadasActuales = new ArrayList<>(emocionesSeleccionadas);
                        // 🔥 aplicar filtro
                        adapter.filtrarPorEmociones(emocionesSeleccionadas);
                    }
            );
            popupBusqueda.show();
        });

        btnCancelar.setOnClickListener(v -> {
            emocionesSeleccionadasActuales.clear();
            dismiss();
        });
    }
}
