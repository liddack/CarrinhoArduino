package com.liddack.carrinhoarduino;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static android.R.attr.action;
import static android.R.attr.dial;


public class MainActivity extends AppCompatActivity {
    // Configura o Adaptador Bluetooth
    BluetoothAdapter meuBluetooth = BluetoothAdapter.getDefaultAdapter();

    static ProgressBar spinner;
    static TextView connectedLabel;
    static ImageView statusIcon;
    ImageButton btnFrente;
    ImageButton btnRe;
    ImageButton btnDireita;
    ImageButton btnEsquerda;
    private static boolean CONECTADO = false;
    private static boolean FALHA_CONEXAO = false;
    private boolean BT_DESLIGADO = false;

    // Definindo constantes para intents e conexões Bluetooth
    public static int ENABLE_BLUETOOTH = 1;
    private static String ENDERECO_CARRINHO = "E0:76:D0:CF:8D:D1"; // Carrinho: 20:15:05:19:11:36; Payleven: E0:76:D0:CF:8D:D1
    private static String PIN_CARRINHO = "502682"; // 1234
    private static byte[] PIN_CARRINHO_BYTES = PIN_CARRINHO.getBytes();

    private BluetoothDevice carrinho;
    private AlertDialog alerta;

    private ConnectionThread connection;

    private static List<FalhaConexaoListener> falhaListeners = new ArrayList<FalhaConexaoListener>();
    private static List<EstadoConexaoListener> connectListeners = new ArrayList<EstadoConexaoListener>();

    public static void setEstadoConexao (boolean value) {
        CONECTADO = value;

        for (EstadoConexaoListener l : connectListeners) {
            l.onVariableChanged();
        }
    }

    public static void setFalhaConexao(boolean value) {
        FALHA_CONEXAO = value;

        for (FalhaConexaoListener l : falhaListeners) {
            l.onVariableChanged();
        }
    }

    public static void addBooleanConnectListener(EstadoConexaoListener l) {
        connectListeners.add(l);
    }

    public static void addBooleanFalhaListener(FalhaConexaoListener l) {
        falhaListeners.add(l);
    }

    // Configura um objeto para o vibrador do aparelho
    private Vibrator viber;
    long tempoVibracao = 100; // milissegundos

    // PRIMEIRAS INSTRUÇÕES A SEREM EXECUTADAS!
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * Definindo os botões de controle do carrinho
         */
        btnFrente = (ImageButton) findViewById(R.id.frente_controle_btn);
        btnRe = (ImageButton) findViewById(R.id.re_controle_btn);
        btnDireita = (ImageButton) findViewById(R.id.dir_controle_btn);
        btnEsquerda = (ImageButton) findViewById(R.id.esq_controle_btn);

        // Definindo instâncias de views
        spinner = (ProgressBar) findViewById(R.id.spinner);
        connectedLabel = (TextView) findViewById(R.id.conectado_a_label);
        statusIcon = (ImageView) findViewById(R.id.status_icon);

        // Criando uma  instância do motor de vibração do aparelho
        viber = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Registrando receptor de transmissões (BroadcastReceiver) de ações de Bluetooth
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        //if (Build.VERSION.SDK_INT >= 19) filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onStart() {
        super.onStart();

        addBooleanFalhaListener(new FalhaConexaoListener() {
            @Override
            public void onVariableChanged() {
                if (FALHA_CONEXAO) {
                    FALHA_CONEXAO = false;
                    spinner.setVisibility(View.GONE);
                    statusIcon.setImageResource(R.drawable.ic_error);
                    statusIcon.setVisibility(View.VISIBLE);
                    displayAlertDialog(4);
                }
            }
        });
        addBooleanConnectListener(new EstadoConexaoListener() {
            @Override
            public void onVariableChanged() {
                if (CONECTADO) {
                    alerta.dismiss();
                }
            }
        });
        /**
         * Isso faz o botão de seta pra cima responder quando
         * ele é pressionado e/ou solto.
         */
        btnFrente.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!CONECTADO) showToast("Carrinho não conectado");
                    else connection.write("w".getBytes());
                    viber.vibrate(tempoVibracao);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    if (CONECTADO) connection.write("nw".getBytes());
                }
                return false;
            }
        });

        /**
         * Isso faz o botão de seta pra baixo responder quando
         * ele é pressionado e/ou solto.
         */
        btnRe.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    viber.vibrate(tempoVibracao);
                    if (!CONECTADO) showToast("Carrinho não conectado");
                    else connection.write("s".getBytes());
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    if (CONECTADO) connection.write("ns".getBytes());
                }
                return false;
            }
        });

        /**
         * Isso faz o botão de seta pra direita responder quando
         * ele é pressionado e/ou solto.
         */
        btnDireita.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!CONECTADO) showToast("Carrinho não conectado");
                    else connection.write("a".getBytes());
                    viber.vibrate(tempoVibracao);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    if (CONECTADO) connection.write("na".getBytes());
                }
                return false;
            }
        });

        /**
         * Isso faz o botão de seta pra esquerda responder quando
         * ele é pressionado e/ou solto.
         */
        btnEsquerda.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!CONECTADO) showToast("Carrinho não conectado");
                    else connection.write("d".getBytes());
                    viber.vibrate(tempoVibracao);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    if (CONECTADO) connection.write("nd".getBytes());
                }
                return false;
            }
        });

        // Solicita a ativação do Bluetooth
        if (!meuBluetooth.isEnabled()) pedidoBluetooth();
    }

    // Inflando (exibindo) o menu na ActionBar
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                telaSobre();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    // Recebe o resultado da busca por dispositivos visíveis
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        // Este método é executado sempre que um novo broadcast for recebido
        public void onReceive(Context context, Intent intent) {
            //  Obtem o Intent que gerou a ação
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                displayState("Procurando o carrinho...");
                // Verifica se a ação corresponde à descoberta de um novo dispositivo
            } if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Obtem um objeto que representa o dispositivo Bluetooth descoberto.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d("pesquisa", "Dispositivo encontrado: " + device.getName() + " - " + device.getAddress());
                // Verifica se o endereço MAC do dispositivo descoberto corresponde ao do carrinho
                if (device.getAddress().equals(ENDERECO_CARRINHO)) {
                    // Seta o dispositivo para uma variável global
                    Log.d("pesquisa", "##### Carrinho encontrado! #####");
                    carrinho = device;
                    meuBluetooth.cancelDiscovery();
                }
                // Verifica se o adaptador terminou o processo de descoberta
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Encontrou? - Verifica se o carrinho foi encontrado
                if (carrinho != null) {
                    // Tenta parear com o carrinho
                    /*if (Build.VERSION.SDK_INT < 19)*/ displayAlertDialog(2); /* Se a versão do Android não for compatível,
                                                                            /* exibe um diálogo de alerta de origem 2 */
                    //else parearAoCarrinho();
                } else {    // Se o carrinho não for encontrado
                    displayState("Carrinho não encontrado :(");
                    spinner.setVisibility(View.GONE);
                    statusIcon.setImageResource(R.drawable.ic_bluetooth_disabled);
                    statusIcon.setVisibility(View.VISIBLE);
                    displayAlertDialog(1);  // Exibe um diálogo de alerta de origem 1
                }
                // Verifica se o dispositivo requisita um pin
            } else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                // Verifica se a versão do Android no aparelho é compatível
                if (Build.VERSION.SDK_INT >= 19) {
                    // Se for, o app gerencia automaticamente a requisição
                    int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                    switch (variant) {
                        // Verifica se o que o dispositivo pede é um pin
                        case BluetoothDevice.PAIRING_VARIANT_PIN:
                            carrinho.setPin(PIN_CARRINHO_BYTES);
                            break;
                    }
                }
                // Verifica se o processo de pareamento com o carrinho terminou
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                // Estado anterior
                final int antes = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                // Estado atual
                final int agora = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                // Verifica se o carrinho foi pareado
                if (antes == BluetoothDevice.BOND_BONDING && agora == BluetoothDevice.BOND_BONDED) {
                    // Tenta se conectar ao carrinho
                    if (carrinhoTaPareado()) conectarAoCarrinho();
                    // Verifica se o pareamento falhou
                } else if (antes == BluetoothDevice.BOND_BONDING && agora == BluetoothDevice.BOND_NONE) {
                    displayAlertDialog(3); // Exibe um diálogo de alerta de origem 3
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int antes = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);
                int agora = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (antes == BluetoothAdapter.STATE_ON && agora == BluetoothAdapter.STATE_TURNING_OFF) {
                    if (connection != null) connection.cancel();
                    CONECTADO = false;
                    BT_DESLIGADO = true;
                    displayAlertDialog(6);
                } else if (antes == BluetoothAdapter.STATE_TURNING_ON && agora == BluetoothAdapter.STATE_ON) {
                    BT_DESLIGADO = false;
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                if (connection != null) connection.cancel();
                CONECTADO = false;
                if (!BT_DESLIGADO) displayAlertDialog(5);
                displayState("Carrinho desconectado");
            }
        }
    };

    public void pedidoBluetooth () {
        statusIcon.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        // Verifica se o aparelho suporta bluetooth
        if (meuBluetooth == null) showToast("Seu dispositivo não suporta Bluetooth.");
        else {
            displayState("Ligando Bluetooth...");
            // Verifica se o Bluetooth está desligado
            if (!meuBluetooth.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH);
            } else {
                // Se o Bluetooth estiver ligado
                conectarAoCarrinho();
            }
        }
    }


    private void conectarAoCarrinho() {
        if (!CONECTADO) {
            statusIcon.setVisibility(View.GONE);
            spinner.setVisibility(View.VISIBLE);
            // Verifica se o carrinho está pareado
            if (!carrinhoTaPareado()) {
                meuBluetooth.startDiscovery();  // Manda o adaptador pesquisar dispositivos visíveis.
                // O resultado é recebido pelo BroadcastReceiver
            } else {
                carrinho = meuBluetooth.getRemoteDevice(ENDERECO_CARRINHO);
                // Se conecta ao carrinho
                String uuid = carrinho.getUuids()[0].toString();
                displayState("Conectando-se ao carrinho...");
                connection = new ConnectionThread(ENDERECO_CARRINHO, uuid);
                connection.startThread();
            }
        }
    }

    public boolean carrinhoTaPareado() {
        // Valida o endereço MAC declarado
        if (BluetoothAdapter.checkBluetoothAddress(ENDERECO_CARRINHO)){
            // Cria um novo BluetoothDevice com o endereço declarado
            BluetoothDevice carrinho = meuBluetooth.getRemoteDevice(ENDERECO_CARRINHO);
            // Verifica se o carrinho está pareado
            if (carrinho.getBondState() == BluetoothDevice.BOND_BONDED) {
                return true;
            } /*else if (carrinho.getBondState() == BluetoothDevice.BOND_NONE) {

            }*/
        }
        return false;
    }

    private void parearAoCarrinho() {
        // Verifica se o objeto 'carrinho' é válido
        if (carrinho != null) {
            try {
                // Tenta se conectar com o carrinho
                Method method = carrinho.getClass().getMethod("createBond", (Class[]) null);
                method.invoke(carrinho, (Object[]) null);
                displayState("Pareando com o carrinho...");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Imprime o texto recebido na label 'Conectado a:'
    public void displayState (CharSequence text) {
        connectedLabel.setText(text);
    }

    // Apenas um meio pra facilitar a exibição de toasts
    public void showToast(CharSequence str) {
        Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
    }

    public void showToast(int resId) {
        Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_SHORT).show();
    }

    // Manuseia respostas de intents
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Verifica se o Bluetooth foi ativado ou não
        if(requestCode == ENABLE_BLUETOOTH) {
            if(resultCode == RESULT_CANCELED) {     // Se foi recusado ou não deu certo
                displayAlertDialog(0);              // Exibe um diálogo de alerta de origem 0
            } else if (resultCode == RESULT_OK) {   // Se foi ativado
                conectarAoCarrinho();
            }
        }
    }

    protected void displayAlertDialog (final int origem) {
        String title = "";
        String msg = "";
        String posBtn = "";
        String negBtn = "";
        int icon = 0;
       switch (origem) {
           case 0:
               // Caso o Bluetooth não tenha sido ligado ou se o pedido foi rejeitado
               icon = R.drawable.ic_report_problem;
               title = "Bluetooth necessário";
               msg = "Este aplicativo depende do Bluetooth ligado para funcionar. Ligar Bluetooth?";
               posBtn ="Sim";
               negBtn = "Sair do app";
               break;
           case 1:
               // Caso o carrinho não esteja pareado e não tenha sido encontrado na pesquisa Bluetooth
               icon = R.drawable.ic_bluetooth_disabled;
               title = "Carrinho não encontrado";
               msg = "O carrinho precisa estar ligado e próximo ao seu dispositivo para se conectar a ele.";
               posBtn ="Procurar de novo";
               negBtn = "Sair do app";
               break;
           case 2:
               // Caso a versão do Android não suporte inserção automática de PIN
               icon = R.drawable.ic_dialpad;
               title = "Pareamento necessário";
               msg = "Na próxima tela, insira o PIN " + PIN_CARRINHO + " para confirmar o pareamento.";
               posBtn ="Continuar";
               break;
           case 3:
               // Caso o pareamento tenha falhado
               icon = R.drawable.ic_error;
               title = "O pareamento falhou";
               msg = "Seu aparelho deve ser pareado com o carrinho para você poder controlá-lo.";
               posBtn = "Tentar de novo";
               negBtn = "Sair do app";
               break;
           case 4:
               // Caso haja uma falha eu uma tentativa de conexão
               icon = R.drawable.ic_error;
               title = "Falha na conexão";
               msg = "O carrinho precisa estar ligado e próximo ao seu dispositivo para se conectar a ele.";
               posBtn = "Tentar de novo";
               negBtn = "Sair do app";
               break;
           case 5:
               // Caso o carrinho tenha se desconectado
               icon = R.drawable.ic_error;
               title = "Carrinho desconectado";
               msg = "Houve uma falha na conexão e o carrinho se desconectou. Verifique se o carrinho está ligado e tente de novo.";
               posBtn = "Tentar de novo";
               negBtn = "Sair do app";
               break;
           case 6:
               // Caso o Bluetooth seja desligado enquanto o aplicativo roda
               icon = R.drawable.ic_bluetooth_disabled;
               title = "O Bluetooth foi desligado";
               msg = "Este aplicativo depende do Bluetooth ligado para funcionar. Ligar Bluetooth?";
               posBtn = "Tentar de novo";
               negBtn = "Sair do app";
               break;
        }

        if (alerta != null) alerta.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(icon);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(posBtn, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (origem == 0 || origem == 6) pedidoBluetooth();         // Se o usuário pediu pra ligar, é solicitada a ativação do Bluetooth de novo
                else if (origem == 1 || origem == 4 || origem == 5) conectarAoCarrinho(); /** Se o usuário confirmar, 1: é feita a pesquisa pelo carrinho novamente
                                                                            *                                         4 e 5: o app tenta se conectar ao carrinho de novo */
                else if (origem == 2 || origem == 3) parearAoCarrinho();   /* Se o usuário confirmar, 2: aparece a tela de inserção de pin
                                                                            *                         3: o pareamento é tentado de novo */
            }
        });
        if (origem != 2) {
            builder.setNegativeButton(negBtn, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
        }
        builder.setCancelable(false);
        alerta = builder.create();
        alerta.setCanceledOnTouchOutside(false);
        alerta.show();
    }

    // Mostra a tela de Sobre do app
    public void telaSobre() {
        final AlertDialog modal;
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.about_dialog, null));
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        modal = builder.create();
        modal.show();
    }

    // Abre o navegador na página do Github do autor
    public void linkAutor(View view) {
        Log.d("About", "Autor clicado");
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.github.com/liddack"));
        startActivity(browserIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (connection != null) connection.cancel();
        CONECTADO = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        conectarAoCarrinho();
    }

    @Override
    protected void onDestroy() {
        /* Linha obrigatória */
        super.onDestroy();
        // Cancela a thread de conexão, se houver alguma
        if (connection != null) connection.cancel();
        // Remove o filtro de descoberta de dispositivos do registro
        unregisterReceiver(receiver);
        // Desliga o Bluetooth
        meuBluetooth.disable();
    }

    // Conectou? - Lida com o resultado do pedido de conexão
    public static Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle = msg.getData();
            byte[] data = bundle.getByteArray("data");
            String dataString = new String(data);

            if(dataString.equals("---N")) {
                connectedLabel.setText("Problema na conexão");
                CONECTADO = false;
                setFalhaConexao(true);
            }
            else if(dataString.equals("---S")) {
                spinner.setVisibility(View.GONE);
                setEstadoConexao(true);
                statusIcon.setImageResource(R.drawable.ic_done);
                statusIcon.setVisibility(View.VISIBLE);
                connectedLabel.setText("Carrinho pronto");
            }

        }
    };

}
