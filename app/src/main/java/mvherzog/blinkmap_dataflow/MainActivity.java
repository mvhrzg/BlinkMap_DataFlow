package mvherzog.blinkmap_dataflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
                                                               GoogleApiClient.OnConnectionFailedListener,
                                                               LocationListener
{
    private GoogleApiClient client;
    public static final String TAG = MainActivity.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final int REQUEST_PERMISSION_FINE_LOCATION = 1;
    private LocationRequest request;
    private BluetoothAdapter adapter;
    private BluetoothDevice device;
    private ParcelUuid[] uuids;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.onResume();
        setContentView(R.layout.activity_main);

        adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter != null)
        {
            if (!adapter.isEnabled())
            {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }

            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            if (pairedDevices.size() > 0)
            {
                for (BluetoothDevice device : pairedDevices)
                {
                    if (device.getName().equals("Adafruit Bluefruit LE"))
                    {
                        this.device = device;
                        this.device.fetchUuidsWithSdp();

                        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

                        Log.d(TAG, "Device paired= " + this.device.getName() + " : " + this.device.getAddress());
                        Log.d(TAG, "Device uuids= " + this.device.getUuids());
                    }
                }
            }
            ConnectionThread connection = new ConnectionThread(this.device);
            connection.start();
        }

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
    }

    @Override
    public void onLocationChanged(Location location)
    {
        handleNewLocation(location);
    }

    private void handleNewLocation(Location location)
    {
        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();
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

        Location location = LocationServices.FusedLocationApi.getLastLocation(client);
        handleNewLocation(location);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, request, this);
        }
        else
        {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_PERMISSION_FINE_LOCATION);
        }
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.i(TAG, "Location Serviced suspended. Please reconnect");
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
                    Toast.makeText(MainActivity.this, "Permission Granted!", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
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
        super.onStop();
        client.disconnect();
    }

    /**
     * Created by mherzog on 10/3/2016.
     */




    private class ConnectionThread extends Thread
    {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;
        private UUID CONNECTION_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        public ConnectedThread connected;

        ConnectionThread(BluetoothDevice device)
        {
            Log.i(TAG, "CONNECTION.CONSTRUCTOR");
            BluetoothSocket tmp_socket = null;
//            CONNECTION_UUID = UUID.fromString(device.ACTION_UUID);
            this.device = device;
            Log.i(TAG, "Device = " + this.device.getName());
            try
            {
                Method getUuidsMethod = BluetoothAdapter.class.getDeclaredMethod("getUuids", null);
                uuids = (ParcelUuid[]) getUuidsMethod.invoke(adapter, null);
                for (ParcelUuid uuid : uuids)
                {
                    Log.d(TAG, "UUID: " + uuid.getUuid().toString());
                }
                tmp_socket = this.device.createInsecureRfcommSocketToServiceRecord(CONNECTION_UUID);//this.device.createRfcommSocketToServiceRecord(CONNECTION_UUID);
                Log.d(TAG, "tmp_socket uuid " + CONNECTION_UUID);
            }
            catch (IOException e)
            {
                Log.d(TAG, e.getMessage());
            }
            catch (NoSuchMethodException e)
            {
                e.printStackTrace();
            }
            catch (IllegalAccessException e)
            {
                e.printStackTrace();
            }
            catch (InvocationTargetException e)
            {
                e.printStackTrace();
            }
            socket = tmp_socket;
        }

        public void run()
        {
            Log.i(TAG, "CONNECTION.RUN");
            adapter.cancelDiscovery();
            try
            {
                socket.connect();
                Log.i(TAG, "Socket.connect()");
            }
            catch (IOException connectException)
            {
                Log.i(TAG, "catch1");
                cancel();
                return;
            }

            connected = new ConnectedThread(socket);
            Log.d(TAG, "connecting");
            connected.start();
        }

        public void cancel()
        {
            try
            {
                socket.close();
                Log.i(TAG, "socket.close()");
            }
            catch (IOException io)
            {
                Log.d(TAG, io.getMessage());
            }
        }
    }

    private class ConnectedThread extends Thread
    {
        private final BluetoothSocket socket;
        private final InputStream input;
        private final OutputStream output;

        ConnectedThread(BluetoothSocket socket)
        {
            Log.i(TAG, "CONNECTED.CONSTRUCTOR");
            this.socket = socket;
            InputStream is = null;
            OutputStream os = null;
            try
            {
                is = socket.getInputStream();
                Log.i(TAG, "****IS: " + is.toString());
                os = socket.getOutputStream();
                Log.i(TAG, "****OS: " + os.toString());
            }
            catch (IOException io)
            {
                Log.d(TAG, io.getMessage());
            }
            input = is;
            output = os;
        }

        public void run()
        {
            Log.i(TAG, "CONNECTED.RUN");
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true)
            {
                try
                {
                    bytes += input.read(buffer, bytes, buffer.length - bytes);
                    for (int i = begin; i < bytes; i++)
                    {
                        if (buffer[i] == "#".getBytes()[0])
                        {
                            handler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            Log.i(TAG, "***writing");
                            write(buffer);
                            Log.i(TAG, "***end_writing");
                            if (i == bytes - 1)
                            {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                }
                catch (IOException io)
                {
                    break;
                }
            }
        }

        void write(byte[] bytes)
        {
            Log.i(TAG, "CONNECTED.WRITE");
            try
            {
                output.write(bytes);
            }
            catch (IOException io)
            {
                Log.d(TAG, io.getMessage());
            }
        }

        public void cancel()
        {
            Log.i(TAG, "CONNECTED.CANCEL");
            try
            {
                socket.close();
            }
            catch (IOException io)
            {
                Log.d(TAG, io.getMessage());
            }
        }

        Handler handler = new Handler()
        {
            @Override
            public void handleMessage(Message msg)
            {
                Log.i(TAG, "HANDLE_MESSAGE");
                byte[] buffer = (byte[]) msg.obj;
                int begin = msg.arg1;
                int end = msg.arg2;
                Log.d(TAG, "*****Message: " + msg.toString());

                switch (msg.what)
                {
                    case 1:
                        String writeMsg = new String(buffer);
                        writeMsg.substring(begin, end);
                        break;
                }
                super.handleMessage(msg);
            }
        };
    }

}


