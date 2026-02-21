package com.example.intentoappdatosmusica;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import androidx.appcompat.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.squareup.picasso.Picasso; // Añade esta línea si usas Picasso para cargar imágenes

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.EditText;

import android.Manifest;

public class MenuPrincipalActivity extends AppCompatActivity {
    GoogleSignInOptions gso;
    GoogleSignInClient gsc;
    TextView name, email;
    ImageView profileImage;

    private TextView tvEmptyState;

    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private List<Song> songList;

    private String firebaseUid;

    private boolean isInteractionBlocked = false;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private SesionAdapter sesionAdapter;
    private List<Sesion> sesionList;

    private Button tabCanciones, tabSesiones;

    private enum ModoLista {
        CANCIONES, SESIONES
    }

    private ModoLista modoActual = ModoLista.CANCIONES;

    ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_principal);

        // Copiar el CSV si no existe
        FileUtils.copiarArchivosIniciales(this);

        tvEmptyState = findViewById(R.id.tv_empty_state);

        tabCanciones = findViewById(R.id.tabCanciones);
        tabSesiones = findViewById(R.id.tabSesiones);

        // Listener de pestañas
        tabCanciones.setOnClickListener(v -> cambiarAModoCanciones());
        tabSesiones.setOnClickListener(v -> cambiarAModoSesiones());

        // POR DEFECTO ESTÁ SELECCIONADA LA PESTAÑA CANCIONES AL ABRIR APLICACIÓN
        tabCanciones.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        tabSesiones.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.gris_neutro)));

        // Inicializar el menú
        setupDrawerMenu();

        // Inicializar RecyclerView
        recyclerView = findViewById(R.id.songListRecyclerView); // El ID debe coincidir con el de tu XML
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        File csvFile = new File(getExternalFilesDir(null), "songdata/palabras/palabras_emociones.csv");
        Map<String, EmocionDisponible> emocionesMap = EmocionDisponible.cargarEmociones(csvFile);

        // Inicializar la lista de canciones (puedes obtenerla desde tu base de datos o
        // Firebase)
        songList = new ArrayList<>(); // Esto es solo un ejemplo, en realidad cargarás datos reales

        // Inicializar el adaptador y asignarlo al RecyclerView
        songAdapter = new SongAdapter(this, songList, datosMusicalesLauncher, emocionesMap);
        sesionList = new ArrayList<>();
        sesionAdapter = new SesionAdapter(this, sesionList);

        recyclerView.setAdapter(songAdapter);

        songAdapter.setOnAddSongClickListener(() -> {
            Intent intent = new Intent(MenuPrincipalActivity.this, NuevaCancionActivity.class);
            startActivityForResult(intent, 100); // ← importante para recibir resultado más adelante
        });

        // Listener para ingresar a una sesión
        sesionAdapter.setOnIngresarClickListener(v -> {
            int position = (int) v.getTag();

            if (sesionList == null || sesionList.size() == 0) {
                Toast.makeText(MenuPrincipalActivity.this, "No hay sesiones cargadas aún", Toast.LENGTH_SHORT).show();
                return;
            }

            // Evitar que se rompa con el botón "+"
            if (position == 0)
                return;

            Sesion sesionSeleccionada = sesionList.get(position - 1);

            Intent intent = new Intent(MenuPrincipalActivity.this, DatosSesionesActivity.class);
            intent.putExtra("sesion", (Serializable) sesionSeleccionada);
            intent.putExtra("lista_canciones", (Serializable) songList);
            startActivityForResult(intent, 300); // ✅ Usamos el mismo requestCode
        });

        // Listener para agregar nueva sesión
        sesionAdapter.setOnAgregarClickListener(v -> {
            // Crear una nueva sesión vacía
            Sesion nuevaSesion = new Sesion();
            nuevaSesion.setId(0);
            nuevaSesion.setNombre("");
            nuevaSesion.setObjetivosCustom("");
            nuevaSesion.setObservaciones("");
            nuevaSesion.setFechaHoraInicio("");
            nuevaSesion.setFechaHoraFinal("");
            nuevaSesion.setTipo(false);
            nuevaSesion.setModo(false);
            nuevaSesion.setCancionesIds(new ArrayList<>());
            nuevaSesion.setCantidadCanciones(0);
            nuevaSesion.setPalabras(new ArrayList<>());
            nuevaSesion.setEstrellas(0);
            nuevaSesion.setFavorito(false);
            nuevaSesion.setColor(0);
            nuevaSesion.setDificultad("");

            // Abrir la interfaz vacía
            Intent intent = new Intent(MenuPrincipalActivity.this, DatosSesionesActivity.class);
            intent.putExtra("sesion", nuevaSesion);
            intent.putExtra("lista_canciones", (Serializable) songList);
            startActivityForResult(intent, 300); // ✅ Usamos el mismo requestCode
        });

        sesionAdapter.setOnCambiarColorClickListener((v, position) -> {
            if (position == 0)
                return; // Evitar el botón "+"

            Sesion sesionSeleccionada = sesionList.get(position - 1);
            mostrarPopupColor(sesionSeleccionada);
        });

        gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build();
        gsc = GoogleSignIn.getClient(this, gso);

        GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            // PASO 1: SE OBTIENEN DATOS DESDE FIREBASE
            String userName = user.getDisplayName(); // Nombre del usuario de Firebase
            String userImage = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null; // URL de la imagen
                                                                                                  // del perfil
            firebaseUid = user.getUid(); // UID del usuario de Firebase

            // CONFIRMAR MUESTRA DE DATOS
            Log.d("FirebaseUser", "Nombre de usuario: " + userName);
            Log.d("FirebaseUser", "Imagen de usuario: " + userImage);
            Log.d("FirebaseUser", "Firebase UID: " + firebaseUid); // Verifica si se imprime correctamente

            // Cargar canciones desde la base de datos o API
            // Bloquear interacción táctil
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            // Bloquear botón de retroceso
            isInteractionBlocked = true;
            // verificarYSincronizarCanciones(() -> cargarCanciones(firebaseUid)); //TODO
            // SINCRONIZACIÓN POSPUESTA PARA CUANDO SE TERMINE EL RESTO
            cargarCanciones(firebaseUid, -1); // Sin canción para desplazar al inicio
            cargarSesiones(firebaseUid);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            isInteractionBlocked = false;

            // PASO 2: OBTENER TOKEN DE USUARIO EN FIREBASE
            user.getIdToken(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Aquí obtienes el token después de que la tarea ha sido completada
                    String firebaseToken = task.getResult().getToken();
                    Log.d("FirebaseToken", "Token obtenido: " + firebaseToken);

                    // Ahora puedes crear tu TokenRequest
                    TokenRequest tokenRequest = new TokenRequest(firebaseToken, userName, userImage, firebaseUid);
                    Log.d("TokenRequest", "Firebase UID en el request: " + firebaseUid);

                    // Aquí puedes hacer lo que necesites con el token
                    Log.d("TokenRequest",
                            "Nombre: " + userName + ", Imagen: " + userImage + ", Firebase UID: " + firebaseUid);

                    // Realiza la solicitud para obtener los datos del usuario desde PostgreSQL
                    apiService.verificarOGuardarUsuario(tokenRequest).enqueue(new Callback<UserResponse>() {
                        @Override
                        public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                            Log.d("Proceso", "Método ejecutado: verificar_o_guardar_usuario");
                        }

                        @Override
                        public void onFailure(Call<UserResponse> call, Throwable t) {
                            Log.e("Error", "Error al verificar o guardar usuario (onFailure): " + t.getMessage());
                        }
                    });

                    // Realiza la solicitud para obtener los datos del usuario desde PostgreSQL
                    apiService.obtenerUsuario(tokenRequest).enqueue(new Callback<UserResponse>() {
                        @Override
                        public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                UserResponse userResponse = response.body();

                                Log.d("UserResponse", "Nombre de usuario: " + userResponse.getNombre()); // Verifica que
                                                                                                         // no sea null
                                Log.d("UserResponse", "Imagen de usuario: " + userResponse.getImagen()); // Verifica que
                                                                                                         // no sea null

                                // Guardar el ID del usuario en SharedPreferences
                                SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putInt("usuario_id", userResponse.getId());
                                editor.apply();
                                Log.e("USUARIO ID SHARED", String.valueOf(userResponse.getId()));

                                // Aquí asignas los valores obtenidos de la API al header del menú lateral
                                name.setText(userResponse.getNombre()); // Muestra el nombre
                                email.setText(acct.getEmail()); // El correo viene de Firebase y ya lo tienes
                                if (userResponse.getImagen() != null) {
                                    Picasso.get().load(userResponse.getImagen()).into(profileImage); // Cargar la imagen
                                                                                                     // desde la URL
                                } else {
                                    profileImage.setImageResource(R.drawable.ic_launcher_foreground); // Imagen
                                                                                                      // predeterminada
                                }
                            } else {
                                try {
                                    Log.e("Error", "Error al obtener usuario: " + response.errorBody().string());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<UserResponse> call, Throwable t) {
                            Log.e("Error", "Error al obtener usuario (onFailure): " + t.getMessage());
                        }
                    });
                } else {
                    // Manejar el error si no se puede obtener el token
                    Log.e("FirebaseToken", "Error al obtener el token", task.getException());
                }
            });
        } else {
            Log.e("FirebaseUser", "FirebaseUser es nulo.");
        }

        MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();
        mediaPlayerList.getCurrentSongIdLiveData().observe(this, songId -> {
            // Notificar al adaptador que el estado de la canción cambió
            if (songAdapter != null) {
                songAdapter.notifyDataSetChanged();
            }
        });

        requestStoragePermissions();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Not needed as we filter in real-time
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (modoActual == ModoLista.CANCIONES) {
                    if (songAdapter != null) {
                        songAdapter.filter(newText);
                    }
                } else if (modoActual == ModoLista.SESIONES) {
                    if (sesionAdapter != null) {
                        sesionAdapter.filter(newText, songList);
                    }
                }
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    private void mostrarPopupColor(Sesion sesion) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.popup_seleccionar_color, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextView tvTitulo = view.findViewById(R.id.tvTituloColor);
        tvTitulo.setText("Selecciona un color para la sesión " + sesion.getId());

        GridLayout gridColores = view.findViewById(R.id.gridColores);

        String[] colores = new String[] {
                "#fff0b6", "#ffe77d", "#fed401",
                "#9adb75", "#62c735", "#28a000",
                "#76c6a6", "#37aa82", "#12936c",
                "#8fc7ff", "#67adff", "#2b7ffe",
                "#a978ee", "#9346ef", "#7c06ea",
                "#e7c3fe", "#d489f7", "#b00ce6",
                "#ff6c6d", "#f64344", "#d40001",
                "#ffb98a", "#ff9956", "#ff7900",
                "#FFABE9", "#FF71DA", "#FA00B9",
                "#dce45b", "#8bc975", "#56c3b3", "#4db1e2", "#6f82c2", "#bb729e",
                "#f48766", "#fdc752", "#ffffff", "#aaaaaa", "#888888", "#000000"
        };

        final ImageView[] ultimoCheck = { null };
        int colorBD = sesion.getColor(); // EJ: 1 = primer color

        final String[] colorSeleccionado = {
                (colorBD > 0 && colorBD <= ColoresSesion.COLORES.length)
                        ? ColoresSesion.COLORES[colorBD - 1]
                        : null
        };

        for (String hex : colores) {
            View itemView = inflater.inflate(R.layout.item_color_cuadro, gridColores, false);

            // 🔹 FORZAMOS TAMAÑO AQUÍ
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(50); // Puedes probar con dpToPx(60) si lo quieres más grande
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            itemView.setLayoutParams(params);

            View cuadro = itemView.findViewById(R.id.viewColor);
            ImageView check = itemView.findViewById(R.id.imgCheck);

            cuadro.setBackgroundColor(Color.parseColor(hex));

            if (colorSeleccionado[0] != null && colorSeleccionado[0].equalsIgnoreCase(hex)) {
                check.setVisibility(View.VISIBLE);
                ultimoCheck[0] = check;
            }

            itemView.setOnClickListener(v -> {
                if (ultimoCheck[0] != null)
                    ultimoCheck[0].setVisibility(View.GONE);
                check.setVisibility(View.VISIBLE);
                ultimoCheck[0] = check;
                colorSeleccionado[0] = hex;
            });

            gridColores.addView(itemView);
        }

        Button btnAceptar = view.findViewById(R.id.btn_confirmar_color);
        Button btnCancelar = view.findViewById(R.id.btn_cancelar_color);

        String colorOriginal = sesion.getColor() != 0 ? String.format("#%06X", (0xFFFFFF & sesion.getColor())) : null;
        btnAceptar.setEnabled(true);

        btnAceptar.setOnClickListener(v -> {
            if (colorSeleccionado[0] != null && !colorSeleccionado[0].equals(colorOriginal)) {

                // Buscar índice del color seleccionado
                int nuevoColorIndex = -1;
                for (int i = 0; i < ColoresSesion.COLORES.length; i++) {
                    if (ColoresSesion.COLORES[i].equalsIgnoreCase(colorSeleccionado[0])) {
                        nuevoColorIndex = i + 1; // +1 porque BD guarda 1-based
                        break;
                    }
                }

                if (nuevoColorIndex != -1) {
                    JsonObject body = new JsonObject();
                    body.addProperty("id", sesion.getId());
                    body.addProperty("color", nuevoColorIndex);

                    ApiService api = ApiClient.getRetrofitInstance().create(ApiService.class);
                    int finalNuevoColorIndex = nuevoColorIndex;
                    api.actualizarColorSesion(body).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                            if (response.isSuccessful()) {
                                sesion.setColor(finalNuevoColorIndex);
                                Log.e("NUEVO_COLOR", "Índice guardado: " + finalNuevoColorIndex);
                                // Buscar la posición real en el adapter
                                int posicionEnAdapter = -1;
                                for (int i = 0; i < sesionList.size(); i++) {
                                    if (sesionList.get(i).getId() == sesion.getId()) {
                                        posicionEnAdapter = i + 1; // +1 porque en el adapter hay un botón en la
                                                                   // posición 0
                                        break;
                                    }
                                }

                                if (posicionEnAdapter != -1) {
                                    sesionAdapter.notifyItemChanged(posicionEnAdapter);
                                }

                                dialog.dismiss();
                            }
                        }

                        @Override
                        public void onFailure(Call<JsonObject> call, Throwable t) {
                            Toast.makeText(MenuPrincipalActivity.this, "Error actualizando color", Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
                }
            } else {
                dialog.dismiss();
            }
        });

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void cambiarAModoCanciones() {
        if (modoActual != ModoLista.CANCIONES) {
            modoActual = ModoLista.CANCIONES;
            recyclerView.setAdapter(songAdapter);
            songAdapter.notifyDataSetChanged();
            actualizarColoresPestanas();
        }
    }

    private void cambiarAModoSesiones() {
        if (modoActual != ModoLista.SESIONES) {
            modoActual = ModoLista.SESIONES;
            recyclerView.setAdapter(sesionAdapter);
            sesionAdapter.notifyDataSetChanged();
            actualizarColoresPestanas();
        }
    }

    private void actualizarColoresPestanas() {
        if (modoActual == ModoLista.CANCIONES) {
            tabCanciones.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
            tabSesiones.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.gris_neutro)));
        } else {
            tabSesiones.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
            tabCanciones.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.gris_neutro)));
        }
    }

    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;

    // Método para solicitar permisos de almacenamiento
    private void requestStoragePermissions() {
        // Solo pedir permiso en versiones anteriores a Android 10 (API 29)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                        REQUEST_CODE_STORAGE_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido
                // Bloquear interacción táctil
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                // Bloquear botón de retroceso
                isInteractionBlocked = true;
                cargarCanciones(firebaseUid, -1);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                isInteractionBlocked = false;
            } else {
                // El permiso fue denegado en una versión antigua de Android
                Toast.makeText(this,
                        "El permiso de almacenamiento es necesario para acceder a las canciones en esta versión de Android.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getFilePath(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.isEmpty())
            return null;

        Pattern pattern = Pattern
                .compile("v=([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})|embed/([a-zA-Z0-9_-]{11})");
        Matcher matcher = pattern.matcher(youtubeUrl);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i); // Retorna el primer grupo no nulo (ID del video)
                }
            }
        }
        return null;
    }

    private void verificarYSincronizarCanciones(Runnable despuesDeSincronizar) {
        List<Song> cancionesLocales = leerArchivoCanciones();

        // No hay nada local para sincronizar
        if (cancionesLocales.isEmpty()) {
            despuesDeSincronizar.run();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        int usuarioId = prefs.getInt("usuario_id", -1);
        if (usuarioId == -1) {
            Log.e("Sync", "Usuario ID no encontrado");
            despuesDeSincronizar.run();
            return;
        }

        // Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // Log.d("SYNC_JSON", gson.toJson(cancionesLocales).substring(0, 500));

        apiService.obtenerCanciones(firebaseUid).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e("Sync", "Error al obtener canciones del servidor");
                    despuesDeSincronizar.run();
                    return;
                }

                JsonArray cancionesRemotas = response.body().getAsJsonArray("canciones");

                boolean requiereSincronizacion = false;

                if (cancionesLocales.size() != cancionesRemotas.size()) {
                    requiereSincronizacion = true;
                } else {
                    Set<Integer> idsRemotos = new HashSet<>();
                    for (JsonElement e : cancionesRemotas) {
                        idsRemotos.add(e.getAsJsonObject().get("id").getAsInt());
                    }

                    for (Song s : cancionesLocales) {
                        if (s.getId() < 0 || !idsRemotos.contains(s.getId())) {
                            requiereSincronizacion = true;
                            break;
                        }
                    }
                }

                if (requiereSincronizacion) {
                    Log.d("Sync", "Se detectó diferencia. Ejecutando sincronización...");
                    CancionConSecciones.sincronizarConServidor(getApplicationContext(), () -> {
                        Log.d("Sync", "Sincronización finalizada. Ahora cargando canciones.");
                        runOnUiThread(despuesDeSincronizar);
                    });
                } else {
                    Log.d("Sync", "No se requiere sincronización.");
                    runOnUiThread(despuesDeSincronizar);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e("Sync", "Error al obtener canciones: " + t.getMessage());
                runOnUiThread(despuesDeSincronizar);
            }
        });
    }

    private int getUsuarioIdFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        return prefs.getInt("usuario_id", -1); // -1 si no existe
    }

    private LocalDateTime parsearFecha(String fecha) {
        if (fecha == null || fecha.trim().isEmpty()) {
            return LocalDateTime.MIN; // Para ordenar al final
        }

        List<DateTimeFormatter> formatos = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US));

        for (DateTimeFormatter f : formatos) {
            try {
                return LocalDateTime.parse(fecha, f);
            } catch (Exception ignored) {
            }
        }

        Log.e("PARSE_FECHA", "Formato desconocido: " + fecha);
        return LocalDateTime.MIN;
    }

    private void cargarSesiones(String firebaseUid) {
        apiService.obtenerSesiones(firebaseUid).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray sesionesArray = response.body().getAsJsonArray("sesiones");
                    List<Sesion> listaSesiones = new ArrayList<>();

                    if (sesionesArray != null) {
                        for (JsonElement sesionEl : sesionesArray) {
                            JsonObject sObj = sesionEl.getAsJsonObject();

                            int id = sObj.get("id").getAsInt();
                            String nombre = sObj.get("nombre").isJsonNull() ? "(Sin nombre)"
                                    : sObj.get("nombre").getAsString();
                            String objetivoCustom = sObj.get("objetivos_custom").isJsonNull() ? ""
                                    : sObj.get("objetivos_custom").getAsString();

                            // Parsear Arrays de IDs (Backend envía integer[])
                            List<Integer> objetivosIds = new ArrayList<>();
                            if (sObj.has("objetivos_ids") && !sObj.get("objetivos_ids").isJsonNull()) {
                                JsonElement elem = sObj.get("objetivos_ids");
                                if (elem.isJsonArray()) {
                                    for (JsonElement e : elem.getAsJsonArray())
                                        objetivosIds.add(e.getAsInt());
                                } else if (elem.isJsonPrimitive()) {
                                    String raw = elem.getAsString().replace("{", "").replace("}", "");
                                    if (!raw.isEmpty()) {
                                        for (String s : raw.split(",")) {
                                            try {
                                                objetivosIds.add(Integer.parseInt(s.trim()));
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                    }
                                }
                            }

                            boolean tipo = sObj.get("tipo").getAsBoolean();
                            boolean modo = sObj.get("modo").getAsBoolean();
                            String inicio = sObj.get("fecha_hora_inicio").isJsonNull() ? ""
                                    : sObj.get("fecha_hora_inicio").getAsString();
                            String fin = sObj.get("fecha_hora_final").isJsonNull() ? ""
                                    : sObj.get("fecha_hora_final").getAsString();
                            String observaciones = sObj.get("observaciones").isJsonNull() ? ""
                                    : sObj.get("observaciones").getAsString();
                            boolean favorito = sObj.get("favorito").getAsBoolean();
                            int estrellas = sObj.get("cantidad_estrellas").getAsInt();
                            int color = sObj.get("color").isJsonNull() ? 0 : sObj.get("color").getAsInt();
                            String dificultad = sObj.get("dificultades").isJsonNull() ? ""
                                    : sObj.get("dificultades").getAsString();
                            String recomendacion = sObj.get("recomendaciones").isJsonNull() ? ""
                                    : sObj.get("recomendaciones").getAsString();

                            // Nuevos campos
                            String institucion = sObj.has("institucion_educativa")
                                    && !sObj.get("institucion_educativa").isJsonNull()
                                            ? sObj.get("institucion_educativa").getAsString()
                                            : "";
                            String gradoSeccion = sObj.has("grado_seccion")
                                    && !sObj.get("grado_seccion").isJsonNull()
                                            ? sObj.get("grado_seccion").getAsString()
                                            : "";

                            // Desarrollo
                            String desarrolloInicio = sObj.has("inicio") && !sObj.get("inicio").isJsonNull()
                                    ? sObj.get("inicio").getAsString()
                                    : "";
                            String desarrolloCentral = sObj.has("actividad_central")
                                    && !sObj.get("actividad_central").isJsonNull()
                                            ? sObj.get("actividad_central").getAsString()
                                            : "";
                            String desarrolloCierre = sObj.has("cierre") && !sObj.get("cierre").isJsonNull()
                                    ? sObj.get("cierre").getAsString()
                                    : "";

                            // Observaciones (Descripción Clima)
                            String descripcionClima = sObj.has("descripcion_clima")
                                    && !sObj.get("descripcion_clima").isJsonNull()
                                            ? sObj.get("descripcion_clima").getAsString()
                                            : "";

                            // Custom Texts
                            String tecnicasCustom = sObj.has("tecnicas_custom")
                                    && !sObj.get("tecnicas_custom").isJsonNull()
                                            ? sObj.get("tecnicas_custom").getAsString()
                                            : "";
                            String materialesCustom = sObj.has("materiales_custom")
                                    && !sObj.get("materiales_custom").isJsonNull()
                                            ? sObj.get("materiales_custom").getAsString()
                                            : "";
                            String logrosCustom = sObj.has("logros_custom") && !sObj.get("logros_custom").isJsonNull()
                                    ? sObj.get("logros_custom").getAsString()
                                    : "";
                            String climaCustom = sObj.has("clima_grupal_custom")
                                    && !sObj.get("clima_grupal_custom").isJsonNull()
                                            ? sObj.get("clima_grupal_custom").getAsString()
                                            : "";

                            // Parsear Listas faltantes
                            List<Integer> tecnicasIds = parsearListaIds(sObj, "tecnicas_ids");
                            List<Integer> materialesIds = parsearListaIds(sObj, "materiales_ids");
                            List<Integer> logrosIds = parsearListaIds(sObj, "logros_ids");
                            List<Integer> climaIds = parsearListaIds(sObj, "clima_grupal_ids");
                            String facilitador = sObj.has("facilitador") && !sObj.get("facilitador").isJsonNull()
                                    ? sObj.get("facilitador").getAsString()
                                    : "";
                            int numeroEstudiantes = sObj.has("numero_estudiantes")
                                    && !sObj.get("numero_estudiantes").isJsonNull()
                                            ? sObj.get("numero_estudiantes").getAsInt()
                                            : 0;
                            int numeroSesion = sObj.has("numero_sesion") && !sObj.get("numero_sesion").isJsonNull()
                                    ? sObj.get("numero_sesion").getAsInt()
                                    : 0;

                            // Parsing de otras listas (tecnicas, materiales, etc) puede agregarse si el
                            // backend lo envía y el modelo lo requiere.
                            // Por ahora lo básico.

                            Log.e("DIFICULTAD", dificultad);

                            // Convertir lista de canciones (Soporte para 'canciones_ids' [Nuevo] y
                            // 'canciones' [Antiguo])
                            List<Integer> cancionesIds = new ArrayList<>();
                            JsonElement cancionesElem = null;

                            if (sObj.has("canciones_ids") && !sObj.get("canciones_ids").isJsonNull()) {
                                cancionesElem = sObj.get("canciones_ids");
                            } else if (sObj.has("canciones") && !sObj.get("canciones").isJsonNull()) {
                                cancionesElem = sObj.get("canciones");
                            }

                            if (cancionesElem != null) {
                                if (cancionesElem.isJsonArray()) {
                                    for (JsonElement cid : cancionesElem.getAsJsonArray())
                                        cancionesIds.add(cid.getAsInt());
                                } else if (cancionesElem.isJsonPrimitive()) {
                                    String raw = cancionesElem.getAsString().replace("{", "").replace("}", "")
                                            .replace("[", "").replace("]", "");
                                    if (!raw.isEmpty()) {
                                        for (String s : raw.split(",")) {
                                            try {
                                                cancionesIds.add(Integer.parseInt(s.trim()));
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                    }
                                }
                            }

                            // Convertir lista de palabras emocionales (Ahora es JSON Array)
                            List<String> palabras = new ArrayList<>();
                            if (sObj.has("palabras") && !sObj.get("palabras").isJsonNull()) {
                                JsonElement elem = sObj.get("palabras");
                                if (elem.isJsonArray()) {
                                    for (JsonElement p : elem.getAsJsonArray())
                                        palabras.add(p.getAsString());
                                } else if (elem.isJsonPrimitive()) {
                                    String raw = elem.getAsString().replace("{", "").replace("}", "").replace("\"", ""); // remove
                                                                                                                         // quotes
                                                                                                                         // if
                                                                                                                         // present
                                                                                                                         // in
                                                                                                                         // string
                                                                                                                         // rep
                                    if (!raw.isEmpty()) {
                                        for (String s : raw.split(",")) {
                                            if (!s.trim().isEmpty())
                                                palabras.add(s.trim());
                                        }
                                    }
                                }
                            }

                            Log.e("SESIÓN DETECTADA", "id: " + id + ", nombre: " + nombre);

                            // Crear objeto usando constructor vacío y setters
                            Sesion sesion = new Sesion(id, nombre, tipo, modo, inicio);
                            sesion.setFechaHoraFinal(fin);
                            sesion.setObjetivosCustom(objetivoCustom);
                            sesion.setObjetivosIds(objetivosIds);

                            sesion.setInstitucionEducativa(institucion);
                            sesion.setGradoSeccion(gradoSeccion);
                            sesion.setFacilitador(facilitador);
                            sesion.setNumeroEstudiantes(numeroEstudiantes);
                            sesion.setNumeroSesion(numeroSesion);

                            sesion.setInicio(desarrolloInicio);
                            sesion.setActividadCentral(desarrolloCentral);
                            sesion.setCierre(desarrolloCierre);
                            sesion.setDescripcionClima(descripcionClima);

                            sesion.setTecnicasIds(tecnicasIds);
                            sesion.setTecnicasCustom(tecnicasCustom);
                            sesion.setMaterialesIds(materialesIds);
                            sesion.setMaterialesCustom(materialesCustom);
                            sesion.setLogrosIds(logrosIds);
                            sesion.setLogrosCustom(logrosCustom);
                            sesion.setClimaGrupalIds(climaIds);
                            sesion.setClimaGrupalCustom(climaCustom);

                            sesion.setObservaciones(observaciones);
                            sesion.setCantidadCanciones(cancionesIds.size());
                            sesion.setEstrellas(estrellas);
                            sesion.setFavorito(favorito);
                            sesion.setColor(color);
                            sesion.setCancionesIds(cancionesIds);
                            sesion.setPalabras(palabras);
                            sesion.setDificultad(dificultad);
                            sesion.setRecomendaciones(recomendacion);

                            listaSesiones.add(sesion);
                            Log.e("COLOR", String.valueOf(color));
                        }
                    }

                    // 🔹 Aquí actualizas tu adapter de sesiones
                    listaSesiones.sort((s1, s2) -> {
                        LocalDateTime d1 = parsearFecha(s1.getFechaHoraInicio());
                        LocalDateTime d2 = parsearFecha(s2.getFechaHoraInicio());
                        return d2.compareTo(d1); // orden descendente
                    });

                    sesionAdapter.setSesionList(listaSesiones);

                    // Dentro de onResponse de cargarCanciones
                    if (listaSesiones.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        tvEmptyState.setVisibility(View.VISIBLE);
                        tvEmptyState.setText("No tienes canciones. ¡Añade una para empezar!");
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        tvEmptyState.setVisibility(View.GONE);
                    }

                    sesionList = listaSesiones; // 🔥 IMPORTANTE
                    sesionAdapter.notifyDataSetChanged();
                } else {
                    Log.e("Sesiones", "Error: " + response.errorBody());
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e("Sesiones", "Error de red: " + t.getMessage());
                Toast.makeText(getApplicationContext(), "No se pudieron cargar las sesiones.", Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void cargarCanciones(String firebaseUid, int songIdToScroll) {
        apiService.obtenerCanciones(firebaseUid).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                Log.e("Print", "Entrando a if dentro de obtenerCanciones");
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject responseObject = response.body();

                    // Procesa la lista de canciones (cada vez que la aplicación se abre y hay
                    // conexión a internet)
                    List<Song> canciones = new ArrayList<>();
                    JsonArray cancionesArray = responseObject.getAsJsonArray("canciones");

                    StringBuilder contenidoTotal = new StringBuilder();

                    if (cancionesArray != null) {
                        for (JsonElement cancionElement : cancionesArray) {
                            JsonObject cancionObject = cancionElement.getAsJsonObject();

                            int id = cancionObject.get("id").getAsInt();
                            String nombre = cancionObject.get("nombre").isJsonNull() ? "(Sin título)"
                                    : cancionObject.get("nombre").getAsString();
                            String autor = cancionObject.get("autor").isJsonNull() ? "(Sin autor)"
                                    : cancionObject.get("autor").getAsString();
                            String album = cancionObject.get("album").isJsonNull() ? "(Sin álbum)"
                                    : cancionObject.get("album").getAsString();
                            String enlace = cancionObject.get("enlace").isJsonNull() ? null
                                    : cancionObject.get("enlace").getAsString();

                            String comentarioGeneral = cancionObject.get("comentario_general").isJsonNull()
                                    ? "(Sin comentario)"
                                    : cancionObject.get("comentario_general").getAsString();
                            boolean estadoCgPublicado = cancionObject.get("estado_cg_publicado").getAsBoolean();
                            boolean estadoPublicado = cancionObject.get("estado_publicado").getAsBoolean();
                            String fecha_creacion = cancionObject.get("f_creacion").getAsString();
                            String fecha_ultima_edicion = cancionObject.get("f_ultima_edicion").getAsString();

                            JsonArray seccionesJson = cancionObject.getAsJsonArray("secciones");
                            List<Seccion> listaSecciones = new ArrayList<>();
                            for (JsonElement seccionEl : seccionesJson) {
                                JsonObject sObj = seccionEl.getAsJsonObject();

                                int sId = sObj.get("id").getAsInt();
                                String inicio = sObj.get("inicio").getAsString();
                                String fin = sObj.get("fin").getAsString();
                                String fCreacion = sObj.get("s_f_creacion").getAsString();
                                String fUltima = sObj.get("s_f_ultima_edicion").getAsString();

                                String nombreSeccion = sObj.has("nombre_seccion")
                                        && !sObj.get("nombre_seccion").isJsonNull()
                                                ? sObj.get("nombre_seccion").getAsString()
                                                : null;

                                String comentario = sObj.has("comentario") && !sObj.get("comentario").isJsonNull()
                                        ? sObj.get("comentario").getAsString()
                                        : null;

                                boolean estadoCsPublicado = sObj.has("publicado")
                                        && sObj.get("publicado").getAsBoolean();

                                // Lista de géneros
                                List<GeneroSeleccionado> generoSeleccionados = new ArrayList<>();
                                if (sObj.has("generos") && sObj.get("generos").isJsonArray()) {
                                    for (JsonElement generoEl : sObj.get("generos").getAsJsonArray()) {
                                        JsonObject gObj = generoEl.getAsJsonObject();
                                        generoSeleccionados.add(new GeneroSeleccionado(gObj.get("id").getAsInt(),
                                                gObj.get("nombre_genero").getAsString()));
                                    }
                                }

                                // Lista de emociones
                                List<EmocionSeleccionada> emociones = new ArrayList<>();
                                if (sObj.has("emociones") && sObj.get("emociones").isJsonArray()) {
                                    for (JsonElement emEl : sObj.get("emociones").getAsJsonArray()) {
                                        String palabra = emEl.getAsString();
                                        emociones.add(new EmocionSeleccionada(palabra));
                                    }
                                }

                                Seccion sec = new Seccion(sId, inicio, fin, fCreacion, fUltima, nombreSeccion,
                                        comentario, estadoCsPublicado, emociones, generoSeleccionados);
                                listaSecciones.add(sec);
                            }

                            // Verificar si el archivo existe
                            String fileName = getFilePath(enlace);

                            String filePath;

                            if (fileName != null) {
                                // Es un enlace de YouTube, se agrega ".mp3"
                                filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/"
                                        + fileName + ".mp3";
                            } else {
                                // Es un archivo local, se usa el nombre del archivo directamente
                                filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/"
                                        + cancionObject.get("nombre").getAsString();
                            }

                            File audioFile = new File(filePath);
                            boolean fileExists = audioFile.exists();
                            Log.e("menu: existe archivo", filePath + " = " + fileExists);

                            // Crea un nuevo objeto Song y agrégalo a la lista (para que SongAdapter pueda
                            // procesar y mostrar en interfaz)
                            Song cancion = new Song(id, nombre, autor, album, enlace, comentarioGeneral,
                                    estadoCgPublicado, estadoPublicado, listaSecciones, fecha_creacion,
                                    fecha_ultima_edicion);
                            cancion.setLoaded(fileExists); // Asigna true si el archivo existe, false si no
                            canciones.add(cancion);

                            // Construir string para el archivo local
                            contenidoTotal.append(id).append(";")
                                    .append(nombre).append(";")
                                    .append(autor).append(";")
                                    .append(album).append(";")
                                    .append(enlace).append(";")
                                    .append(comentarioGeneral).append(";")
                                    .append(estadoCgPublicado).append(";")
                                    .append(estadoPublicado).append(";")
                                    .append(fecha_creacion).append(";")
                                    .append(fecha_ultima_edicion).append(";")
                                    .append(serializarSecciones(listaSecciones)).append("\n");
                        }
                    }

                    // Escribe el total del contenido acumulado en el archivo de texto
                    guardarArchivoTexto(contenidoTotal.toString());
                    Log.e("Archivo txt generado", String.valueOf(contenidoTotal));

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

                    Collections.sort(canciones, (s1, s2) -> {
                        LocalDateTime d1 = LocalDateTime.parse(s1.getFecha_creacion(), formatter);
                        LocalDateTime d2 = LocalDateTime.parse(s2.getFecha_creacion(), formatter);
                        return d2.compareTo(d1); // invertido
                    });

                    // Actualiza el adaptador con la lista de canciones y notifica los cambios
                    songAdapter.setSongList(canciones);

                    // Dentro de onResponse de cargarCanciones
                    if (canciones.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        tvEmptyState.setVisibility(View.VISIBLE);
                        tvEmptyState.setText("No tienes canciones. ¡Añade una para empezar!");
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        tvEmptyState.setVisibility(View.GONE);
                    }

                    songList = canciones;
                    songAdapter.notifyDataSetChanged();
                } else {
                    Log.e("Error", "isSuccessful:" + response.isSuccessful());
                    Log.e("Error", "Body Response:" + response.body());
                    Log.e("Error", "Error al cargar canciones: " + response.errorBody());
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // TODO VERIFICAR LECTURA DE DATOS (LEÍDOS DESDE EL ARCHIVO SONGDATA.TXT)
                Log.e("Error", "Error de red: " + t.getMessage());

                // Intentar leer las canciones desde el archivo local
                List<Song> canciones = leerArchivoCanciones();
                if (canciones != null && !canciones.isEmpty()) {
                    // Actualiza el adaptador con la lista de canciones
                    songAdapter.setSongList(canciones);
                    songList = canciones;
                    songAdapter.notifyDataSetChanged();
                    Toast.makeText(getApplicationContext(), "Conexión fallida. Cargando datos locales.",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "No hay datos disponibles.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private List<Song> leerArchivoCanciones() {
        List<Song> canciones = new ArrayList<>();
        int usuarioId = getUsuarioIdFromPrefs();
        if (usuarioId == -1) {
            Log.e("FileError", "Usuario ID no encontrado en SharedPreferences");
            return canciones;
        }

        File fileDir = new File(getApplicationContext().getExternalFilesDir(null), "songdata");
        File file = new File(fileDir, "songdata_" + usuarioId + ".txt");

        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(";");
                    if (parts.length >= 11) {
                        int id = Integer.parseInt(parts[0]);
                        String nombre = parts[1];
                        String autor = parts[2];
                        String album = parts[3];
                        String enlace = parts[4];
                        String comentario_general = parts[5];
                        boolean estadoCgPublicado = Boolean.parseBoolean(parts[6]);
                        boolean estadoPublicado = Boolean.parseBoolean(parts[7]);
                        String f_creacion = parts[8];
                        String f_ultima_edicion = parts[9];
                        List<Seccion> secciones = parsearSecciones(parts[10]);

                        Song song = new Song(id, nombre, autor, album, enlace, comentario_general, estadoCgPublicado,
                                estadoPublicado, secciones, f_creacion, f_ultima_edicion);
                        canciones.add(song);
                    }
                }
            } catch (IOException e) {
                Log.e("FileError", "Error al leer el archivo: " + e.getMessage());
            }
        } else {
            Log.e("FileError", "El archivo no existe en: " + file.getAbsolutePath());
        }

        return canciones;
    }

    private List<Seccion> parsearSecciones(String seccionesStr) {
        List<Seccion> secciones = new ArrayList<>();

        if (seccionesStr == null || seccionesStr.trim().isEmpty())
            return secciones;

        String[] seccionesArray = seccionesStr.split("\\|");
        for (String s : seccionesArray) {
            try {
                // id / resto
                String[] partes1 = s.split("/", 2); // solo dividir en 2 partes
                int id = Integer.parseInt(partes1[0]);

                String[] resto = partes1[1].split("//");
                String[] tiempos = resto[0].split("-");
                String tiempoInicio = tiempos[0];
                String tiempoFin = tiempos[1];
                String fechaCreacion = resto[1];
                String fechaUltimaEdicion = resto[2];
                String nombre = resto.length > 3 ? resto[3] : "";
                String comentario = resto.length > 4 ? resto[4] : "";
                boolean estadoPublicado = resto.length > 5 && Boolean.parseBoolean(resto[5]);
                String emocionesStr = resto.length > 6 ? resto[6] : "";
                String generosStr = resto.length > 7 ? resto[7] : "";

                List<EmocionSeleccionada> emociones = new ArrayList<>();
                List<GeneroSeleccionado> listaGeneroSeleccionados = new ArrayList<>();

                if (!emocionesStr.isEmpty()) {
                    for (String palabra : emocionesStr.split(",")) {
                        emociones.add(new EmocionSeleccionada(palabra.trim()));
                    }
                }

                if (!generosStr.isEmpty()) {
                    for (String generoNombre : generosStr.split(",")) {
                        listaGeneroSeleccionados.add(new GeneroSeleccionado(-1, generoNombre.trim()));
                    }
                }

                Seccion seccion = new Seccion(
                        id, tiempoInicio, tiempoFin, fechaCreacion, fechaUltimaEdicion,
                        nombre, comentario, estadoPublicado, emociones, listaGeneroSeleccionados);

                secciones.add(seccion);
            } catch (Exception e) {
                Log.e("ParseError", "Error al parsear sección: " + s + " -> " + e.getMessage());
            }
        }

        return secciones;
    }

    private String serializarSecciones(List<Seccion> secciones) {
        StringBuilder builder = new StringBuilder();

        for (Seccion s : secciones) {
            String emocionesStr = s.getEmociones() != null
                    ? s.getEmociones().stream().map(EmocionSeleccionada::getPalabra).collect(Collectors.joining(","))
                    : "";

            // Generos: lista de objetos, hay que extraer los nombres
            String generosStr = s.getGeneros() != null
                    ? s.getGeneros().stream().map(GeneroSeleccionado::getNombre).collect(Collectors.joining(","))
                    : "";

            builder.append(s.getId()).append("/")
                    .append(s.getTiempoInicio()).append("-").append(s.getTiempoFinal())
                    .append("//").append(s.getFecha_creacion())
                    .append("//").append(s.getFecha_ultima_edicion())
                    .append("//").append(s.getNombre() != null ? s.getNombre() : "")
                    .append("//").append(s.getComentario() != null ? s.getComentario() : "")
                    .append("//").append(s.isPublicado())
                    .append("//").append(emocionesStr)
                    .append("//").append(generosStr)
                    .append("|");
        }

        if (builder.length() > 0) {
            builder.setLength(builder.length() - 1); // quitar último "|"
        }

        return builder.toString();
    }

    private void guardarArchivoTexto(String fileContent) {
        try {
            int usuarioId = getUsuarioIdFromPrefs();
            if (usuarioId == -1) {
                Log.e("FileError", "Usuario ID no encontrado en SharedPreferences");
                return;
            }

            File fileDir = new File(getApplicationContext().getExternalFilesDir(null), "songdata");
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }

            // Archivo único por usuario
            File file = new File(fileDir, "songdata_" + usuarioId + ".txt");

            FileOutputStream fos = new FileOutputStream(file, false); // sobrescribir
            fos.write(fileContent.getBytes());
            fos.close();

            Log.i("File", "Archivo guardado en: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e("FileError", "Error al guardar el archivo: " + e.getMessage());
        }
    }

    // Mét0do para inicializar el menú lateral
    private void setupDrawerMenu() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open_drawer,
                R.string.close_drawer);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Obtener los elementos XML del header
        View headerView = navigationView.getHeaderView(0); // Aquí obtienes el header del navigationView
        profileImage = headerView.findViewById(R.id.profile_image);
        name = headerView.findViewById(R.id.profile_name);
        email = headerView.findViewById(R.id.profile_email);

        // Manejar los clics en el menú lateral
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_item1) {
                // Acción para el Item 1
                Toast.makeText(getApplicationContext(), "Has tocado la opción 1.", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_item2) {
                // Acción para el Item 2
                // Aquí mostramos la ventana flotante para cambiar el nombre
                showChangeNameDialog();
                return true;
            } else if (id == R.id.nav_item3) {
                // Acción para el Item 3
                Toast.makeText(getApplicationContext(), "Has tocado la opción 3.", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_item5) {
                // Acción para el Item 4
                Toast.makeText(getApplicationContext(), "Has cerrado tu sesión.", Toast.LENGTH_SHORT).show();
                signOut();
            } else if (id == R.id.nav_item4) {
                // 🟢 Acción al tocar "Sincronizar"
                Toast.makeText(getApplicationContext(), "Sincronizando datos...", Toast.LENGTH_SHORT).show();

                /*
                 * // Bloquear interacción táctil
                 * getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                 * WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                 * isInteractionBlocked = true;
                 * 
                 * CancionConSecciones.sincronizarConServidor(MenuPrincipalActivity.this, () ->
                 * runOnUiThread(() -> {
                 * Toast.makeText(getApplicationContext(), "Sincronización completada.",
                 * Toast.LENGTH_SHORT).show();
                 * cargarCanciones(firebaseUid); // Solo si firebaseUid está disponible aquí
                 * getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                 * isInteractionBlocked = false;
                 * }));
                 */
            }

            // Cerrar el drawer después de la selección
            drawerLayout.closeDrawer(GravityCompat.START);
            return false;
        });
    }

    private void showChangeNameDialog() {
        // Crear el diálogo usando el layout personalizado
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.popup_change_name, null);
        builder.setView(dialogView);

        EditText editTextName = dialogView.findViewById(R.id.edit_text_name);

        builder.setTitle("Cambiar Nombre")
                .setPositiveButton("Confirmar", (dialog, which) -> {
                    // Código para manejar la confirmación y cambiar el nombre
                    String newName = editTextName.getText().toString();
                    if (!newName.isEmpty()) {
                        updateUserName(newName); // Función para actualizar el nombre del usuario
                    }
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());

        // Mostrar el diálogo
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateUserName(String newName) {
        // Obtener el UID de Firebase del usuario autenticado
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            String firebaseUid = user.getUid();

            user.getIdToken(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Aquí obtienes el token después de que la tarea ha sido completada
                    String token = task.getResult().getToken();
                    String firebaseToken = "Bearer " + token;
                    Log.d("FirebaseToken", "Token obtenido: " + firebaseToken);
                    // Enviar el token a tu servidor o hacer otra acción
                    // Crear el objeto de solicitud para actualizar el nombre
                    UserNameUpdateRequest updateRequest = new UserNameUpdateRequest(firebaseUid, newName);

                    // Realizar la llamada a la API usando Retrofit
                    apiService.updateUserName(firebaseToken, updateRequest).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                // Si la respuesta es exitosa, actualizar la interfaz
                                Log.d("UpdateUserName", "Nombre actualizado exitosamente.");
                                name.setText(newName); // Actualizar el nombre en la interfaz
                                Toast.makeText(MenuPrincipalActivity.this, "Nombre actualizado correctamente",
                                        Toast.LENGTH_SHORT).show();
                            } else if (response.code() == 403) {
                                // Si la respuesta es 403, significa que el usuario intentó cambiar el nombre
                                // antes de las 24 horas
                                Toast.makeText(MenuPrincipalActivity.this,
                                        "Solo 1 cambio cada 24 horas, por favor esperar.", Toast.LENGTH_LONG).show();
                            } else {
                                // Manejar el error si la actualización falla
                                Log.e("UpdateUserName", "Error al actualizar el nombre: " + response.code());
                                Toast.makeText(MenuPrincipalActivity.this, "Error al actualizar el nombre",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            // Manejar la falla de la llamada a la API
                            Log.e("UpdateUserName", "Error de red: " + t.getMessage());
                            Toast.makeText(MenuPrincipalActivity.this, "Error de red al actualizar el nombre",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                } else {
                    // Manejar el error si no se puede obtener el token
                    Log.e("FirebaseToken", "Error al obtener el token", task.getException());
                }
            });
        } else {
            Log.e("UpdateUserName", "Usuario no autenticado.");
            Toast.makeText(MenuPrincipalActivity.this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarArchivosCanciones();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("MENU PRINCIPAL", "EJECUTANDO ONACTIVITYRESULT");
        Log.e("MENU PRINCIPAL", "requestCode: " + requestCode);
        Log.e("MENU PRINCIPAL", "resultCode: " + resultCode);
        Log.e("MENU PRINCIPAL", "data: " + data);

        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == 100) { // Nueva canción desde NuevaCancionActivity
                boolean nuevaCancionAgregada = data.getBooleanExtra("NC", false);
                if (nuevaCancionAgregada) {
                    int newSongId = data.getIntExtra("newSongId", -1);
                    cargarCanciones(firebaseUid, newSongId);
                    Toast.makeText(MenuPrincipalActivity.this, "Canción agregada exitosamente.", Toast.LENGTH_SHORT)
                            .show();
                }
            } else if (requestCode == 300) { // Actualización desde DatosSesionesActivity
                boolean sesionActualizada = data.getBooleanExtra("sesion_actualizada", false);
                if (sesionActualizada) {
                    cargarSesiones(firebaseUid);
                    Toast.makeText(MenuPrincipalActivity.this, "Sesiones actualizadas.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void verificarArchivosCanciones() {
        MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();

        Iterator<Song> iterator = songList.iterator();
        while (iterator.hasNext()) {
            Song song = iterator.next();
            String filePath = "/storage/emulated/0/Android/data/com.example.intentoappdatosmusica/files/media/"
                    + song.getFilePath() + ".mp3";
            File audioFile = new File(filePath);

            if (!audioFile.exists()) {
                // Si el archivo no existe, detener y eliminar el MediaPlayer
                if (mediaPlayerList.isPlaying(song.getId())) {
                    mediaPlayerList.pause(song.getId());
                }

                mediaPlayerList.removeMediaPlayer(song.getId()); // Eliminar de la lista de MediaPlayers

                // Notificar al RecyclerView para actualizar UI
                int songIndex = getSongIndexById(song.getId());
                if (songIndex != -1) {
                    songAdapter.notifyItemChanged(songIndex);
                }
            }
        }
    }

    void signOut() {
        // Limpiar SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UsuarioPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // Detén el MediaPlayer antes de cerrar sesión
        gsc.signOut().addOnCompleteListener(task -> {
            finish();
            startActivity(new Intent(MenuPrincipalActivity.this, MainActivity.class));
        });
    }

    private final ActivityResultLauncher<Intent> datosMusicalesLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    int songId = result.getData().getIntExtra("songId", -1);
                    int currentPosition = result.getData().getIntExtra("currentPosition", 0);
                    boolean isPlaying = result.getData().getBooleanExtra("isPlaying", false);
                    boolean cancionActualizada = data.getBooleanExtra("NC", false); // ✅ esto es clave

                    if (songId != -1) {
                        MediaPlayerList mediaPlayerList = MediaPlayerList.getInstance();
                        mediaPlayerList.seekTo(songId, currentPosition); // 🔹 Mantener la posición exacta

                        if (isPlaying) {
                            mediaPlayerList.play(songId); // 🔹 Reproducir solo si antes estaba sonando
                        } else {
                            mediaPlayerList.pause(songId); // 🔹 Mantener pausa si estaba pausada
                        }

                        // Notifica el estado actualizado al LiveData
                        mediaPlayerList.notifySongStateChanged(songId);

                        // 🔹 🔥 Refrescar SOLO el elemento afectado en la lista en lugar de toda la
                        // lista
                        int songIndex = getSongIndexById(songId);
                        if (songIndex != -1) {
                            songAdapter.notifyItemChanged(songIndex); // ✅ Eliminamos el +1 innecesario
                        }
                    }

                    // ✅ Si hubo cambios en los datos, recargar toda la lista
                    if (cancionActualizada) {
                        Log.e("MENU PRINCIPAL", "canción modificada, ejecutando cargarCanciones()");
                        // Bloquear interacción táctil
                        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                        // Bloquear botón de retroceso
                        isInteractionBlocked = true;
                        cargarCanciones(firebaseUid, -1);
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                        isInteractionBlocked = false;
                    }
                }
            });

    private int getSongIndexById(int songId) {
        for (int i = 0; i < songList.size(); i++) {
            if (songList.get(i).getId() == songId) {
                return i;
            }
        }
        return -1; // No encontrada
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (!isInteractionBlocked) {
            super.onBackPressed();
        }
    }

    private List<Integer> parsearListaIds(JsonObject sObj, String key) {
        List<Integer> ids = new ArrayList<>();
        if (sObj.has(key) && !sObj.get(key).isJsonNull()) {
            JsonElement elem = sObj.get(key);
            if (elem.isJsonArray()) {
                for (JsonElement e : elem.getAsJsonArray())
                    ids.add(e.getAsInt());
            } else if (elem.isJsonPrimitive()) {
                String raw = elem.getAsString().replace("{", "").replace("}", "").replace("[", "").replace("]", "");
                if (!raw.isEmpty()) {
                    for (String s : raw.split(",")) {
                        try {
                            ids.add(Integer.parseInt(s.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return ids;
    }

    @Override
    protected void onDestroy() {
        Log.e("MenuPrincipal", "ejecutando destroy");
        super.onDestroy();

        if (isFinishing()) { // Solo si la app realmente se cierra
            Log.e("MenuPrincipal", "ejecutando isfinishing");
        }
    }
}