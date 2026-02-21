package com.example.intentoappdatosmusica;

import android.media.MediaPlayer;
import android.os.Handler;
import android.widget.SeekBar;
import android.widget.TextView;

public class ReproductorUtils {

    public static String milisegundosAMinutosSegundos(int milliseconds) {
        int minutes = (milliseconds / 1000) / 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static int convertirStringAMilisegundos(String tiempo) {
        String[] partes = tiempo.split("[:.]");
        int minutos = Integer.parseInt(partes[0]);
        int segundos = Integer.parseInt(partes[1]);
        int milisegundos = Integer.parseInt(partes[2]);
        return (minutos * 60 + segundos) * 1000 + milisegundos;
    }

    public static void configurarSeekBarListeners(SeekBar seekBar, MediaPlayer mediaPlayer,
                                                  TextView tvProgreso, Handler handler,
                                                  Runnable updateRunnable) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    String progresoActual = milisegundosAMinutosSegundos(progress);
                    String duracionTotal = milisegundosAMinutosSegundos(seekBar.getMax());
                    tvProgreso.setText(progresoActual + " / " + duracionTotal);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(updateRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                handler.postDelayed(updateRunnable, 100);
            }
        });
    }
}