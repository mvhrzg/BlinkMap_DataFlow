package mvherzog.blinkmap_dataflow;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
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
    Button btnConnect, btnDisconnect;
    BluetoothAdapter adapter = null;
    BluetoothSocket socket = null;
    private boolean isBLEConnected = false;
    public static final UUID ADA_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    String address;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Intent mainIntent = getIntent();
        address = mainIntent.getStringExtra(MainActivity.EXTRA_ADDRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_led);

        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);

        new ConnectBLE().execute(); //Call the class to connect
        btnConnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                sendData();
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                disconnect(); //close connection
            }
        });
    }

    private void disconnect()
    {
        if (socket != null) //If the btSocket is busy
        {
            try
            {
                socket.close(); //close connection
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
        finish(); //return to the first layout
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private void sendData()
    {
        if (socket != null)
        {
            try
            {
                socket.getOutputStream().write("TO".toString().getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
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
                    BluetoothDevice dev = adapter.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    socket = dev.createInsecureRfcommSocketToServiceRecord(ADA_UUID);//create a RFCOMM (SPP) connection
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

    }

}
