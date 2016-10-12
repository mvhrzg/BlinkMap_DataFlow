package mvherzog.blinkmap_dataflow;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;
import java.io.IOException;
import java.util.UUID;

public class LED extends AppCompatActivity
{
    private ProgressDialog progress;
    BluetoothAdapter adapter = null;
    BluetoothSocket socket = null;
    private boolean isBLEConnected = false;
    public static final UUID adaUUID = UUID.fromString(MainActivity.EXTRA_ADDRESS);
    String mainAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Intent mainIntent = getIntent();
        mainAddress = mainIntent.getStringExtra(MainActivity.EXTRA_ADDRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_led);
        
//        ConnectBLE.execute(getSystemService());

    }
    public class ConnectBLE extends AsyncTask<Void, Void, Void>
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected
        
        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(LED.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }
        
        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (socket == null || !isBLEConnected)
                {
                    adapter = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dev = adapter.getRemoteDevice(mainAddress);//connects to the device's address and checks if it's available
                    socket = dev.createInsecureRfcommSocketToServiceRecord(adaUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    socket.connect();//start connection
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
                isBLEConnected = true;
            }
            progress.dismiss();
        }

        private void msg(String s)
        {
            Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
        }

        private void Disconnect()
        {
            if (socket!=null) //If the btSocket is busy
            {
                try
                {
                    socket.close(); //close connection
                }
                catch (IOException e)
                { msg("Error");}
            }
            finish(); //return to the first layout
        }
    }
    
}
