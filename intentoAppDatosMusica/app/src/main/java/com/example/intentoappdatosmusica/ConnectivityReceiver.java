package com.example.intentoappdatosmusica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class ConnectivityReceiver extends BroadcastReceiver {

    private static boolean isSyncing = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isConnected(context)) {
            iniciarSincronizacionSiEsNecesario(context);
        }
    }

    public static void iniciarSincronizacionSiEsNecesario(Context context) {
        if (!isSyncing) {
            isSyncing = true;
            Log.d("ConnectivityReceiver", "Iniciando sincronización...");

            new Thread(() -> {
                try {
                    //CancionConSecciones.sincronizarConServidor(context);
                } catch (Exception e) {
                    Log.e("ConnectivityReceiver", "Error durante la sincronización", e);
                } finally {
                    isSyncing = false;
                }
            }).start();
        } else {
            Log.d("ConnectivityReceiver", "Ya se está sincronizando. Se ignora.");
        }
    }

    public static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }
}
