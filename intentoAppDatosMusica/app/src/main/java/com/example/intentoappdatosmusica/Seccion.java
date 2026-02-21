package com.example.intentoappdatosmusica;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Seccion implements Serializable {
    @SerializedName("id")
    private int id;

    @SerializedName("tiempo_inicio")
    private String tiempoInicio;

    @SerializedName("tiempo_final")
    private String tiempoFinal;

    @SerializedName("valence")
    private double valence;

    @SerializedName("arousal")
    private double arousal;

    @SerializedName("fecha_creacion")
    private String fecha_creacion;

    @SerializedName("fecha_ultima_edicion")
    private String fecha_ultima_edicion;

    // NUEVOS CAMPOS para DatosSeccionesController
    @SerializedName("nombre")
    private String nombre;

    @SerializedName("comentario")
    private String comentario;

    @SerializedName("publicado")
    private boolean publicado;

    @SerializedName("emociones")
    private List<EmocionSeleccionada> emociones;

    @SerializedName("generoSeleccionados")
    private List<GeneroSeleccionado> generoSeleccionados;

    @SerializedName("valenceReal")
    private double valenceReal;
    @SerializedName("arousalReal")
    private double arousalReal;

    public double getValenceReal() { return valenceReal; }
    public double getArousalReal() { return arousalReal; }
    public void setValenceReal(double valenceReal) { this.valenceReal = valenceReal; }
    public void setArousalReal(double arousalReal) { this.arousalReal = arousalReal; }

    // Constructor vacío requerido por Retrofit
    public Seccion() {
        this.emociones = new ArrayList<>();
        this.generoSeleccionados = new ArrayList<>();
    }

    // Constructor personalizado para tiempos, usado en popup_definir_secciones
    public Seccion(int id, String tiempoInicio, String tiempoFinal, String fecha_creacion, String fecha_ultima_edicion) {
        this.id = id;
        this.tiempoInicio = tiempoInicio;
        this.tiempoFinal = tiempoFinal;
        this.fecha_creacion = fecha_creacion;
        this.fecha_ultima_edicion = fecha_ultima_edicion;
    }

    // Constructor extendido opcional (si lo deseas)
    public Seccion(int id, String tiempoInicio, String tiempoFinal, String fecha_creacion, String fecha_ultima_edicion,
                   String nombre, String comentario, boolean publicado,
                   List<EmocionSeleccionada> emociones, List<GeneroSeleccionado> generoSeleccionados) {
        this(id, tiempoInicio, tiempoFinal, fecha_creacion, fecha_ultima_edicion);
        this.nombre = nombre;
        this.comentario = comentario;
        this.publicado = publicado;
        this.emociones = (emociones != null) ? emociones : new ArrayList<>();
        this.generoSeleccionados = (generoSeleccionados != null) ? generoSeleccionados : new ArrayList<>();
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTiempoInicio() {
        return tiempoInicio;
    }

    public String getTiempoFinal() {
        return tiempoFinal;
    }

    public double getValence() { return valence; }

    public void setValence(double valence) { this.valence = valence; }

    public double getArousal() { return arousal; }

    public void setArousal(double arousal) { this.arousal = arousal; }

    public void setTiempoInicio(String tiempoInicio) {
        this.tiempoInicio = tiempoInicio;
    }

    public void setTiempoFinal(String tiempoFinal) {
        this.tiempoFinal = tiempoFinal;
    }

    public String getFecha_creacion() {
        return fecha_creacion;
    }

    public void setFecha_creacion(String fecha_creacion) {
        this.fecha_creacion = fecha_creacion;
    }

    public String getFecha_ultima_edicion() {
        return fecha_ultima_edicion;
    }

    public void setFecha_ultima_edicion(String fecha_ultima_edicion) {
        this.fecha_ultima_edicion = fecha_ultima_edicion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public boolean isPublicado() {
        return publicado;
    }

    public void setPublicado(boolean publicado) {
        this.publicado = publicado;
    }

    public List<EmocionSeleccionada> getEmociones() {
        return emociones;
    }

    public void setEmociones(List<EmocionSeleccionada> emociones) {
        this.emociones = emociones;
    }

    public List<GeneroSeleccionado> getGeneros() {
        return generoSeleccionados;
    }

    public void setGeneros(List<GeneroSeleccionado> generoSeleccionados) {
        this.generoSeleccionados = generoSeleccionados;
    }
}