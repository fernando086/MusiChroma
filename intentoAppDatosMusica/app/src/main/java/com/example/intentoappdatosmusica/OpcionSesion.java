package com.example.intentoappdatosmusica;

public class OpcionSesion {
    private int id;
    private String nombre;
    private int imagenResId;
    private boolean seleccionado;
    private boolean esEditable;
    private String textoPersonalizado; // Para cuando esEditable es true

    public OpcionSesion(int id, String nombre, int imagenResId, boolean esEditable) {
        this.id = id;
        this.nombre = nombre;
        this.imagenResId = imagenResId;
        this.esEditable = esEditable;
        this.seleccionado = false;
        this.textoPersonalizado = "";
    }

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

    public int getImagenResId() {
        return imagenResId;
    }

    public void setImagenResId(int imagenResId) {
        this.imagenResId = imagenResId;
    }

    public boolean isSeleccionado() {
        return seleccionado;
    }

    public void setSeleccionado(boolean seleccionado) {
        this.seleccionado = seleccionado;
    }

    public boolean isEditable() {
        return esEditable;
    }

    public void setEditable(boolean esEditable) {
        this.esEditable = esEditable;
    }

    public String getTextoPersonalizado() {
        return textoPersonalizado;
    }

    public void setTextoPersonalizado(String textoPersonalizado) {
        this.textoPersonalizado = textoPersonalizado;
    }
}
