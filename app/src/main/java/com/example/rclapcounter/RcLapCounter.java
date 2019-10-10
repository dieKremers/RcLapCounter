package com.example.rclapcounter;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import android.widget.Button;

public class RcLapCounter extends AppCompatActivity {

    Boolean active = false;
    int tickTime;
    private long lastRaceStarted;
    long deadline;
    TrainingResultList results = new TrainingResultList();
    private String originalMinutes;
    private String originalSeconds;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    ConnectBT btConnector = null;
    private boolean isBtConnected = false;
    Button btnStart;
    ListView listView_Results;
    EditText editText_Minutes, editText_Seconds;
    TextView textView_LastResultsSummary;
    MediaPlayer mediaPlayer = null;

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
        listView_Results = findViewById(R.id.list_lapTimes);
        editText_Minutes = findViewById(R.id.entry_minutes);
        editText_Seconds = findViewById(R.id.entry_seconds);
        textView_LastResultsSummary = findViewById(R.id.tvLastResults);

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

        AsyncTask.execute(lapCounterThread);
    }

    private void startTraining_ButtonPressed() {
        if (!active) {
            //TODO: Countdown
            mediaPlayer = MediaPlayer.create(this, R.raw.startbeep);
            mediaPlayer.start(); // no need to call prepare(); create() does that for you
            while( mediaPlayer.isPlaying() )
            {
                // wait until finished
            }
            startTraining();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        else
        {
            stopTraining();
        }
    }

    private void stopTraining(){
        active = false;
        long duration = System.currentTimeMillis() - lastRaceStarted;
        long mins = duration / 1000 / 60;
        long secs = (duration / 1000) % 60;
        String resultTime = String.format("Time:\t %2d:%02d min", mins, secs);
        String resultLaps = String.format("Laps: %2d", results.getNumberOfLaps());
        String resultFastestLap = String.format("Lap %2d: %.2f sec", results.getFastetLapNumber(), results.getFastestLapTime_sec());
        String resultString = resultTime+"\n"+resultLaps+"\n"+resultFastestLap;
        textView_LastResultsSummary.setText(resultString);
        editText_Minutes.getText().clear();
        editText_Minutes.getText().append(originalMinutes);
        editText_Seconds.getText().clear();
        editText_Seconds.getText().append(originalSeconds);
        btnStart.setText("START");
        if( mediaPlayer != null ) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void startTraining()
    {
        originalMinutes = editText_Minutes.getText().toString();
        originalSeconds = editText_Seconds.getText().toString();
        int mins = Integer.parseInt( originalMinutes );
        int secs = Integer.parseInt( originalSeconds );
        int milliseconds = (secs*1000) + (mins*60*1000);
        lastRaceStarted = System.currentTimeMillis();
        deadline =  lastRaceStarted + milliseconds;
        //read already sent data from Bluetooth and clear the Queue afterwards
        readFromBluetooth( 500 );
        globalReadQueue.clear();
        results.clear();
        btnStart.setText("STOP");
        active = true;
    }

    public void newTimeReceived(){
        String text = globalReadQueue.remove();
        text = text.replaceAll("[^0-9]", "");
        System.out.println(text);
        if( text.length() > 3 )
        {
            long time_ms = Long.parseLong(text);
            results.notifyCarPassedSensor(time_ms);
            final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, results.getResults());
            listView_Results.setAdapter(adapter);
            listView_Results.smoothScrollToPosition(adapter.getCount()-1);
            if( isTimeElapsed() ){
                stopTraining();
            }
        }
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
                          elapsedNotificationPlayed = true;
                          runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  playTimeElapsedNotification();
                              }
                          });
                      }
                }
                else{
                    elapsedNotificationPlayed = false;
                }

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
        editText_Minutes.getText().clear();
        editText_Minutes.getText().append(mins);
        editText_Seconds.getText().clear();
        editText_Seconds.getText().append(secs);
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

    private void playTimeElapsedNotification()
    {
        mediaPlayer = MediaPlayer.create(this, R.raw.timeisover);
        mediaPlayer.start();
        while(mediaPlayer.isPlaying()){

        }
        mediaPlayer.release();
        mediaPlayer = null;
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
