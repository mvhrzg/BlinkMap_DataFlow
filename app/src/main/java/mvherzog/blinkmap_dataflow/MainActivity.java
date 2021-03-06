package mvherzog.blinkmap_dataflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, Uart.Callback, ObtainDirections.Response, BlinkmapGeocoder.Response {
    private static final double EPSILON = 0.00001;
    private static final double HEADSUP = 0.00003;

    Button btnConnect, btnDisconnect;
    EditText destinationText;
    InputMethodManager inputManager;
    TextView originText, oLat, oLng, destTitle, originTitle;
    public static final String TAG = MainActivity.class.getSimpleName();

    //UART & GATT connection
    private Uart uart;
    private BluetoothAdapter adapter;
    public BluetoothDevice adafruit;
    private static final String btAddress = "C5:12:82:4F:6F:CD";
    public BluetoothGatt gatt;

    //Data
    public byte[] left = {0x31, 0x4C, 0x21, 0x0D};        //1L!CR --> left
    public byte[] right = {0x31, 0x52, 0x21, 0x0D};       //1R!CR --> right
    public byte[] uturn = {0x31, 0x55, 0x21, 0x0D};       //1U!CR --> uturn
    public byte[] nextCommand = {0x31, 0x23, 0x21, 0x0D}; //1#!CR --> read next
    public byte[] disconnected = {0x31, 0x40, 0x21, 0x0D};//1@!CR --> disconnected
    private String oldManeuver = "";

    //Location Services
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final int REQUEST_PERMISSION_FINE_LOCATION = 1;
    private GoogleApiClient client;
    private LocationRequest request;
    public String destination = "";
    public double destLat, destLng, endLat, endLng;
    private boolean requested = false, started = false, ended = false;
    //ObtainDirections
    private String[] stepManeuver, stepStartLats, stepStartLngs, stepEndLats, stepEndLngs;
    private boolean executeDirectionsFinished, executeAddressFinished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uart = new Uart(getApplicationContext());
        super.onResume();
        setContentView(R.layout.activity_main);

        //Listen for Bluetooth devices
        initializeAdapter();

        //Set up buttons & texts
        originTitle = (TextView) findViewById(R.id.originTitle);
        originText = (TextView) findViewById(R.id.displayOrigin);
        oLat = (TextView) findViewById(R.id.oLat);
        oLng = (TextView) findViewById(R.id.oLng);
        destTitle = (TextView) findViewById(R.id.destTitle);
        destinationText = (EditText) findViewById(R.id.inputDestination);
        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);

        //Set up buttons and texts
        setupClickListeners();

        //Set up Google API client
        client = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API).addApi(AppIndex.API).build();

        //Set up location requests
        request = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(2000).setFastestInterval(1000);
    }

    public void setupClickListeners() {

        //Disable origin text
        originTitle.setVisibility(View.VISIBLE);

        //Disable destination text
        destTitle.setVisibility(View.VISIBLE);
        destinationText.setEnabled(false);
        destinationText.setVisibility(View.VISIBLE);

        //Disable disconnect button
        btnDisconnect.setClickable(false);
        btnDisconnect.setEnabled(false);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast("Connecting...");
                setUpBtnConnect();
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeLine("btnDisconnect", "Clicked to Disconnect");
                client.disconnect();
                writeLine("btnDisconnect", "Sending disconnected command to BLE", Arrays.toString(disconnected));
                sendData(disconnected);
                uart.unregisterCallback(MainActivity.this);
                uart.disconnect();

                //Disable all input except for btnConnect
                destinationText.setText("");
                destinationText.setEnabled(false);
                destinationText.setVisibility(View.VISIBLE);
                btnDisconnect.setEnabled(false);
                btnDisconnect.setClickable(false);

            }
        });

        destinationText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    handled = true;
                    //Get destination text
                    destination = destinationText.getText().toString();
                    writeLine("editText", "destination", destination);
                    //Once we get the destination, get the latitude and longitude so we can build the directions request
                    if (destination != null) {
                        getLatLntFromTextAddress(destination);
                    }
                }

                return handled;
            }
        });

    }

    public void getDirections(double oLat, double oLng, double dLat, double dLng) {
        btnDisconnect.setClickable(true);
        btnDisconnect.setEnabled(true);
        //        btnDisconnect.setVisibility(View.VISIBLE);

        String[] urlParams = {String.valueOf(oLat), String.valueOf(oLng), String.valueOf(dLat), String.valueOf(dLng)};

        executeDirectionsFinished = false;

        new ObtainDirections(this).execute(urlParams);
    }

    @Override
    public void populateDirectionArrays(String[] rManeuvers, String[] startLats, String[] startLngs, String[] endLats, String[] endLngs) {
        stepManeuver = new String[rManeuvers.length];
        stepStartLats = new String[startLats.length];
        stepStartLngs = new String[startLngs.length];
        stepEndLats = new String[endLats.length];
        stepEndLngs = new String[endLngs.length];
        for (int i = 0; i < rManeuvers.length; i++) {
            stepManeuver[i] = rManeuvers[i];
            stepStartLats[i] = startLats[i];
            stepStartLngs[i] = startLngs[i];
            stepEndLats[i] = endLats[i];
            stepEndLngs[i] = endLngs[i];
        }
    }

    public void getLatLntFromTextAddress(String strAddress) {

        String[] splitAddr = strAddress.split(getString(R.string.emptyString));
        executeAddressFinished = false;

        new BlinkmapGeocoder(MainActivity.this).execute(splitAddr);
    }

    @Override
    public void onExecuteFinished() {executeDirectionsFinished = true;}

    @Override
    public void onExecuteAddressFinished() {executeAddressFinished = true;}

    // region Location Services
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        writeLine("onConnected", "Location Services connected");
        //Get last known location, set invisible fields' text for origin latitude and origin
        // longitude (so we can build the url when user input destination)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                                                                                                                                                                          Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, request, this);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_FINE_LOCATION);
        }
        //Get last know location after we request location updates (for accuracy)
        Location location = LocationServices.FusedLocationApi.getLastLocation(client);

        //        originText.setEnabled(true);
        originText.setVisibility(View.VISIBLE);

        //Set origin text as address
        if (originText.getText().toString().isEmpty()) {
            writeLine("onConnected", "setting originText");
            originText.append(getTextAddressFromLocation(location), 0, getTextAddressFromLocation(location).length());
        }

        //Set hidden origin latitude field
        if (oLat.getText().toString().isEmpty()) {
            oLat.append(String.valueOf(location.getLatitude()));
        }

        //Set hidden origin longitude field
        if (oLng.getText().toString().isEmpty()) {
            oLng.append(String.valueOf(location.getLongitude()));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        writeLine("onConnectionSuspended", "Location Serviced suspended.");
        onDestroy();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            }
            catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            writeLine("onConnectionFailed", "Location services connection failed with code " + connectionResult.getErrorCode());
            onDestroy();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (executeAddressFinished && !executeDirectionsFinished && !requested) {
            getDirections(Double.valueOf(oLat.getText().toString()), Double.valueOf(oLng.getText().toString()), destLat, destLng);
            requested = true;
            //Collapse keyboard once we get directions
            inputManager = (InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(MainActivity.this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
        handleNewLocation(location);
    }

    private void handleNewLocation(Location location) {
        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();

        if (executeDirectionsFinished) {  //if execution finished
            writeLine("handleNewLocation", String.format("latitude: %f, longitude: %f", currentLatitude, currentLongitude));
            analyzeManeuver(currentLatitude, currentLongitude);
            writeLine("handleNewLocation", "...............................................................................................");
        }
    }

    /**
     * Loops through direction arrays and matches current coordinates to starting coordinates.
     * Calls checkEnds.
     *
     * @param lat Current latitude
     * @param lng Current longitude
     */
    public void analyzeManeuver(double lat, double lng) {
        double startLat, startLng;
        String maneuver;

        for (int i = 0; i < stepManeuver.length; i++) {
            if (!started) {
                if (stepManeuver[i] != null) {
                    maneuver = stepManeuver[i];
                    //Get starting and ending coordinates
                    startLat = Double.valueOf(stepStartLats[i]);
                    startLng = Double.valueOf(stepStartLngs[i]);
                    endLat = Double.valueOf(stepEndLats[i]);
                    endLng = Double.valueOf(stepEndLngs[i]);
                    if (shouldStart(lat, startLat) && shouldStart(lng, startLng)) {
                        sendManeuver(maneuver, oldManeuver);        //send to BLE
                        oldManeuver = maneuver;                     //set oldManeuver
                        started = true;                             //set starter flag
                        checkEnds(lat, endLat, lng, endLng);
                    }
                }
            } else {
                checkEnds(lat, endLat, lng, endLng);
            }
        }
    }

    /**
     * Checks ending coordinates to end a started maneuver
     *
     * @param currLat Current latitude
     * @param endLat  Ending latitude
     * @param currLng Current longitude
     * @param endLng  Ending longitude
     * @return false if move ended, true otherwise
     */

    public boolean checkEnds(double currLat, double endLat, double currLng, double endLng) {
        if (shouldEnd(currLat, endLat) && shouldEnd(currLng, endLng)) {
            sendData(nextCommand);      //tell BLE to stop blinking
            oldManeuver = "";
            started = false;
        }
        return started; //returns true until the move ends
    }

    private void sendManeuver(String maneuver, String previousManeuver) {
        if (!previousManeuver.equals(maneuver)) {     //if this is a different maneuver than the last
            writeLine("sendManeuver", "START", ".......................................");
            // nextCommand should only be sent if a maneuver has already happened
            if (previousManeuver.equals(""))  //oldManeuver != empty means at least one maneuver has already happened
            {                             //at this point, we can send nextCommand
                sendData(nextCommand);
            }
            //Analyze Maneuver
            if (maneuver.contains("left")) {
                writeLine("sendData(left)", "sending maneuver left", maneuver);
                sendData(left);
                writeLine("sendData(LEFT)", "oldManeuver", previousManeuver);
                writeLine("sendManeuver", "END", ".......................................");
            } else if (maneuver.contains("right")) {
                writeLine("sendData(right)", "sending maneuver right", maneuver);
                sendData(right);
                writeLine("sendData(RIGHT)", "oldManeuver", previousManeuver);
                writeLine("sendManeuver", "END", ".......................................");
            } else if (maneuver.contains("uturn") || maneuver.contains("u-turn")) {
                writeLine("sendData(uturn)", "sending uturn maneuver", maneuver);
                sendData(uturn);
                writeLine("sendData(UTURN)", "oldManeuver", previousManeuver);
                writeLine("sendManeuver", "END", ".......................................");
            } else {
                writeLine("maneuver", "didn't contain left, right or uturn");
                sendData(nextCommand);
                writeLine("sendManeuver", "END", ".......................................");
            }
        }
    }

    //Compare doubles
    public boolean shouldStart(double a, double b) {
        if (a == b) { return true; }
        return Math.abs(a - b) < HEADSUP * Math.max(Math.abs(a), Math.abs(b));
    }

    public boolean shouldEnd(double a, double b) {
        if (a == b) { return true; }
        return Math.abs(a - b) < EPSILON * Math.max(Math.abs(a), Math.abs(b));
    }

    @Override
    public void populateCoordinates(Double lat, Double lng) {
        destLat = lat;
        destLng = lng;
    }

    public String getTextAddressFromLocation(Location location) {
        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(this);
        String fullAddress = "";

        try {
            addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            String addressLine = addresses.get(0).getAddressLine(0);
            String city = addresses.get(0).getLocality();
            String state = addresses.get(0).getAdminArea();
            String zip = addresses.get(0).getPostalCode();
            String country = addresses.get(0).getCountryCode();
            fullAddress = String.format("%s, %s, %s, %s, %s", addressLine, city, state, zip, country);
            writeLine("getTextAddressFromLocation", "fullAddress", fullAddress);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return fullAddress;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    toast("Accessing location...");
                    writeLine(TAG, "Accessing location...");
                } else {
                    toast("Location permissions denied");
                    writeLine(TAG, "Location permissions denied");
                }
        }
    }
    //endregion Location

    //region Android Stuff
    private void toast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private void setUpBtnConnect() {
        //If adapter isn't initialized, initialize it
        if (!adapterInitialized()) {
            initializeAdapter();
        } else {    //if it is initialized, check if BLE is already paired
            //if BLE isn't already paired
            if (!isBLEPaired(btAddress)) { //most of the time, only pre-paired if bonded
                //check if adafruit object is null, and initialize it with the BLE device
                if (adafruit == null) {
                    writeLine("setUpBtnConnect", "Adafruit is null. Getting remote device...");
                    adafruit = adapter.getRemoteDevice(btAddress);
                    if (adafruit != null) {
                        connectServices();
                    }
                } else {  //if adafruit object is not null
                    writeLine("setUpBtnConnect", "Adafruit not null.");
                    connectServices();
                }
            } else {    //BLE is already paired
                connectServices();
            }
        }
    }

    public void connectServices() {
        //if adafruit object is not null
        if (adafruit != null) {
            if (!isBLEPaired(btAddress)) {
                writeLine("setUpBtnConnect", "Adafruit no longer null. Connecting BLE...");
                //connect BLE
                uart.connectFirstAvailable();
            }
            //if already connected to location services, refresh it
            if (client.isConnected()) {
                writeLine("connectServices", "Client is connected. Disconnecting...");
                client.disconnect();
                writeLine("connectServices", "Client is disconnected. Connecting...");
                client.connect();
            } else {  //otherwise connect to location services
                writeLine("connectServices", "Client is disconnected. Connecting...");
                client.connect();
            }
        }
    }

    @Override
    protected void onPause() {
        writeLine(TAG, "onPause()");
        super.onPause();
    }

    @Override
    protected void onResume() {
        writeLine(TAG, "onResume()");
        super.onResume();
        uart.registerCallback(this);
        //Only connect the client onResume if BLE is connected
        if (uart.isConnected()) {
            client.connect();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }
    //endregion //Android

    //region  REGION GATT

    //This method will receive JSON
    public void sendData(byte[] hexCommand) {
        if (uart.isConnected()) {
            writeLine("sendData", "Sending", Arrays.toString(hexCommand));
            uart.send(hexCommand);
            writeLine("sendData", "Sent", Arrays.toString(hexCommand));
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
                destTitle.setEnabled(true);
                destTitle.setVisibility(View.VISIBLE);
                destinationText.setEnabled(true);
                destinationText.setVisibility(View.VISIBLE);
                btnDisconnect.setEnabled(true);
                btnDisconnect.setClickable(true);
            }
        });
        while (!uart.isConnected()) ;
        uart.registerCallback(MainActivity.this);
        writeLine(TAG, "Connected!");
        writeLine("setUpBtnConnect", "Connecting Location Services...");
        client.connect();
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
            }
        });
        toast("CONNECTION FAILED");
    }

    @Override
    public void onDisconnected(Uart uart) {
        // Called when the UART device disconnected.
        writeLine(TAG, "Disconnected!");
        destinationText.setText("");
        if (client.isConnected()) {
            client.disconnect();
        }

        btnDisconnect.setClickable(false);
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
        writeLine(TAG, "Waiting for a connection...");
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
        } else if (text instanceof Float) {
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
        } else if (prompt instanceof Float) {
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
        //                if (device.getAddress().shouldStart(address))
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
            //            toast("Bluetooth Device unavailable");
            writeLine(TAG, "Bluetooth Device unavailable");
        }
    }

    //Returns true if adapter is initialized
    private boolean adapterInitialized() {
        return (!(adapter == null)) && adapter.isEnabled();
    }

    //endregion //BLE
}

