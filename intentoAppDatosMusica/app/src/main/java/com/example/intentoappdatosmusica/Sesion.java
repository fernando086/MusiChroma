package com.example.intentoappdatosmusica;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Sesion implements Serializable {
    private int id;

    @SerializedName("numero_sesion")
    private int numeroSesion;
    private String nombre;

    @SerializedName("institucion_educativa")
    private String institucionEducativa;

    @SerializedName("grado_seccion")
    private String gradoSeccion;

    private String facilitador;

    @SerializedName("numero_estudiantes")
    private int numeroEstudiantes;

    private boolean tipo; // true = Individual, false = Grupal
    private boolean modo; // true = Presencial, false = Virtual

    @SerializedName("fecha_hora_inicio")
    private String fechaHoraInicio;

    @SerializedName("fecha_hora_final")
    private String fechaHoraFinal;

    // Desarrollo
    private String inicio;

    @SerializedName("actividad_central")
    private String actividadCentral;

    private String cierre;

    // Observaciones
    @SerializedName("descripcion_clima")
    private String descripcionClima;

    private String observaciones;

    @SerializedName("cantidad_estrellas")
    private float estrellas;

    private boolean favorito;
    private int color;

    @SerializedName("cantidad_canciones")
    private int cantidadCanciones;

    @SerializedName("canciones_ids")
    private List<Integer> cancionesIds = new ArrayList<>();

    private List<String> palabras = new ArrayList<>();

    private String dificultades;
    private String recomendaciones;

    // Grid Selections
    @SerializedName("objetivos_ids")
    private List<Integer> objetivosIds = new ArrayList<>();

    @SerializedName("objetivos_custom")
    private String objetivosCustom;

    @SerializedName("tecnicas_ids")
    private List<Integer> tecnicasIds = new ArrayList<>();

    @SerializedName("tecnicas_custom")
    private String tecnicasCustom;

    @SerializedName("materiales_ids")
    private List<Integer> materialesIds = new ArrayList<>();

    @SerializedName("materiales_custom")
    private String materialesCustom;

    @SerializedName("logros_ids")
    private List<Integer> logrosIds = new ArrayList<>();

    @SerializedName("logros_custom")
    private String logrosCustom;

    @SerializedName("clima_grupal_ids")
    private List<Integer> climaGrupalIds = new ArrayList<>();

    @SerializedName("clima_grupal_custom")
    private String climaGrupalCustom;

    // Constructor vacío
    public Sesion() {
    }

    // Constructor completo (simplificado para uso común, usar setters para el
    // resto)
    public Sesion(int id, String nombre, boolean tipo, boolean modo, String fechaHoraInicio) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.modo = modo;
        this.fechaHoraInicio = fechaHoraInicio;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getNumeroSesion() {
        return numeroSesion;
    }

    public void setNumeroSesion(int numeroSesion) {
        this.numeroSesion = numeroSesion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getInstitucionEducativa() {
        return institucionEducativa;
    }

    public void setInstitucionEducativa(String institucionEducativa) {
        this.institucionEducativa = institucionEducativa;
    }

    public String getGradoSeccion() {
        return gradoSeccion;
    }

    public void setGradoSeccion(String gradoSeccion) {
        this.gradoSeccion = gradoSeccion;
    }

    public String getFacilitador() {
        return facilitador;
    }

    public void setFacilitador(String facilitador) {
        this.facilitador = facilitador;
    }

    public int getNumeroEstudiantes() {
        return numeroEstudiantes;
    }

    public void setNumeroEstudiantes(int numeroEstudiantes) {
        this.numeroEstudiantes = numeroEstudiantes;
    }

    public boolean isTipo() {
        return tipo;
    }

    public void setTipo(boolean tipo) {
        this.tipo = tipo;
    }

    public boolean isModo() {
        return modo;
    }

    public void setModo(boolean modo) {
        this.modo = modo;
    }

    public String getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public void setFechaHoraInicio(String fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    public String getFechaHoraFinal() {
        return fechaHoraFinal;
    }

    public void setFechaHoraFinal(String fechaHoraFinal) {
        this.fechaHoraFinal = fechaHoraFinal;
    }

    public String getInicio() {
        return inicio;
    }

    public void setInicio(String inicio) {
        this.inicio = inicio;
    }

    public String getActividadCentral() {
        return actividadCentral;
    }

    public void setActividadCentral(String actividadCentral) {
        this.actividadCentral = actividadCentral;
    }

    public String getCierre() {
        return cierre;
    }

    public void setCierre(String cierre) {
        this.cierre = cierre;
    }

    public String getDescripcionClima() {
        return descripcionClima;
    }

    public void setDescripcionClima(String descripcionClima) {
        this.descripcionClima = descripcionClima;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public float getEstrellas() {
        return estrellas;
    }

    public void setEstrellas(float estrellas) {
        this.estrellas = estrellas;
    }

    public boolean isFavorito() {
        return favorito;
    }

    public void setFavorito(boolean favorito) {
        this.favorito = favorito;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getCantidadCanciones() {
        return cantidadCanciones;
    }

    public void setCantidadCanciones(int cantidadCanciones) {
        this.cantidadCanciones = cantidadCanciones;
    }

    public List<Integer> getCancionesIds() {
        return cancionesIds;
    }

    public void setCancionesIds(List<Integer> cancionesIds) {
        this.cancionesIds = cancionesIds;
    }

    public List<String> getPalabras() {
        return palabras;
    }

    public void setPalabras(List<String> palabras) {
        this.palabras = palabras;
    }

    public String getDificultades() {
        return dificultades;
    } // Mapped to 'dificultades' text

    public void setDificultad(String dificultades) {
        this.dificultades = dificultades;
    } // Setter name match UI usage? UI called setDificultad

    public String getRecomendaciones() {
        return recomendaciones;
    }

    public void setRecomendaciones(String recomendaciones) {
        this.recomendaciones = recomendaciones;
    }

    // Grid Lists & Customs
    public List<Integer> getObjetivosIds() {
        return objetivosIds;
    }

    public void setObjetivosIds(List<Integer> objetivosIds) {
        this.objetivosIds = objetivosIds;
    }

    public String getObjetivosCustom() {
        return objetivosCustom;
    }

    public void setObjetivosCustom(String objetivosCustom) {
        this.objetivosCustom = objetivosCustom;
    }

    public List<Integer> getTecnicasIds() {
        return tecnicasIds;
    }

    public void setTecnicasIds(List<Integer> tecnicasIds) {
        this.tecnicasIds = tecnicasIds;
    }

    public String getTecnicasCustom() {
        return tecnicasCustom;
    }

    public void setTecnicasCustom(String tecnicasCustom) {
        this.tecnicasCustom = tecnicasCustom;
    }

    public List<Integer> getMaterialesIds() {
        return materialesIds;
    }

    public void setMaterialesIds(List<Integer> materialesIds) {
        this.materialesIds = materialesIds;
    }

    public String getMaterialesCustom() {
        return materialesCustom;
    }

    public void setMaterialesCustom(String materialesCustom) {
        this.materialesCustom = materialesCustom;
    }

    public List<Integer> getLogrosIds() {
        return logrosIds;
    }

    public void setLogrosIds(List<Integer> logrosIds) {
        this.logrosIds = logrosIds;
    }

    public String getLogrosCustom() {
        return logrosCustom;
    }

    public void setLogrosCustom(String logrosCustom) {
        this.logrosCustom = logrosCustom;
    }

    public List<Integer> getClimaGrupalIds() {
        return climaGrupalIds;
    }

    public void setClimaGrupalIds(List<Integer> climaGrupalIds) {
        this.climaGrupalIds = climaGrupalIds;
    }

    public String getClimaGrupalCustom() {
        return climaGrupalCustom;
    }

    public void setClimaGrupalCustom(String climaGrupalCustom) {
        this.climaGrupalCustom = climaGrupalCustom;
    }
}
