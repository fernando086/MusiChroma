package com.example.intentoappdatosmusica;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    //private static final String SERVER_URL = "http://192.168.1.33:5000"; // Dirección del teléfono
    //private static final String SERVER_URL = "http://10.0.2.2:5000"; // Dirección del servidor local (emulador de Android Studio)
    //private static final String SERVER_URL = "https://flask-render-app-29fn.onrender.com/"; //SERVIDOR REAL
    public static final String SERVER_URL = "https://uncheerable-runtgenologic-michel.ngrok-free.dev/"; //SERVIDOR DE PRUEBAS (LOCAL PERO ACCESIBLE DESDE CUALQUIER PARTE)

    private static Retrofit retrofit = null;

    public static Retrofit getRetrofitInstance() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(SERVER_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static Retrofit getRetrofitForLargeTransfers() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MINUTES)       // Más tiempo para iniciar conexión
                .readTimeout(5, TimeUnit.MINUTES)           // Más tiempo para leer datos
                .writeTimeout(5, TimeUnit.MINUTES)          // Más tiempo para enviar datos si fuera necesario
                .retryOnConnectionFailure(true)
                .build();

        return new Retrofit.Builder()
                .baseUrl(SERVER_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
