package mvherzog.blinkmap_dataflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
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
import android.support.v4.content.LocalBroadcastManager;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
    public BluetoothDevice adafruit;
    public static String EXTRA_ADDRESS;
    public BluetoothGatt gatt;
    public double currentLat, currentLon;

    //Are these needed?
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    BluetoothSocket socket = null;
    public static final UUID ADA_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private OutputStream output;
    private InputStream input;
    private ConnectionThread ct;

    public NotificationReceiver nreceiver;
    public NotificationReader nreader;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.onResume();
        setContentView(R.layout.activity_main);
        //Might only work if native Notification
        nreceiver = new NotificationReceiver();
        nreader = new NotificationReader();
        IntentFilter filter = new IntentFilter();
        //or .Msg
        filter.addAction("mherzog.blinkmap_dataflow.NotificationReader.Msg");
        registerReceiver(onNotice,filter);
        //Brings up notification acess screen
        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        startActivity(intent);


        Log.i("Notification?", "Registering receiver");
        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));
        Log.i("Notification?", "After registering receiver");

        //Set up Google API client
        client = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(AppIndex.API).build();

        //Set up location requests
        request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(100);

        //Set up Google Maps listener HERE
        //TODO: Use 4 pinned tabs in Chrome to launch Google Maps app from BlinkMap


        //Listen for Bluetooth devices
        initializeAdapter();

        //Set up buttons
        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);

        btnConnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                //I want to connect and pair the Adafruit here
                //If pairing is unsuccessful, throw an error
                if(!adapterInitialized()){
                    toast("Please make sure Bluetooth is turned on.");
                    initializeAdapter();
                }
                else{
//                  IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
//                  registerReceiver(receiver, filter);
                    if(!isBLEPaired(ADAFRUIT_NAME)){
                        toast("Could not find Adafruit.");
                    }
                    if (isBLEPaired(ADAFRUIT_NAME))
                    {
                        Log.d(TAG, "BLE is paired");
                        //Connect GoogleAPI client and setup GATT
                        EXTRA_ADDRESS = adafruit.getAddress();
                        setupGatt();
                        client.connect();
                        //Possibly open Google Maps app here
                        //Can I have the user enter their destination and grab that info/generated JSON?
                    }
                }
            }
        });


        btnDisconnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
//                disconnect(); //close connection
            }
        });
    }

    private void toast(String s)
    {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    //region GATT
    private final BluetoothGattCallback callback = new BluetoothGattCallback()
    {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic)
        {
            //read the characteristic data
            byte[] data = characteristic.getValue();
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState)
        {
            // this will get called when a device connects or disconnects
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status)
        {
            List<BluetoothGattService> services = gatt.getServices();
            for (BluetoothGattService service : services)
            {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic characteristic : characteristics)
                {
                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
                    {
                        //find descriptor UUID that matches Client Characteristic Configuration (0x2902)
                        // and then call setValue on that descriptor

                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }
    };

    //Call this once the Adafruit is paired and connected and we have navigation data.
    //This will send the navigation + location data to adafruit
    private void sendData()
    {
        Log.d(TAG, "sendData()");
        boolean conn = false;
        if (gatt != null)
        {
            adapter.cancelDiscovery();
//            gatt.connect();
            conn = true;
        }
        if (conn)
        {
            //TODO: START HERE
//            try
//            {
//                ct = new ConnectionThread(adafruit, true, adapter, ADA_UUID);

//                socket.connect();
            }
//            catch (IOException e)
//            {
//                e.printStackTrace();
//            }
        }

    //Does this force Bluetooth pairing? If not, what exactly is it doing?
    private void setupGatt()
    {
        gatt = adafruit.connectGatt(MainActivity.this, false, callback);
        gatt.discoverServices();
        int status = 0;
        callback.onServicesDiscovered(gatt, status);
        if (status == 0)
        {
            List<BluetoothGattService> services = gatt.getServices();
            for (BluetoothGattService service : services)
            {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic characteristic : characteristics)
                {
                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
                    {
                        //find descriptor UUID that matches Client Characteristic Configuration (0x2902)
                        // and then call setValue on that descriptor

                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
            //DO I NEED THIS?
//          gatt.disconnect();
//          gatt.close();
        }
    }
    //endregion

    //region Location Services
    @Override
    public void onLocationChanged(Location location)
    {
        handleNewLocation(location);
    }

    private void handleNewLocation(Location location)
    {
        currentLat = location.getLatitude();
        currentLon = location.getLongitude();
        // TODO: 10/11/2016 Send Lat & Long info to LED, so it can be sent to Adafruit
        // TODO: Follow tutorial (http://www.instructables.com/id/Android-Bluetooth-Control-LED-Part-2/?ALLSTEPS)
//        toast("Latitude= " + currentLatitude + "\n" + "Longitude= " + currentLongitude);
        Log.i(TAG, "CurrentLat: " + currentLat);
        Log.i(TAG, "CurrentLon: " + currentLon);
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
                    toast("Accessing location...");
                }
                else
                {
                    toast("Location permissions denied");
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

    @Override
    protected void onDestroy()
    {
        Log.d(TAG, "onDestroy()");
//        unregisterReceiver(receiver);
        gatt.disconnect();
        gatt.close();
        super.onDestroy();
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
//                    adapter.cancelDiscovery();
                    return true;
                }
            }
        }
        return false;
    }

    //Pretty sure this isn't needed anymore (as of 10/23)
    public void setLEDConnection(BluetoothDevice device)
    {
        Intent ledIntent = new Intent(MainActivity.this, LED.class);
        //First argument is probably wrong
        ledIntent.putExtra(EXTRA_ADDRESS, device.getAddress());
        startActivity(ledIntent);
    }

    //Pretty sure this isn't needed anymore if using GATT Services (as of 10/23)
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
            toast("Bluetooth Device unavailable");
        }
    }

    //Returns true if adapter is initialized
    private boolean adapterInitialized()
    {
        return (!(adapter == null)) && adapter.isEnabled();
    }

//    private final BroadcastReceiver receiver = new BroadcastReceiver()
//    {
//        public void onReceive(Context context, Intent intent)
//        {
//            String action = intent.getAction();
//
//            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
//            {
//                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
//
//                if (state == BluetoothAdapter.STATE_ON)
//                {
//                    Log.d(TAG, "Enabled");
//                }
//            }
//            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
//            {
//                devices = new ArrayList<>();
//            }
//            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
//            {
//                Intent newIntent = new Intent(MainActivity.this, MainActivity.class);
//                newIntent.putParcelableArrayListExtra("device.list", devices);
//                startActivity(newIntent);
//            }
//            else if (BluetoothDevice.ACTION_FOUND.equals(action))
//            {
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                if (device != null)
//                {
//                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE)
//                    {
//                        Log.d(TAG, "Found " + device.getName());
//                        devices.add(device);
//                    }
//                }
//
//                for (BluetoothDevice foundDevice : devices)
//                {
//                    if (foundDevice.getName().equals(ADAFRUIT_NAME))
//                    {
//                        adapter.cancelDiscovery();
//                        pairDevice(foundDevice);
//                    }
//                }
//            }
//        }
//    };
    //endregion



    //region public Notification
    private BroadcastReceiver onNotice= new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("RECEIVER", "Received!");
            String pack = intent.getStringExtra("package");
            String title = intent.getStringExtra("title");
            String text = intent.getStringExtra("text");

            toast(String.format("Title= %s", title));
            Log.i("Receiver", String.format("Title= %s", title));
            toast(String.format("Text= %s", text));
            Log.i("Receiver", String.format("Text= %s", text));
            toast(String.format("Pack= %s", pack));
            Log.i("Receiver", String.format("Pack= %s", pack));
            toast(String.format("getContext= %s", getApplicationContext()));
            Log.i("Receiver", String.format("getContext= %s", getApplicationContext()));
        }
    };
    //endregion
    class NotificationReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            String temp = intent.getStringExtra("notification_event") + "n";// + txtView.getText();
            Log.i("NotificationReceiver", temp);
            //txtView.setText(temp);
        }
    }
}


