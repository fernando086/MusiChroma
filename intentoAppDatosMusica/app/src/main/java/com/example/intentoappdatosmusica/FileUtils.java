package com.example.intentoappdatosmusica;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtils {

    public static void copiarArchivosIniciales(Context context) {
        copiarArchivo(context, "palabras_emociones.csv", "songdata/palabras/palabras_emociones.csv");
        copiarArchivo(context, "generos.csv", "songdata/generos/generos.csv");
        copiarArchivo(context, "instrumentos.csv", "songdata/instrumentos/instrumentos.csv");
    }

    private static void copiarArchivo(Context context, String assetName, String relativeDestinationPath) {
        File dest = new File(context.getExternalFilesDir(null), relativeDestinationPath);

        // Crear directorio si no existe
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }

        // Si ya existe, no copiar de nuevo
        if (dest.exists()) {
            Log.i("FileUtils", "El archivo ya existe: " + dest.getAbsolutePath());
            return;
        }

        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            Log.i("FileUtils", "Archivo copiado a: " + dest.getAbsolutePath());

        } catch (Exception e) {
            Log.e("FileUtils", "Error copiando " + assetName + ": " + e.getMessage());
        }
    }
}
