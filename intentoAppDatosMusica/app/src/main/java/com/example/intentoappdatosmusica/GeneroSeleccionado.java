package com.example.intentoappdatosmusica;

import java.io.Serializable;

public class GeneroSeleccionado implements Serializable, Seleccionable {
    private int id;             // ID del género en la tabla 'genero'
    private String nombre;      // Nombre del género (útil para mostrar)
    private int seccionId;      // ID de la sección (si es necesario para insertar)

    @Override
    public String getNombreParaMostrar() {
        return this.nombre;
    }

    public GeneroSeleccionado(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
        this.seccionId = -1;
    }

    public GeneroSeleccionado(int id, String nombre, int seccionId) {
        this.id = id;
        this.nombre = nombre;
        this.seccionId = seccionId;
    }

    // Getters y setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getSeccionId() {
        return seccionId;
    }

    public void setSeccionId(int seccionId) {
        this.seccionId = seccionId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GeneroSeleccionado that = (GeneroSeleccionado) obj;
        return this.id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
