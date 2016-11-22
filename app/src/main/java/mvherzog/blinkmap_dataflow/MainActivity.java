package mvherzog.blinkmap_dataflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, Uart.Callback {
    Button btnConnect, btnStart, btnDisconnect;
    EditText destinationText;
    TextView originText, oLat, oLng;
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
    //    public BluetoothGattCharacteristic chartoWrite;

    //Location Services
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final int REQUEST_PERMISSION_FINE_LOCATION = 1;
    public double currentLat, currentLon;
    private GoogleApiClient client;
    private LocationRequest request;
    public String destination = "";
    public double destinationLatitude, destinationLongitude;
    public static String[] stepStartLocationCoordinates, stepEndLocationCoordinates, stepManeuver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uart = new Uart(getApplicationContext());
        super.onResume();
        setContentView(R.layout.activity_main);

        //Listen for Bluetooth devices
        initializeAdapter();

        //Set up buttons & texts
        originText = (TextView) findViewById(R.id.displayOrigin);
        oLat = (TextView) findViewById(R.id.oLat);
        oLng = (TextView) findViewById(R.id.oLng);
        destinationText = (EditText) findViewById(R.id.inputDestination);
        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);

        //Set up buttons and texts
        setupClickListeners();

        //Set up Google API client
        client = new GoogleApiClient.Builder(this).addConnectionCallbacks(
                this).addOnConnectionFailedListener(this).addApi(LocationServices.API).addApi(
                AppIndex.API).build();
        writeLine("onCreate", "googleAPIClient", client.toString());

        //Set up location requests
        request = LocationRequest.create().setPriority(
                LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(2000).setFastestInterval(1000);
        writeLine("onCreate", "locationRequest", request.toString());
    }

    public void setupClickListeners() {

        //Disable origin text
        //        originText.setEnabled(false);
        originText.setVisibility(View.INVISIBLE);

        //Disable destination text
        destinationText.setVisibility(View.INVISIBLE);
        destinationText.setEnabled(false);

        //Disable disconnect button
        btnDisconnect.setClickable(false);
        btnDisconnect.setEnabled(false);
        btnDisconnect.setVisibility(View.INVISIBLE);

        //Disable start transmitting button
        btnStart.setClickable(false);
        btnStart.setEnabled(false);
        btnStart.setVisibility(View.INVISIBLE);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setUpBtnConnect(uart);
            }
        });

        //Sending data stops btnDisconnect from working
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData(uart);//getNewLocation());
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeLine("btnDisconnect", "Clicked");
                client.disconnect();
                uart.unregisterCallback(MainActivity.this);
                uart.disconnect();
            }
        });

        destinationText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    //                    MessageApi.SendMessageResult();
                    handled = true;
                    //IME_ACTIONSEND = 4
                    writeLine("editText", "IME_ACTION_SEND", actionId);
                    //gets tet inside box
                    destination = destinationText.getText().toString();
                    writeLine("editText", "destination", destination);
                    //Once we get the destination, get the latitude and longitude so we can build the directions request
                    if (destination != null) {
                        destinationLatitude = getLatLntFromTextAddress(destination).latitude;
                        destinationLongitude = getLatLntFromTextAddress(destination).longitude;
                        writeLine("onEditorAction", "Lat of destination", destinationLatitude);
                        writeLine("onEditorAction", "Lng of destination", destinationLongitude);

                        //get last know location (should be the same as origin text's lat and lng)

                        buildUrlAndSendHTTPRequest(Double.valueOf(oLat.getText().toString()),
                                                   Double.valueOf(oLng.getText().toString()),
                                                   destinationLatitude, destinationLongitude
                        );
                    }
                }
                return handled;
            }
        });
    }

    public void buildUrlAndSendHTTPRequest(double oLat, double oLng, double dLat, double dLng) {
        btnDisconnect.setClickable(true);
        btnDisconnect.setEnabled(true);
        btnDisconnect.setVisibility(View.VISIBLE);

        //Make start transmitting button enabled, visible and clickable
        btnStart.setVisibility(View.VISIBLE);
        btnStart.setClickable(true);
        btnStart.setEnabled(true);

        String[] urlParams = {String.valueOf(oLat),
                                String.valueOf(oLng),
                                String.valueOf(dLat), String.valueOf(dLng)
        };

        new HttpRequest().execute(urlParams);
    }

    // region Location Services
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        writeLine("onConnected", "Location Services connected");
        //Get last known location, set invisible fields' text for origin latitude and origin
        // longitude (so we can build the url when user input destination)
        Location location = LocationServices.FusedLocationApi.getLastLocation(client);

        originText.setEnabled(true);
        originText.setVisibility(View.VISIBLE);

        //Set origin text as address
        if (originText.getText().toString().isEmpty()) {
            writeLine("onConnected", "setting originText");
            originText.append(getTextAddressFromLocation(location), 0,
                              getTextAddressFromLocation(location).length()
            );
        }

        //Set hidden origin latitude field
        if (oLat.getText().toString().isEmpty()) {
            oLat.append(String.valueOf(location.getLatitude()));
        }

        //Set hidden origin longitude field
        if (oLng.getText().toString().isEmpty()) {
            oLng.append(String.valueOf(location.getLongitude()));
        }

        if (ActivityCompat.checkSelfPermission(this,
                                               Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                                                                                     Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, request, this);
        } else {
            ActivityCompat.requestPermissions(this,
                                              new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                              REQUEST_PERMISSION_FINE_LOCATION
            );
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        writeLine("onConnectionSuspended", "Location Serviced suspended. Please reconnect");
        onDestroy();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this,
                                                          CONNECTION_FAILURE_RESOLUTION_REQUEST
                );
            }
            catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            writeLine("onConnectionFailed",
                      "Location services connection failed with code " + connectionResult.getErrorCode()
            );
            onDestroy();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        //        writeLine("onLocationChanged", "location changed");
        handleNewLocation(location);
    }

    public LatLng getLatLntFromTextAddress(String strAddress) {

        Geocoder coder = new Geocoder(this);
        List<Address> address;
        LatLng dest = null;

        try {
            address = coder.getFromLocationName(strAddress, 5);
            if (address == null) {
                return null;
            }
            Address location = address.get(0);
            for (int i = 0; i < address.size(); i++) {
                Address loc2 = address.get(i);
                writeLine("address.get(", String.valueOf(i), ") " + loc2.toString());
                writeLine("address.getAddressLine(", String.valueOf(i),
                          ") " + loc2.getAddressLine(i)
                );
            }
            dest = new LatLng(location.getLatitude(), location.getLongitude());

            return dest;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        //return null if try fails
        return null;
    }

    public String getTextAddressFromLocation(Location location) {
        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(this, Locale.getDefault());
        String fullAddress = "";

        try {
            addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(),
                                                 1
            );
            String addressLine = addresses.get(0).getAddressLine(0);
            String city = addresses.get(0).getLocality();
            String state = addresses.get(0).getAdminArea();
            String zip = addresses.get(0).getPostalCode();
            String country = addresses.get(0).getCountryCode();
            fullAddress = String.format("%s, %s, %s, %s, %s", addressLine, city, state, zip,
                                        country
            );
            writeLine("getTextAddressFromLocation", "fullAddress", fullAddress);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return fullAddress;
    }

    private Location handleNewLocation(Location location) {

        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();
        //        writeLine("CurrentLat: ", currentLatitude);
        //        writeLine("CurrentLon: ", currentLongitude);

        //once we have the JSON with directions, handleNewLocation will use the current lat and lng
        return location;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    toast("Accessing location...");
                } else {
                    toast("Location permissions denied");
                }
        }
    }
    //endregion Location
    //region Android Stuff

    private void toast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private void setUpBtnConnect(Uart uart) {
        Log.i("Clicked", "btnConnect");
        //I want to connect and pair the Adafruit here
        //If pairing is unsuccessful, throw an error
        if (!adapterInitialized()) {
            Log.i("Adapter", "not initialized");
            toast("Please make sure Bluetooth is turned on.");
            initializeAdapter();
        } else {
            writeLine("setUpBtnConnect", "adapter.initialized()", adapterInitialized());
            if (!isBLEPaired(btAddress)) //only pre-paired if bonded
            {
                writeLine("setUpBtnConnect", "isBLEPaired", isBLEPaired(btAddress));
                if (adafruit == null) {
                    writeLine("setUpBtnConnect", "adafruit is null. getting remove device ",
                              btAddress
                    );
                    adafruit = adapter.getRemoteDevice(btAddress);
                    if (adafruit != null) {
                        writeLine("setUpBtnConnect",
                                  "adafruit no longer null, calling uart.connectFirstAvailable"
                        );
                        uart.connectFirstAvailable();
                    }
                }
                if (adafruit != null) {
                    writeLine("setUpBtnConnect",
                              "Adafruit != null, calling uart.connectFirstAvalable"
                    );
                    uart.connectFirstAvailable();
                    //  connectGATT(uart);
                } else {
                    toast("Could not find Adafruit.");
                }
            }
            if (isBLEPaired(btAddress)) //only true if bonded
            {
                writeLine("setUpBtnConnect", "isBLEPaired", isBLEPaired(btAddress));
                //  connectGATT();
            }
        }
    }

    @Override
    protected void onPause() {
        writeLine(TAG, "onPause()");
        super.onPause();
        if (client.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
            client.disconnect();
        }
    }

    @Override
    protected void onResume() {
        writeLine(TAG, "onResume()");
        super.onResume();
        if (!uart.isConnected()) {
            toast("Scanning for device...");
        }
        uart.registerCallback(this);
        //Only connect the client onResume if BLE is connected
        if (uart.isConnected()) {
            writeLine("onResume", "calling client.connect()...");
            client.connect();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        //if calling this here, API connects before everything else
        //        client.connect();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
//        uart.unregisterCallback(this);
        //        unregisterReceiver(receiver);
        if (client.isConnected()) {
            client.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
//        uart.unregisterCallback(this);
        //this is being called when the device rotates and disconnecting
        if (client.isConnected()) {
            client.disconnect();
        }
        //        unregisterReceiver(onReader);
    }
    //endregion //Android
    //region  REGION GATT

    //This method will receive JSON
    public void sendData(Uart uart/*, byte[] hexCommand*/) {
        writeLine(TAG, "Inside sendData()");
        //Once notifications are set up, change to while
        if (uart.isConnected()) {
            writeLine("sendData", "Uart connected");
            /*if(hexCommand[1] == 0x31){  //'l'
                writeLine("sendData", "byteArray[1] = 'l'", hexCommand[1]);
            }else if(hexCommand[1] == 0x72) {  //'r'
                writeLine("sendData", "byteArray[1] = 'r'", hexCommand[1]);
            }else if(hexCommand[1] == 0x75){  //'u'
                writeLine("sendData", "byteArray[1] = 'u'", hexCommand[1]);
            }*/
            //"1l" + LF
            byte[] data = {0x31, 0x6C, /*0X65, 0X66, 0X74,*/ 0X0A};
            uart.send(data);
            //            writeLine("sendData", "sending", hexCommand[1]);
            //            uart.send(hexCommand);
        }
    }

    @Override
    public void onConnected(Uart uart) {
        // Called when UART device is connected and ready to send/receive data.
        // Enable the btnStart, btnDisconnect buttons
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Make destination visible and enabled
                destinationText.setEnabled(true);
                destinationText.setVisibility(View.VISIBLE);
            }
        });
        while (!uart.isConnected()) {
            ;
        }
        writeLine(TAG, "Connected!");
        writeLine("setUpBtnConnect", "Calling client.connect()");
        client.connect();

        //        String left = "1left";
        //        writeLine("onConnected", String.format("calling uart.send(%s)", left));
        //        uart.send(left);
    }

    @Override
    public void onConnectFailed(Uart uart) {
        // Called when some error occured which prevented UART connection from completing.
        writeLine(TAG, "Error connecting to device! Please press the Connect button again.");
        // Disable 'Disconnect' button
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
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
    public void onDisconnected(Uart uart) {
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
    public void onReceive(Uart uart, BluetoothGattCharacteristic rx) {
        // Called when data is received by the UART.
        writeLine(TAG, "Received: " + rx.getStringValue(0));
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        // Called when a UART device is discovered (after calling startScan).
        writeLine(TAG, "Found device : " + device.getAddress());
        writeLine(TAG, "Waiting for a connection ...");
    }

    @Override
    public void onDeviceInfoAvailable() {
        writeLine(TAG, uart.getDeviceInfo());
    }

    public static void writeLine(final String tag, final String prompt, Object text) {
        if (text instanceof Integer) {
            Log.i(tag, String.format(prompt + ": %d", text));
        } else if (text instanceof String) {
            Log.i(tag, String.format(prompt + ": %s", text));
        } else if (text instanceof Double) {
            Log.i(tag, String.format(prompt + ": %f", text));
        } else {
            Log.i(tag, String.format(prompt + ": %b", text));
        }
    }

    public static void writeLine(final String tag, final Object prompt) {
        if (prompt instanceof Integer) {
            Log.i(tag, String.valueOf(prompt));
        } else if (prompt instanceof String) {
            Log.i(tag, String.valueOf(prompt));
        } else if (prompt instanceof Double) {
            Log.i(tag, String.valueOf(prompt));
        } else {
            Log.i(tag, String.valueOf(prompt));
        }
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void connectGATT(Uart uart) {
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
    //endregion //GATT
    //region REGION BLE

    /**
     * Checks Adafruit connection
     *
     * @return true if found Adafruit, false otherwise
     */
    private boolean isBLEPaired(String address) {
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
    private void pairDevice(BluetoothDevice device) {
        try {
            adafruit = device;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Initializes BluetoothAdapter and creates BLUETOOTH_REQUEST_ENABLE intent
     */
    private void initializeAdapter() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        adapter.startDiscovery();
        if (adapter != null) {
            if (!adapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
                Log.i("Adapter", "enabled");
            }
        } else {
            Log.i("Adapter", "Bluetooth unavailable");
            toast("Bluetooth Device unavailable");
        }
    }

    //Returns true if adapter is initialized
    private boolean adapterInitialized() {
        return (!(adapter == null)) && adapter.isEnabled();
    }

    //endregion //BLE

    //region Notification
    //THIS WOULD GO INSIDE ONCREATE
    //Not doing this rn. Trying to implement own navigation instead
    //        launchNotificationAccessIfNotEnabled(false);
    //        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));
    //Might only work if native Notification
    //        writeLine(TAG, "launchNotificationAccessIfNotEnabled()", launchNotificationAccessIfNotEnabled());
    //        if (launchNotificationAccessIfNotEnabled())
    //        {
    //            writeLine(TAG, "Notification listener enabled");
    ////            registerReceiver(onReader, new IntentFilter());
    //        }
    //        else
    //        {
    //            writeLine(TAG, "Notification listener not enabled");
    //            //service is not enabled try to enabled by calling...
    ////            getApplicationContext().startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    //        }
    //END ONCREATE
    //    private BroadcastReceiver onNotice = new BroadcastReceiver()
    //    {
    //        @Override
    //        public void onReceive(Context context, Intent intent)
    //        {
    //            writeLine("RECEIVER", "Received!");
    //            String pack = intent.getStringExtra("package");
    //            String title = intent.getStringExtra("title");
    //            String text = intent.getStringExtra("text");
    //
    //            writeLine("Receiver", String.format("Title= %s", title));
    //            writeLine("Receiver", String.format("Text= %s", text));
    //            writeLine("Receiver", String.format("Pack= %s", pack));
    //            writeLine("Receiver", String.format("getContext= %s", getApplicationContext()));
    //        }
    //    };
    //
    //    private BroadcastReceiver onReader = new BroadcastReceiver()
    //    {
    //        @Override
    //        public void onReceive(Context context, Intent intent)
    //        {
    //            Log.d("RECEIVER", "Received!");
    //            String temp = intent.getStringExtra("notification_event") + "n";// + txtView.getText();
    //            Log.i("NotificationReceiver", temp);
    //            //txtView.setText(temp);
    //        }
    //    };
    //
    //    public void launchNotificationAccessIfNotEnabled(boolean preCheck)
    //    {
    //        writeLine("preCheck is", preCheck);
    //        if (preCheck)
    //        {
    //            if (Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners").contains(getApplicationContext().getPackageName()))
    //            {
    //                //settings are enabled
    //                writeLine(TAG, "notification access enabled");
    //            }
    //            else
    //            {
    //                writeLine(TAG, "Please enable notification access on the next screen.");
    ////            toast("Please enable notification access on the next screen");
    //                getApplicationContext().startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    //            }
    //        }
    //        else
    //        {
    //            getApplicationContext().startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    //        }
    //
    //    }
    //
    //endregion //Notification
}

