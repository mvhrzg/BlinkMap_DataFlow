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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    public static final UUID ADA_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // UUIDs for UART service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    // UUID for the UART BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Device Information service and associated characeristics.
    public static UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_MANUF_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_MODEL_UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_HWREV_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static UUID DIS_SWREV_UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");

    //Write to BLE
    public BluetoothGattCharacteristic chartoWrite;
    public String dataToWrite = "HELLO BLE from Characteristic";
    //Internal UART
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    //Device info
    private BluetoothGattCharacteristic disManuf;
    private BluetoothGattCharacteristic disModel;
    private BluetoothGattCharacteristic disHWRev;
    private BluetoothGattCharacteristic disSWRev;
    private Queue<BluetoothGattCharacteristic> readQueue = new ConcurrentLinkedQueue<BluetoothGattCharacteristic>();
    ;

    public final static String ACTION_GATT_CONNECTED = "mvherzog.blinkmap_dataflow.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "mvherzog.blinkmap_dataflow.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "mvherzog.blinkmap_dataflow.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "mvherzog.blinkmap_dataflow.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "mvherzog.blinkmap_dataflow.EXTRA_DATA";
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final int REQUEST_PERMISSION_FINE_LOCATION = 1;
    private final String btAddress = "C5:12:82:4F:6F:CD";
    public BluetoothGatt gatt;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();

    private BluetoothAdapter adapter;
    public BluetoothDevice adafruit;

    //Are these needed?
    public double currentLat, currentLon;
    private GoogleApiClient client;
    private LocationRequest request;
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
        registerReceiver(mGattUpdateReceiver, new IntentFilter());

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
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Log.i("btnDisconnect", "Clicked");
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
//            if (!uart.isConnected())
//            {
//                Log.i("setUpBtnConnect", "UART not connected");
//                uart.connectFirstAvailable();
//            }
            if (!isBLEPaired(btAddress))
            {
                Log.i("BLE", "is NOT paired");
                if (adafruit == null)
                {
                    adafruit = adapter.getRemoteDevice(btAddress);
                }
                if (adafruit != null)
                {
                    Log.i("setUpBtnConnect", "Adafruit != null");
                    uart.connectFirstAvailable();
//                    connectGATT(uart);
                }
                else
                {
                    toast("Could not find Adafruit.");
                }
            }
            if (isBLEPaired(btAddress))
            {
                Log.d("BLE", "is paired");
//                connectGATT();
//                client.connect();
            }
        }
    }

    public void sendData(BluetoothGattService uart)
    {
        writeLine("Inside sendData()");
        if (isGattConnected)
        {
            writeLine("isGattConnected is true!");
            chartoWrite = new BluetoothGattCharacteristic(uart.getCharacteristic(RX_UUID).getUuid(), BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
            chartoWrite.setValue(dataToWrite);
            uart.addCharacteristic(chartoWrite);
            //TODO: Start Here. Almost there but service is busy!!!
            if (gatt.writeCharacteristic(chartoWrite))
            {
                writeLine("Wrote " + chartoWrite.getStringValue(0));
            }
        }
//        while(uart.isConnected())
//        {
//            writeLine("While UART.isConnected");
//            chartoWrite.setValue("AT+BLEUARTTX=Hello from BlinkMap");
//            uart.onCharacteristicWrite(uart.getGatt(), chartoWrite, uart.getGatt().getConnectionState(adafruit));
//            writeLine("After onCharWrite('Hello from BlinkMap')");
//        }
    }

    //region GATT

        @Override
    public void onConnected(Uart uart)
    {
        // Called when UART device is connected and ready to send/receive data.
        writeLine("Connected!");
        uart.send("Connected".getBytes());
//        sendData(uart);
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
        uart.disconnect();
        writeLine("Disconnected!");
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
//
    private void writeLine(final CharSequence text)
    {
        Log.w("WriteLine", String.valueOf(text));
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
//            super.onCharacteristicRead(gatt, characteristic, status);
//            if (status == BluetoothGatt.GATT_SUCCESS)
//            {
//                Log.i("onCharacteristicRead", String.format("C_UUID: %s, C_Value: %s:", characteristic.getUuid(), characteristic.getStringValue(0)));
////              setCharacteristicIndication(characteristic, true);
//                BluetoothGattCharacteristic nextRequest = readQueue.poll();
//                if (nextRequest != null)
//                {
//                    gatt.readCharacteristic(nextRequest);
//                    writeLine("nextRequest = " + nextRequest.getStringValue(0));
//                }
////                else
////                {
////                    writeLine("nextRequest is null");
////                    BluetoothGattService blinkMap = gatt.getService(ADA_UUID);
////                    chartoWrite = new BluetoothGattCharacteristic(RX_UUID, 1, 0);
////                    chartoWrite.setValue(dataToWrite.getBytes());
////                    blinkMap.addCharacteristic(chartoWrite);
////                    if (!gatt.writeCharacteristic(chartoWrite))
////                    {
////                        writeLine("Couldn't write characteristic " + chartoWrite.getStringValue(0));
////                    }
////                }
//            }
//            else
//            {
//                writeLine(String.format("Failed reading characteristic. UUID: %s, Value: %s", characteristic.getUuid(), characteristic.getStringValue(0)));
//            }
//        }
//
//        @Override
//        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
//        {
//            if (status == BluetoothGatt.GATT_SUCCESS)
//            {
//                super.onCharacteristicWrite(gatt, characteristic, status);
//                writeLine(String.format("Wrote characteristic! Value = %s, Status = %d, ServiceUUID = %s", characteristic.getStringValue(0), status, characteristic.getService().getUuid()));
//            }
//            else
//            {
//                writeLine(String.format("Wrote characteristic! Value = %s, Status = %d, Service UUID = %s", characteristic.getStringValue(0), status, characteristic.getService().getUuid()));
//            }
//
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
//                isGattConnected = true;
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
//        public void onServicesDiscovered(BluetoothGatt gatt, final int status)
//        {
//            if (status == BluetoothGatt.GATT_FAILURE)
//            {
//                writeLine(String.format("Status = GATT_FAILURE(%d)", status));
//                return;
//            }
//            super.onServicesDiscovered(gatt, status);
//            Log.i("CALLBACK", "onServicesDiscovered");
//
//            // Save reference to each UART characteristic.
//            tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
//            rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);
//
//            // Save reference to each DIS characteristic.
//            disManuf = gatt.getService(DIS_UUID).getCharacteristic(DIS_MANUF_UUID);
//            disModel = gatt.getService(DIS_UUID).getCharacteristic(DIS_MODEL_UUID);
//            disHWRev = gatt.getService(DIS_UUID).getCharacteristic(DIS_HWREV_UUID);
//            disSWRev = gatt.getService(DIS_UUID).getCharacteristic(DIS_SWREV_UUID);
//
//            // Add device information characteristics to the read queue
//            // These need to be queued because we have to wait for the response to the first
//            // read request before a second one can be processed (which makes you wonder why they
//            // implemented this with async logic to begin with???)
//            readQueue.offer(disManuf);
//            readQueue.offer(disModel);
//            readQueue.offer(disHWRev);
//            readQueue.offer(disSWRev);
//
////            readQueue.offer(tx);
////            readQueue.offer(rx);
//
//            // Request a dummy read to get the device information queue going
////            writeLine("Requesting dummy read " + gatt.readCharacteristic(disManuf));
//            if (!gatt.setCharacteristicNotification(rx, true))
//            {
//                // Stop if the characteristic notification setup failed.
//                writeLine("Couldn't setup characteristic notification");
////            connectFailure();
//                return;
//            }
//            // Next update the RX characteristic's client descriptor to enable notifications.
//            BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
//            if (desc == null)
//            {
//                // Stop if the RX characteristic has no client descriptor.
//                writeLine("Descriptor is null");
////                connectFailure();
//                return;
//            }
//            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            if (!gatt.writeDescriptor(desc))
//            {
//                writeLine(String.format("couldn't write desc (from %s service, %s characteristic). Contents: %s, Value: %s", desc.getCharacteristic().getService().describeContents(), desc.getCharacteristic().getStringValue(0), desc.describeContents(), new String(desc.getValue())));
//                // Stop if the client descriptor could not be written.
////                connectFailure();
//                return;
//            }
//            else
//            {
//                writeLine("Wrote descriptor " + new String(desc.getValue()));
//
//                sendData(desc.getCharacteristic().getService());
//            }
//
////            List<BluetoothGattService> services = gatt.getServices();
////            for (BluetoothGattService service : services)
////            {
////                Log.i("onServicesDiscovered", String.format("Service UUID: %s", service.getUuid().toString()));
////
////                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
////                for (BluetoothGattCharacteristic characteristic : characteristics)
////                {
////                    Log.i("onServicesDiscovered, ", String.format("C_UUID: %s, C_VALUE: %s", characteristic.getUuid(), characteristic.getStringValue(0)));
////                    gatt.writeCharacteristic(characteristic);
////
//////                    if (((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) | (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0)
//////                    {
//////                        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//////                        characteristic.setValue(dataToWrite.getBytes());
//////                        gatt.writeCharacteristic(characteristic);
//////                        writeLine("after gatt.write " + dataToWrite);
//////                     }
////                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
////                    {
////                        //find descriptor UUID that matches Client Characteristic Configuration (0x2902)
////                        // and then call setValue on that descriptor
////                        writeLine("Writing descriptor: " + Arrays.toString(descriptor.getValue()));
////                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
////                        if (!gatt.writeDescriptor(descriptor))
////                        {
////                            writeLine("Couldn't write descriptor (in loop) " + Arrays.toString(descriptor.getValue()));
////                        }
////                    }
////                }
////            }
//        }
//    };

    private void broadcastUpdate(final String action)
    {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void connectGATT(Uart uart)
    {
//        byte[] data = dataToWrite.getBytes();
        String data = "1left";
        BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(UART_UUID, 2, 2);
        c.setValue(data);
        Log.i("connectGATT", "inside");
        //This connects to BLE (blue light)
//        gatt = adafruit.connectGatt(MainActivity.this, true, callback);
        //This sets off discover services and writes characteristics and descriptors
        while(gatt.discoverServices());
        gatt.writeCharacteristic(c);


//            gatt.writeCharacteristic(c);

//            if (service != null)
//            {
//                gatt.beginReliableWrite();
//                Log.i("BLE", "is paired");
//                BluetoothGattCharacteristic characteristic = service.getCharacteristic(ADA_UUID);
//                characteristic.setValue("HELLO");
//                gatt.writeCharacteristic(characteristic);
//                Log.i("GATT", characteristic.toString());
//            }
//            else{
//                Log.i("GATT SERVICE", "is null");
//            }

//            List<BluetoothGattService> services = gatt.getServices();

//            connectBTSocket();

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
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        unregisterReceiver(onReader);
        //disconnect bluetooth here
//        if (isGattConnected)
//        {
            gatt.disconnect();
            gatt.close();
//        }

    }
    //endregion

    //region BLE Connection Methods & Properties

    /**
     * Checks Adafruit connection
     *
     * @return true if found Adafruit, false otherwise
     */
    private boolean isBLEPaired(String address)
    {
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                if (device.getAddress().equals(address))
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
                    if (foundDevice.getAddress().equals(btAddress))
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
        private BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                final String action = intent.getAction();
                if (BLEService.ACTION_GATT_CONNECTED.equals(action))
                {
                    isGattConnected = true;
                    writeLine("ACTION_GATT_CONNECTED");
//                    updateConnectionState(R.string.connected);
//                    invalidateOptionsMenu();
                }
                else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action))
                {
                    isGattConnected = false;
                    writeLine("ACTION_GATT_DISCONNECTED");
//                    updateConnectionState(R.string.disconnected);
                    invalidateOptionsMenu();
//                    clearUI();
                }
                else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
                {
                    // Show all the supported services and characteristics on the
                    // user interface.

                    writeLine("ACTION_GATT_SERVICES_DISCOVERED " + String.valueOf(action));
//                    displayGattServices(BLEService.getSupportedGattServices());
                }
                else if (BLEService.ACTION_DATA_AVAILABLE.equals(action))
                {
                    writeLine("Action data available");
//                    displayData(intent.getStringExtra(BLEService.EXTRA_DATA));
                }
            }
        };
    //endregion

}

