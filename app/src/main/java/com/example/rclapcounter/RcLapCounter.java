package com.example.rclapcounter;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import android.widget.Button;

public class RcLapCounter extends AppCompatActivity {

    Boolean ledState = false;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    ConnectBT btConnector = null;
    private boolean isBtConnected = false;
    Button btnStart;
    ListView results;
    EditText minutes, seconds;
    ArrayList<ResultModel> resultList = new ArrayList<ResultModel>();
    Queue<String> globalReadQueue = new LinkedList<String>();

    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(RcLapCounter.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //receive the address of the bluetooth device
        Intent newint = getIntent();
        address = newint.getStringExtra("EXTRA_ADDRESS");

        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.buttonStart);
        results = findViewById(R.id.list_lapTimes);
        minutes = findViewById(R.id.entry_minutes);
        seconds = findViewById(R.id.entry_seconds);

        btConnector = new ConnectBT();
        btConnector.execute();

        btnStart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startTraining();      //method to turn on
            }
        });
    }

    private void startTraining() {
        Integer min, sec;
        min = Integer.parseInt(minutes.getText().toString());
        sec = Integer.parseInt(seconds.getText().toString());
        switchLed();
        String text = "";
        if( readFromBluetooth(500) ){
            text = globalReadQueue.remove();
        }
        else{
            text = "no new data";
        }
        addNewLap(text,  (min+sec)/60.0);
        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, resultList);
        results.setAdapter(adapter);
    }

    private void addNewLap(String text, double time){
        resultList.add(new ResultModel(text, resultList.size()+1, time));
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    private void switchLed()
    {
        if (btSocket!=null)
        {
            try
            {
                String message;
                if(ledState==true){
                    message="0";
                    ledState = false;
                }
                else{
                    message="1";
                    ledState = true;
                }
                btSocket.getOutputStream().write(message.toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }

    /**
     * Returns true if new Data was available.
     * Last result in globalReadQueue
     * @return
     */
    private boolean readFromBluetooth(int timeout_ms){
        long deadline = System.currentTimeMillis() + timeout_ms;
        boolean newDataReceived = false;
        try{
            byte[] buffer = new byte[256];
            while( deadline > System.currentTimeMillis() ){
                int bytes = btSocket.getInputStream().available();
                while( bytes > 0 ){
                    bytes = btSocket.getInputStream().read(buffer);            //read bytes from input buffer
                    globalReadQueue.add( new String(buffer, 0, bytes) );
                    newDataReceived = true;
                    Thread.sleep(50);
                    bytes = btSocket.getInputStream().available();
                }
                if( newDataReceived) {
                    break;
                }
            }
        }
        catch (IOException |InterruptedException e)
        {
            msg("Error");
        }
        return newDataReceived;
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout
    }

}
