package com.example.intentoappdatosmusica;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SONG = 0;

    private final Map<Integer, Runnable> activeRunnables = new HashMap<>();
    private final Map<Integer, Handler> activeHandlers = new HashMap<>();

    private final ActivityResultLauncher<Intent> datosMusicalesLauncher;

    private List<Song> songList; // This will now be the filtered list
    private List<Song> songListFull; // This will hold the original, complete list
    private Context context;

    private int currentlyPlayingPosition = -1; // Indica qué canción está sonando (-1 significa ninguna)

    ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);
    ApiService audioService = ApiClient.getRetrofitForLargeTransfers().create(ApiService.class);

    private Map<String, EmocionDisponible> emocionesMap;

    public interface OnLinkSongListener {
        void onLinkSong(Song song);
    }

    public interface PaginationListener {
        void onPageChanged(int currentPage, int totalPages);
    }


    private OnLinkSongListener linkSongListener;
    private PaginationListener paginationListener;
    
    private List<Song> songListFiltered;
    private int currentPage = 1;
    private int itemsPerPage = 10;

    public void setPaginationListener(PaginationListener listener) {
        this.paginationListener = listener;
    }

    public void setOnLinkSongListener(OnLinkSongListener listener) {
        this.linkSongListener = listener;
    }

    public SongAdapter(Context context, List<Song> songList, ActivityResultLauncher<Intent> datosMusicalesLauncher, Map<String, EmocionDisponible> emocionesMap) {
        this.context = context;
        this.songListFull = new ArrayList<>(songList);
        this.songListFiltered = new ArrayList<>(songList);
        this.songList = new ArrayList<>();
        this.datosMusicalesLauncher = datosMusicalesLauncher;
        this.emocionesMap = emocionesMap;
        updatePaginatedList();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SongViewHolder songHolder = (SongViewHolder) holder;
        configurarCancion(songHolder, position);
    }

    private void configurarCancion(SongViewHolder holder, int songIndex) {
        if (holder != null) {
            MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance(); //obtener lista completa y actualizada de mediaplayers
            Song song = songList.get(songIndex);
            if (song != null) {
                activeHandlers.remove(song.getId());
                activeRunnables.remove(song.getId());
            }

            holder.tvSongName.setText(song.getNombre());
            holder.tvSongAuthor.setText(song.getAutor());

            int colorBordeInicial;
            if (!song.isLoaded()) {
                colorBordeInicial = ContextCompat.getColor(context, R.color.black); // canción no descargada
            } else {
                colorBordeInicial = calcularColorBorde(song,
                        MediaPlayerList.getInstance().getCurrentPosition(song.getId()));
            }
            holder.cardContainer.setStrokeColor(colorBordeInicial);

            // En BYOM, el archivo físico siempre es el enlace sanitizado.
            String filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + song.getSafeFileName();

            File audioFileCheck = new File(filePath);
            boolean fileIsValid = esAudioValido(audioFileCheck);

            if (!fileIsValid) {
                // Si está corrupto o vacío, lo borramos para evitar loops infinitos
                audioFileCheck.delete();
            }

            song.setLoaded(fileIsValid);

            //Log.e("songadapter: existe archivo", filePath + " = " + fileIsValid);

            // Configurar icono de botón play/pause según si el archivo existe
            // ✅ Siempre limpia el listener anterior
            holder.btnPlayPause.setOnClickListener(null);
            // ✅ Verifica si la canción está descargada (En BYOM significa que verificamos el filesystem base)
            boolean estaDescargada = song.isLoaded();

            // ✅ Determina qué ícono mostrar (Ya no consideramos descargas en progreso porque ya no se descarga nada)
            if (!estaDescargada) {
                // Se utiliza el icono de descargas (que ahora indica vinculación local)
                holder.btnPlayPause.setImageResource(R.drawable.cadena);
            } else if (mediaPlayerList.isPlaying(song.getId())) {
                holder.btnPlayPause.setImageResource(R.drawable.iconopause);
            } else {
                holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
            }

            // ✅ Asigna siempre el listener, independiente del estado
            holder.btnPlayPause.setOnClickListener(v -> manejarReproduccion(song, songIndex, holder));

            // Listener para el botón de modificar datos de canción
            holder.btnModifySongData.setOnClickListener(v -> {
                Context context = holder.itemView.getContext();
                Intent intent = new Intent(context, DatosMusicalesActivity.class); // Cambia a la clase de tu actividad
                intent.putExtra("song_id", song.getId()); // Pasamos el ID de la canción
                intent.putExtra("name", song.getNombre());
                intent.putExtra("author", song.getAutor());
                intent.putExtra("album", song.getAlbum());
                intent.putExtra("link", song.getEnlace());
                //intent.putExtra("isLoaded", song.isLoaded());
                intent.putExtra("isLoaded", MediaPlayerList.getInstance().isSongLoaded(song.getId()));
                intent.putExtra("currentPosition", MediaPlayerList.getInstance().getCurrentPosition(song.getId()));
                intent.putExtra("isPlaying", MediaPlayerList.getInstance().isPlaying(song.getId()));
                intent.putExtra("cg", song.getComentario_general());
                intent.putExtra("estado_cg", song.isEstadoCgPublicado());
                intent.putExtra("estado_cancion", song.isEstadoPublicado());
                intent.putExtra("secciones", new ArrayList<>(song.getSecciones()));
                datosMusicalesLauncher.launch(intent); // Usa el launcher en lugar de startActivity
            });

            holder.btnDeleteSong.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Eliminar canción")
                        .setMessage("¿Estás seguro de que deseas eliminar '" + song.getNombre() + "'?")
                        .setPositiveButton("Eliminar", (dialog, which) -> {
                            deleteSong(song, songIndex);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }); // ESTE CIERRE FALTABA

            // Se ha removido el bloque para "Detectar en tiempo real si alguna canción está descargándose"
            // puesto que BYOM asume disponibilidad local estricta o re-vinculación.

            MediaPlayer mediaPlayer;
            if (fileIsValid) { // Solo intenta obtener un MediaPlayer si el archivo existe
                mediaPlayer = mediaPlayerList.getMediaPlayer(song.getId(), filePath);
                metodoExtra(mediaPlayer, mediaPlayerList, holder, song); //Línea 216
            }
        }
    }

    private void metodoExtra(MediaPlayer mediaPlayer, MediaPlayerList mediaPlayerList, SongViewHolder holder, Song song) {
        File archivo = new File(context.getExternalFilesDir("media"), song.getSafeFileName());
        if (mediaPlayer == null || !archivo.exists()) {
            // Cancelar cualquier runnable anterior si existe
            if (holder.handler != null && holder.updateSeekBarRunnable != null) {
                holder.handler.removeCallbacks(holder.updateSeekBarRunnable);
            }

            // 👇 AGREGAR ESTO PARA EVITAR EL CRASH
            holder.seekBarProgress.setVisibility(View.INVISIBLE);
            holder.tvSongDuration.setText("--:-- / --:--");
            holder.btnPlayPause.setImageResource(R.drawable.cadena);
            return;
        }

        // Limpiar cualquier runnable existente
        if (holder.handler != null && holder.updateSeekBarRunnable != null) {
            holder.handler.removeCallbacks(holder.updateSeekBarRunnable);
        }

        holder.seekBarProgress.setTag(song.getId());
        holder.tvSongDuration.setTag(song.getId());

        holder.seekBarProgress.setMax(mediaPlayer.getDuration()); // Línea 238
        holder.seekBarProgress.setProgress(mediaPlayer.getCurrentPosition()); // Establecer progreso actual
        holder.tvSongDuration.setText(
                formatoTiempo(mediaPlayer.getCurrentPosition()) + " / " + formatoTiempo(mediaPlayer.getDuration()));

        // ✅ Mostrar thumb si la canción está cargada, ocultarlo si no
        if (song.isLoaded()) {
            holder.seekBarProgress.setVisibility(View.VISIBLE);
        } else {
            holder.seekBarProgress.setVisibility(View.INVISIBLE);
        }

        if (mediaPlayerList.isPlaying(song.getId())) {
            holder.btnPlayPause.setImageResource(R.drawable.iconopause);
        } else if (!mediaPlayerList.isPlaying(song.getId())) {
            holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
        } else if (!mediaPlayerList.isPlaying(song.getId()) && (formatoTiempo(mediaPlayerList.getCurrentPosition(song.getId())) + " / " + formatoTiempo(mediaPlayerList.getDuration(song.getId()))) == "00:00 / 00:00") {
            holder.btnPlayPause.setImageResource(R.drawable.cadena);
        }

        // Cancelar Runnable anterior para esta canción si existe
        Runnable oldRunnable = activeRunnables.get(song.getId());
        Handler oldHandler = activeHandlers.get(song.getId());

        if (oldHandler != null && oldRunnable != null) {
            oldHandler.removeCallbacks(oldRunnable);
        }

        // 🔁 Guardar handler y runnable para poder cancelarlos
        holder.handler = new Handler();
        holder.updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                // ⚠️ Asegurar que el tag no se ha reciclado
                Object tag = holder.seekBarProgress.getTag();
                if (tag == null || !(tag instanceof Integer) || (Integer) tag != song.getId()) {
                    return; // Vista reciclada, no actualizar este SeekBar
                }

                int currentPosition = mediaPlayerList.getCurrentPosition(song.getId());

                // 🎨 Actualizar borde SIEMPRE (tanto en play como en pausa)
                int colorBorde = calcularColorBorde(song, currentPosition);
                holder.cardContainer.setStrokeColor(colorBorde);

                if (mediaPlayerList.isPlaying(song.getId())) {
                    holder.seekBarProgress.setProgress(currentPosition);
                    holder.tvSongDuration.setText(
                            formatoTiempo(currentPosition) + " / " + formatoTiempo(mediaPlayerList.getDuration(song.getId())));
                    holder.btnPlayPause.setImageResource(R.drawable.iconopause);
                    holder.handler.postDelayed(this, 100);
                } else {
                    holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
                }
            }
        };
        holder.handler.post(holder.updateSeekBarRunnable);

        // Guardar en los mapas para futuras cancelaciones
        activeHandlers.put(song.getId(), holder.handler);
        activeRunnables.put(song.getId(), holder.updateSeekBarRunnable);

        // Escuchar cambios manuales en la barra de progreso
        holder.seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaPlayerList.seekTo(song.getId(), progress); // Permitir que el usuario busque la canción

                    // Actualizar el TextView con el tiempo actual del SeekBar, incluso si la canción está en pausa
                    holder.tvSongDuration.setText(formatoTiempo(progress) + " / " + formatoTiempo(mediaPlayerList.getDuration(song.getId())));

                    // 🎨 actualizar borde aunque esté en pausa
                    int colorBorde = calcularColorBorde(song, progress);
                    holder.cardContainer.setStrokeColor(colorBorde);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void manejarReproduccion(Song song, int songIndex, SongViewHolder holder) {
        MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance(); //obtener lista completa y actualizada de mediaplayers

        // Crear objeto RequestBody para enviar en formato Form-Data / JSON según backend
        // pero la interfaz en ApiService define Query/Params. Confirmemos la lectura limpia.
        // Aquí pediremos al servidor la descarga del archivo exacto usando su nombre
        String fileName = song.getSafeFileName();
        String filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName;

        // ✅ Verificar si el archivo realmente existe antes de cargarlo
        File audioFile = new File(filePath);
        if (!audioFile.exists()) { // si la canción localmente fue borrada o la base de datos migró
            new AlertDialog.Builder(context)
                    .setTitle("Archivo no encontrado")
                    .setMessage("El archivo de audio relacionado a esta canción no se encuentra en el dispositivo. ¿Deseas vincular un MP3 local nuevamente?")
                    .setPositiveButton("Vincular", (dialog, which) -> {
                        if (linkSongListener != null) {
                            linkSongListener.onLinkSong(song);
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        if (currentlyPlayingPosition == songIndex) {
            // Si la canción ya está en reproducción, pausarla
            mediaPlayerList.pause(song.getId());
            currentlyPlayingPosition = -1;
            holder.btnPlayPause.setImageResource(R.drawable.iconoplay); // ✅ cambiar solo el icono
        } else {
            if (currentlyPlayingPosition != -1) {
                int prevSongId = songList.get(currentlyPlayingPosition).getId();
                Handler oldHandler = activeHandlers.remove(prevSongId);
                Runnable oldRunnable = activeRunnables.remove(prevSongId);
                if (oldHandler != null && oldRunnable != null) {
                    oldHandler.removeCallbacks(oldRunnable);
                }

                int previousIndex = currentlyPlayingPosition;
                String previousLink = songList.get(previousIndex).getSafeFileName();
                String previousFilePath = context.getExternalFilesDir("media") + "/" + previousLink;

                // 🔹 Si la canción en reproducción es diferente y está descargada, cambiar su ícono
                if (new File(previousFilePath).exists()) {
                    mediaPlayerList.pause(songList.get(previousIndex).getId());
                    //notifyItemChanged(previousIndex);  // 🔹 Solo actualizar la canción anterior
                    // ⚡ Intentar obtener el ViewHolder visible de la canción anterior
                    RecyclerView.ViewHolder prevHolder =
                            ((RecyclerView) holder.itemView.getParent()).findViewHolderForAdapterPosition(currentlyPlayingPosition);

                    if (prevHolder instanceof SongViewHolder) {
                        ((SongViewHolder) prevHolder).btnPlayPause.setImageResource(R.drawable.iconoplay);
                    }
                }
            }
            // Iniciar reproducción de la canción seleccionada
            mediaPlayerList.pauseAllExcept(song.getId());
            mediaPlayerList.play(song.getId());
            currentlyPlayingPosition = songIndex;
            holder.btnPlayPause.setImageResource(R.drawable.iconopause); // ✅ cambiar solo el icono

        }
    }

    private void deleteSong(Song song, int position) {
        apiService.deleteSong(new DeleteSongRequest(song.getId())).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // Eliminar el archivo local
                    String filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + song.getSafeFileName();
                    File file = new File(filePath);
                    if (file.exists()) {
                        // Se ha retirado el audioFileCheck.delete() porque las validaciones de BYOM pueden tener asincronía
                        // y no queremos borrar los audios locales del usuario de forma destructiva por falsos negativos.
                    }

                    // Detener el MediaPlayer si se está reproduciendo
                    MediaPlayerList.getInstance().stopAndRelease(song.getId());

                    // Eliminar de las listas completas de datos para evitar que reaparezca al cambiar de página
                    songListFull.removeIf(s -> s.getId() == song.getId());
                    songListFiltered.removeIf(s -> s.getId() == song.getId());
                    updatePaginatedList();

                    Toast.makeText(context, "'" + song.getNombre() + "' eliminada", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Error al eliminar la canción", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(context, "Error de red", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public int findSongPositionById(int songId) {
        for (int i = 0; i < songList.size(); i++) {
            if (songList.get(i).getId() == songId) {
                return i;
            }
        }
        return -1; // No encontrada
    }





    private String formatoTiempo(int milisegundos) {
        int minutos = (milisegundos / 60000) % 60;
        int segundos = (milisegundos / 1000) % 60;
        return String.format("%02d:%02d", minutos, segundos);
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardContainer;

        TextView tvSongName, tvSongAuthor, tvSongDuration;
        ImageButton btnPlayPause, btnModifySongData, btnDeleteSong;
        SeekBar seekBarProgress;

        Handler handler;
        Runnable updateSeekBarRunnable;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);

            // Inicializar los componentes de item_song.xml
            cardContainer = itemView.findViewById(R.id.mi_cardview); // id en item_song.xml

            tvSongName = itemView.findViewById(R.id.tv_song_name);
            tvSongAuthor = itemView.findViewById(R.id.tv_song_author);
            tvSongDuration = itemView.findViewById(R.id.tv_song_duration);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            btnModifySongData = itemView.findViewById(R.id.btn_modify_song_data);
            btnDeleteSong = itemView.findViewById(R.id.btn_delete);
            seekBarProgress = itemView.findViewById(R.id.seekBar_progress);
        }
    }



    public void setSongList(List<Song> songList) {
        this.songListFull = new ArrayList<>(songList);
        this.songListFiltered = new ArrayList<>(songList);
        this.currentPage = 1;
        updatePaginatedList();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof SongViewHolder) {
            SongViewHolder songHolder = (SongViewHolder) holder;
            // Limpiar cualquier callback pendiente
            if (songHolder.handler != null && songHolder.updateSeekBarRunnable != null) {
                songHolder.handler.removeCallbacks(songHolder.updateSeekBarRunnable);
            }
            // Limpiar el listener del SeekBar para evitar fugas de memoria
            songHolder.seekBarProgress.setOnSeekBarChangeListener(null);
            songHolder.btnPlayPause.setOnClickListener(null);
            songHolder.btnPlayPause.setImageResource(R.drawable.iconodescargar); // Estado neutral
            songHolder.tvSongDuration.setText("00:00 / 00:00");
            songHolder.seekBarProgress.setVisibility(View.INVISIBLE);
        }
    }

    private int obtenerColorPorEmocion(EmocionDisponible data) {
        switch (data.getEmocionBase().toLowerCase()) {
            case "anger": // Ira
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.rojo_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.rojo_normal);
                return ContextCompat.getColor(context, R.color.rojo_oscuro);

            case "fear": // Miedo
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.turquesa_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.turquesa_normal);
                return ContextCompat.getColor(context, R.color.turquesa_oscuro);

            case "joy": // Alegría
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.amarillo_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.amarillo_normal);
                return ContextCompat.getColor(context, R.color.amarillo_oscuro);

            case "sadness": // Tristeza
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.morado_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.morado_normal);
                return ContextCompat.getColor(context, R.color.morado_oscuro);

            case "anticipation": // Anticipación
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.naranja_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.naranja_normal);
                return ContextCompat.getColor(context, R.color.naranja_oscuro);

            case "surprise": // Sorpresa
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.azul_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.azul_normal);
                return ContextCompat.getColor(context, R.color.azul_oscuro);

            case "trust": // Confianza
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.verde_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.verde_normal);
                return ContextCompat.getColor(context, R.color.verde_oscuro);

            case "disgust": // Asco
                if (data.getNivelArousal().equals("bajo")) return ContextCompat.getColor(context, R.color.rosado_claro);
                if (data.getNivelArousal().equals("medio")) return ContextCompat.getColor(context, R.color.rosado_normal);
                return ContextCompat.getColor(context, R.color.rosado_oscuro);

            default:
                return ContextCompat.getColor(context, R.color.gris_neutro);
        }
    }

    private int calcularColorBorde(Song song, int currentPosition) {
        if (!song.isLoaded()) {
            return ContextCompat.getColor(context, R.color.black);
        }
        Seccion seccion = song.getSeccionActual(currentPosition);
        if (seccion == null) {
            return ContextCompat.getColor(context, R.color.gris_neutro);
        }
        int colorFinal = ContextCompat.getColor(context, R.color.gris_neutro);
        for (EmocionSeleccionada emocionSel : seccion.getEmociones()) {
            EmocionDisponible data = emocionesMap.get(emocionSel.getPalabra().toLowerCase());
            if (data != null) {
                colorFinal = obtenerColorPorEmocion(data);
            }
        }
        return colorFinal;
    }

    private boolean esAudioValido(File file) {
        if (file == null || !file.exists()) return false;
        // Solo comprobamos que tenga un peso mínimo razonable (20 KB)
        // para esquivar archivos corruptos, saltándonos el rígido MetadataRetriever.
        return file.length() > 20 * 1024;
    }

    public void filter(String query) {
        songListFiltered.clear();
        if (query.isEmpty()) {
            songListFiltered.addAll(songListFull);
        } else {
            String filterPattern = query.toLowerCase().trim();
            for (Song song : songListFull) {
                // Check basic song info
                if (song.getNombre().toLowerCase().contains(filterPattern) ||
                    song.getAutor().toLowerCase().contains(filterPattern) ||
                    (song.getAlbum() != null && song.getAlbum().toLowerCase().contains(filterPattern)) ||
                    (song.getComentario_general() != null && song.getComentario_general().toLowerCase().contains(filterPattern))) {
                    songListFiltered.add(song);
                    continue; // Already added, no need to check sections
                }

                // Check sections info
                if (song.getSecciones() != null) {
                    for (Seccion seccion : song.getSecciones()) {
                        if ((seccion.getNombre() != null && seccion.getNombre().toLowerCase().contains(filterPattern)) ||
                            (seccion.getComentario() != null && seccion.getComentario().toLowerCase().contains(filterPattern))) {
                            songListFiltered.add(song);
                            break; // Found in a section, add the song and move to the next song
                        }
                    }
                }
            }
        }
        currentPage = 1;
        updatePaginatedList();
    }

    public void updatePaginatedList() {
        songList.clear();
        // Item virtual '+' cuenta como compensación, así que al paginar necesitamos abstraernos para la canción
        // Aseguramos incluir siempre el botón de agregar canción en el total visual, pero solo manejaremos los datos reales.
        int totalItems = songListFiltered.size();
        
        int start = (currentPage - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, totalItems);
        
        if (start < totalItems) {
            songList.addAll(songListFiltered.subList(start, end));
        }
        notifyDataSetChanged();
        
        if (paginationListener != null) {
            paginationListener.onPageChanged(currentPage, getTotalPages());
        }
    }

    public int getTotalPages() {
        int totalDatos = songListFiltered.size();
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
    
    public void setSongListFull(List<Song> newList) {
        this.songListFull = new ArrayList<>(newList);
        this.songListFiltered = new ArrayList<>(newList);
        this.currentPage = 1;
        updatePaginatedList();
    }
}
