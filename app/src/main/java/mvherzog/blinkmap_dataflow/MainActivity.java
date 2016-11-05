package mvherzog.blinkmap_dataflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
                                                               GoogleApiClient.OnConnectionFailedListener,
                                                               Uart.Callback
//                                                               LocationListener
{
    Button btnConnect, btnDisconnect;
    public static final String TAG = MainActivity.class.getSimpleName();
    private int connectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    //UART
    private Uart uart;

    //UUIDs
    public static final UUID TRUE_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    public static final UUID ADA_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");

    //Write to BLE
    public BluetoothGattCharacteristic writeCharacteristicToBLE;
    public String dataToWrite = "HELLO BLE from Characteristic";

    public final static String ACTION_GATT_CONNECTED = "mvherzog.blinkmap_dataflow.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "mvherzog.blinkmap_dataflow.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "mvherzog.blinkmap_dataflow.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "mvherzog.blinkmap_dataflow.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "mvherzog.blinkmap_dataflow.EXTRA_DATA";
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
        uart = new Uart(getApplicationContext());
        btnConnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setUpBtnConnect(uart);
                writeLine("Calling uart.connectFirstAvailable()");
//                uart.getGatt().connect();
                writeLine("After uart.connectFirstAvailable()");
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Log.i("btnDisconnect", "Clicked");
//                if (handler != null)
//                {
//                    msg.what = DataThread.QUIT_CODE;
//                    handler.sendMessage(msg);
//
//                }
                onDisconnected(uart);
            }
        });
    }

    private void toast(String s)
    {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private void setUpBtnConnect(Uart uart)
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
            if (!uart.isConnected())
            {
                Log.i("setUpBtnConnect", "UART not connected");
                uart.connectFirstAvailable();
            }
            else
            {
                isGattConnected = true;
                Log.i("setUpBtnConnect", "UART connected");
            }
//            if (!isBLEPaired(ADAFRUIT_NAME))
//            {
//                Log.i("BLE", "is NOT paired");
//                if (adafruit == null)
//                {
//                    adafruit = adapter.getRemoteDevice(btAddress);
//                }
//                if (adafruit != null)
//                {
//                    Log.i("setUpBtnConnect", "Adafruit != null");
////                    connectGATT();
//                }
//                else
//                {
//                    toast("Could not find Adafruit.");
//                }
//            }
//            if (isBLEPaired(ADAFRUIT_NAME))
//            {
//                Log.d("BLE", "is paired");
////                connectGATT();
////                client.connect();
//            }
        }
        if(isGattConnected && uart.connectFirst){
            sendData(uart);
        }
    }

    public void sendData(Uart uart)
    {
        while(uart.isConnected())
        {
            writeLine("While UART.isConnected");
            writeCharacteristicToBLE.setValue("Hello from BlinkMap");
            uart.onCharacteristicWrite(uart.getGatt(), writeCharacteristicToBLE, uart.getGatt().getConnectionState(adafruit));
            writeLine("After onCharWrite('Hello from BlinkMap')");
        }
    }

    //region GATT

    @Override
    public void onConnected(Uart uart)
    {
        // Called when UART device is connected and ready to send/receive data.
        writeLine("Connected!");
        sendData(uart);
        // Enable the send button
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                send = (Button)findViewById(R.id.send);
//                send.setClickable(true);
//                send.setEnabled(true);
//            }
//        });
    }

    @Override
    public void onConnectFailed(Uart uart)
    {
        // Called when some error occured which prevented UART connection from completing.
        writeLine("Error connecting to device! Please press the Connect button again.");
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                send = (Button)findViewById(R.id.send);
//                send.setClickable(false);
//                send.setEnabled(false);
//            }
//        });
    }

    @Override
    public void onDisconnected(Uart uart)
    {
        // Called when the UART device disconnected.
        writeLine("Disconnected!");
        uart.disconnect();
        // Disable the send button.
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                send = (Button)findViewById(R.id.send);
//                send.setClickable(false);
//                send.setEnabled(false);
//            }
//        });
    }

    @Override
    public void onReceive(Uart uart, BluetoothGattCharacteristic rx)
    {
        // Called when data is received by the UART.
        writeLine("Received: " + rx.getStringValue(0));
    }

    @Override
    public void onDeviceFound(BluetoothDevice device)
    {
        // Called when a UART device is discovered (after calling startScan).
        writeLine("Found device : " + device.getAddress());
        writeLine("Waiting for a connection ...");
    }

    @Override
    public void onDeviceInfoAvailable()
    {
        writeLine(uart.getDeviceInfo());
    }

    private void writeLine(final CharSequence text)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                Log.i("WriteLine", String.valueOf(text));
//                messages.append(text);
//                messages.append("\n");
            }
        });
    }

//    private final BluetoothGattCallback callback = new BluetoothGattCallback()
//    {
//
//        @Override
//        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic)
//        {
//            Log.i("onCharacteristicChanged", "Changed!");
//            //read the characteristic data
//            byte[] data = characteristic.getValue();
//            for (Byte d : data)
//            {
//                Log.i("Byte:", d.toString());
//            }
//        }
//
//        @Override
//        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
//        {
//            Log.i("onCharacteristicRead", "Read!");
//            Log.i("onCharacteristicRead", String.format("Characteristic UUID: %s, Status: %d:", String.valueOf(characteristic.getUuid()), status));
//            setCharacteristicIndication(characteristic, true);
//        }
//
//        @Override
//        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
//        {
//            Log.i("onCharacteristicWrite", "Wrote!");
//            Log.i("onCharacteristicWrite", String.format("Characteristic: %s, Status: %d:, Service UUID: %s, WriteType: %d", Arrays.toString(characteristic.getValue()), status, characteristic.getService().getUuid(), characteristic.getWriteType()));
//            for(int i = 0; i < characteristic.getValue().length; i++){
//                for(char c : characteristic.getStringValue(i).toCharArray())
//                {
//                    Log.i("char.getValue(i)", String.format("i: %d, c: %s", i, c));
//                }
//
//            }
//        }
//
//        void setCharacteristicIndication(BluetoothGattCharacteristic characteristic, boolean enabled)
//        {
//            Log.i("setCharacIndication", "called");
////            gatt.setCharacteristicNotification(characteristic, enabled);
////
////            List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors()
////            for (BluetoothGattDescriptor descriptor : descriptors)
////            {
//
////                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
////                gatt.writeDescriptor(descriptor);
////                if (descriptor != null)
////                {
////                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
////                    Log.v(TAG, "Enabling indications for " + characteristic.getUuid());
////                    Log.d(TAG, "gatt.writeDescriptor(" + characteristic.getDescriptor(descriptor.getUuid()) + ", value=0x02-00?)");
////                    gatt.writeDescriptor(descriptor);
////                }
////                else
////                {
////                    Log.v(TAG, "Could not enable indications for " + characteristic.getUuid());
////                }
////            }
//        }
//
//        @Override
//        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState)
//        {
//            Log.i("CALLBACK", "connectionStateChanged");
//            String intentAction;
//            if (newState == BluetoothProfile.STATE_CONNECTED)
//            {
//                intentAction = ACTION_GATT_CONNECTED;
//                connectionState = STATE_CONNECTED;
//                broadcastUpdate(intentAction);
//                Log.i(TAG, "Connected to GATT server.");
//                // Attempts to discover services after successful connection.
//                Log.i(TAG, "Attempting to start service discovery:" + gatt.discoverServices());
//            }
//            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
//            {
//                intentAction = ACTION_GATT_DISCONNECTED;
//                connectionState = STATE_DISCONNECTED;
//                Log.i(TAG, "Disconnected from GATT server.");
//                broadcastUpdate(intentAction);
//            }
//        }
//
//        @Override
//        public void onServicesDiscovered(final BluetoothGatt gatt, final int status)
//        {
//            Log.i("CALLBACK", "onServicesDiscovered");
////            BluetoothGattCharacteristic writeCharacteristicToBLE;
////            String dataToWrite = "HELLO BLE from Characteristic";
////            byte[] strBytes;
////            byte[] bytes;
//
//            List<BluetoothGattService> services = gatt.getServices();
//            for (BluetoothGattService service : services)
//            {
//                Log.i("onServicesDiscovered", String.format("Service UUID: %s", service.getUuid().toString()));
//
//                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
//                for (BluetoothGattCharacteristic characteristic : characteristics)
//                {
//                    Log.i("onServicesDiscovered, ", String.format("Characteristic UUID: %s", characteristic.getUuid().toString()));
////                    gatt.writeCharacteristic(characteristic);
//                    if (((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) |
//                            (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0)
//                    {
//                        if (characteristic.getService().getUuid().equals(UART_UUID))
//                        {
//                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//                            Log.i("UART", "UART CHARACTERISTIC DISCOVERED : Writing Characteristic (from loop) " + dataToWrite);
//                            writeCharacteristicToBLE = characteristic;
//                            writeCharacteristicToBLE.setValue(9999, BluetoothGattCharacteristic.FORMAT_SINT32, 0);
//                            gatt.writeCharacteristic(writeCharacteristicToBLE);
////                        }
//                            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
//                            {
//                                //find descriptor UUID that matches Client Characteristic Configuration (0x2902)
//                                // and then call setValue on that descriptor
//                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//                                gatt.writeDescriptor(descriptor);
//                            }
//                        }
//                    }
//                }
//            }
//            isGattConnected = true;
//        }
//    };
//
//    private void broadcastUpdate(final String action)
//    {
//        final Intent intent = new Intent(action);
//        sendBroadcast(intent);
//    }
//
//    private void connectGATT()
//    {
//        byte[] data = dataToWrite.getBytes();
//        BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(UART_UUID, 2, 2);
//        c.setValue(data);
//        Log.i("connectGATT", "inside");
//        gatt = adafruit.connectGatt(MainActivity.this, false, callback);
//        gatt.discoverServices();
//        Log.i("connectGATT", "after adafruit.connectGatt");
//        if (isGattConnected)
//        {
//            Log.i("connectGATT", "isGattConnected == true. Writing characteristic" + dataToWrite);
//            gatt.writeCharacteristic(c);
//
//        }
//
////            if (service != null)
////            {
////                gatt.beginReliableWrite();
////                Log.i("BLE", "is paired");
////                BluetoothGattCharacteristic characteristic = service.getCharacteristic(ADA_UUID);
////                characteristic.setValue("HELLO");
////                gatt.writeCharacteristic(characteristic);
////                Log.i("GATT", characteristic.toString());
////            }
////            else{
////                Log.i("GATT SERVICE", "is null");
////            }
//
////            List<BluetoothGattService> services = gatt.getServices();
//
////            connectBTSocket();
//
//    }

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

