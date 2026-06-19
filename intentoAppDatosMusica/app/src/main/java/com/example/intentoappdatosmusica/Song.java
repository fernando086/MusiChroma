package com.example.intentoappdatosmusica;

import android.util.Log;

import java.io.Serializable;
import java.util.List;

public class Song implements Serializable {
    private int id;
    private String nombre;
    private String autor;
    private String enlace;
    private String album;
    private String comentario_general;
    private boolean isLoaded = false; // Indica si la canción ha sido cargada en MediaPlayerList
    private boolean estadoCgPublicado, estadoPublicado;
    private List<Seccion> secciones; // 🔹 Ej: 00:00.000-01:10.000|01:10.000-02:20.000
    private String fecha_creacion, fecha_ultima_edicion;

    // Constructor
    public Song(int id, String nombre, String autor, String album, String enlace, String comentario_general, Boolean estadoCgPublicado, Boolean estadoPublicado, List<Seccion> secciones, String fecha_creacion, String fecha_ultima_edicion) {
        this.id = id;
        this.nombre = nombre;
        this.autor = autor;
        this.album = album;
        this.enlace = enlace;
        this.comentario_general = comentario_general;
        this.estadoCgPublicado = estadoCgPublicado;
        this.estadoPublicado = estadoPublicado;
        this.secciones = secciones;
        this.fecha_creacion = fecha_creacion;
        this.fecha_ultima_edicion = fecha_ultima_edicion;
    }

    public Song(){}

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getAutor() {
        return autor;
    }

    public String getEnlace() {
        return enlace;
    }

    public String getAlbum() {
        return album;
    }

    public boolean isEstadoPublicado() {
        return estadoPublicado;
    }

    public boolean isEstadoCgPublicado() {
        return estadoCgPublicado;
    }

    public String getComentario_general() {
        return comentario_general;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void setLoaded(boolean loaded) {
        this.isLoaded = loaded;
    }

    public List<Seccion> getSecciones() {
        return secciones;
    }

    public void setSecciones(List<Seccion> secciones) {
        this.secciones = secciones;
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

    public void setId(int id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public void setEnlace(String enlace) {
        this.enlace = enlace;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public void setComentario_general(String comentario_general) {
        this.comentario_general = comentario_general;
    }

    public void setEstadoCgPublicado(boolean estadoCgPublicado) {
        this.estadoCgPublicado = estadoCgPublicado;
    }

    public void setEstadoPublicado(boolean estadoPublicado) {
        this.estadoPublicado = estadoPublicado;
    }

    public String getFilePath() {
        if (enlace == null || enlace.isEmpty()) return null;
        return enlace; // En BYOM, el enlace es ya el filename local
    }

    public Seccion getSeccionActual(int currentPositionMs) {
        if (secciones == null) return null;

        for (Seccion s : secciones) {
            int inicio = parseTiempoMs(s.getTiempoInicio());
            int fin = parseTiempoMs(s.getTiempoFinal());

            if (currentPositionMs >= inicio && currentPositionMs <= fin) {
                //Log.e("GET SECCION ACTUAL", s.getNombre() + s.getTiempoInicio() + s.getTiempoFinal()); //SÍ RETORNA DATOS
                return s;
            }
        }
        //Log.e("GET SECCION ACTUAL", "NULL");
        return null;
    }

    private int parseTiempoMs(String tiempo) {
        // Formato esperado: mm:ss.SSS
        try {
            String[] partes = tiempo.split("[:.]");
            int minutos = Integer.parseInt(partes[0]);
            int segundos = Integer.parseInt(partes[1]);
            int milis = (partes.length > 2) ? Integer.parseInt(partes[2]) : 0;

            return (minutos * 60 * 1000) + (segundos * 1000) + milis;
        } catch (Exception e) {
            return 0;
        }
    }

    public String getSafeFileName() {
        if (enlace == null || enlace.trim().isEmpty()) {
            return "unknown_" + id + ".mp3";
        }
        // Reemplaza caracteres prohibidos en cualquier Sistema de Archivos para evitar FileNotFounds
        String safeName = enlace.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // Si originalmente era una URL de Youtube, probablemente carece de la extensión de audio mp3, así que la agregamos explícitamente.
        if (enlace.contains("youtube.com") || enlace.contains("youtu.be")) {
            return safeName + ".mp3";
        }
        return safeName;
    }
}
