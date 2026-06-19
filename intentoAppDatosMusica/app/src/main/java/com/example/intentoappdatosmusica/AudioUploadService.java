package com.example.intentoappdatosmusica;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AudioUploadService extends Service {

    public static final String ACTION_UPLOAD_SUCCESS = "com.example.intentoappdatosmusica.UPLOAD_SUCCESS";
    public static final String ACTION_UPLOAD_FAILURE = "com.example.intentoappdatosmusica.UPLOAD_FAILURE";
    public static final String EXTRA_PROCESSED_SONGS = "extra_processed_songs";
    public static final String EXTRA_ERROR_MESSAGE = "extra_error_message";

    private static final String CHANNEL_ID = "AudioUploadChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int NOTIFICATION_RESULT_ID = 1002;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Iniciar en foreground inmediatamente
        startForeground(NOTIFICATION_ID, createForegroundNotification());

        int usuarioId = intent.getIntExtra("usuarioId", -1);
        String metadatosStr = intent.getStringExtra("metadatos");

        if (usuarioId == -1 || metadatosStr == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        JsonArray metadatosArray = new JsonParser().parse(metadatosStr).getAsJsonArray();
        List<MultipartBody.Part> partesArchivos = new ArrayList<>();

        for (JsonElement element : metadatosArray) {
            JsonObject meta = element.getAsJsonObject();
            String rutaCopia = meta.get("ruta_audio_local").getAsString();
            File file = new File(rutaCopia);

            if (file.exists()) {
                RequestBody requestFile = RequestBody.create(MediaType.parse("audio/*"), file);
                MultipartBody.Part body = MultipartBody.Part.createFormData("archivos", file.getName(), requestFile);
                partesArchivos.add(body);
            }
        }

        if (partesArchivos.isEmpty()) {
            enviarError("Ningún archivo válido fue encontrado para subir");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        RequestBody usuarioIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(usuarioId));
        RequestBody metadatosBody = RequestBody.create(MediaType.parse("application/json"), metadatosStr);

        ApiService apiService = ApiClient.getRetrofitForLargeTransfers().create(ApiService.class);
        Call<List<AudioUploadResponse>> call = apiService.subirArchivosAudioLote(partesArchivos, usuarioIdBody,
                metadatosBody);

        call.enqueue(new Callback<List<AudioUploadResponse>>() {
            @Override
            public void onResponse(Call<List<AudioUploadResponse>> call, Response<List<AudioUploadResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<AudioUploadResponse> resList = response.body();
                    ArrayList<CancionPrediccion> cancionesProcesadas = new ArrayList<>();

                    for (int i = 0; i < resList.size(); i++) {
                        AudioUploadResponse res = resList.get(i);
                        JsonObject metaDataEnviada = metadatosArray.get(i).getAsJsonObject();
                        String rutaCopia = metaDataEnviada.get("ruta_audio_local").getAsString();

                        int songId = res.getId();
                        String nombre = res.getNombre();
                        String duracion = res.getDuracion();
                        boolean esTemporal = res.isTemporal();

                        List<Seccion> seccionesPredichas = new ArrayList<>();
                        if (res.getSecciones() != null && !res.getSecciones().isEmpty()) {
                            for (Seccion s : res.getSecciones()) {
                                String inicioStr, finStr;
                                try {
                                    double inicioDouble = Double.parseDouble(s.getTiempoInicio());
                                    double finDouble = Double.parseDouble(s.getTiempoFinal());
                                    inicioStr = String.valueOf(s.getTiempoInicio());
                                    finStr = String.valueOf(s.getTiempoFinal());
                                } catch (NumberFormatException e) {
                                    inicioStr = s.getTiempoInicio();
                                    finStr = s.getTiempoFinal();
                                }

                                Seccion nuevaSeccion = new Seccion(-1, inicioStr, finStr, "", "");
                                nuevaSeccion.setValence(s.getValence());
                                nuevaSeccion.setArousal(s.getArousal());
                                seccionesPredichas.add(nuevaSeccion);
                            }
                        } else {
                            seccionesPredichas.add(new Seccion(-1, "00:00.000", duracion, "", ""));
                        }

                        // Prepare mediaplayer instance internally
                        MediaPlayerList.getInstance().resetMediaPlayer(songId, rutaCopia);

                        CancionPrediccion cancionPrediccion = new CancionPrediccion(
                                songId,
                                metaDataEnviada.get("enlace").getAsString(),
                                nombre != null ? nombre : res.getNombre(),
                                rutaCopia,
                                duracion,
                                "archivo",
                                "(Sin autor)",
                                "(Sin álbum)",
                                esTemporal,
                                seccionesPredichas);

                        cancionesProcesadas.add(cancionPrediccion);
                    }

                    // Enviar broadcast local
                    Intent successIntent = new Intent(ACTION_UPLOAD_SUCCESS);
                    successIntent.putExtra(EXTRA_PROCESSED_SONGS, cancionesProcesadas);
                    LocalBroadcastManager.getInstance(AudioUploadService.this).sendBroadcast(successIntent);

                    // Enviar notificacion de exito que abre los resultados en caso este minimizado
                    showResultNotification(cancionesProcesadas);

                } else {
                    enviarError("Error al procesar archivos en el servidor.");
                }

                stopForeground(true);
                stopSelf();
            }

            @Override
            public void onFailure(Call<List<AudioUploadResponse>> call, Throwable t) {
                Log.e("AudioUploadService", "Fallo de red", t);
                enviarError("Error de red al procesar en servidor. Fallo en conexión.");
                stopForeground(true);
                stopSelf();
            }
        });

        return START_NOT_STICKY;
    }

    private void enviarError(String msg) {
        Intent errorIntent = new Intent(ACTION_UPLOAD_FAILURE);
        errorIntent.putExtra(EXTRA_ERROR_MESSAGE, msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Error de Análisis")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (manager != null) {
            manager.notify(NOTIFICATION_RESULT_ID, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Subida de Audios",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Muestra el progreso de la subida y análisis de audios");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MusiChroma")
                .setContentText("Subiendo y analizando canciones en segundo plano...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void showResultNotification(ArrayList<CancionPrediccion> cancionesProcesadas) {
        Intent intent = new Intent(this, DatosPrediccionActivity.class);
        intent.putExtra("canciones_procesadas", (Serializable) cancionesProcesadas);
        intent.putExtra("NC", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Análisis Completado")
                .setContentText("Toca para ver los resultados de las canciones subidas.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_RESULT_ID, builder.build());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
