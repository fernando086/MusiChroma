package com.example.intentoappdatosmusica;

import android.app.Application;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

public class MyApp extends Application {

    private ConnectivityReceiver connectivityReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        // Registrar el BroadcastReceiver para cambios de red
        /*
        connectivityReceiver = new ConnectivityReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityReceiver, filter);
        // Verificar si hay conexión al iniciar la app y aún no se sincronizó
        if (ConnectivityReceiver.isConnected(this)) {
            ConnectivityReceiver.iniciarSincronizacionSiEsNecesario(this);
        }*/
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        // Esto puede no llamarse en dispositivos reales, pero es una buena práctica
        //unregisterReceiver(connectivityReceiver);
    }
}
