package com.example.intentoappdatosmusica;

import java.io.Serializable;

public class EmocionSeleccionada implements Serializable, Seleccionable {
    private int id;            // ID del registro en la BD (opcional si no sincronizas)
    private int seccionId;     // ID de la sección a la que pertenece
    private String palabra;    // Palabra emocional

    @Override
    public String getNombreParaMostrar() {
        return this.palabra;
    }

    public EmocionSeleccionada(int id, int seccionId, String palabra) {
        this.id = id;
        this.seccionId = seccionId;
        this.palabra = palabra;
    }

    public EmocionSeleccionada(String palabra) {
        this(-1, -1, palabra); // Constructor simplificado, sin ID
    }

    // Getters y setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSeccionId() {
        return seccionId;
    }

    public void setSeccionId(int seccionId) {
        this.seccionId = seccionId;
    }

    public String getPalabra() {
        return palabra;
    }

    public void setPalabra(String palabra) {
        this.palabra = palabra;
    }

    @Override
    public String toString() {
        return palabra;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmocionSeleccionada)) return false;
        EmocionSeleccionada that = (EmocionSeleccionada) o;
        return palabra.equalsIgnoreCase(that.palabra);
    }

    @Override
    public int hashCode() {
        return palabra.toLowerCase().hashCode();
    }
}
