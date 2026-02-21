package com.example.intentoappdatosmusica;

import static android.provider.Settings.System.getString;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    GoogleSignInOptions gso;
    GoogleSignInClient gsc;
    FirebaseAuth mAuth;  // Añadir instancia de FirebaseAuth
    Button googleBtn;

    ApiService apiService = ApiClient.getRetrofitInstance().create(ApiService.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Copiar el CSV si no existe
        FileUtils.copiarArchivosIniciales(this);

        googleBtn = findViewById(R.id.btnIniciarSesion);
        mAuth = FirebaseAuth.getInstance();  // Inicializar FirebaseAuth

        // Configurar GoogleSignInOptions para obtener el idToken
        gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))  // Usar el ID de cliente
                .requestEmail()
                .build();

        gsc = GoogleSignIn.getClient(this, gso);

        GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);
        if(acct!=null){
            navigateToSecondActivity();
        }

        googleBtn.setOnClickListener(v -> signIn());
    }

    void signIn(){
        Intent signInIntent = gsc.getSignInIntent();
        startActivityForResult(signInIntent,1000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 1000){
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());  // Autenticar con Firebase
            } catch (ApiException e) {
                Toast.makeText(getApplicationContext(), "Error de autenticación: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Mét0do para autenticar con Firebase
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Inicio de sesión exitoso
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Log.d("Auth", "Usuario autenticado: " + user.getEmail());

                            //PROCEDIMIENTO PARA TOKEN
                            user.getIdToken(true).addOnCompleteListener(task1 -> {
                                if (task1.isSuccessful()) {
                                    String firebaseToken = task1.getResult().getToken();
                                    TokenRequest tokenRequest = new TokenRequest(firebaseToken);

                                    // Enviar el token a tu servidor Flask
                                    apiService.sendToken(tokenRequest).enqueue(new retrofit2.Callback<Void>() {
                                        @Override
                                        public void onResponse(Call<Void> call, retrofit2.Response<Void> response) {
                                            if (response.isSuccessful()) {
                                                Log.d("Token", "Token enviado exitosamente");
                                            } else {
                                                Log.e("Token", "Error al enviar token: " + response.code());
                                            }
                                        }

                                        @Override
                                        public void onFailure(Call<Void> call, Throwable t) {
                                            Log.e("Token", "Error de red: " + t.getMessage());
                                        }
                                    });

                                    // Enviar el token para verificación
                                    apiService.verifyToken(tokenRequest).enqueue(new retrofit2.Callback<Void>() {
                                        @Override
                                        public void onResponse(Call<Void> call, retrofit2.Response<Void> response) {
                                            if (response.isSuccessful()) {
                                                Log.d("Token", "Token verificado exitosamente");
                                            } else {
                                                Log.e("Token", "Error al verificar token: " + response.code());
                                            }
                                        }

                                        @Override
                                        public void onFailure(Call<Void> call, Throwable t) {
                                            Log.e("Token", "Error de red: " + t.getMessage());
                                        }
                                    });

                                    navigateToSecondActivity();
                                } else {
                                    // Manejar el error si no se puede obtener el token
                                    Log.e("Auth", "Error al obtener token de Firebase");
                                }
                            });
                        }
                    } else {
                        // Fallo en la autenticación
                        Toast.makeText(MainActivity.this, "Fallo en la autenticación", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    void navigateToSecondActivity(){
        finish();
        Intent intent = new Intent(MainActivity.this,MenuPrincipalActivity.class);
        startActivity(intent);
    }
}