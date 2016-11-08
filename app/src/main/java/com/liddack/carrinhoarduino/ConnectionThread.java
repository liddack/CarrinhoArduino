package com.liddack.carrinhoarduino;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Liddack on 04/11/2016.
 */

public class ConnectionThread extends Thread {
    BluetoothSocket btSocket = null;
    String enderecoCarrinho = null;
    boolean rodando = false;
    String myUUID = "";
    OutputStream output;
    boolean deuPiti = false;

    // Construtor
    public ConnectionThread(String enderecoMac, String uuid) {
        this.enderecoCarrinho = enderecoMac;
        this.myUUID = uuid;

    }

    public void startThread() {
        Log.i("ConnectionThread", "Chamada iniciada");
        if (!rodando) {
            //  Anuncia que a thread está sendo executada.
            this.rodando = true;
            start();
        }
    }

    /*  O método run() contem as instruções que serão efetivamente realizadas
    em uma nova thread.
     */
    @Override
    public void run() {
        deuPiti = false;
        try {
            // Configura o adaptador Bluetooth
            BluetoothAdapter meuBluetooth = BluetoothAdapter.getDefaultAdapter();
            /* Obtem uma representação do dispositivo Bluetooth com
             * endereço btDevAddress.*/
            BluetoothDevice carrinho = meuBluetooth.getRemoteDevice(enderecoCarrinho);
            // Cria um socket Bluetooth.
            btSocket = carrinho.createRfcommSocketToServiceRecord(UUID.fromString(myUUID));
            /*  Envia ao sistema um comando para cancelar qualquer processo
             *  de descoberta em execução.
             */
            meuBluetooth.cancelDiscovery();
            /*  Solicita uma conexão ao dispositivo cujo endereço é
                btDevAddress.
                Permanece em estado de espera até que a conexão seja
                estabelecida.
            */
            if (btSocket != null) btSocket.connect();
            Log.e("Conexão", "Conectado!");
        } catch (IOException e) {
            /*  Caso ocorra alguma exceção, exibe o stack trace para debug.
             *  Envia um código para a Activity principal, informando que
             *  a conexão falhou. */
            e.printStackTrace();
            deuPiti = true;
            toMainActivity("---N".getBytes());
        }

        if(btSocket != null && !deuPiti) {

            /*  Envia um código para a Activity principal informando que a
            a conexão ocorreu com sucesso.
             */
            toMainActivity("---S".getBytes());

            try {
                /*  Obtem referências para os fluxos de entrada e saída do
                socket Bluetooth.
                 */
                //input = btSocket.getInputStream();
                output = btSocket.getOutputStream();

                /*  Cria um byte array para armazenar temporariamente uma
                mensagem recebida.
                    O inteiro bytes representará o número de bytes lidos na
                última mensagem recebida.
                 */
                //byte[] buffer = new byte[1024];
                //int bytes;


            } catch (IOException e) {

                /*  Caso ocorra alguma exceção, exibe o stack trace para debug.
                    Envia um código para a Activity principal, informando que
                a conexão falhou.
                 */
                e.printStackTrace();
                toMainActivity("---N".getBytes());
            }
        }


    }

    /*  Utiliza um handler para enviar um byte array à Activity principal.
        O byte array é encapsulado em um Bundle e posteriormente em uma Message
    antes de ser enviado.
     */
    private void toMainActivity(byte[] data) {
        Bundle bundle = new Bundle();
        Message message = new Message();
        bundle.putByteArray("data", data);
        message.setData(bundle);
        MainActivity.handler.sendMessage(message);
    }

    //  Método utilizado pela Activity principal para encerrar a conexão
    public void cancel() {
        try {
            rodando = false;
            btSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        rodando = false;
    }
}
