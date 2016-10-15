package mvherzog.blinkmap_dataflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
                                                               GoogleApiClient.OnConnectionFailedListener,
                                                               LocationListener
{
    Button btnConnect, btnDisconnect;
    public static final String TAG = MainActivity.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final int REQUEST_PERMISSION_FINE_LOCATION = 1;
    private final String ADAFRUIT_NAME = "Adafruit Bluefruit LE";
    private GoogleApiClient client;
    private LocationRequest request;
    private BluetoothAdapter adapter;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    public BluetoothDevice adafruit;
    BluetoothSocket socket = null;
    public static String EXTRA_ADDRESS;
    public static final UUID ADA_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.onResume();
        setContentView(R.layout.activity_main);

        client = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(AppIndex.API).build();

        request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(100);

        client.connect();

        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);

        btnConnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                sendData();
            }
        });

//        btnDisconnect.setOnClickListener(new View.OnClickListener()
//        {
//            @Override
//            public void onClick(View v)
//            {
//                disconnect(); //close connection
//            }
//        });

        initializeAdapter();
        if (adapterInitialized())
        {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(receiver, filter);
            if (isBLEPaired(ADAFRUIT_NAME))
            {
                Log.d(TAG, "BLE is paired");
                EXTRA_ADDRESS = adafruit.getAddress();
                try
                {
                    socket = adafruit.createInsecureRfcommSocketToServiceRecord(ADA_UUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    socket.connect();
                }
                catch(IOException io){
                    Log.d(TAG, io.getMessage());
                }
//                setLEDConnection(adafruit);
            }
        }
    }

    private void sendData()
    {
        Log.d(TAG, "send data");
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

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    //region Location Services
    @Override
    public void onLocationChanged(Location location)
    {
        handleNewLocation(location);
    }

    private void handleNewLocation(Location location)
    {
        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();
        // TODO: 10/11/2016 Send Lat & Long info to LED, so it can be sent to Adafruit
        // TODO: Follow tutorial (http://www.instructables.com/id/Android-Bluetooth-Control-LED-Part-2/?ALLSTEPS)
        Toast.makeText(MainActivity.this, "Latitude= " + currentLatitude + "\n" + "Longitude= " + currentLongitude, Toast.LENGTH_LONG).show();
        Log.i(TAG, "CurrentLat: " + currentLatitude);
        Log.i(TAG, "CurrentLon: " + currentLongitude);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (client.isConnected())
        {
            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
            client.disconnect();
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        client.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        Log.i(TAG, "Location Services connected");

//        Location location = LocationServices.FusedLocationApi.getLastLocation(client);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, request, this);
        }
        else
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_FINE_LOCATION);
        }
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.i(TAG, "Location Serviced suspended. Please reconnect");
        onDestroy();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    {
        if (connectionResult.hasResolution())
        {
            try
            {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            }
            catch (IntentSender.SendIntentException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
            onDestroy();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case REQUEST_PERMISSION_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Toast.makeText(MainActivity.this, "Accessing location...", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(MainActivity.this, "Location permissions denied", Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();
        client.connect();
    }

    @Override
    public void onStop()
    {
        Log.d(TAG, "onStop()");
//        unregisterReceiver(receiver);
        client.disconnect();
        super.onStop();
    }
    //endregion

    //region BLE Connection Methods & Properties

    /**
     * Checks Adafruit connection
     *
     * @return true if found Adafruit, false otherwise
     */
    private boolean isBLEPaired(String name)
    {
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                if (device.getName().equals(name))
                {
                    adafruit = device;
                    return true;
                }
            }
        }
        return false;
    }

    public void setLEDConnection(BluetoothDevice device)
    {
        Intent ledIntent = new Intent(MainActivity.this, LED.class);
        //First argument is probably wrong
        ledIntent.putExtra(EXTRA_ADDRESS, device.getAddress());
        startActivity(ledIntent);
    }

    @Override
    protected void onDestroy()
    {
        Log.d(TAG, "onDestroy()");
        unregisterReceiver(receiver);
        super.onDestroy();
        if(adapter != null){
            adapter.cancelDiscovery();
        }
    }

    private void pairDevice(BluetoothDevice device)
    {
        try
        {
            Method method = device.getClass().getMethod("createBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
            adafruit = device;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Initializes BluetoothAdapter and creates BLUETOOTH_REQUEST_ENABLE intent
     */
    private void initializeAdapter()
    {
        adapter = BluetoothAdapter.getDefaultAdapter();



        adapter.startDiscovery();
        if (adapter != null)
        {
            if (!adapter.isEnabled())
            {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
        else
        {
            Toast.makeText(MainActivity.this, "Bluetooth Device unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean adapterInitialized()
    {
        return !(adapter == null) && adapter.isEnabled();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
            {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON)
                {
                    Log.d(TAG, "Enabled");
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
            {
                devices = new ArrayList<>();
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                Intent newIntent = new Intent(MainActivity.this, MainActivity.class);
                newIntent.putParcelableArrayListExtra("device.list", devices);
                startActivity(newIntent);
            }
            else if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null)
                {
                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE)
                    {
                        Log.d(TAG, "Found " + device.getName());
                        devices.add(device);
                    }
                }

                for (BluetoothDevice foundDevice : devices)
                {
                    if (foundDevice.getName().equals(ADAFRUIT_NAME))
                    {
                        pairDevice(foundDevice);
                    }
                }
            }
        }
    };
    //endregion
}


