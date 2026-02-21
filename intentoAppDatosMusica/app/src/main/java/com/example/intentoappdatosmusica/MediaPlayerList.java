package com.example.intentoappdatosmusica;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageButton;

import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MediaPlayerList {
    private static MediaPlayerList instance;
    private Map<Integer, MediaPlayer> mediaPlayerMap;
    private Map<Integer, Integer> progressMap; // Guarda el progreso de cada canción
    private MutableLiveData<Integer> currentSongIdLiveData; // Notifica cambios en el estado
    private Map<Integer, Boolean> downloadingMap; // 🔹 Nueva variable
    private MutableLiveData<Map<Integer, Boolean>> downloadingStateLiveData; // 🔹 Nueva variable para sincronizar descargas
    private Map<Integer, MutableLiveData<Integer>> songProgressLiveDataMap = new HashMap<>();

    public MutableLiveData<Integer> getSongProgressLiveData(int songId) {
        if (!songProgressLiveDataMap.containsKey(songId)) {
            songProgressLiveDataMap.put(songId, new MutableLiveData<>());
        }
        return songProgressLiveDataMap.get(songId);
    }

    private MediaPlayerList() {
        mediaPlayerMap = new HashMap<>();
        progressMap = new HashMap<>();
        downloadingMap = new HashMap<>(); // 🔹 Inicializar
        currentSongIdLiveData = new MutableLiveData<>();
        downloadingStateLiveData = new MutableLiveData<>(new HashMap<>());
    }

    public static synchronized MediaPlayerList getInstance() {
        if (instance == null) {
            instance = new MediaPlayerList();
        }
        return instance;
    }

    public void setDownloading(int songId, boolean isDownloading) {
        downloadingMap.put(songId, isDownloading);
        Map<Integer, Boolean> currentState = new HashMap<>(downloadingMap);
        downloadingStateLiveData.postValue(currentState);
    }

    public boolean isDownloading(int songId) {
        return downloadingMap.getOrDefault(songId, false);
    }

    public boolean isAnySongDownloading() {
        for (boolean isDownloading : downloadingMap.values()) {
            if (isDownloading) {
                return true;
            }
        }
        return false;
    }

    public MutableLiveData<Map<Integer, Boolean>> getDownloadingStateLiveData() {
        return downloadingStateLiveData;
    }

    public void updateButtonState(ImageButton playPauseButton, int songId) {
        playPauseButton.setEnabled(!isDownloading(songId)); // 🔹 Bloquea/desbloquea automáticamente
    }

    public MutableLiveData<Integer> getCurrentSongIdLiveData() {
        return currentSongIdLiveData;
    }

    public void notifySongStateChanged(int songId) {
        currentSongIdLiveData.postValue(songId); // Notificar a observadores que esta canción cambió
    }

    public void notifyProgressChanged(int songId, int progress) {
        progressMap.put(songId, progress); // 🔹 Guardar progreso localmente
        MutableLiveData<Integer> liveData = getSongProgressLiveData(songId);
        liveData.postValue(progress);

        // 🔹 También forzar la actualización de estado de la canción para asegurar sincronización
        //notifySongStateChanged(songId);
        //LA LÍNEA ANTERIOR FUE COMENTADA Y YA NO SE INTERRUMPE EL THUMB EN MENU PRINCIPAL
    }

    public MediaPlayer getMediaPlayer(int songId, String filePath) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(filePath);
                mediaPlayer.prepare(); // Prepara el MediaPlayer para la reproducción
            } catch (IOException e) {
                e.printStackTrace();
                return null; // Retorna null si hay un error para evitar estados inconsistentes
            }
            mediaPlayerMap.put(songId, mediaPlayer);
        }
        return mediaPlayer;
    }

    public MediaPlayer getCurrentMediaPlayer() {
        for (MediaPlayer mediaPlayer : mediaPlayerMap.values()) {
            if (mediaPlayer.isPlaying()) {
                return mediaPlayer;
            }
        }
        return null; // Retorna null si ningún MediaPlayer está en reproducción
    }

    public void saveProgress(int songId, int progress) {
        progressMap.put(songId, progress);
    }

    public int getSavedProgress(int songId) {
        return progressMap.getOrDefault(songId, 0);
    }

    public void releaseAll() {
        for (MediaPlayer mp : mediaPlayerMap.values()) {
            if (mp != null) {
                mp.release();
            }
        }
        mediaPlayerMap.clear();
    }

    public void setOnCompletionListener(int songId, MediaPlayer.OnCompletionListener listener) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(listener);
        }
    }

    // Métodos adicionales
    public boolean isSongLoaded(int songId) {
        return mediaPlayerMap.containsKey(songId);
    }

    public int getCurrentPosition(int songId) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public boolean isPlaying(int songId) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void seekTo(int songId, int position) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
            notifyProgressChanged(songId, position); // 🔹 Notificar el nuevo progreso
        }
    }

    public void resetMediaPlayer(int songId, String filePath) {
        if (mediaPlayerMap.containsKey(songId)) {
            MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
            if (mediaPlayer != null) {
                mediaPlayer.reset(); // 🔹 Reiniciar el MediaPlayer
                try {
                    mediaPlayer.setDataSource(filePath);
                    mediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // 🔹 Si el MediaPlayer no existe, crearlo
            MediaPlayer newMediaPlayer = new MediaPlayer();
            try {
                newMediaPlayer.setDataSource(filePath);
                newMediaPlayer.prepare();
                mediaPlayerMap.put(songId, newMediaPlayer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 🔹 Notificar el cambio al LiveData para que las UI se actualicen correctamente
        notifySongStateChanged(songId);
    }

    public int getDuration(int songId) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public void pause(int songId) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            notifySongStateChanged(songId); // Notificar pausa
            notifyProgressChanged(songId, mediaPlayer.getCurrentPosition()); // 🔹 Sincronizar progreso
        }
    }

    public void play(int songId) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            notifySongStateChanged(songId); // Notificar reproducción
            notifyProgressChanged(songId, mediaPlayer.getCurrentPosition()); // 🔹 Sincronizar progreso
        }
    }

    public void pauseAllExcept(int songId) {
        for (Map.Entry<Integer, MediaPlayer> entry : mediaPlayerMap.entrySet()) {
            int currentSongId = entry.getKey();
            MediaPlayer mediaPlayer = entry.getValue();

            if (currentSongId != songId && mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        }
    }

    public void removeMediaPlayer(int songId) {
        if (mediaPlayerMap.containsKey(songId)) {
            MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
            mediaPlayerMap.remove(songId);
            progressMap.remove(songId); // También eliminar el progreso guardado
        }
    }

    public void stopAndRelease(int songId) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayerMap.remove(songId);
        }
    }

    // En MediaPlayerList.java
    public void rewind(int songId, int seconds) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null) {
            int currentPosition = mediaPlayer.getCurrentPosition();
            int newPosition = Math.max(0, currentPosition - (seconds * 1000)); // No permitir valores negativos
            mediaPlayer.seekTo(newPosition);
            notifyProgressChanged(songId, newPosition);
            notifySongStateChanged(songId);
        }
    }

    public void forward(int songId, int seconds) {
        MediaPlayer mediaPlayer = mediaPlayerMap.get(songId);
        if (mediaPlayer != null) {
            int currentPosition = mediaPlayer.getCurrentPosition();
            int duration = mediaPlayer.getDuration();
            int newPosition = Math.min(duration, currentPosition + (seconds * 1000)); // No superar la duración
            mediaPlayer.seekTo(newPosition);
            notifyProgressChanged(songId, newPosition);
            notifySongStateChanged(songId);
        }
    }
}
