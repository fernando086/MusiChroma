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



    private Context context;
    private List<Sesion> sesionList;
    private List<Sesion> sesionListFull;

    private ApiService apiService;



    // ✅ Listeners asignables opcionalmente
    public interface OnSesionAccionClickListener {
        void onClick(View v, Sesion sesion);
    }
    
    private OnSesionAccionClickListener onIngresarClickListener;
    private OnSesionAccionClickListener onCambiarColorClickListener;
    private View.OnClickListener onFavoritoClickListener;
    
    public interface PaginationListener {
        void onPageChanged(int currentPage, int totalPages);
    }
    private PaginationListener paginationListener;
    
    private List<Sesion> sesionListFiltered;
    private int currentPage = 1;
    private int itemsPerPage = 10;

    public void setPaginationListener(PaginationListener listener) {
        this.paginationListener = listener;
    }

    public SesionAdapter(Context context, List<Sesion> sesionList) {
        this.context = context;
        this.sesionListFull = new ArrayList<>(sesionList);
        this.sesionListFiltered = new ArrayList<>(sesionList);
        this.sesionList = new ArrayList<>();
        this.apiService = ApiClient.getRetrofitInstance().create(ApiService.class);
        updatePaginatedList();
    }



    public void setOnIngresarClickListener(OnSesionAccionClickListener listener) {
        this.onIngresarClickListener = listener;
    }

    public void setOnCambiarColorClickListener(OnSesionAccionClickListener listener) {
        this.onCambiarColorClickListener = listener;
    }

    public void setOnFavoritoClickListener(View.OnClickListener listener) {
        this.onFavoritoClickListener = listener;
    }

    public void setSesionList(List<Sesion> nuevaLista) {
        this.sesionListFull = new ArrayList<>(nuevaLista);
        this.sesionListFiltered = new ArrayList<>(nuevaLista);
        this.currentPage = 1;
        updatePaginatedList();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sesion, parent, false);
        return new SesionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SesionViewHolder sesionHolder = (SesionViewHolder) holder;
        Sesion sesion = sesionList.get(position);

        sesionHolder.txtTitulo.setText(sesion.getNumeroSesion() + " - " + sesion.getNombre());
        sesionHolder.txtFechas.setText(sesion.getFechaHoraInicio() + " → " + sesion.getFechaHoraFinal());
        // Activar marquee luego de un pequeño delay
        sesionHolder.txtFechas.postDelayed(() -> {
            sesionHolder.txtFechas.setSelected(true); // Necesario para activar marquee
        }, 2000); // Espera 2 segundos antes de empezar a desplazarse

        sesionHolder.txtEstrellas.setText(String.valueOf(sesion.getEstrellas()));
        sesionHolder.txtCanciones.setText(String.valueOf(sesion.getCantidadCanciones()));

        if (onIngresarClickListener != null) {
            sesionHolder.btnIngresar.setOnClickListener(v -> onIngresarClickListener.onClick(v, sesion));
        }

        if (onCambiarColorClickListener != null)
            sesionHolder.btnCambiarColor.setOnClickListener(v -> onCambiarColorClickListener.onClick(v, sesion));

        sesionHolder.btnEliminar.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Eliminar sesión")
                    .setMessage("¿Estás seguro de que deseas eliminar la sesión '" + sesion.getNombre() + "'?")
                    .setPositiveButton("Eliminar", (dialog, which) -> {
                        deleteSesion(sesion, position);
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

    private void deleteSesion(Sesion sesion, int position) {
        apiService.deleteSesion(new DeleteSesionRequest(sesion.getId())).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    sesionListFull.removeIf(s -> s.getId() == sesion.getId());
                    sesionListFiltered.removeIf(s -> s.getId() == sesion.getId());
                    updatePaginatedList();
                    Toast.makeText(context, "Sesión eliminada correctamente", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Error al eliminar la sesión", Toast.LENGTH_SHORT).show();
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
        return sesionList != null ? sesionList.size() : 0;
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



    public void filter(String query, List<Song> allSongs) {
        sesionListFiltered.clear();
        if (query.isEmpty()) {
            sesionListFiltered.addAll(sesionListFull);
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
                    sesionListFiltered.add(sesion);
                    continue; // Already added
                }

                // Check song names
                if (sesion.getCancionesIds() != null && allSongs != null) {
                    for (Integer songId : sesion.getCancionesIds()) {
                        for (Song song : allSongs) {
                            if (song.getId() == songId && song.getNombre().toLowerCase().contains(filterPattern)) {
                                sesionListFiltered.add(sesion);
                                break; // Song found, add session and move to next session
                            }
                        }
                    }
                }
            }
        }
        currentPage = 1;
        updatePaginatedList();
    }

    public void updatePaginatedList() {
        sesionList.clear();
        int totalItems = sesionListFiltered.size();
        
        int start = (currentPage - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, totalItems);
        
        if (start < totalItems) {
            sesionList.addAll(sesionListFiltered.subList(start, end));
        }
        notifyDataSetChanged();
        
        if (paginationListener != null) {
            paginationListener.onPageChanged(currentPage, getTotalPages());
        }
    }

    public int getTotalPages() {
        int totalDatos = sesionListFiltered.size();
        if (totalDatos == 0) return 1;
        return (int) Math.ceil((double) totalDatos / itemsPerPage);
    }

    public void nextPage() {
        if (currentPage < getTotalPages()) {
            currentPage++;
            updatePaginatedList();
        }
    }

    public void prevPage() {
        if (currentPage > 1) {
            currentPage--;
            updatePaginatedList();
        }
    }
    
    public int getCurrentPage() {
        return currentPage;
    }

    public void actualizarColorSesionGlobal(int sesionId, int nuevoColor) {
        for (Sesion s : sesionListFull) if (s.getId() == sesionId) s.setColor(nuevoColor);
        for (Sesion s : sesionListFiltered) if (s.getId() == sesionId) s.setColor(nuevoColor);
        for (int i=0; i<sesionList.size(); i++) {
             if (sesionList.get(i).getId() == sesionId) {
                 sesionList.get(i).setColor(nuevoColor);
                 notifyItemChanged(i);
             }
        }
    }
}
