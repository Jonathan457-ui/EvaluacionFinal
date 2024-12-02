package com.example.evaluacionfinal;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
//Librerias de la aplicacion y firebase
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//librerias de MQTT y Formulario
import android.widget.Button;
import android.widget.TextView;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MainActivity extends AppCompatActivity {

    //Variables de la conexion a MQTT
    private static String mqttHost = "tcp://woolshirt187.cloud.shiftr.io:1883";
    private static String IdUsuario = "AppAndroid";

    private static String Topico = "Mensaje";
    private static String User = "woolshirt187";
    private static String Pass = "RaRlZ0MVHQoAnS4j";

    //Variable utilizada para imprimir los datos del sensor
    private TextView textView;
    private EditText editTextMessage;
    private Button botonEnvio;

    //Libreria MQTT
    private MqttClient mqttClient;

    //Declaro variables
    private EditText txtCodigo, txtNombre, txtDireccion;
    private ListView lista;
    private Spinner spTipo;

    //Variable de la conexion firebase
    private FirebaseFirestore db;

    //Datos del Spinner
    String[] TiposVuelo = {"Vieje Privado", "Normal"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Llamamos al metodo que carga la lista
        CargarListaFirestore();
        //Iniciamos Firestore
        db = FirebaseFirestore.getInstance();
        //Uno loas variables con los del XML
        txtCodigo = findViewById(R.id.txtCodigo);
        txtDireccion = findViewById(R.id.txtDireccion);
        txtNombre = findViewById(R.id.txtNombre);
        spTipo = findViewById(R.id.spTipo);
        lista = findViewById(R.id.lista);
        //Poblar Spinner con los datos de tipo vuelo
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TiposVuelo);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTipo.setAdapter(adapter);

        //Enlace de la variable del ID que esta en e activity main donde imprimimos los datos
        textView = findViewById(R.id.textView);
        editTextMessage = findViewById(R.id.txtMensaje);
        botonEnvio = findViewById(R.id.botonEnviarMensaje);
        try {
            //Creacion de un cliente MQTT
            mqttClient = new MqttClient(mqttHost, IdUsuario, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(User);
            options.setPassword(Pass.toCharArray());
            //Conexion al servidor MQTT
            mqttClient.connect(options);
            //Si se conecta dira un mensaje en MQTT
            Toast.makeText(this, "Aplicaion conectada al servidor MQTT", Toast.LENGTH_SHORT).show();

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.d("MQTT", "Conexion Perdida");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    runOnUiThread(() -> textView.setText(payload));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.d("MQTT", "Entrega Completa");
                }
            });
        }catch (MqttException e){
            e.printStackTrace();
        }

        botonEnvio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mensaje = editTextMessage.getText().toString();
                try {
                    if (mqttClient != null && mqttClient.isConnected()){
                        mqttClient.publish(Topico, mensaje.getBytes(), 0, false);
                        textView.append("\n - "+ mensaje);
                        Toast.makeText(MainActivity.this, "Mensaje Enviado", Toast.LENGTH_SHORT).show();
                    }else {
                        Toast.makeText(MainActivity.this, "Error: No se pudo enviar el mensaje. La conexion MQTT no esta activada", Toast.LENGTH_SHORT).show();
                    }
                }catch (MqttException e){
                    e.printStackTrace();
                }
            }
        });
    }

    //Metodo Enviar Datos
    public void enviarDatosFirestore(View view){
        //Obtenemos los campos ingresados en el formulario
        String codigo = txtCodigo.getText().toString();
        String direccion = txtDireccion.getText().toString();
        String nombre = txtNombre.getText().toString();
        String tipoVuelo = spTipo.getSelectedItem().toString();

        //Creamos un mapa con los datos a enviar
        Map<String, Object> vuelo = new HashMap<>();
        vuelo.put("codigo", codigo);
        vuelo.put("direccion", direccion);
        vuelo.put("nombre", nombre);
        vuelo.put("tipoVuelo", tipoVuelo);

        //Enviamos los datos a Firestore
        db.collection("vuelos")
                .document(codigo)
                .set(vuelo).addOnSuccessListener(aVoid -> {
                    Toast.makeText(MainActivity.this, "Datos enviados a Firestore correctamente", Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e ->{
                    Toast.makeText(MainActivity.this, "Error al enviar datos a Firestore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    //Botos que carga la lista
    public void CargaLista(View view){
        CargarListaFirestore();
    }

    //Metodo Cargar Lista
    public void CargarListaFirestore(){
        //Obtenemos la instancia de Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        //Hacemos una consulta a la coleccion llamada "vuelos"
        db.collection("vuelos")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            //Si la consulta es exitosa, procesamos los documentos obtenidos
                            //Creando una lista para almacenar las cadenas de informacion de vuelos
                            List<String> listaVuelos = new ArrayList<>();
                            //Recorre todos los datos obtenidos ordenandolos en la lista
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String linea = "|| " + document.getString("codigo") + " ||" +
                                        document.getString("direccion") + " ||" +
                                        document.getString("nombre");
                                listaVuelos.add(linea);
                            }
                            //Crear un ArrayAdapter con la lista de vuelos
                            //y establecer el adaptador en el LisView
                            ArrayAdapter<String> adaptador = new ArrayAdapter<>(
                                    MainActivity.this,
                                    android.R.layout.simple_list_item_1,
                                    listaVuelos
                            );
                            lista.setAdapter(adaptador);
                        }else {
                            //Se imprimira en consola si hay errores al traer los datos
                            Log.e("TAG", "Error al obtener datos de Firestore", task.getException());
                        }
                    }
                });
    }
}