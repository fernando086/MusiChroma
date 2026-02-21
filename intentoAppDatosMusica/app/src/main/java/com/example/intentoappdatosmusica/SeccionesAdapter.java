package com.example.intentoappdatosmusica;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SeccionesAdapter extends RecyclerView.Adapter<SeccionesAdapter.SeccionViewHolder> {
    private List<Seccion> listaSecciones;
    private SeccionEditTextListener listener; // 🔹 Cambiado a interfaz genérica
    private boolean modoEditarFin = false;
    private int seccionSeleccionada = -1;
    private boolean mostrarControlesEdicion; // 🔹 Nueva variable para controlar la visibilidad

    // 🔹 NUEVA interfaz para comunicación
    public interface SeccionEditTextListener {
        void actualizarTiempoDesdeEditText(int indexSeccion, String tiempo, boolean esTiempoInicio);
        void eliminarSeccion(int posicion);
        void setSeccionSeleccionada(int index);
        int obtenerCantidadThumbs();
        void reconstruirTiemposSecciones();
        void cargarThumbsDesdeSecciones();
        void limpiarThumbs();
    }

    // 🔹 NUEVO: Constructor modificado para recibir el contexto
    public SeccionesAdapter(List<Seccion> listaSecciones, SeccionEditTextListener listener, boolean mostrarControlesEdicion) {
        this.listaSecciones = listaSecciones;
        this.listener = listener;
        this.mostrarControlesEdicion = mostrarControlesEdicion;
    }

    // 🔹 NUEVO: Método para cambiar la visibilidad de controles
    public void setMostrarControlesEdicion(boolean mostrar) {
        this.mostrarControlesEdicion = mostrar;
        notifyDataSetChanged();
    }

    // 🔹 NUEVO: Método para actualizar la lista
    public void actualizarLista(List<Seccion> nuevasSecciones) {
        this.listaSecciones = nuevasSecciones;
        notifyDataSetChanged();
    }

    // 🔹 NUEVO: Método para establecer sección seleccionada
    public void setSeccionSeleccionada(int index) {
        this.seccionSeleccionada = index;
        notifyDataSetChanged();
    }

    // 🔹 NUEVO: Método para establecer modo edición
    public void setModoEditarFin(boolean editarFin) {
        this.modoEditarFin = editarFin;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SeccionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_seccion, parent, false);
        return new SeccionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SeccionViewHolder holder, int position) {
        Seccion seccion = listaSecciones.get(position);

        // 🔹 CONFIGURAR VISIBILIDAD DE CONTROLES
        if (mostrarControlesEdicion) {
            // MODO POPUP: Mostrar RadioButton y botón eliminar
            holder.rbSeccion.setVisibility(View.VISIBLE);
            holder.btnEliminarSeccion.setVisibility(View.VISIBLE);
        } else {
            // MODO DATOS PREDICCIÓN: Ocultar RadioButton y botón eliminar
            holder.rbSeccion.setVisibility(View.GONE);
            holder.btnEliminarSeccion.setVisibility(View.GONE);
        }

        // 🔄 Configurar los EditText según el modo de edición
        boolean esPrimera = position == 0;
        boolean esUltima = position == listaSecciones.size() - 1;

        // 👈 Si el modo es editar Fin, desactivar todos los EditTextInicio (excepto el primero)
        if (modoEditarFin || esPrimera) {
            holder.etInicioSeccion.setFocusable(false);
            holder.etInicioSeccion.setFocusableInTouchMode(false);
            holder.etInicioSeccion.setClickable(false);
            holder.etInicioSeccion.setEnabled(false);
        } else {
            holder.etInicioSeccion.setEnabled(true);
            holder.etInicioSeccion.setFocusable(true);
            holder.etInicioSeccion.setFocusableInTouchMode(true);
            holder.etInicioSeccion.setClickable(true);
        }

        // 👉 Si el modo es editar Inicio, desactivar todos los EditTextFin (excepto el último)
        if (!modoEditarFin || esUltima) {
            holder.etFinSeccion.setFocusable(false);
            holder.etFinSeccion.setFocusableInTouchMode(false);
            holder.etFinSeccion.setClickable(false);
            holder.etFinSeccion.setEnabled(false);
        } else {
            holder.etFinSeccion.setEnabled(true);
            holder.etFinSeccion.setFocusable(true);
            holder.etFinSeccion.setFocusableInTouchMode(true);
            holder.etFinSeccion.setClickable(true);
        }

        holder.tvNumeroSeccion.setText("Sección " + (position + 1) + ":");
        holder.etInicioSeccion.setText(seccion.getTiempoInicio());
        holder.etFinSeccion.setText(seccion.getTiempoFinal());

        // 🔘 RadioButton - Solo configurar si está visible
        if (mostrarControlesEdicion) {
            holder.rbSeccion.setEnabled(!esUltima);
            holder.rbSeccion.setChecked(seccionSeleccionada == position && !esUltima);

            // 🔹 Detectar si el usuario toca el mismo RadioButton ya marcado
            holder.rbSeccion.setOnClickListener(v -> {
                if (seccionSeleccionada == position) {
                    holder.rbSeccion.setChecked(false);
                    if (listener != null) {
                        listener.setSeccionSeleccionada(-1);
                    }
                } else {
                    if (listener != null) {
                        listener.setSeccionSeleccionada(position);
                    }
                }
            });
        }

        // ⏮️ Lógica EditTextInicio
        holder.etInicioSeccion.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String nuevoInicio = holder.etInicioSeccion.getText().toString();
                String tiempoAnterior = seccion.getTiempoInicio();

                if (nuevoInicio.equals(tiempoAnterior)) return;

                // Validar y normalizar formato
                String tiempoNormalizado = esFormatoValido(holder.etInicioSeccion);
                if (tiempoNormalizado == null) {
                    holder.etInicioSeccion.setText(tiempoAnterior);
                    return;
                }

                seccion.setTiempoInicio(tiempoNormalizado);

                // 🔹 NUEVO: Llamar al método para actualizar desde EditText
                if (listener != null) {
                    listener.actualizarTiempoDesdeEditText(position, tiempoNormalizado, true);
                }

                if (position > 0) {
                    listaSecciones.get(position - 1).setTiempoFinal(tiempoNormalizado);
                }
                notifyDataSetChanged();
            }
        });

        // ⏭️ Lógica EditTextFin
        holder.etFinSeccion.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String nuevoFin = holder.etFinSeccion.getText().toString();
                String tiempoAnterior = seccion.getTiempoFinal();

                if (nuevoFin.equals(tiempoAnterior)) return;

                // Validar y normalizar formato
                String tiempoNormalizado = esFormatoValido(holder.etFinSeccion);
                if (tiempoNormalizado == null) {
                    holder.etFinSeccion.setText(tiempoAnterior);
                    return;
                }

                seccion.setTiempoFinal(tiempoNormalizado);

                // 🔹 NUEVO: Llamar al método para actualizar desde EditText
                if (listener != null) {
                    listener.actualizarTiempoDesdeEditText(position, tiempoNormalizado, false);
                }

                if (position < listaSecciones.size() - 1) {
                    listaSecciones.get(position + 1).setTiempoInicio(tiempoNormalizado);
                }
                notifyDataSetChanged();
            }
        });

        // 🗑️ Eliminar sección - Solo configurar si está visible
        if (mostrarControlesEdicion) {
            holder.btnEliminarSeccion.setEnabled(!esUltima);
            holder.btnEliminarSeccion.setOnClickListener(v -> {
                if (listener != null) {
                    listener.eliminarSeccion(position);
                }
            });
        }

        InputFilter filtroCaracteresPermitidos = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!Character.isDigit(c) && c != ':' && c != '.') {
                    return ""; // Bloquea el carácter
                }
            }
            return null; // Acepta entrada válida
        };

        holder.etInicioSeccion.setFilters(new InputFilter[] { filtroCaracteresPermitidos });
        holder.etFinSeccion.setFilters(new InputFilter[] { filtroCaracteresPermitidos });
    }

    private String esFormatoValido(EditText editText) {
        String textoOriginal = editText.getText().toString();
        // 🔧 Limpia espacios y reemplaza comas o punto y coma por punto decimal
        String tiempo = textoOriginal
                .replaceAll("\\s+", "")     // quita espacios
                .replace(",", ".")          // coma → punto
                .replace(";", ".");         // punto y coma → punto (opcional)

        Pattern pattern = Pattern.compile("^(\\d{1,2}):(\\d{1,2})\\.(\\d{1,3})$");
        Matcher matcher = pattern.matcher(tiempo);
        if (!matcher.matches()) return null;

        String minutos = matcher.group(1);
        String segundos = matcher.group(2);
        String milis = matcher.group(3);

        int segundosInt = Integer.parseInt(segundos);
        int milisInt = Integer.parseInt(milis);
        if (segundosInt >= 60 || milisInt >= 1000) return null;

        // Normalización
        if (minutos.length() < 2) minutos = "0" + minutos;
        if (segundos.length() < 2) segundos = "0" + segundos;
        while (milis.length() < 3) milis += "0";

        String tiempoNormalizado = minutos + ":" + segundos + "." + milis;

        if (!textoOriginal.equals(tiempoNormalizado)) {
            editText.setText(tiempoNormalizado);
            editText.setSelection(tiempoNormalizado.length());
        }

        return tiempoNormalizado;
    }

    // 🔹 NUEVO: Método para sincronizar la selección desde el Popup
    public void sincronizarSeleccion(int seleccion) {
        this.seccionSeleccionada = seleccion;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return listaSecciones.size();
    }

    static class SeccionViewHolder extends RecyclerView.ViewHolder {
        RadioButton rbSeccion;
        TextView tvNumeroSeccion;
        EditText etInicioSeccion, etFinSeccion;
        ImageButton btnEliminarSeccion;

        SeccionViewHolder(View itemView) {
            super(itemView);
            rbSeccion = itemView.findViewById(R.id.rb_seccion);
            tvNumeroSeccion = itemView.findViewById(R.id.tv_numero_seccion);
            etInicioSeccion = itemView.findViewById(R.id.et_inicio_seccion);
            etFinSeccion = itemView.findViewById(R.id.et_fin_seccion);
            btnEliminarSeccion = itemView.findViewById(R.id.btn_eliminar_seccion);
        }
    }
}