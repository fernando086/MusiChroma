import re
import os

filepath = r"c:\Users\thepe\AndroidStudioProjects\intentoAppDatosMusica\intentoAppDatosMusica\app\src\main\java\com\example\intentoappdatosmusica\DatosPrediccionFragment.java"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Imports
content = content.replace("import androidx.appcompat.app.AppCompatActivity;", "import androidx.fragment.app.Fragment;\nimport android.view.LayoutInflater;\nimport android.view.ViewGroup;")

# Class definition
content = content.replace("public class DatosPrediccionActivity extends AppCompatActivity", "public class DatosPrediccionFragment extends Fragment")

# onCreate -> onCreateView
content = content.replace("protected void onCreate(Bundle savedInstanceState) {", "public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {")
content = content.replace("super.onCreate(savedInstanceState);", "View view = inflater.inflate(R.layout.fragment_datos_prediccion, container, false);")
content = content.replace("setContentView(R.layout.activity_datos_prediccion);", "")

# Return view at the end of onCreateView
# We need to find the end of the onCreate method.
# Let's just use a crude replace for the end of that block.
# "cargarThumbsIniciales();\n    }" -> "cargarThumbsIniciales();\n        return view;\n    }"
content = content.replace("cargarThumbsIniciales();\n    }", "cargarThumbsIniciales();\n        return view;\n    }")

# findViewById -> view.findViewById
# Careful with findViewById called in other methods. Let's replace all findViewById to view.findViewById in onCreateView
# Wait, other methods also use findViewById? No, mostly in inicializarVistasSecciones.
# Let's just pass `view` to inicializarVistasSecciones and mostrarMetadatos
content = content.replace("inicializarVistasSecciones();", "inicializarVistasSecciones(view);")
content = content.replace("private void inicializarVistasSecciones() {", "private void inicializarVistasSecciones(View view) {")

content = content.replace("mostrarMetadatos();", "mostrarMetadatos(view);")
content = content.replace("private void mostrarMetadatos() {", "private void mostrarMetadatos(View view) {")

content = content.replace("configurarBotonesAccion();", "") # remove the bottom buttons from fragment
content = content.replace("private void configurarBotonesAccion() {", "private void configurarBotonesAccion() { // REMOVED \n /*")
content = content.replace("btnCancelar.setOnClickListener(v -> cancelarYCerrar());\n    }", "btnCancelar.setOnClickListener(v -> cancelarYCerrar());\n    */}")

# Replace findViewById in the whole file if it's easy, or just `view.findViewById`
content = re.sub(r'findViewById\(', 'view.findViewById(', content)

# But wait, we renamed `view.findViewById` in methods that don't have `view`. 
# Let's fix that.
# In DatosPrediccionFragment, we can make `View rootView;` as a class member and assign it in onCreateView.
# Then we don't need to pass `view` everywhere.
content = content.replace("public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {\n        View view = inflater.inflate(R.layout.fragment_datos_prediccion, container, false);", "private View rootView;\n\n    @Override\n    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {\n        rootView = inflater.inflate(R.layout.fragment_datos_prediccion, container, false);")
content = content.replace("return view;", "return rootView;")
content = content.replace("view.findViewById", "rootView.findViewById")

# Fix `this` contexts
content = content.replace("Toast.makeText(this", "Toast.makeText(requireContext()")
content = content.replace("Toast.makeText(DatosPrediccionActivity.this", "Toast.makeText(requireContext()")
content = content.replace("ContextCompat.getColor(this", "ContextCompat.getColor(requireContext()")
content = content.replace("new SeccionesAdapter(listaSecciones, this, false)", "new SeccionesAdapter(listaSecciones, this, false)") # "this" is the interface listener, it's fine.
content = content.replace("new ImageView(this)", "new ImageView(requireContext())")

content = content.replace("getSharedPreferences", "requireActivity().getSharedPreferences")
content = content.replace("getExternalFilesDir", "requireActivity().getExternalFilesDir")

# Fix Intents
content = content.replace("Intent intent = getIntent();", "Bundle arguments = getArguments();\n        if (arguments == null) return;\n")
content = content.replace("getIntent().getSerializableExtra", "getArguments().getSerializable")
content = content.replace("intent.getIntExtra", "arguments.getInt")
content = content.replace("intent.getBooleanExtra", "arguments.getBoolean")
content = content.replace("intent.getStringExtra", "arguments.getString")
content = content.replace("intent.hasExtra", "arguments.containsKey")
content = content.replace("intent.getExtras().get", "arguments.get")

# We need to expose a method to get the final list of sections.
content += """
    public List<Seccion> getSeccionesFinales() {
        // Asegurarse de actualizar el fin de la última sección y asignar emociones si no se hizo
        asignarEmocionesASecciones();
        return listaSecciones;
    }
    
    public CancionPrediccion getCancionPrediccion() {
        return new CancionPrediccion(songId, link, nombreCancion, rutaAudio, duracion, tipoOrigen, artista, album, esOffline, listaSecciones);
    }
"""

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("Refactored fragment.")
