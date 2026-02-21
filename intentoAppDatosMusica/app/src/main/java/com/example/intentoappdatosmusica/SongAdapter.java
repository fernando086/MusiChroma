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
    private static final int VIEW_TYPE_ADD_SONG = 1;

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

    public interface OnAddSongClickListener {
        void onAddSongClick();
    }

    private OnAddSongClickListener addSongClickListener;

    public void setOnAddSongClickListener(OnAddSongClickListener listener) {
        this.addSongClickListener = listener;
    }

    public SongAdapter(Context context, List<Song> songList, ActivityResultLauncher<Intent> datosMusicalesLauncher, Map<String, EmocionDisponible> emocionesMap) {
        this.context = context;
        this.songList = new ArrayList<>(songList);
        this.songListFull = new ArrayList<>(songList);
        this.datosMusicalesLauncher = datosMusicalesLauncher;
        this.emocionesMap = emocionesMap;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_ADD_SONG : VIEW_TYPE_SONG;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
                viewType == VIEW_TYPE_ADD_SONG ? R.layout.item_add_to_list : R.layout.item_song, parent, false);
        return viewType == VIEW_TYPE_ADD_SONG ? new AddSongViewHolder(view) : new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_ADD_SONG) {
            configurarBotonAgregar((AddSongViewHolder) holder); //el primer item del recyclerview es el botón
        } else {
            SongViewHolder songHolder = (SongViewHolder) holder; //el resto es para cada canción encontrada en la lista
            configurarCancion(songHolder,position - 1); //cada songHolder representa cada item_song.xml
        }
    }

    private void configurarBotonAgregar(AddSongViewHolder holder) {
        holder.btnAddSong.setOnClickListener(v -> {
            if (addSongClickListener != null) {
                addSongClickListener.onAddSongClick();
            }
        });
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

            // Verificar si el archivo realmente existe
            // Determinar si es un enlace de YouTube o un archivo local
            String fileName = getFilePath(song.getEnlace()); // Devuelve null si no es un enlace de YouTube
            String filePath;
            if (fileName == null) {
                // Es un archivo local, usar directamente el nombre del archivo
                filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + song.getEnlace();
            } else {
                // Es un enlace de YouTube, agregar extensión .mp3
                filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName + ".mp3";
            }

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

            // ✅ Verifica si la canción está descargada
            boolean estaDescargada = song.isLoaded();

            // ✅ Determina qué ícono mostrar
            if (mediaPlayerList.isDownloading(song.getId())) {
                holder.btnPlayPause.setImageResource(R.drawable.iconolocked); // Descargando
            } else if (!estaDescargada) {
                holder.btnPlayPause.setImageResource(R.drawable.iconodescargar); // No descargada
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
            });

            // Detectar en tiempo real si alguna canción está descargándose
            int expectedId = song.getId(); // Guardar el id esperado

            if (context instanceof LifecycleOwner) {
                MediaPlayerList.getInstance().getDownloadingStateLiveData().observe((LifecycleOwner) context, downloadingMap -> {
                    // 🔐 Validar si este holder aún representa al mismo song
                    int positionInAdapter = holder.getAdapterPosition();
                    if (positionInAdapter == RecyclerView.NO_POSITION || positionInAdapter >= getItemCount()) return;

                    Song currentSong = songList.get(positionInAdapter - 1); // -1 porque el primer ítem es el botón
                    if (currentSong.getId() != expectedId) return;

                    boolean isDownloading = MediaPlayerList.getInstance().isAnySongDownloading();
                    boolean shouldBeLocked = isDownloading && !currentSong.isLoaded();
                    holder.btnPlayPause.setEnabled(!shouldBeLocked);

                    if (shouldBeLocked) {
                        holder.btnPlayPause.setImageResource(R.drawable.iconolocked);
                    } else if (!currentSong.isLoaded()) {
                        holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
                    } else if (mediaPlayerList.isPlaying(currentSong.getId())) {
                        holder.btnPlayPause.setImageResource(R.drawable.iconopause);
                    } else {
                        holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
                    }
                });
            }

            MediaPlayer mediaPlayer;
            if (fileIsValid) { // Solo intenta obtener un MediaPlayer si el archivo existe
                mediaPlayer = mediaPlayerList.getMediaPlayer(song.getId(), filePath);
                metodoExtra(mediaPlayer, mediaPlayerList, holder, song); //Línea 216
            }
        }
    }

    private void metodoExtra(MediaPlayer mediaPlayer, MediaPlayerList mediaPlayerList, SongViewHolder holder, Song song) {
        File archivo = new File(context.getExternalFilesDir("media"), getFilePath(song.getEnlace()) == null ? song.getEnlace() : getFilePath(song.getEnlace()) + ".mp3");
        if (mediaPlayer == null || !archivo.exists()) {
            // Cancelar cualquier runnable anterior si existe
            if (holder.handler != null && holder.updateSeekBarRunnable != null) {
                holder.handler.removeCallbacks(holder.updateSeekBarRunnable);
            }

            // 👇 AGREGAR ESTO PARA EVITAR EL CRASH
            holder.seekBarProgress.setVisibility(View.INVISIBLE);
            holder.tvSongDuration.setText("--:-- / --:--");
            holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
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
            holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
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
                    holder.handler.postDelayed(this, 100);
                } /*else {
                    holder.cardContainer.setStrokeColor(ContextCompat.getColor(context, R.color.black));
                    //holder.cardContainer.setStrokeWidth(6);
                }*/
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

        // Determinar si es un enlace de YouTube o un archivo local
        String fileName = getFilePath(song.getEnlace()); // Devuelve null si no es un enlace de YouTube
        String filePath;
        if (fileName == null) {
            // Es un archivo local, usar directamente el nombre del archivo
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + song.getEnlace();
        } else {
            // Es un enlace de YouTube, agregar extensión .mp3
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName + ".mp3";
        }

        // ✅ Verificar si el archivo realmente existe antes de cargarlo
        File audioFile = new File(filePath);
        if (!audioFile.exists()) { // si la canción no está descargada
            if (!mediaPlayerList.isDownloading(song.getId())) { // si la canción no está descargándose ahora mismo
                // Bloquear el botón de play/pausa antes de descargar
                mediaPlayerList.setDownloading(song.getId(), true);
                mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                // Si el archivo no existe, mostrar icono de descarga
                holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
                Toast.makeText(context, "Descargando " + song.getNombre() + ", por favor espere.", Toast.LENGTH_SHORT).show();
                // Si no existe, primero llama a getAudio para obtener la URL
                if (fileName == null) {
                    // 📥 Es un archivo subido, usar getArchivo
                    ArchivoRequest request = new ArchivoRequest(song.getId());
                    audioService.getArchivo(request).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                File directory = new File(context.getExternalFilesDir(null), "media");
                                if (!directory.exists()) directory.mkdirs();
                                File archivoDestino = new File(directory, song.getEnlace());

                                new DownloadAudioTask(response.body().byteStream(), archivoDestino, song, holder, songIndex, mediaPlayerList).execute();
                            } else {
                                Log.e("API_ERROR", "No se pudo obtener el archivo subido");
                                mediaPlayerList.setDownloading(song.getId(), false);
                                mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            Log.e("API_ERROR", "Error en getArchivo", t);
                            mediaPlayerList.setDownloading(song.getId(), false);
                            mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                        }
                    });
                } else {
                    AudioRequest request = new AudioRequest(song.getEnlace());
                    audioService.getAudio(request).enqueue(new Callback<ResponseBody>() { // ✅ Retrofit recibe el stream
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                Log.e("AUDIO_STREAM", "Recibiendo datos de audio en streaming...");

                                try {
                                    // Crear archivo local en la carpeta correcta
                                    File directory = new File(context.getExternalFilesDir(null), "media");
                                    if (!directory.exists()) {
                                        directory.mkdirs();
                                    }
                                    File audioFile = new File(directory, getFilePath(song.getEnlace()) + ".mp3");

                                    // **Iniciar la descarga usando DownloadAudioTask**
                                    new DownloadAudioTask(response.body().byteStream(), audioFile, song, holder, songIndex, mediaPlayerList).execute();

                                } catch (Exception e) {
                                    Log.e("AUDIO_STREAM", "Error al iniciar la descarga", e);
                                }
                            } else {
                                Log.e("API_ERROR", "Error al obtener URL del audio: " + response.message());
                                mediaPlayerList.setDownloading(song.getId(), false); // 🔹 Desbloquear en caso de error
                                mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            Log.e("API_ERROR", "Falló la llamada a getAudio", t);
                            mediaPlayerList.setDownloading(song.getId(), false); // 🔹 Desbloquear en caso de error
                            mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                        }
                    });
                }
            }
            return;
        }

        if (currentlyPlayingPosition == songIndex) {
            // Si la canción ya está en reproducción, pausarla
            mediaPlayerList.pause(song.getId());
            currentlyPlayingPosition = -1;
            //QUITADO: notifyItemChanged(songIndex);  // 🔹 Solo actualizar el ícono de la canción actual
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
                String previousLink = songList.get(previousIndex).getEnlace();
                String previousFilePath;

                String fileNameMR = getFilePath(previousLink);
                if (fileNameMR == null) {
                    // Es un archivo local, usar directamente el nombre del archivo
                    previousFilePath = context.getExternalFilesDir("media") + "/" + previousLink;
                } else {
                    // Es un enlace de YouTube
                    previousFilePath = context.getExternalFilesDir("media") + "/" + fileNameMR + ".mp3";
                }

                // 🔹 Si la canción en reproducción es diferente y está descargada, cambiar su ícono
                if (new File(previousFilePath).exists()) {
                    mediaPlayerList.pause(songList.get(previousIndex).getId());
                    //notifyItemChanged(previousIndex);  // 🔹 Solo actualizar la canción anterior
                    // ⚡ Intentar obtener el ViewHolder visible de la canción anterior
                    RecyclerView.ViewHolder prevHolder =
                            ((RecyclerView) holder.itemView.getParent()).findViewHolderForAdapterPosition(currentlyPlayingPosition + 1);
                    // +1 porque la posición 0 es el botón "Agregar Canción"

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

            // Configurar el OnCompletionListener
            mediaPlayerList.setOnCompletionListener(song.getId(), mp -> {
                currentlyPlayingPosition = -1; // Reiniciar posición actual
                //QUITADO: notifyItemChanged(songIndex);  // 🔹 Solo actualizar la canción que terminó
                holder.btnPlayPause.setImageResource(R.drawable.iconoplay); // ✅ sin notifyItemChanged
            });
        }
    }

    private void deleteSong(Song song, int position) {
        apiService.deleteSong(new DeleteSongRequest(song.getId())).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // Eliminar el archivo local
                    String fileName = getFilePath(song.getEnlace());
                    String filePath;
                    if (fileName == null) {
                        filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + song.getEnlace();
                    } else {
                        filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName + ".mp3";
                    }
                    File file = new File(filePath);
                    if (file.exists()) {
                        file.delete();
                    }

                    // Detener el MediaPlayer si se está reproduciendo
                    MediaPlayerList.getInstance().stopAndRelease(song.getId());

                    // Eliminar de la lista y notificar al adaptador
                    songList.remove(position);
                    notifyItemRemoved(position + 1);
                    notifyItemRangeChanged(position + 1, songList.size());

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
                return i + 1; // +1 porque la posición 0 es el botón "Add Song"
            }
        }
        return -1; // No encontrada
    }

    private class DownloadAudioTask extends AsyncTask<Void, Void, Boolean> {
        private InputStream inputStream;
        private File audioFile;
        private Song song;
        private SongViewHolder holder;
        private int position;
        private MediaPlayerList mediaPlayerList;

        public DownloadAudioTask(InputStream inputStream, File audioFile, Song song, SongViewHolder holder, int position, MediaPlayerList mediaPlayerList) {
            this.inputStream = inputStream;
            this.audioFile = audioFile;
            this.song = song;
            this.holder = holder;
            this.position = position;
            this.mediaPlayerList = mediaPlayerList;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                FileOutputStream outputStream = new FileOutputStream(audioFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                outputStream.close();
                inputStream.close();
                return true;
            } catch (Exception e) {
                Log.e("DownloadAudioTask", "Error durante la descarga del archivo de audio", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            MediaPlayerList.getInstance().setDownloading(song.getId(), false);
            MediaPlayerList.getInstance().updateButtonState(holder.btnPlayPause, song.getId());

            // Verificar el tamaño del archivo manualmente
            boolean archivoDescargado = audioFile.exists() && audioFile.length() > 0;

            Log.e("songAdapter", "success = " + success);
            Log.e("songAdapter", "Archivo existe = " + archivoDescargado);
            Log.e("songAdapter", "Tamaño del archivo = " + audioFile.length());

            if (archivoDescargado) { // ✅ Solo verificamos si el archivo realmente existe
                Log.d("onPostExecute: ", "Descargado");
                //Log.d("onPostExecute: ", "Archivo a limpiar: " + audioFile.getAbsolutePath());
                Toast.makeText(context, "Descarga completa: " + song.getNombre(), Toast.LENGTH_SHORT).show();
                holder.btnPlayPause.setImageResource(R.drawable.iconoplay);

                // 🔹 Después de descargar, actualizar el estado en la lista de canciones
                for (Song song : songList) {
                    if (song.getEnlace().equals(song.getEnlace())) { // Compara con el enlace de la canción
                        song.setLoaded(true); // Marcar como descargado
                        break;
                    }
                }

                // 🔹 Actualizar UI en RecyclerView
                notifyItemChanged(position);

                // ✅ Notificar a LiveData que la canción se ha descargado
                MediaPlayerList.getInstance().notifySongStateChanged(song.getId());

            } else {
                Log.e("DownloadAudioTask", "Error al descargar el archivo de audio, ProtocolException seguramente.");
                Toast.makeText(context, "Error en la descarga de " + song.getNombre(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getFilePath(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.isEmpty()) return null;

        // Extraer los 11 caracteres del ID del video
        String videoId = "";
        Pattern pattern = Pattern.compile("v=([a-zA-Z0-9_-]{11})|/videos/([a-zA-Z0-9_-]{11})|embed/([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})|/v/([a-zA-Z0-9_-]{11})|/e/([a-zA-Z0-9_-]{11})|watch\\?v=([a-zA-Z0-9_-]{11})|/shorts/([a-zA-Z0-9_-]{11})|/live/([a-zA-Z0-9_-]{11})");
        Matcher matcher = pattern.matcher(youtubeUrl);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i); // Retorna el primer grupo no nulo (ID del video)
                }
            }
        }

        if (videoId.isEmpty()) return null;

        // Construir la ruta del archivo
        return videoId;
    }

    private String formatoTiempo(int milisegundos) {
        int minutos = (milisegundos / 60000) % 60;
        int segundos = (milisegundos / 1000) % 60;
        return String.format("%02d:%02d", minutos, segundos);
    }

    @Override
    public int getItemCount() {
        return songList.size() + 1;
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

    // ViewHolder para el botón de agregar canción
    public class AddSongViewHolder extends RecyclerView.ViewHolder {

        ImageButton btnAddSong;

        public AddSongViewHolder(View itemView) {
            super(itemView);
            btnAddSong = itemView.findViewById(R.id.btn_add_item);
        }
    }

    public void setSongList(List<Song> songList) {
        this.songList = new ArrayList<>(songList);
        this.songListFull = new ArrayList<>(songList);
        notifyDataSetChanged();
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

        // 1️⃣ Verificar tamaño mínimo (por ejemplo > 50KB)
        if (file.length() < 50 * 1024) return false;

        // 2️⃣ Intentar extraer duración usando MediaMetadataRetriever
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();

            // Si no tiene duración, está corrupto
            if (durationStr == null) return false;

            long durationMs = Long.parseLong(durationStr);
            return durationMs > 0; // duración válida
        } catch (Exception e) {
            return false;
        }
    }

    public void filter(String query) {
        songList.clear();
        if (query.isEmpty()) {
            songList.addAll(songListFull);
        } else {
            String filterPattern = query.toLowerCase().trim();
            for (Song song : songListFull) {
                // Check basic song info
                if (song.getNombre().toLowerCase().contains(filterPattern) ||
                    song.getAutor().toLowerCase().contains(filterPattern) ||
                    (song.getAlbum() != null && song.getAlbum().toLowerCase().contains(filterPattern)) ||
                    (song.getComentario_general() != null && song.getComentario_general().toLowerCase().contains(filterPattern))) {
                    songList.add(song);
                    continue; // Already added, no need to check sections
                }

                // Check sections info
                if (song.getSecciones() != null) {
                    for (Seccion seccion : song.getSecciones()) {
                        if ((seccion.getNombre() != null && seccion.getNombre().toLowerCase().contains(filterPattern)) ||
                            (seccion.getComentario() != null && seccion.getComentario().toLowerCase().contains(filterPattern))) {
                            songList.add(song);
                            break; // Found in a section, add the song and move to the next song
                        }
                    }
                }
            }
        }
        notifyDataSetChanged();
    }
}
