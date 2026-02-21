package com.example.intentoappdatosmusica;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// Nueva clase para la búsqueda global
class PalabraEmocionalUnica implements Seleccionable {
    private final String palabra;
    private final String emocion;
    private final String nivelArousal;
    private final int backgroundColor;
    private final int textColor;

    public PalabraEmocionalUnica(String palabra, String emocion, String nivelArousal, int backgroundColor, int textColor) {
        this.palabra = palabra;
        this.emocion = emocion;
        this.nivelArousal = nivelArousal;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
    }

    @Override
    public String getNombreParaMostrar() {
        return palabra + " (" + emocion + " " + nivelArousal + ")";
    }

    public String getPalabra() {
        return palabra;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getTextColor() {
        return textColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PalabraEmocionalUnica that = (PalabraEmocionalUnica) o;
        return Objects.equals(palabra, that.palabra);
    }

    @Override
    public int hashCode() {
        return Objects.hash(palabra);
    }
}

public class PopupListaAdapter<T extends Seleccionable> extends RecyclerView.Adapter<PopupListaAdapter.ViewHolder> {
    private final List<T> listaCompleta;
    private final List<T> listaFiltrada;
    private final Set<T> seleccionados;
    private static final int LIMITE_SELECCION = 10;

    private final TextView tvContadorGlobal;
    private final TextView tvContadorLocal;
    private final int textColor;

    private int seleccionGlobalTotal;
    private boolean mostrarSoloSeleccionados = false;
    private String textoBusqueda = "";

    public PopupListaAdapter(List<T> listaOriginal, Set<T> seleccionInicial, TextView tvContadorGlobal, TextView tvContadorLocal, int textColor, int seleccionGlobalTotal) {
        this.listaCompleta = new ArrayList<>(listaOriginal);
        this.listaFiltrada = new ArrayList<>(listaOriginal);
        this.seleccionados = new HashSet<>(seleccionInicial);
        this.tvContadorGlobal = tvContadorGlobal;
        this.tvContadorLocal = tvContadorLocal;
        this.textColor = textColor;
        this.seleccionGlobalTotal = seleccionGlobalTotal;

        actualizarContadores();
    }

    private void actualizarContadores() {
        if (tvContadorGlobal != null) {
            String textoGlobal = "Total: " + seleccionGlobalTotal + "/" + LIMITE_SELECCION;
            tvContadorGlobal.setText(textoGlobal);
        }
        if (tvContadorLocal != null) {
            long countLocal = listaCompleta.stream().filter(seleccionados::contains).count();
            String textoLocal = "En esta lista: " + countLocal + "/" + listaCompleta.size();
            tvContadorLocal.setText(textoLocal);
        }
    }

    public Set<T> getSeleccionados() {
        return seleccionados;
    }

    public int getSeleccionGlobalTotal() {
        return seleccionGlobalTotal;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checkbox, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        T item = listaFiltrada.get(position);
        holder.checkBox.setText(item.getNombreParaMostrar());
        holder.checkBox.setChecked(seleccionados.contains(item));

        int currentTextColor = this.textColor;
        int currentBackgroundColor = Color.TRANSPARENT;

        if (item instanceof PalabraEmocionalUnica) {
            PalabraEmocionalUnica palabraUnica = (PalabraEmocionalUnica) item;
            currentTextColor = palabraUnica.getTextColor();
            currentBackgroundColor = palabraUnica.getBackgroundColor();
        }

        if (currentTextColor != 0) {
            holder.checkBox.setTextColor(currentTextColor);
            CompoundButtonCompat.setButtonTintList(holder.checkBox, ColorStateList.valueOf(currentTextColor));
        }

        if (seleccionados.contains(item)) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.item_seleccionado));
        } else {
            holder.itemView.setBackgroundColor(currentBackgroundColor);
        }

        holder.itemView.setOnClickListener(v -> {
            holder.checkBox.toggle();
        });
        holder.checkBox.setOnClickListener(v -> alternarSeleccion(item, holder));
    }

    private void alternarSeleccion(T item, ViewHolder holder) {
        if (!seleccionados.contains(item)) {
            if (seleccionGlobalTotal >= LIMITE_SELECCION) {
                Toast.makeText(tvContadorGlobal.getContext(), "Límite total de 10 palabras alcanzado", Toast.LENGTH_SHORT).show();
                holder.checkBox.setChecked(false); // Revert visual change
                return;
            }
            seleccionados.add(item);
            seleccionGlobalTotal++;
        } else {
            seleccionados.remove(item);
            seleccionGlobalTotal--;
        }

        if (mostrarSoloSeleccionados) {
            filtrar();
        } else {
            notifyItemChanged(holder.getAdapterPosition());
        }
        actualizarContadores();
    }

    @Override
    public int getItemCount() {
        return listaFiltrada.size();
    }

    public void setTextoBusqueda(String texto) {
        this.textoBusqueda = texto.toLowerCase(Locale.ROOT);
        filtrar();
    }

    public void setMostrarSoloSeleccionados(boolean mostrar) {
        this.mostrarSoloSeleccionados = mostrar;
        filtrar();
    }

    private void filtrar() {
        listaFiltrada.clear();
        List<T> listaBase;
        if (mostrarSoloSeleccionados) {
            // Filter the complete list to show only selected items
            listaBase = listaCompleta.stream().filter(seleccionados::contains).collect(Collectors.toList());
        } else {
            listaBase = new ArrayList<>(listaCompleta);
        }

        if (textoBusqueda.isEmpty()) {
            listaFiltrada.addAll(listaBase);
        } else {
            for (T item : listaBase) {
                if (item.getNombreParaMostrar().toLowerCase(Locale.ROOT).contains(textoBusqueda)) {
                    listaFiltrada.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_item);
        }
    }
}
