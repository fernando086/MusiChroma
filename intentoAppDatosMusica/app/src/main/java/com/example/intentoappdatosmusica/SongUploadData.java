package com.example.intentoappdatosmusica;

import android.net.Uri;

public class SongUploadData {
    private Uri uri;
    private String nombreArchivo;
    private String enlace;

    public SongUploadData() {
        this.uri = null;
        this.nombreArchivo = null;
        this.enlace = "";
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getEnlace() {
        return enlace;
    }

    public void setEnlace(String enlace) {
        this.enlace = enlace;
    }
}
