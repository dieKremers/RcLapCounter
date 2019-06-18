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

    Boolean active = false;
    int lapCounter = 0;
    int tickTime;
    long lastTimestamp = 0;
    long deadline;
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
    ArrayList<ResultModel> resultList = new ArrayList<>();
    Queue<String> globalReadQueue = new LinkedList<>();

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
        tickTime = 100;

        btnStart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startTraining_ButtonPressed();      //method to turn on
            }
        });
        lapCounter = 0;

        AsyncTask.execute(lapCounterThread);
    }

    private void startTraining_ButtonPressed() {
        if (!active) {
            startTraining();
        }
        else
        {
            stopTraining();
        }
    }

    private void stopTraining(){
        active = false;
        btnStart.setText("START TRAINING");
    }
    private void startTraining()
    {
        int mins = Integer.parseInt( minutes.getText().toString() );
        int secs = Integer.parseInt( seconds.getText().toString() );
        int milliseconds = (secs*1000) + (mins*60*1000);
        deadline = System.currentTimeMillis() + milliseconds;
        //read already sent data from Bluetooth and clear the Queue afterwards
        readFromBluetooth( 500 );
        globalReadQueue.clear();
        resultList.clear();
        lapCounter = 0;
        btnStart.setText("STOP TRAINING");
        active = true;
    }

    public void newTimeReceived(){
        String text = globalReadQueue.remove();
        text = text.replaceAll("[^0-9]", "");
        System.out.println(text);
        if( text.length() > 3 )
        {
            long time_ms = Long.parseLong(text);
            if( lapCounter > 0 ) //First Time is not a Lap but the initial Start-Time
            {
                double time_s = (time_ms - lastTimestamp) / 1000.0;
                addNewLap("Lap",  time_s);
                final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, resultList);
                results.setAdapter(adapter);
            }
            lastTimestamp = time_ms;
            lapCounter++;
            if( isTimeElapsed() ){
                stopTraining();
            }
        }
    }

    private void addNewLap(String text, double time){
        resultList.add(new ResultModel(text, lapCounter, time));
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    private final Runnable lapCounterThread = new Runnable() {
        @Override
        public void run() {
            boolean elapsedNotificationPlayed = false;
            while (true) {
                if (active) {
                      if ( readFromBluetooth( 100 ) ){ //newDataReceived) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    newTimeReceived();
                                }
                            });
                      }
                      runOnUiThread(new Runnable() {
                          @Override
                          public void run() {
                              updateTimeView();
                          }
                      });
                      if( !elapsedNotificationPlayed && isTimeElapsed() ){
                          //TODO Alarm ausgeben
                      }

                }
                elapsedNotificationPlayed = true;
                try {
                    Thread.sleep(tickTime);
                } catch (InterruptedException e) {
                    msg("Error");
                }
            }
        }
    };

    /**
     * Returns true if new Data was available.
     * Last result in globalReadQueue
     * @return true if new Data was received
     */
    private boolean readFromBluetooth(int timeout_ms){
        long deadline = System.currentTimeMillis() + timeout_ms;
        boolean newDataReceived = false;
        try{
            byte[] buffer = new byte[256];
            while( deadline > System.currentTimeMillis() ){
                int bytes = btSocket.getInputStream().available();
                if( bytes > 0 ){
                    Thread.sleep(100); //wait for complete message to arrive
                    bytes = btSocket.getInputStream().available();
                    bytes = btSocket.getInputStream().read(buffer);            //read bytes from input buffer
                    newDataReceived = true;
                }
                if( newDataReceived) {
                    globalReadQueue.add( new String(buffer, 0, bytes) );
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

    private void updateTimeView()
    {
        int milliseconds = (int)(deadline - System.currentTimeMillis());
        String mins = String.valueOf((milliseconds/1000) / 60);
        String secs = String.valueOf((milliseconds/1000) % 60);
        minutes.getText().clear();
        minutes.getText().append(mins);
        seconds.getText().clear();
        seconds.getText().append(secs);
    }

    private boolean isTimeElapsed(){
        if( active )
        {
            if(deadline < System.currentTimeMillis() ){
                return true;
            }
        }
        return false;
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
