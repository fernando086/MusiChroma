package com.example.intentoappdatosmusica;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CancionSeleccionAdapter extends RecyclerView.Adapter<CancionSeleccionAdapter.SongSelectionViewHolder> {

    private final Map<Integer, Runnable> activeRunnables = new HashMap<>();
    private final Map<Integer, Handler> activeHandlers = new HashMap<>();

    private List<Song> songList, listaCompletaOriginal;
    private List<Integer> idsSeleccionados;
    private Context context;

    private List<String> emocionesSeleccionadasActuales = new ArrayList<>();

    private int currentlyPlayingPosition = -1;

    ApiService audioService = ApiClient.getRetrofitForLargeTransfers().create(ApiService.class);

    private Map<String, EmocionDisponible> emocionesMap;

    private boolean soloSeleccionados = false;

    public void setSoloSeleccionados(boolean solo) {
        soloSeleccionados = solo;
        aplicarFiltrosCombinados();
    }

    public void filtrarPorEmociones(List<String> emocionesSeleccionadas) {
        emocionesSeleccionadasActuales = emocionesSeleccionadas != null
                ? new ArrayList<>(emocionesSeleccionadas)
                : new ArrayList<>();
        aplicarFiltrosCombinados();
    }

    private void aplicarFiltrosCombinados() {
        if (listaCompletaOriginal == null) return;

        List<Song> filtradas = new ArrayList<>();

        for (Song s : listaCompletaOriginal) {

            // 🔹 Filtro 1: si está activado "solo seleccionados"
            if (soloSeleccionados && !idsSeleccionados.contains(s.getId())) {
                continue;
            }

            // 🔹 Filtro 2: si hay emociones seleccionadas
            if (emocionesSeleccionadasActuales != null && !emocionesSeleccionadasActuales.isEmpty()) {
                boolean coincide = false;

                if (s.getSecciones() != null) {
                    for (Seccion sec : s.getSecciones()) {
                        if (sec.getEmociones() == null) continue;

                        for (EmocionSeleccionada emo : sec.getEmociones()) {
                            if (emo == null || emo.getPalabra() == null) continue;

                            EmocionDisponible data = emocionesMap.get(emo.getPalabra().toLowerCase());
                            if (data != null) {
                                String emocionBase = data.getEmocionBase().toLowerCase();
                                for (String seleccionada : emocionesSeleccionadasActuales) {
                                    if (emocionBase.equalsIgnoreCase(seleccionada)) {
                                        coincide = true;
                                        break;
                                    }
                                }
                            }

                            if (coincide) break;
                        }
                        if (coincide) break;
                    }
                }

                if (!coincide) continue; // Si no coincide con las emociones, se salta
            }

            // Si pasa ambos filtros, se añade
            filtradas.add(s);
        }

        songList = filtradas;
        notifyDataSetChanged();

        Log.d("FiltroCombinado", "Canciones visibles: " + songList.size());
    }

    public CancionSeleccionAdapter(Context context, List<Song> songList, List<Integer> idsPreseleccionados, Map<String, EmocionDisponible> emocionesMap) {
        this.context = context;
        this.songList = songList;
        this.listaCompletaOriginal = new ArrayList<>(songList);
        this.idsSeleccionados = new ArrayList<>(idsPreseleccionados);
        this.emocionesMap = emocionesMap;
    }

    @NonNull
    @Override
    public SongSelectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song_breve, parent, false);
        return new SongSelectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongSelectionViewHolder holder, int position) {
        configurarCancion(holder, position);
    }

    private void configurarCancion(SongSelectionViewHolder holder, int songIndex) {
        if (holder != null) {
            MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();
            Song song = songList.get(songIndex);
            if (song != null) {
                activeHandlers.remove(song.getId());
                activeRunnables.remove(song.getId());
            }

            // Configurar checkbox de selección
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(idsSeleccionados.contains(song.getId()));
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!idsSeleccionados.contains(song.getId())) {
                        idsSeleccionados.add(song.getId());
                    }
                } else {
                    idsSeleccionados.remove(Integer.valueOf(song.getId()));
                }

                if (soloSeleccionados) {
                    aplicarFiltrosCombinados(); // 🔥 Esto hace que desaparezca de inmediato si ya no debe mostrarse
                }
            });

            // Configurar datos de la canción
            holder.tvSongName.setText(song.getNombre());
            holder.tvSongAuthor.setText(song.getAutor());

            // Activar marquee solo si aún no está activo
            if (!holder.tvSongName.isSelected()) {
                holder.tvSongName.setSelected(true); // ✅ Actívalo sin delay
            }

            int colorBordeInicial;
            if (!song.isLoaded()) {
                colorBordeInicial = ContextCompat.getColor(context, R.color.black);
            } else {
                colorBordeInicial = calcularColorBorde(song,
                        MediaPlayerList.getInstance().getCurrentPosition(song.getId()));
            }
            holder.cardContainer.setStrokeColor(colorBordeInicial);

            // Verificar si el archivo existe
            String fileName = getFilePath(song.getEnlace());
            String filePath;
            if (fileName == null) {
                filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + song.getEnlace();
            } else {
                filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName + ".mp3";
            }

            File audioFileCheck = new File(filePath);
            boolean fileIsValid = esAudioValido(audioFileCheck);

            if (!fileIsValid) {
                audioFileCheck.delete();
            }

            song.setLoaded(fileIsValid);

            // Configurar botón play/pause
            holder.btnPlayPause.setOnClickListener(null);

            boolean estaDescargada = song.isLoaded();

            if (mediaPlayerList.isDownloading(song.getId())) {
                holder.btnPlayPause.setImageResource(R.drawable.iconolocked);
            } else if (!estaDescargada) {
                holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
            } else if (mediaPlayerList.isPlaying(song.getId())) {
                holder.btnPlayPause.setImageResource(R.drawable.iconopause);
            } else {
                holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
            }

            holder.btnPlayPause.setOnClickListener(v -> manejarReproduccion(song, songIndex, holder));

            // Click en toda la tarjeta para seleccionar/deseleccionar
            holder.cardContainer.setOnClickListener(v -> {
                holder.checkBox.setChecked(!holder.checkBox.isChecked());
            });

            // Detectar descargas en tiempo real
            int expectedId = song.getId();

            if (context instanceof LifecycleOwner) {
                MediaPlayerList.getInstance().getDownloadingStateLiveData().observe((LifecycleOwner) context, downloadingMap -> {
                    int positionInAdapter = holder.getAdapterPosition();
                    if (positionInAdapter == RecyclerView.NO_POSITION || positionInAdapter >= getItemCount()) return;

                    Song currentSong = songList.get(positionInAdapter);
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
            if (fileIsValid) {
                mediaPlayer = mediaPlayerList.getMediaPlayer(song.getId(), filePath);
                metodoExtra(mediaPlayer, mediaPlayerList, holder, song);
            }
        }
    }

    private void metodoExtra(MediaPlayer mediaPlayer, MediaPlayerList mediaPlayerList, SongSelectionViewHolder holder, Song song) {
        File archivo = new File(context.getExternalFilesDir("media"), getFilePath(song.getEnlace()) == null ? song.getEnlace() : getFilePath(song.getEnlace()) + ".mp3");
        if (mediaPlayer == null || !archivo.exists()) {
            if (holder.handler != null && holder.updateSeekBarRunnable != null) {
                holder.handler.removeCallbacks(holder.updateSeekBarRunnable);
            }

            holder.seekBarProgress.setVisibility(View.INVISIBLE);
            holder.tvSongDuration.setText("--:-- / --:--");
            holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
            return;
        }

        if (holder.handler != null && holder.updateSeekBarRunnable != null) {
            holder.handler.removeCallbacks(holder.updateSeekBarRunnable);
        }

        holder.seekBarProgress.setTag(song.getId());
        holder.tvSongDuration.setTag(song.getId());

        holder.seekBarProgress.setMax(mediaPlayer.getDuration());
        holder.seekBarProgress.setProgress(mediaPlayer.getCurrentPosition());
        holder.tvSongDuration.setText(
                formatoTiempo(mediaPlayer.getCurrentPosition()) + " / " + formatoTiempo(mediaPlayer.getDuration()));

        if (song.isLoaded()) {
            holder.seekBarProgress.setVisibility(View.VISIBLE);
        } else {
            holder.seekBarProgress.setVisibility(View.INVISIBLE);
        }

        if (mediaPlayerList.isPlaying(song.getId())) {
            holder.btnPlayPause.setImageResource(R.drawable.iconopause);
        } else if (!mediaPlayerList.isPlaying(song.getId())) {
            holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
        }

        Runnable oldRunnable = activeRunnables.get(song.getId());
        Handler oldHandler = activeHandlers.get(song.getId());

        if (oldHandler != null && oldRunnable != null) {
            oldHandler.removeCallbacks(oldRunnable);
        }

        holder.handler = new Handler();
        holder.updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                Object tag = holder.seekBarProgress.getTag();
                if (tag == null || !(tag instanceof Integer) || (Integer) tag != song.getId()) {
                    return;
                }

                int currentPosition = mediaPlayerList.getCurrentPosition(song.getId());
                String nuevoTexto = formatoTiempo(currentPosition) + " / " + formatoTiempo(mediaPlayerList.getDuration(song.getId()));

                int nuevoColor = calcularColorBorde(song, currentPosition);

                // ✅ Solo actualizar si realmente cambia
                if (holder.cardContainer.getStrokeColor() != nuevoColor) {
                    holder.cardContainer.setStrokeColor(nuevoColor);
                }

                if (mediaPlayerList.isPlaying(song.getId())) {
                    holder.seekBarProgress.setProgress(currentPosition);
                    if (!holder.tvSongDuration.getText().toString().equals(nuevoTexto)) {
                        holder.tvSongDuration.setText(nuevoTexto); // ✅ Solo actualiza si es distinto
                    }
                }

                holder.handler.postDelayed(this, 100);
            }
        };
        holder.handler.post(holder.updateSeekBarRunnable);

        activeHandlers.put(song.getId(), holder.handler);
        activeRunnables.put(song.getId(), holder.updateSeekBarRunnable);

        holder.seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaPlayerList.seekTo(song.getId(), progress);
                    holder.tvSongDuration.setText(formatoTiempo(progress) + " / " + formatoTiempo(mediaPlayerList.getDuration(song.getId())));
                    int colorBorde = calcularColorBorde(song, progress);
                    holder.cardContainer.setStrokeColor(colorBorde);
                    Log.d("DEBUG_MARQUEE", "SeekBar movido en canción ID=" + song.getId());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void manejarReproduccion(Song song, int songIndex, SongSelectionViewHolder holder) {
        MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();

        String fileName = getFilePath(song.getEnlace());
        String filePath;
        if (fileName == null) {
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + song.getEnlace();
        } else {
            filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/" + fileName + ".mp3";
        }

        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            if (!mediaPlayerList.isDownloading(song.getId())) {
                mediaPlayerList.setDownloading(song.getId(), true);
                // ✅ ACTUALIZAR: Notificar a todas las canciones que están bloqueadas
                MediaPlayerList.getInstance().notifySongStateChanged(song.getId());
                //mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
                Toast.makeText(context, "Descargando " + song.getNombre() + ", por favor espere.", Toast.LENGTH_SHORT).show();

                if (fileName == null) {
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
                    audioService.getAudio(request).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                try {
                                    File directory = new File(context.getExternalFilesDir(null), "media");
                                    if (!directory.exists()) {
                                        directory.mkdirs();
                                    }
                                    File audioFile = new File(directory, getFilePath(song.getEnlace()) + ".mp3");
                                    new DownloadAudioTask(response.body().byteStream(), audioFile, song, holder, songIndex, mediaPlayerList).execute();
                                } catch (Exception e) {
                                    Log.e("AUDIO_STREAM", "Error al iniciar la descarga", e);
                                }
                            } else {
                                Log.e("API_ERROR", "Error al obtener URL del audio: " + response.message());
                                mediaPlayerList.setDownloading(song.getId(), false);
                                mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            Log.e("API_ERROR", "Falló la llamada a getAudio", t);
                            mediaPlayerList.setDownloading(song.getId(), false);
                            mediaPlayerList.updateButtonState(holder.btnPlayPause, song.getId());
                        }
                    });
                }
            }
            return;
        }

        if (currentlyPlayingPosition == songIndex) {
            mediaPlayerList.pause(song.getId());
            currentlyPlayingPosition = -1;
            holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
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
                    previousFilePath = context.getExternalFilesDir("media") + "/" + previousLink;
                } else {
                    previousFilePath = context.getExternalFilesDir("media") + "/" + fileNameMR + ".mp3";
                }

                if (new File(previousFilePath).exists()) {
                    mediaPlayerList.pause(songList.get(previousIndex).getId());
                    RecyclerView.ViewHolder prevHolder =
                            ((RecyclerView) holder.itemView.getParent()).findViewHolderForAdapterPosition(currentlyPlayingPosition);

                    if (prevHolder instanceof SongSelectionViewHolder) {
                        ((SongSelectionViewHolder) prevHolder).btnPlayPause.setImageResource(R.drawable.iconoplay);
                    }
                }
            }

            mediaPlayerList.pauseAllExcept(song.getId());
            mediaPlayerList.play(song.getId());
            currentlyPlayingPosition = songIndex;
            holder.btnPlayPause.setImageResource(R.drawable.iconopause);

            mediaPlayerList.setOnCompletionListener(song.getId(), mp -> {
                currentlyPlayingPosition = -1;
                holder.btnPlayPause.setImageResource(R.drawable.iconoplay);
            });
        }
    }

    private class DownloadAudioTask extends AsyncTask<Void, Void, Boolean> {
        private InputStream inputStream;
        private File audioFile;
        private Song song;
        private SongSelectionViewHolder holder;
        private int position;
        private MediaPlayerList mediaPlayerList;

        public DownloadAudioTask(InputStream inputStream, File audioFile, Song song, SongSelectionViewHolder holder, int position, MediaPlayerList mediaPlayerList) {
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
            //MediaPlayerList.getInstance().updateButtonState(holder.btnPlayPause, song.getId());
            MediaPlayerList.getInstance().notifySongStateChanged(song.getId());

            boolean archivoDescargado = audioFile.exists() && audioFile.length() > 0;

            if (archivoDescargado) {
                Toast.makeText(context, "Descarga completa: " + song.getNombre(), Toast.LENGTH_SHORT).show();
                holder.btnPlayPause.setImageResource(R.drawable.iconoplay);

                for (Song s : songList) {
                    if (s.getId() == song.getId()) {
                        s.setLoaded(true);
                        break;
                    }
                }

                notifyItemChanged(position);
                MediaPlayerList.getInstance().notifySongStateChanged(song.getId());

            } else {
                Log.e("DownloadAudioTask", "Error al descargar el archivo de audio");
                Toast.makeText(context, "Error en la descarga de " + song.getNombre(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Métodos auxiliares (copiados de SongAdapter)
    private String getFilePath(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.isEmpty()) return null;

        Pattern pattern = Pattern.compile("v=([a-zA-Z0-9_-]{11})|/videos/([a-zA-Z0-9_-]{11})|embed/([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})|/v/([a-zA-Z0-9_-]{11})|/e/([a-zA-Z0-9_-]{11})|watch\\?v=([a-zA-Z0-9_-]{11})|/shorts/([a-zA-Z0-9_-]{11})|/live/([a-zA-Z0-9_-]{11})");
        Matcher matcher = pattern.matcher(youtubeUrl);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i);
                }
            }
        }
        return null;
    }

    private String formatoTiempo(int milisegundos) {
        int minutos = (milisegundos / 60000) % 60;
        int segundos = (milisegundos / 1000) % 60;
        return String.format("%02d:%02d", minutos, segundos);
    }

    private boolean esAudioValido(File file) {
        if (file == null || !file.exists()) return false;
        if (file.length() < 50 * 1024) return false;

        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();

            if (durationStr == null) return false;
            long durationMs = Long.parseLong(durationStr);
            return durationMs > 0;
        } catch (Exception e) {
            return false;
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

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public List<Integer> getIdsSeleccionados() {
        return idsSeleccionados;
    }

    public void setSongList(List<Song> songList) {
        this.songList = songList;
    }

    @Override
    public void onViewRecycled(@NonNull SongSelectionViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.handler != null && holder.updateSeekBarRunnable != null) {
            holder.handler.removeCallbacks(holder.updateSeekBarRunnable);
        }
        holder.seekBarProgress.setOnSeekBarChangeListener(null);
        holder.btnPlayPause.setOnClickListener(null);
        holder.btnPlayPause.setImageResource(R.drawable.iconodescargar);
        holder.tvSongDuration.setText("00:00 / 00:00");
        holder.seekBarProgress.setVisibility(View.INVISIBLE);
    }

    // ViewHolder para canción con selección
    public static class SongSelectionViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardContainer;
        TextView tvSongName, tvSongAuthor, tvSongDuration;
        ImageButton btnPlayPause;
        SeekBar seekBarProgress;
        CheckBox checkBox;

        Handler handler;
        Runnable updateSeekBarRunnable;

        public SongSelectionViewHolder(@NonNull View itemView) {
            super(itemView);

            // Inicializar los componentes de item_song.xml
            cardContainer = itemView.findViewById(R.id.mi_cardview_breve); // id en item_song.xml

            tvSongName = itemView.findViewById(R.id.tv_song_name_breve);
            tvSongAuthor = itemView.findViewById(R.id.tv_song_author_breve);
            tvSongDuration = itemView.findViewById(R.id.tv_song_duration_breve);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause_breve);
            seekBarProgress = itemView.findViewById(R.id.seekBar_progress_breve);
            checkBox = itemView.findViewById(R.id.cbSeleccionar);
        }
    }
}