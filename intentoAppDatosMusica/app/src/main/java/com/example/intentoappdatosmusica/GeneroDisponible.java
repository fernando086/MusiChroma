package com.example.intentoappdatosmusica;

import java.io.Serializable;

public class GeneroDisponible implements Serializable, Seleccionable {
    private int id;
    private String nombre;

    @Override
    public String getNombreParaMostrar() {
        return this.nombre; // o palabra si usas eso como nombre visual
    }

    public GeneroDisponible(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GeneroDisponible that = (GeneroDisponible) obj;
        return this.id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
