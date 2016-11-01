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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.provider.Settings;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
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
    private String btAddress = "C5:12:82:4F:6F:CD";
    public BluetoothGatt gatt;
    public double currentLat, currentLon;

    //Are these needed?
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    BluetoothSocket socket = null;
    public static final UUID ADA_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private OutputStream output;
    private InputStream input;
    private byte[] buffer;
    private boolean isGattConnected = false;

    public NotificationReader nReader;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.onResume();
        setContentView(R.layout.activity_main);
        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        //Might only work if native Notification
        if (checkNotificationEnabled())
        {
            registerReceiver(onReader, new IntentFilter());
//            startActivity(new Intent(this, NotificationReader.class));
        }
        else
        {
            //service is not enabled try to enabled by calling...
            getApplicationContext().startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }

//        //Set up Google API client
//        client = new GoogleApiClient.Builder(this)
//                .addConnectionCallbacks(this)
//                .addOnConnectionFailedListener(this)
//                .addApi(LocationServices.API)
//                .addApi(AppIndex.API).build();
//
//        //Set up location requests
//        request = LocationRequest.create()
//                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
//                .setInterval(1000)
//                .setFastestInterval(100);

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
                setUpBtnConnect();
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (isGattConnected)
                {
                    gatt.disconnect();
                    gatt.close();
                }
            }
        });
    }

    private void toast(String s)
    {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private void setUpBtnConnect()
    {
        Log.i("Clicked", "btnConnect");
        //I want to connect and pair the Adafruit here
        //If pairing is unsuccessful, throw an error
        if (!adapterInitialized())
        {
            Log.i("Adapter", "not initialized");
            toast("Please make sure Bluetooth is turned on.");
            initializeAdapter();
        }
        else
        {
            Log.i("Adapter", "initialized");

            if (!isBLEPaired(ADAFRUIT_NAME))
            {
                Log.i("BLE", "is NOT paired");
                adafruit = adapter.getRemoteDevice(btAddress);
                if (adafruit != null)
                {
                    setupGatt();
                }
                else
                {
                    toast("Could not find Adafruit.");
                }
            }
            if (isBLEPaired(ADAFRUIT_NAME))
            {
                Log.d("BLE", "is paired");
                setupGatt();
//                client.connect();
            }
        }
    }

    //region GATT
    private final BluetoothGattCallback callback = new BluetoothGattCallback()
    {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic)
        {
            //read the characteristic data
            byte[] data = characteristic.getValue();
            for (Byte d : data)
            {
                Log.i("Byte:", d.toString());
            }
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
    private void connectBTSocket()
    {
        boolean conn = false;
        if (gatt != null)
        {
            adapter.cancelDiscovery();
            conn = true;
        }
        if (conn && isGattConnected)
        {
            try
            {
                socket = adafruit.createRfcommSocketToServiceRecord(ADA_UUID);
                Method m = adafruit.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) m.invoke(adafruit, 1);
                sendData(socket);
//                socket.connect();
            }
            catch (IOException e)
            {
                e.printStackTrace();
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
        }
    }

    private void sendData(BluetoothSocket socket)
    {
        int bytes;
        int length = 10;
        try
        {
            input = socket.getInputStream();
            output = socket.getOutputStream();
            int sType = socket.getConnectionType();
            Log.i("sType", String.format("%d", sType));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        final DataThread thread = new DataThread(socket);
        thread.start();

        AsyncTask<Handler, DataThread, Void> task = new AsyncTask<Handler, DataThread, Void>()
        {
            @Override
            protected Void doInBackground(Handler... params)
            {
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                return null;
            }
        };
        Handler handler = thread.getThreadHandler();
        task.execute(handler);
//        thread.run();
        Log.i("SEND_CODE", String.format("%d", DataThread.SEND_CODE));
        Message msg;
        while (handler != null)
        {
            msg = handler.obtainMessage(DataThread.SEND_CODE);
            msg.setTarget(handler);
            msg.what = DataThread.SEND_CODE;
            Log.i("HND.ObtainMessage", String.format("%s", msg));
            Log.i("1|2|D|T|C|S", String.format("%d | %d | %s | %s | %s | %s", msg.arg1, msg.arg2, msg.getData(), msg.getTarget(), msg.getCallback(), msg.toString()));
            msg.sendToTarget();
        }
//        Message msg = hnd.obtainMessage(DataThread.SEND_CODE, 0, 0, new Object());
//        for (int i = 0; i < 100; i++)
//        {
//            msg.getData().describeContents();
//        }

//        try
//        {
//            input = socket.getInputStream();
//            int available = 0;
//
//            while (true)
//            {
//                available = input.available();
//                if (available > 0) { break; }
//                Thread.sleep(1);
//                // here you can optionally check elapsed time, and time out
//            }
//
//            Log.i( "1) I/O", "available bits: " + available );
//            bytes = input.read(buffer, 0, length);
//            Log.i( "2) I/O", "available bits: " + input.available() );
//            handler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
//
//            output = socket.getOutputStream();
////            output.write("TO".toString().getBytes(), 0, 10);//"TO".toString().getBytes());
//            Log.i("INPUT", input.toString());
//            Log.i("OUTPUT", output.toString());
////            output.flush();
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//        }
//        catch (InterruptedException e)
//        {
//            e.printStackTrace();
//        }

    }

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
            isGattConnected = true;
            Log.i("Gatt", "set up");
            connectBTSocket();

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
//        Log.i(TAG, "CurrentLat: " + currentLat);
//        Log.i(TAG, "CurrentLon: " + currentLon);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
//        if (client.isConnected())
//        {
//            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
//            client.disconnect();
//        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
//        client.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        Log.i(TAG, "Location Services connected");

//        Location location = LocationServices.FusedLocationApi.getLastLocation(client);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
//            LocationServices.FusedLocationApi.requestLocationUpdates(client, request, this);
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
//        client.connect();
        super.onStart();
    }

    @Override
    public void onStop()
    {
        Log.d(TAG, "onStop()");
//        unregisterReceiver(receiver);
//        client.disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        Log.d(TAG, "onDestroy()");
        unregisterReceiver(onReader);
        //disconnect bluetooth here

        if (socket != null)
        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        if (isGattConnected)
        {
            gatt.disconnect();
            gatt.close();
        }

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
                    return true;
                }
            }
        }
        return false;
    }

    //Pretty sure this isn't needed anymore if using GATT Services (as of 10/23)
    private void pairDevice(BluetoothDevice device)
    {
        try
        {
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
                Log.i("Adapter", "enabled");
            }
        }
        else
        {
            Log.i("Adapter", "Bluetooth unavailable");
            toast("Bluetooth Device unavailable");
        }
    }

    //Returns true if adapter is initialized
    private boolean adapterInitialized()
    {
        return (!(adapter == null)) && adapter.isEnabled();
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
                        adapter.cancelDiscovery();
                        pairDevice(foundDevice);
                    }
                }
            }
        }
    };
    //endregion

    //region public Notification
    private BroadcastReceiver onNotice = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
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

    private BroadcastReceiver onReader = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.d("RECEIVER", "Received!");
            String temp = intent.getStringExtra("notification_event") + "n";// + txtView.getText();
            Log.i("NotificationReceiver", temp);
            //txtView.setText(temp);
        }
    };

    public boolean checkNotificationEnabled()
    {
        if (Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners").contains(getApplicationContext().getPackageName()))
        {
            return true;
//        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
//        startActivity(intent);
        }
        else
        {
            toast("Please enable notification access on the next screen");
            return false;
        }
    }
    //endregion

}

