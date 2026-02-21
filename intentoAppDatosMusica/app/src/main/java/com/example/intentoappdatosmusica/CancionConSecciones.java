package com.example.intentoappdatosmusica;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CancionConSecciones {
    public int id;
    public String nombre, autor, album, enlaceRuta, comentario;
    public boolean estadoComentario1, publicado;
    public String fechaCreacion, fechaUltimaEdicion;
    public List<Seccion> secciones;

    // ➊ NUEVO campo en la clase (añade @SerializedName)
    @SerializedName("archivoBase64")
    public String archivoBase64;   // nullable

    // Clase anidada para las secciones
    public static class Seccion {
        public int id;
        public String tiempoInicio, tiempoFinal, fechaCreacion, fechaUltimaEdicion;
    }

    private static boolean esArchivoLocal(String ruta) {
        if (ruta == null) return false;
        String lower = ruta.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg");
    }

    public static void sincronizarConServidor(Context context, Runnable onComplete) {
        List<CancionConSecciones> cancionesLocales = cargarDesdeSongdataTxt();
        Log.d("PRUEBA_CARGA", "Se cargaron " + cancionesLocales.size() + " canciones.");
        for (CancionConSecciones c : cancionesLocales) {
            if (esArchivoLocal(c.enlaceRuta)) {

                File mediaDir = new File(context.getExternalFilesDir(null), "media");
                File f = new File(mediaDir, c.enlaceRuta);

                Log.d("SYNC", "Buscando archivo: " + f.getAbsolutePath());

                if (!f.exists()) {
                    Log.e("SYNC", "Archivo NO encontrado: " + f.getAbsolutePath());
                }

                byte[] bytes = null;
                try (FileInputStream fis = new FileInputStream(f)) {
                    bytes = new byte[(int) f.length()];
                    //noinspection ResultOfMethodCallIgnored
                    int read = fis.read(bytes);
                    Log.d("SYNC", "Bytes leídos: " + read);
                } catch (IOException e) {
                    Log.e("SYNC", "Error leyendo audio local: " + f.getAbsolutePath(), e);
                }

                if (bytes != null) {
                    c.archivoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    //c.enlaceRuta = null;           // se enviará como archivo
                } else {
                    c.archivoBase64 = null;        // se enviará solo metadata
                }
            }

            Log.d("CANCION", c.id + ": " + c.nombre + " (" + c.secciones.size() + " secciones)");
        }

        SharedPreferences prefs = context.getSharedPreferences("UsuarioPrefs", Context.MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);

        if (usuarioId == -1 || cancionesLocales.isEmpty()) {
            Log.e("Sync", "Usuario no encontrado o lista vacía. usuarioId = " + usuarioId);
            Log.e("Sync", "Lista = " + (cancionesLocales == null ? "null" : cancionesLocales));
            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).post(onComplete);
            }
            return;
        }

        ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);

        Call<ResponseBody> call = apiService.sincronizarCanciones(usuarioId, cancionesLocales);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("Sync", "Sincronización exitosa: " + response.body().toString());

                    // ---> Aquí va el Toast (ya terminó el proceso completamente)
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> Toast.makeText(context, "Sincronización completada", Toast.LENGTH_SHORT).show());
                } else {
                    Log.e("Sync", "Error al sincronizar: " + response.code());
                }
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("Sync", "Fallo al conectar: " + t.getMessage());

                // Mostrar Toast en hilo principal
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> Toast.makeText(context, "Error al sincronizar con el servidor", Toast.LENGTH_LONG).show());
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        });
    }

    public static List<CancionConSecciones> cargarDesdeSongdataTxt() {
        List<CancionConSecciones> listaCanciones = new ArrayList<>();

        File file = new File("/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/songdata/songdata.txt");

        if (!file.exists()) return listaCanciones;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String linea;

            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(";", -1); // Asegura incluir campos vacíos

                if (partes.length != 11) {
                    Log.e("CARGA_CANCIONES", "Línea con formato incorrecto (esperado 11 campos): " + linea);
                    continue;
                }

                try {
                    CancionConSecciones cancion = new CancionConSecciones();
                    cancion.id = Integer.parseInt(partes[0]);
                    cancion.nombre = partes[1];
                    cancion.autor = partes[2];
                    cancion.album = partes[3];
                    cancion.enlaceRuta = partes[4];
                    cancion.comentario = partes[5];
                    cancion.estadoComentario1 = Boolean.parseBoolean(partes[6]);
                    cancion.publicado = Boolean.parseBoolean(partes[7]); // ojo, estaba mal antes
                    cancion.fechaCreacion = partes[8];
                    cancion.fechaUltimaEdicion = partes[9];
                    cancion.secciones = new ArrayList<>();

                    String seccionesRaw = partes[10];
                    String[] seccionesSplit = seccionesRaw.split("\\|");

                    for (String s : seccionesSplit) {
                        if (s.trim().isEmpty()) continue;

                        String[] partesSeccion = s.split("//");
                        if (partesSeccion.length != 3) {
                            Log.e("CARGA_SECCION", "Sección mal formada (esperado 3 partes): " + s);
                            continue;
                        }

                        String[] idYTiempo = partesSeccion[0].split("/");
                        if (idYTiempo.length != 2) {
                            Log.e("CARGA_SECCION", "ID y tiempo mal formado: " + partesSeccion[0]);
                            continue;
                        }

                        String[] tiempo = idYTiempo[1].split("-");
                        if (tiempo.length != 2) {
                            Log.e("CARGA_SECCION", "Tiempos mal formados (esperado inicio-fin): " + idYTiempo[1]);
                            continue;
                        }

                        Seccion seccion = new Seccion();
                        seccion.id = Integer.parseInt(idYTiempo[0]);
                        seccion.tiempoInicio = tiempo[0];
                        seccion.tiempoFinal = tiempo[1];
                        seccion.fechaCreacion = partesSeccion[1];
                        seccion.fechaUltimaEdicion = partesSeccion[2];

                        cancion.secciones.add(seccion);
                    }

                    listaCanciones.add(cancion);
                } catch (Exception ex) {
                    Log.e("CARGA_CANCION", "Error al procesar línea: " + linea, ex);
                }
            }

        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }

        return listaCanciones;
    }
}
