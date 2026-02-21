package com.example.intentoappdatosmusica;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class EmocionDisponible implements Serializable, Seleccionable {
    private String palabra;
    private String emocionBase;
    private float valence;
    private float arousal;
    private float dominance;
    private String nivelArousal; // Puede ser "bajo", "medio", "alto"

    @Override
    public String getNombreParaMostrar() {
        return this.palabra;
    }

    public EmocionDisponible(String palabra, String emocionBase, float valence, float arousal, String nivelArousal) {
        this.palabra = palabra;
        this.emocionBase = emocionBase;
        this.valence = valence;
        this.arousal = arousal;
        this.dominance = dominance;
        this.nivelArousal = nivelArousal;
    }

    public EmocionDisponible(String emocionBase, float valence, float arousal, float dominance, String nivelArousal) {
        this.emocionBase = emocionBase;
        this.valence = valence;
        this.arousal = arousal;
        this.dominance = dominance;
        this.nivelArousal = nivelArousal;
    }

    // Getters
    public String getPalabra() { return palabra; }
    public String getEmocionBase() { return emocionBase; }
    public float getValence() { return valence; }
    public float getArousal() { return arousal; }
    public float getDominance() { return dominance; }
    public String getNivelArousal() { return nivelArousal; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EmocionDisponible that = (EmocionDisponible) obj;
        return this.palabra == that.palabra;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(Integer.parseInt(palabra)); //TODO CAMBIAR INTEGER PARSE INT
    }

    public static Map<String, EmocionDisponible> cargarEmociones(File csvFile) {
        Map<String, EmocionDisponible> emocionesMap = new HashMap<>();

        if (!csvFile.exists()) {
            Log.e("CSV", "No existe el archivo: " + csvFile.getAbsolutePath());
            return emocionesMap;
        } else {
            Log.e("CSV", "Archivo encontrado");
        }

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String linea;
            br.readLine(); // Saltar encabezado
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length < 6) continue;

                String palabra = partes[0].trim().toLowerCase(); // la palabra clave
                String emocion = partes[1].trim();
                float valence = Float.parseFloat(partes[2]);
                float arousal = Float.parseFloat(partes[3]);
                float dominance = Float.parseFloat(partes[4]);
                String nivelArousal = partes[5].trim();

                EmocionDisponible data = new EmocionDisponible(emocion, valence, arousal, dominance, nivelArousal);
                emocionesMap.put(palabra, data);
            }
        } catch (IOException e) {
            Log.e("CSV", "Error leyendo CSV: " + e.getMessage());
        }

        return emocionesMap;
    }
}
