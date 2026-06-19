package com.example.intentoappdatosmusica;

import java.io.Serializable;
import java.util.List;

public class CancionPrediccion implements Serializable {
    private int songId;
    private String link;
    private String nombre;
    private String rutaAudio;
    private String duracion;
    private String tipoOrigen;
    private String artista;
    private String album;
    private boolean offline;
    private List<Seccion> secciones;

    public CancionPrediccion(int songId, String link, String nombre, String rutaAudio, String duracion, String tipoOrigen, String artista, String album, boolean offline, List<Seccion> secciones) {
        this.songId = songId;
        this.link = link;
        this.nombre = nombre;
        this.rutaAudio = rutaAudio;
        this.duracion = duracion;
        this.tipoOrigen = tipoOrigen;
        this.artista = artista;
        this.album = album;
        this.offline = offline;
        this.secciones = secciones;
    }

    public int getSongId() { return songId; }
    public String getLink() { return link; }
    public String getNombre() { return nombre; }
    public String getRutaAudio() { return rutaAudio; }
    public String getDuracion() { return duracion; }
    public String getTipoOrigen() { return tipoOrigen; }
    public String getArtista() { return artista; }
    public String getAlbum() { return album; }
    public boolean isOffline() { return offline; }
    public List<Seccion> getSecciones() { return secciones; }
    public void setSecciones(List<Seccion> secciones) { this.secciones = secciones; }
}
