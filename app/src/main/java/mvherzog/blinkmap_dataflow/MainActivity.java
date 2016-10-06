package mvherzog.blinkmap_dataflow;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
//import android.view.View;
//import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
//import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.location.LocationListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    private GoogleApiClient mGoogleApiClient;
    public static final String TAG = MainActivity.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final int REQUEST_PERMISSION_FINE_LOCATION = 1;
    private LocationRequest mLocationRequest;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice dev;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.onResume();
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                Log.i(TAG, "ON_CREATE.ADAPTER-ENABLED");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }

            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals("Adafruit Bluefruit LE")) {
                        dev = device;
                        Log.i(TAG, "ON_CREATE.DEVICE-SET");
                    }
                }
            }
            ConnectionThread mConnectionThread = new ConnectionThread(dev);
            mConnectionThread.start();
            Log.i(TAG, "ON_CREATE.CONNECTION-START");
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();


        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds

        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "location changed");
        handleNewLocation(location);
    }

    private void handleNewLocation(Location location) {
        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();
        Log.d(TAG, "CurrentLat: " + currentLatitude);
        Log.d(TAG, "CurrentLon: " + currentLongitude);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "Location Services connected");

        Location location = null;
        if (location == null) {
            location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            handleNewLocation(location);
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_FINE_LOCATION);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location Serviced suspended. Please reconnect");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Permission Granted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                }
        }
    }

    /**
     * Created by mherzog on 10/3/2016.
     */

    private class ConnectionThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice dev;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        public ConnectionThread(BluetoothDevice device) {
            Log.i(TAG, "CONNECTION.CONSTRUCTOR");
            BluetoothSocket tmp = null;
            dev = device;
            Log.i(TAG, "DEV.NAME = " + dev.getName());
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
            }
            socket = tmp;
        }

        public void run() {
            Log.i(TAG, "CONNECTION.RUN");
            mBluetoothAdapter.cancelDiscovery();
            try {
                socket.connect();
                Log.i(TAG, "SOCKET::::::::::" + socket.getInputStream().toString());
            } catch (IOException connectException) {
                try {
                    socket.close();
                } catch (IOException closeException) {
                }
                return;
            }
            ConnectedThread ct = new ConnectedThread(socket);
            ct.start();
            ct.run();
        }

        public void cancel() {
            Log.i(TAG, "CONNECTION.CANCEL");
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket bsocket;
        private final InputStream input;
        private final OutputStream output;

        public ConnectedThread(BluetoothSocket socket) {
            Log.i(TAG, "CONNECTED.CONSTRUCTOR");
            bsocket = socket;
            InputStream is = null;
            OutputStream os = null;
            try {
                is = socket.getInputStream();
                Log.i(TAG, "****IS: " + is.toString());
                os = socket.getOutputStream();
                Log.i(TAG, "****OS: " + os.toString());
            } catch (IOException io) {

            }
            input = is;
            output = os;
            Log.i(TAG, "***BEGIN WRITE");
            this.write(input.toString().getBytes());
            Log.i(TAG, "***END WRITE");
        }

        public void run() {
            Log.i(TAG, "CONNECTED.RUN");
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += input.read(buffer, bytes, buffer.length - bytes);
                    for (int i = begin; i < bytes; i++) {
                        if (buffer[i] == "#".getBytes()[0]) {
                            handler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            Log.i(TAG, "***writing");
                            output.write(bytes);
                            Log.i(TAG, "***end_writing");
                            if (i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException io) {
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            Log.i(TAG, "CONNECTED.WRITE");
            try {
                output.write(bytes);
            } catch (IOException io) {

            }
        }

        public void cancel() {
            Log.i(TAG, "CONNECTED.CANCEL");
            try {
                bsocket.close();
            } catch (IOException io) {

            }
        }

        android.os.Handler handler = new android.os.Handler() {
            @Override
            public void handleMessage(android.os.Message msg) {
                Log.i(TAG, "IN HANDLE_MESSAGE");
                byte[] writebuf = (byte[]) msg.obj;
                int begin = msg.arg1;
                int end = msg.arg2;
                Log.d(TAG, "*****Message: " + msg.toString());

                switch (msg.what) {
                    case 1:
                        String writeMsg = new String(writebuf);
                        writeMsg.substring(begin, end);
                        break;

                }
            }
        };
    }
}


