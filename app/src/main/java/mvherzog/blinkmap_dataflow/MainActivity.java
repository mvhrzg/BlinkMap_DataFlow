package mvherzog.blinkmap_dataflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
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
import com.google.android.gms.maps.GoogleMap;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
                                                               GoogleApiClient.OnConnectionFailedListener,
                                                               Uart.Callback
//                                                               LocationListener
{
    Button btnConnect, btnStart, btnDisconnect;
    public static final String TAG = MainActivity.class.getSimpleName();

    //UART & GATT connection
    private Uart uart;
    private BluetoothAdapter adapter;
    public BluetoothDevice adafruit;
    private final String btAddress = "C5:12:82:4F:6F:CD";
    public BluetoothGatt gatt;
    private boolean isGattConnected = false;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();

    //Write to BLE
    public BluetoothGattCharacteristic chartoWrite;

    //Location Services
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final int REQUEST_PERMISSION_FINE_LOCATION = 1;
    public double currentLat, currentLon;
    private GoogleApiClient client;
    private LocationRequest request;

    //Notification Listener
    public NotificationReader nReader;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        uart = new Uart(getApplicationContext());
        super.onResume();
        setContentView(R.layout.activity_main);
        nReader = new NotificationReader();
//        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));
        //Might only work if native Notification
        writeLine(TAG, "checkNotificationEnabled()", checkNotificationEnabled());
        if (checkNotificationEnabled())
        {
            writeLine(TAG, "Notification listener enabled");
//            registerReceiver(onReader, new IntentFilter());
        }
        else
        {
            writeLine(TAG, "Notification listener not enabled");
            //service is not enabled try to enabled by calling...
//            getApplicationContext().startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
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
        btnStart = (Button) findViewById(R.id.btnStart);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
        //Disable 'Disconnect' and "Start Transmitting' buttons until connected
        btnDisconnect.setClickable(false);
        btnDisconnect.setEnabled(false);

        btnStart.setClickable(false);
        btnStart.setEnabled(false);

        btnConnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setUpBtnConnect(uart);
            }
        });

        //Sending data stops btnDisconnect from working
        btnStart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                sendData(uart);
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Log.i("btnDisconnect", "Clicked");
                uart.unregisterCallback(MainActivity.this);
                uart.disconnect();
//                onDisconnected(uart);
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
            if (!isBLEPaired(btAddress)) //only pre-paired if bonded
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
            if (isBLEPaired(btAddress)) //only true if bonded
            {
                Log.d("BLE", "is paired");
//                connectGATT();
//                client.connect();
            }
        }
    }

    public void sendData(Uart uart)
    {
        writeLine(TAG, "Inside sendData()");
        if (uart.isConnected())
        {
            writeLine("sendData", "Uart connected");
            //"1left" + LF
            byte[] data = {0x31, 0x6C, 0X65, 0X66, 0X74, 0X0A};
            uart.send(data);

        }
//        while(uart.isConnected())
//        {
//            writeLine(TAG, "While UART.isConnected");
//            chartoWrite.setValue("AT+BLEUARTTX=Hello from BlinkMap");
//            uart.onCharacteristicWrite(uart.getGatt(), chartoWrite, uart.getGatt().getConnectionState(adafruit));
//            writeLine(TAG, "After onCharWrite('Hello from BlinkMap')");
//        }
    }

    //region GATT

    @Override
    public void onConnected(Uart uart)
    {
        // Called when UART device is connected and ready to send/receive data.

//        sendData(uart);
        // Enable the 'Disconnect' button
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
                btnDisconnect.setClickable(true);
                btnDisconnect.setEnabled(true);
                btnStart = (Button) findViewById(R.id.btnStart);
                btnStart.setClickable(true);
                btnStart.setEnabled(true);
            }
        });
        while (!uart.isConnected())
        {
            ;
        }
        writeLine(TAG, "Connected!");
//        String left = "1left";
//        writeLine("onConnected", String.format("calling uart.send(%s)", left));
//        uart.send(left);
    }

    @Override
    public void onConnectFailed(Uart uart)
    {
        // Called when some error occured which prevented UART connection from completing.
        writeLine(TAG, "Error connecting to device! Please press the Connect button again.");
        // Disable 'Disconnect' button
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
                btnDisconnect.setClickable(false);
                btnDisconnect.setEnabled(false);
                btnStart = (Button) findViewById(R.id.btnStart);
                btnStart.setClickable(false);
                btnStart.setEnabled(false);
            }
        });
    }

    @Override
    public void onDisconnected(Uart uart)
    {
        // Called when the UART device disconnected.
//        gatt.disconnect();
//        gatt.close();
        writeLine(TAG, "Disconnected!");
//        runOnUiThread(new Runnable()
//        {
//            @Override
//            public void run()
//            {
//                btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
//                btnDisconnect.setClickable(false);
//                btnDisconnect.setEnabled(false);
//            }
//        });
        writeLine("is device still bonded?", adafruit.getBondState());
    }

    @Override
    public void onReceive(Uart uart, BluetoothGattCharacteristic rx)
    {
        // Called when data is received by the UART.
        writeLine(TAG, "Received: " + rx.getStringValue(0));
    }

    @Override
    public void onDeviceFound(BluetoothDevice device)
    {
        // Called when a UART device is discovered (after calling startScan).
        writeLine(TAG, "Found device : " + device.getAddress());
        writeLine(TAG, "Waiting for a connection ...");
    }

    @Override
    public void onDeviceInfoAvailable()
    {
        writeLine(TAG, uart.getDeviceInfo());
    }

    public static void writeLine(final String tag, final String prompt, Object text)
    {
        if (text instanceof Integer)
        {
            Log.v(tag, String.format(prompt + ": %d", text));
        }
        else if (text instanceof String)
        {

            Log.v(tag, String.format(prompt + ": %s", text));
        }
        else
        {
            Log.v(tag, String.format(prompt + "%b", text));
        }
    }

    public static void writeLine(final String tag, final Object prompt)
    {
        if (prompt instanceof Integer)
        {
            Log.v(tag, String.valueOf(prompt));
        }
        else if (prompt instanceof String)
        {
            Log.v(tag, String.valueOf(prompt));
        }
        else
        {
            Log.v(tag, String.valueOf(prompt));
        }
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
//                    writeLine(TAG, "nextRequest = " + nextRequest.getStringValue(0));
//                }
////                else
////                {
////                    writeLine(TAG, "nextRequest is null");
////                    BluetoothGattService blinkMap = gatt.getService(ADA_UUID);
////                    chartoWrite = new BluetoothGattCharacteristic(RX_UUID, 1, 0);
////                    chartoWrite.setValue(dataToWrite.getBytes());
////                    blinkMap.addCharacteristic(chartoWrite);
////                    if (!gatt.writeCharacteristic(chartoWrite))
////                    {
////                        writeLine(TAG, "Couldn't write characteristic " + chartoWrite.getStringValue(0));
////                    }
////                }
//            }
//            else
//            {
//                writeLine(TAG, String.format("Failed reading characteristic. UUID: %s, Value: %s", characteristic.getUuid(), characteristic.getStringValue(0)));
//            }
//        }
//
//        @Override
//        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
//        {
//            if (status == BluetoothGatt.GATT_SUCCESS)
//            {
//                super.onCharacteristicWrite(gatt, characteristic, status);
//                writeLine(TAG, String.format("Wrote characteristic! Value = %s, Status = %d, ServiceUUID = %s", characteristic.getStringValue(0), status, characteristic.getService().getUuid()));
//            }
//            else
//            {
//                writeLine(TAG, String.format("Wrote characteristic! Value = %s, Status = %d, Service UUID = %s", characteristic.getStringValue(0), status, characteristic.getService().getUuid()));
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
//                writeLine(TAG, String.format("Status = GATT_FAILURE(%d)", status));
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
////            writeLine(TAG, "Requesting dummy read " + gatt.readCharacteristic(disManuf));
//            if (!gatt.setCharacteristicNotification(rx, true))
//            {
//                // Stop if the characteristic notification setup failed.
//                writeLine(TAG, "Couldn't setup characteristic notification");
////            connectFailure();
//                return;
//            }
//            // Next update the RX characteristic's client descriptor to enable notifications.
//            BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
//            if (desc == null)
//            {
//                // Stop if the RX characteristic has no client descriptor.
//                writeLine(TAG, "Descriptor is null");
////                connectFailure();
//                return;
//            }
//            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            if (!gatt.writeDescriptor(desc))
//            {
//                writeLine(TAG, String.format("couldn't write desc (from %s service, %s characteristic). Contents: %s, Value: %s", desc.getCharacteristic().getService().describeContents(), desc.getCharacteristic().getStringValue(0), desc.describeContents(), new String(desc.getValue())));
//                // Stop if the client descriptor could not be written.
////                connectFailure();
//                return;
//            }
//            else
//            {
//                writeLine(TAG, "Wrote descriptor " + new String(desc.getValue()));
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
//////                        writeLine(TAG, "after gatt.write " + dataToWrite);
//////                     }
////                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
////                    {
////                        //find descriptor UUID that matches Client Characteristic Configuration (0x2902)
////                        // and then call setValue on that descriptor
////                        writeLine(TAG, "Writing descriptor: " + Arrays.toString(descriptor.getValue()));
////                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
////                        if (!gatt.writeDescriptor(descriptor))
////                        {
////                            writeLine(TAG, "Couldn't write descriptor (in loop) " + Arrays.toString(descriptor.getValue()));
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
//        String data = "1left";
//        BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(UART_UUID, 2, 2);
//        c.setValue(data);
        Log.i("connectGATT", "inside");
        //This connects to BLE (blue light)
//        gatt = adafruit.connectGatt(MainActivity.this, true, callback);
        //This sets off discover services and writes characteristics and descriptors
        gatt.discoverServices();
//        gatt.writeCharacteristic(c);

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
        writeLine(TAG, "onPause()");
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
        writeLine(TAG, "onResume()");
        super.onResume();
        if (!uart.isConnected())
        {
            toast("Scanning for device...");
        }
        uart.registerCallback(this);

        //I only want this to happen when btnConnect is clicked
//        uart.connectFirstAvailable();
//        client.connect();
    }

    // region Location Services
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
        super.onStop();
        uart.unregisterCallback(this);
        //Do I want this here?
        uart.disconnect();
//        unregisterReceiver(receiver);
//        client.disconnect();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        uart.unregisterCallback(this);
        uart.disconnect();
        unregisterReceiver(onReader);

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
        return uart.isConnected();
//        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
//        if (pairedDevices.size() > 0)
//        {
//            for (BluetoothDevice device : pairedDevices)
//            {
//                if (device.getAddress().equals(address))
//                {
//                    adafruit = device;
//                    return true;
//                }
//            }
//        }
//        return false;
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
//            return true;
        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        startActivity(intent);
            return true;
        }
        else{
            toast("Please enable notification access on the next screen");
            return false;
        }

    }

    //endregion

}

