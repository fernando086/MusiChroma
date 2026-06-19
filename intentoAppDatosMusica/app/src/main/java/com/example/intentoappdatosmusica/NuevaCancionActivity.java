package com.example.intentoappdatosmusica;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NuevaCancionActivity extends AppCompatActivity {

    private ImageView btnBack;
    private ImageButton btnConfirmar;
    private TextView textIndicadorPagina;
    private ViewPager2 viewPager;

    private static final int PICK_AUDIO_REQUEST = 1;
    private int currentPickingPage = -1;

    private List<SongUploadData> cancionesSeleccionadas;
    private NuevaCancionAdapter adapter;

    private FrameLayout pantallaCarga;
    private ProgressBar progresoCarga;
    private TextView textoPorcentaje;
    private List<String> rutasTemporalesCopiadas = new ArrayList<>();

    private BroadcastReceiver uploadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AudioUploadService.ACTION_UPLOAD_SUCCESS.equals(action)) {
                ArrayList<CancionPrediccion> cancionesProcesadas = 
                    (ArrayList<CancionPrediccion>) intent.getSerializableExtra(AudioUploadService.EXTRA_PROCESSED_SONGS);
                
                Toast.makeText(NuevaCancionActivity.this, "Archivos procesados exitosamente", Toast.LENGTH_SHORT).show();

                Intent nextIntent = new Intent(NuevaCancionActivity.this, DatosPrediccionActivity.class);
                nextIntent.putExtra("canciones_procesadas", cancionesProcesadas);
                nextIntent.putExtra("NC", true);

                rutasTemporalesCopiadas.clear(); // Ya no son basura, son archivos definitivos
                restaurarControles();
                startActivityForResult(nextIntent, 200);
            } else if (AudioUploadService.ACTION_UPLOAD_FAILURE.equals(action)) {
                String error = intent.getStringExtra(AudioUploadService.EXTRA_ERROR_MESSAGE);
                restaurarControles();
                limpiarArchivosBasura();
                Toast.makeText(NuevaCancionActivity.this, error != null ? error : "Error al procesar", Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioUploadService.ACTION_UPLOAD_SUCCESS);
        filter.addAction(AudioUploadService.ACTION_UPLOAD_FAILURE);
        LocalBroadcastManager.getInstance(this).registerReceiver(uploadReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uploadReceiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nueva_cancion);

        btnBack = findViewById(R.id.btnBack);
        btnConfirmar = findViewById(R.id.btnConfirmar);
        textIndicadorPagina = findViewById(R.id.textIndicadorPagina);
        viewPager = findViewById(R.id.viewPagerNuevaCancion);
        pantallaCarga = findViewById(R.id.pantallaCarga);
        progresoCarga = findViewById(R.id.progresoCarga);
        textoPorcentaje = findViewById(R.id.textoPorcentaje);

        btnBack.setOnClickListener(v -> finish());

        cancionesSeleccionadas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cancionesSeleccionadas.add(new SongUploadData());
        }

        adapter = new NuevaCancionAdapter();
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                textIndicadorPagina.setText("Canción " + (position + 1) + " de 5");
            }
        });

        btnConfirmar.setOnClickListener(v -> mostrarDialogoConfirmacion());
    }

    private void mostrarDialogoConfirmacion() {
        List<SongUploadData> seleccionadas = new ArrayList<>();
        for (SongUploadData data : cancionesSeleccionadas) {
            if (data.getUri() != null) {
                seleccionadas.add(data);
            }
        }

        if (seleccionadas.isEmpty()) {
            Toast.makeText(this, "No has seleccionado ninguna canción", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder mensaje = new StringBuilder();
        for (SongUploadData data : seleccionadas) {
            mensaje.append("• ").append(data.getNombreArchivo()).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirmar Selección")
                .setMessage("Has seleccionado las siguientes canciones:\n\n" + mensaje.toString() + "\n¿Estás seguro de que deseas continuar?")
                .setPositiveButton("Aceptar", (dialog, which) -> procesarArchivos(seleccionadas))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public void solicitarSeleccionArchivo(int position) {
        currentPickingPage = position;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (currentPickingPage != -1) {
                Uri audioUri = data.getData();
                String nombre = getFileNameFromUri(audioUri);
                
                // Obtener extensión del archivo para validarlo
                String extension = "";
                if (nombre != null && nombre.lastIndexOf('.') > 0) {
                    extension = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase();
                }

                if (!extension.isEmpty() && (extension.equals("mp3") || extension.equals("wav") || extension.equals("ogg") || extension.equals("m4a"))) {
                    SongUploadData songData = cancionesSeleccionadas.get(currentPickingPage);
                    songData.setUri(audioUri);
                    songData.setNombreArchivo(nombre);
                    
                    adapter.notifyItemChanged(currentPickingPage);
                } else {
                    Toast.makeText(this, "Formato de archivo no permitido. Use MP3, WAV u OGG", Toast.LENGTH_SHORT).show();
                }
                
                currentPickingPage = -1;
            }
        }
        else if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);
            finish();
        }
        else if (requestCode == 200 && resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void mostrarPantallaCarga() {
        pantallaCarga.setVisibility(View.VISIBLE);
        progresoCarga.setProgress(0);
        textoPorcentaje.setText("Cargando...");
    }

    private void ocultarPantallaCarga() {
        pantallaCarga.setVisibility(View.GONE);
    }

    private void procesarArchivos(List<SongUploadData> seleccionadas) {
        mostrarPantallaCarga();
        btnBack.setEnabled(false);
        btnConfirmar.setEnabled(false);
        viewPager.setUserInputEnabled(false);

        List<MultipartBody.Part> partesArchivos = new ArrayList<>();
        JsonArray metadatosArray = new JsonArray();

        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);
        if (usuarioId == -1) {
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show();
            ocultarPantallaCarga();
            return;
        }

        for (SongUploadData songData : seleccionadas) {
            String rutaCopia = copyFileToAppMediaFolder(songData.getUri());
            if (rutaCopia == null) {
                Toast.makeText(this, "Error al copiar " + songData.getNombreArchivo(), Toast.LENGTH_SHORT).show();
                continue;
            }

            File file = new File(rutaCopia);
            String formattedTime = obtenerDuracionFormateada(file.getAbsolutePath());

            // Crear MultipartBody.Part para el archivo
            RequestBody requestFile = RequestBody.create(MediaType.parse("audio/*"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("archivos", file.getName(), requestFile);
            partesArchivos.add(body);

            // Crear objeto de metadatos
            JsonObject meta = new JsonObject();
            meta.addProperty("nombre_archivo", file.getName());
            meta.addProperty("enlace", songData.getEnlace());
            meta.addProperty("tiempo_fin", formattedTime);
            meta.addProperty("ruta_audio_local", rutaCopia);
            metadatosArray.add(meta);
        }

        if (partesArchivos.isEmpty()) {
            Toast.makeText(this, "Ningún archivo pudo ser procesado", Toast.LENGTH_SHORT).show();
            restaurarControles();
            return;
        }

        Intent serviceIntent = new Intent(this, AudioUploadService.class);
        serviceIntent.putExtra("usuarioId", usuarioId);
        serviceIntent.putExtra("metadatos", metadatosArray.toString());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void restaurarControles() {
        ocultarPantallaCarga();
        btnBack.setEnabled(true);
        btnConfirmar.setEnabled(true);
        viewPager.setUserInputEnabled(true);
    }

    private String obtenerDuracionFormateada(String filePath) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        String formattedTime = "00:00:00.000";
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            int durationInMillis = mediaPlayer.getDuration();

            int hours = (durationInMillis / (1000 * 60 * 60)) % 24;
            int minutes = (durationInMillis / (1000 * 60)) % 60;
            int seconds = (durationInMillis / 1000) % 60;
            int milliseconds = durationInMillis % 1000;

            formattedTime = String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mediaPlayer.release();
        }
        return formattedTime;
    }

    private String copyFileToAppMediaFolder(Uri uri) {
        String nombreArchivo = getFileNameFromUri(uri);
        File mediaDir = new File(getExternalFilesDir("media"), "");

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

            String rutaCopia = destino.getAbsolutePath();
            rutasTemporalesCopiadas.add(rutaCopia);
            return rutaCopia;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String nombre = null;
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
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            limpiarArchivosBasura();
        }
    }

    private void limpiarArchivosBasura() {
        for (String ruta : rutasTemporalesCopiadas) {
            File file = new File(ruta);
            if (file.exists()) {
                file.delete();
                Log.d("Limpieza", "Archivo huérfano eliminado: " + ruta);
            }
        }
        rutasTemporalesCopiadas.clear();
    }

    private class NuevaCancionAdapter extends RecyclerView.Adapter<NuevaCancionAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_nueva_cancion, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SongUploadData data = cancionesSeleccionadas.get(position);

            if (data.getUri() != null && data.getNombreArchivo() != null) {
                holder.textInstrucciones.setText(data.getNombreArchivo());
                holder.btnSeleccionarArchivo.setBackgroundResource(R.drawable.nota_musical);
            } else {
                holder.textInstrucciones.setText("Toca este ícono para agregar el audio");
                holder.btnSeleccionarArchivo.setBackgroundResource(android.R.color.transparent);
            }

            // Remove existing textwatcher to prevent updating the wrong model when views are recycled
            if (holder.textWatcher != null) {
                holder.editTextEnlace.removeTextChangedListener(holder.textWatcher);
            }

            holder.editTextEnlace.setText(data.getEnlace());

            holder.textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    data.setEnlace(s.toString());
                }
            };
            holder.editTextEnlace.addTextChangedListener(holder.textWatcher);

            holder.layoutArchivo.setOnClickListener(v -> solicitarSeleccionArchivo(holder.getAdapterPosition()));
            holder.btnSeleccionarArchivo.setOnClickListener(v -> solicitarSeleccionArchivo(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return cancionesSeleccionadas.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View layoutArchivo;
            ImageButton btnSeleccionarArchivo;
            TextView textInstrucciones;
            EditText editTextEnlace;
            TextWatcher textWatcher;

            ViewHolder(View itemView) {
                super(itemView);
                layoutArchivo = itemView.findViewById(R.id.layoutArchivo);
                btnSeleccionarArchivo = itemView.findViewById(R.id.btnSeleccionarArchivo);
                textInstrucciones = itemView.findViewById(R.id.textArchivo);
                editTextEnlace = itemView.findViewById(R.id.editTextEnlace);
            }
        }
    }
}