package com.example.intentoappdatosmusica;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class ReproductorController {
    private MediaPlayer mediaPlayer;
    private SeekBar seekBar;
    private TextView tvProgresoDuracion;
    private ImageButton btnPlayPause;
    private ImageButton btnRewind;
    private ImageButton btnForward;
    private Handler handler = new Handler();
    private boolean isPlaying = false;
    private int duracionTotalMs = 0;
    private Context context;

    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(currentPosition);
                actualizarTextoProgreso(currentPosition);
                handler.postDelayed(this, 100);
            }
        }
    };

    public ReproductorController(Context context) {
        this.context = context;
    }

    public void inicializarReproductor(String rutaAudio, SeekBar seekBar, TextView tvProgreso,
                                       ImageButton btnPlayPause, ImageButton btnRewind, ImageButton btnForward) {
        this.seekBar = seekBar;
        this.tvProgresoDuracion = tvProgreso;
        this.btnPlayPause = btnPlayPause;
        this.btnRewind = btnRewind;
        this.btnForward = btnForward;

        configurarMediaPlayer(rutaAudio);
        configurarListeners();
    }

    private void configurarMediaPlayer(String rutaAudio) {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(rutaAudio);
            mediaPlayer.prepare();

            duracionTotalMs = mediaPlayer.getDuration();
            String duracionTotalFormateada = milisegundosAMinutosSegundos(duracionTotalMs);
            tvProgresoDuracion.setText("00:00 / " + duracionTotalFormateada);

            seekBar.setMax(duracionTotalMs);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error al cargar el audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void configurarListeners() {
        btnPlayPause.setOnClickListener(v -> toggleReproduccion());
        btnRewind.setOnClickListener(v -> cambiarPosicion(-10000));
        btnForward.setOnClickListener(v -> cambiarPosicion(10000));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    actualizarTextoProgreso(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(updateSeekBar);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                handler.postDelayed(updateSeekBar, 100);
            }
        });

        mediaPlayer.setOnCompletionListener(mp -> detenerReproduccion());
    }

    private void toggleReproduccion() {
        if (isPlaying) {
            pausarReproduccion();
        } else {
            iniciarReproduccion();
        }
    }

    private void iniciarReproduccion() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            btnPlayPause.setImageResource(R.drawable.iconopause);
            handler.postDelayed(updateSeekBar, 100);
        }
    }

    private void pausarReproduccion() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlayPause.setImageResource(R.drawable.iconoplay);
            handler.removeCallbacks(updateSeekBar);
        }
    }

    private void detenerReproduccion() {
        isPlaying = false;
        btnPlayPause.setImageResource(R.drawable.iconoplay);
        handler.removeCallbacks(updateSeekBar);
    }

    private void cambiarPosicion(int cambioMs) {
        if (mediaPlayer != null) {
            int nuevaPosicion = mediaPlayer.getCurrentPosition() + cambioMs;
            nuevaPosicion = Math.max(0, Math.min(nuevaPosicion, duracionTotalMs));

            mediaPlayer.seekTo(nuevaPosicion);
            seekBar.setProgress(nuevaPosicion);
            actualizarTextoProgreso(nuevaPosicion);
        }
    }

    private String milisegundosAMinutosSegundos(int milliseconds) {
        int minutes = (milliseconds / 1000) / 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void actualizarTextoProgreso(int currentPositionMs) {
        String progresoActual = milisegundosAMinutosSegundos(currentPositionMs);
        String duracionTotal = milisegundosAMinutosSegundos(duracionTotalMs);
        tvProgresoDuracion.setText(progresoActual + " / " + duracionTotal);
    }

    public void eliminarArchivoTemporal(String rutaAudio) {
        if (rutaAudio != null) {
            File archivo = new File(rutaAudio);
            if (archivo.exists() && archivo.delete()) {
                Toast.makeText(context, "Canción cancelada", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void liberarRecursos() {
        handler.removeCallbacks(updateSeekBar);
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}