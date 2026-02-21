package com.example.intentoappdatosmusica;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SesionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SESION = 0;
    private static final int VIEW_TYPE_ADD_SESION = 1;

    private Context context;
    private List<Sesion> sesionList;
    private List<Sesion> sesionListFull;

    private ApiService apiService;

    // ✅ Listener para el botón de agregar sesión
    private View.OnClickListener onAgregarClickListener;

    // ✅ Listeners asignables opcionalmente
    private View.OnClickListener onIngresarClickListener;
    private OnCambiarColorClickListener onCambiarColorClickListener;
    private View.OnClickListener onFavoritoClickListener;

    public SesionAdapter(Context context, List<Sesion> sesionList) {
        this.context = context;
        this.sesionList = new ArrayList<>(sesionList);
        this.sesionListFull = new ArrayList<>(sesionList);
        this.apiService = ApiClient.getRetrofitInstance().create(ApiService.class);
    }

    // ✅ Setters de listeners
    public void setOnAgregarClickListener(View.OnClickListener listener) {
        this.onAgregarClickListener = listener;
    }

    public void setOnIngresarClickListener(View.OnClickListener listener) {
        this.onIngresarClickListener = listener;
    }

    public interface OnCambiarColorClickListener {
        void onClick(View v, int position);
    }

    public void setOnCambiarColorClickListener(OnCambiarColorClickListener listener) {
        this.onCambiarColorClickListener = listener;
    }

    public void setOnFavoritoClickListener(View.OnClickListener listener) {
        this.onFavoritoClickListener = listener;
    }

    public void setSesionList(List<Sesion> nuevaLista) {
        this.sesionList = new ArrayList<>(nuevaLista);
        this.sesionListFull = new ArrayList<>(nuevaLista);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_ADD_SESION : VIEW_TYPE_SESION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
                viewType == VIEW_TYPE_ADD_SESION ? R.layout.item_add_to_list : R.layout.item_sesion,
                parent,
                false);
        return viewType == VIEW_TYPE_ADD_SESION ? new AddSesionViewHolder(view) : new SesionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_ADD_SESION) {
            // ✅ Configurar botón "+"
            AddSesionViewHolder addHolder = (AddSesionViewHolder) holder;
            if (onAgregarClickListener != null) {
                addHolder.btnAgregar.setOnClickListener(v -> onAgregarClickListener.onClick(v));
            }
        } else {
            SesionViewHolder sesionHolder = (SesionViewHolder) holder;
            Sesion sesion = sesionList.get(position - 1); // 👈 IMPORTANTE: correr índice

            sesionHolder.txtTitulo.setText(sesion.getNumeroSesion() + " - " + sesion.getNombre());
            sesionHolder.txtFechas.setText(sesion.getFechaHoraInicio() + " → " + sesion.getFechaHoraFinal());
            // Activar marquee luego de un pequeño delay
            sesionHolder.txtFechas.postDelayed(() -> {
                sesionHolder.txtFechas.setSelected(true); // Necesario para activar marquee
            }, 2000); // Espera 2 segundos antes de empezar a desplazarse

            sesionHolder.txtEstrellas.setText(String.valueOf(sesion.getEstrellas()));
            sesionHolder.txtCanciones.setText(String.valueOf(sesion.getCantidadCanciones()));

            if (onIngresarClickListener != null) {
                sesionHolder.btnIngresar.setTag(position); // 👈 Guarda la posición
                sesionHolder.btnIngresar.setOnClickListener(v -> onIngresarClickListener.onClick(v));
            }

            if (onCambiarColorClickListener != null)
                sesionHolder.btnCambiarColor.setOnClickListener(v -> onCambiarColorClickListener.onClick(v, position));

            sesionHolder.btnEliminar.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Eliminar sesión")
                        .setMessage("¿Estás seguro de que deseas eliminar la sesión '" + sesion.getNombre() + "'?")
                        .setPositiveButton("Eliminar", (dialog, which) -> {
                            deleteSesion(sesion, position - 1);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });

            if (onFavoritoClickListener != null)
                sesionHolder.btnFavorito.setOnClickListener(v -> onFavoritoClickListener.onClick(v));

            int colorIndex = sesion.getColor(); // EJ: si vale 1, es el primer color
            MaterialCardView card = (MaterialCardView) sesionHolder.itemView;

            if (colorIndex > 0 && colorIndex <= ColoresSesion.COLORES.length) {
                String hex = ColoresSesion.COLORES[colorIndex - 1];
                card.setStrokeColor(Color.parseColor(hex));
            } else {
                card.setStrokeColor(Color.parseColor("#000000")); // Color negro por defecto
            }
        }
    }

    private void deleteSesion(Sesion sesion, int position) {
        apiService.deleteSesion(new DeleteSesionRequest(sesion.getId())).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    sesionList.remove(position);
                    notifyItemRemoved(position + 1);
                    notifyItemRangeChanged(position + 1, sesionList.size());
                    Toast.makeText(context, "Sesión '" + sesion.getId() + "' eliminada", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Error al eliminar la sesión " + sesion.getId(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(context, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return sesionList != null ? sesionList.size() + 1 : 1; // +1 por el botón de agregar
    }

    // ✅ ViewHolder de sesión normal
    public static class SesionViewHolder extends RecyclerView.ViewHolder {
        ImageButton btnIngresar, btnCambiarColor, btnEliminar, btnFavorito;
        TextView txtTitulo, txtFechas, txtEstrellas, txtCanciones;

        public SesionViewHolder(@NonNull View itemView) {
            super(itemView);
            btnIngresar = itemView.findViewById(R.id.btnIngresarSesion);
            btnCambiarColor = itemView.findViewById(R.id.btnClasificarColor);
            btnEliminar = itemView.findViewById(R.id.btnEliminarSesion);
            btnFavorito = itemView.findViewById(R.id.btnMarcarFavorito);

            txtTitulo = itemView.findViewById(R.id.tvIDNombre);
            txtFechas = itemView.findViewById(R.id.tvFechaSesion);
            txtEstrellas = itemView.findViewById(R.id.tvEstrellas);
            txtCanciones = itemView.findViewById(R.id.tvCantidadCanciones);
        }
    }

    // ✅ ViewHolder del botón de agregar
    public static class AddSesionViewHolder extends RecyclerView.ViewHolder {
        ImageButton btnAgregar;

        public AddSesionViewHolder(@NonNull View itemView) {
            super(itemView);
            btnAgregar = itemView.findViewById(R.id.btn_add_item); // 👈 este debe ser el ID del botón en
                                                                   // item_add_to_list.xml
        }
    }

    public void filter(String query, List<Song> allSongs) {
        sesionList.clear();
        if (query.isEmpty()) {
            sesionList.addAll(sesionListFull);
        } else {
            String filterPattern = query.toLowerCase().trim();
            for (Sesion sesion : sesionListFull) {
                // Check basic sesion info
                if (sesion.getNombre().toLowerCase().contains(filterPattern) ||
                        (sesion.getObjetivosCustom() != null
                                && sesion.getObjetivosCustom().toLowerCase().contains(filterPattern))
                        ||
                        (sesion.getObservaciones() != null
                                && sesion.getObservaciones().toLowerCase().contains(filterPattern))) {
                    sesionList.add(sesion);
                    continue; // Already added
                }

                // Check song names
                if (sesion.getCancionesIds() != null && allSongs != null) {
                    for (Integer songId : sesion.getCancionesIds()) {
                        for (Song song : allSongs) {
                            if (song.getId() == songId && song.getNombre().toLowerCase().contains(filterPattern)) {
                                sesionList.add(sesion);
                                break; // Song found, add session and move to next session
                            }
                        }
                    }
                }
            }
        }
        notifyDataSetChanged();
    }
}
