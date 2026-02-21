package com.example.intentoappdatosmusica;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GridOpcionesAdapter extends RecyclerView.Adapter<GridOpcionesAdapter.ViewHolder> {

    private List<OpcionSesion> opciones;
    private OnOpcionChangeListener listener;
    private boolean isBinding = false; // Flag para evitar bucles infinitos en TextWatcher

    public interface OnOpcionChangeListener {
        void onOpcionChanged();
    }

    public GridOpcionesAdapter(List<OpcionSesion> opciones, OnOpcionChangeListener listener) {
        this.opciones = opciones;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_opcion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OpcionSesion opcion = opciones.get(position);
        isBinding = true; // Iniciando bind

        holder.tvNombre.setText(opcion.getNombre());
        holder.ivIcono.setImageResource(opcion.getImagenResId());

        // Configurar estado de selección visual
        if (opcion.isSeleccionado()) {
            holder.cardView
                    .setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.teal_200));
            holder.tvNombre.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
        } else {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
            holder.tvNombre.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.black));
        }

        // Lógica para el item editable (última posición o flag isEditable)
        if (opcion.isEditable()) {
            if (opcion.isSeleccionado()) {
                holder.etPersonalizado.setVisibility(View.VISIBLE);
                holder.etPersonalizado.setText(opcion.getTextoPersonalizado());
                holder.tvNombre.setVisibility(View.GONE);
            } else {
                holder.etPersonalizado.setVisibility(View.GONE);
                holder.tvNombre.setVisibility(View.VISIBLE);
                holder.tvNombre.setText(opcion.getNombre()); // "Otros" o el nombre por defecto
            }
        } else {
            holder.etPersonalizado.setVisibility(View.GONE);
            holder.tvNombre.setVisibility(View.VISIBLE);
        }

        // Click listener para la tarjeta
        holder.itemView.setOnClickListener(v -> {
            opcion.setSeleccionado(!opcion.isSeleccionado());
            notifyItemChanged(position);
            if (listener != null)
                listener.onOpcionChanged();
        });

        isBinding = false; // Fin bind
    }

    @Override
    public int getItemCount() {
        return opciones.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView ivIcono;
        TextView tvNombre;
        EditText etPersonalizado;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardViewContainer);
            ivIcono = itemView.findViewById(R.id.ivIconoOpcion);
            tvNombre = itemView.findViewById(R.id.tvNombreOpcion);
            etPersonalizado = itemView.findViewById(R.id.etOpcionPersonalizada);

            // Listener para el EditText
            etPersonalizado.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (isBinding)
                        return; // Evitar disparar mientras se recicla la vista
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        OpcionSesion op = opciones.get(pos);
                        if (op.isEditable() && op.isSeleccionado()) {
                            op.setTextoPersonalizado(s.toString());
                            if (listener != null)
                                listener.onOpcionChanged();
                        }
                    }
                }
            });
        }
    }
}
