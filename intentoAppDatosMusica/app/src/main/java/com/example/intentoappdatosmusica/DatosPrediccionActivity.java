package com.example.intentoappdatosmusica;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DatosPrediccionActivity extends AppCompatActivity {

    private ImageView btnBack;
    private ImageView btnInfo;
    private TextView textIndicadorPaginaPrediccion;
    private ViewPager2 viewPager;
    private Button btnCancelar;
    private Button btnConfirmar;
    private FrameLayout pantallaCarga;

    private List<CancionPrediccion> cancionesProcesadas;
    private PrediccionesPagerAdapter adapter;
    private int usuarioId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datos_prediccion);

        btnBack = findViewById(R.id.btnBack);
        btnInfo = findViewById(R.id.btnInfo);
        textIndicadorPaginaPrediccion = findViewById(R.id.textIndicadorPaginaPrediccion);
        viewPager = findViewById(R.id.viewPagerPredicciones);
        btnCancelar = findViewById(R.id.btn_cancelar_prediccion);
        btnConfirmar = findViewById(R.id.btn_confirmar_prediccion);
        pantallaCarga = findViewById(R.id.pantallaCarga);

        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", Context.MODE_PRIVATE);
        usuarioId = prefs.getInt("usuario_id", -1);

        cancionesProcesadas = (List<CancionPrediccion>) getIntent().getSerializableExtra("canciones_procesadas");

        if (cancionesProcesadas == null || cancionesProcesadas.isEmpty()) {
            Toast.makeText(this, "No se recibieron predicciones", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adapter = new PrediccionesPagerAdapter(this, cancionesProcesadas);
        viewPager.setAdapter(adapter);

        textIndicadorPaginaPrediccion.setText("Canción 1 de " + cancionesProcesadas.size());

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                textIndicadorPaginaPrediccion.setText("Canción " + (position + 1) + " de " + cancionesProcesadas.size());
            }
        });

        btnBack.setOnClickListener(v -> finish());
        btnCancelar.setOnClickListener(v -> cancelarLote());
        btnConfirmar.setOnClickListener(v -> guardarLoteServidor());
    }

    private void cancelarLote() {
        for (CancionPrediccion c : cancionesProcesadas) {
            if (c.getRutaAudio() != null) {
                File archivo = new File(c.getRutaAudio());
                if (archivo.exists()) archivo.delete();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void guardarLoteServidor() {
        // Recolectar secciones actualizadas de los fragmentos instanciados
        for (int i = 0; i < cancionesProcesadas.size(); i++) {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + i);
            if (fragment instanceof DatosPrediccionFragment) {
                List<Seccion> seccionesActualizadas = ((DatosPrediccionFragment) fragment).getSeccionesFinales();
                cancionesProcesadas.get(i).setSecciones(seccionesActualizadas);
            }
        }

        // Enviar todas al servidor secuencialmente
        enviarSiguienteCancion(0);
    }

    private void enviarSiguienteCancion(int index) {
        if (index >= cancionesProcesadas.size()) {
            ocultarCarga();
            Toast.makeText(this, "Todas las canciones guardadas exitosamente", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MenuPrincipalActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        if (index == 0) mostrarCarga();

        CancionPrediccion cancion = cancionesProcesadas.get(index);
        ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);

        File file = new File(cancion.getRutaAudio());
        RequestBody nombreArchivoBody = RequestBody.create(MediaType.parse("text/plain"), file.getName());
        RequestBody usuarioIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(usuarioId));
        RequestBody nombreBody = RequestBody.create(MediaType.parse("text/plain"), cancion.getNombre() == null || cancion.getNombre().isEmpty() ? "Sin título" : cancion.getNombre());
        RequestBody autorBody = RequestBody.create(MediaType.parse("text/plain"), cancion.getArtista() == null || cancion.getArtista().isEmpty() ? "Artista desconocido" : cancion.getArtista());
        RequestBody albumBody = RequestBody.create(MediaType.parse("text/plain"), cancion.getAlbum() == null || cancion.getAlbum().isEmpty() ? "Álbum desconocido" : cancion.getAlbum());
        RequestBody tipoBody = RequestBody.create(MediaType.parse("text/plain"), cancion.getTipoOrigen());
        
        // Convertir duración a milisegundos
        int duracionTotalMs = 0;
        try {
            String[] partes = cancion.getDuracion().split("[:.]");
            if (partes.length == 4) {
                int horas = Integer.parseInt(partes[0]);
                int minutos = Integer.parseInt(partes[1]);
                int segundos = Integer.parseInt(partes[2]);
                int milis = Integer.parseInt(partes[3]);
                duracionTotalMs = (horas * 3600 + minutos * 60 + segundos) * 1000 + milis;
            } else if (partes.length == 3) {
                int minutos = Integer.parseInt(partes[0]);
                int segundos = Integer.parseInt(partes[1]);
                int milis = Integer.parseInt(partes[2]);
                duracionTotalMs = (minutos * 60 + segundos) * 1000 + milis;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        RequestBody duracionBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(duracionTotalMs));

        Gson gson = new Gson();
        String seccionesJson = gson.toJson(cancion.getSecciones());
        RequestBody seccionesBody = RequestBody.create(MediaType.parse("application/json"), seccionesJson);

        Call<GuardarCancionResponse> call = apiService.guardarCancionDefinitivaArchivo(
                nombreArchivoBody, usuarioIdBody, nombreBody, autorBody, albumBody, tipoBody, duracionBody, seccionesBody
        );

        call.enqueue(new Callback<GuardarCancionResponse>() {
            @Override
            public void onResponse(Call<GuardarCancionResponse> call, Response<GuardarCancionResponse> response) {
                if (response.isSuccessful()) {
                    
                    // Guardar mapeo para auto-enlazado en MenuPrincipalActivity
                    SharedPreferences prefs = getSharedPreferences("AutoLinkPrefs", Context.MODE_PRIVATE);
                    prefs.edit().putString("local_song_" + cancion.getSongId(), cancion.getRutaAudio()).apply();

                    enviarSiguienteCancion(index + 1);
                } else {
                    ocultarCarga();
                    Toast.makeText(DatosPrediccionActivity.this, "Error guardando canción " + (index + 1), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GuardarCancionResponse> call, Throwable t) {
                ocultarCarga();
                Toast.makeText(DatosPrediccionActivity.this, "Fallo de conexión al guardar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mostrarCarga() {
        pantallaCarga.setVisibility(View.VISIBLE);
    }

    private void ocultarCarga() {
        pantallaCarga.setVisibility(View.GONE);
    }

    private class PrediccionesPagerAdapter extends FragmentStateAdapter {
        private List<CancionPrediccion> canciones;

        public PrediccionesPagerAdapter(@NonNull FragmentActivity fragmentActivity, List<CancionPrediccion> canciones) {
            super(fragmentActivity);
            this.canciones = canciones;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            DatosPrediccionFragment fragment = new DatosPrediccionFragment();
            Bundle args = new Bundle();
            CancionPrediccion cancion = canciones.get(position);
            args.putInt("song_id", cancion.getSongId());
            args.putBoolean("offline", cancion.isOffline());
            args.putString("name", cancion.getNombre());
            args.putString("author", cancion.getArtista());
            args.putString("album", cancion.getAlbum());
            args.putString("tipo_origen", cancion.getTipoOrigen());
            args.putString("link", cancion.getLink());
            args.putString("ruta_audio", cancion.getRutaAudio());
            args.putString("duracion", cancion.getDuracion());
            args.putSerializable("secciones", (java.io.Serializable) cancion.getSecciones());
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return canciones.size();
        }
    }
}