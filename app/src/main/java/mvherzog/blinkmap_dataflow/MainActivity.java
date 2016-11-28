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
    private static final double HEADSUP_EPISLON = 0.0001;
    private static final double EPSILON = 0.00001;

    Button btnConnect, btnDisconnect;
    EditText destinationText;
    TextView originText, oLat, oLng, enterDest;
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
    public double destLat, destLng;
    private boolean requested = false;
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
        originText = (TextView) findViewById(R.id.displayOrigin);
        oLat = (TextView) findViewById(R.id.oLat);
        oLng = (TextView) findViewById(R.id.oLng);
        enterDest = (TextView) findViewById(R.id.enterDest);
        destinationText = (EditText) findViewById(R.id.inputDestination);
        btnConnect = (Button) findViewById(R.id.btnConnect);
        //        btnStart = (Button) findViewById(R.id.btnStart);
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
        //        originText.setEnabled(false);
        originText.setVisibility(View.INVISIBLE);

        //Disable destination text
        enterDest.setVisibility(View.INVISIBLE);
        enterDest.setEnabled(false);

        destinationText.setVisibility(View.INVISIBLE);
        destinationText.setEnabled(false);

        //Disable disconnect button
        btnDisconnect.setClickable(false);
        btnDisconnect.setEnabled(false);
        btnDisconnect.setVisibility(View.INVISIBLE);

        //Disable start transmitting button
        //        btnStart.setClickable(false);
        //        btnStart.setEnabled(false);
        //        btnStart.setVisibility(View.INVISIBLE);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast("You clicked connect");
                setUpBtnConnect();
            }
        });

        //Sending data stops btnDisconnect from working
        //        btnStart.setOnClickListener(new View.OnClickListener() {
        //            @Override
        //            public void onClick(View v) {
        //                sendData(uart);//getNewLocation());
        //            }
        //        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeLine("btnDisconnect", "Clicked to Disconnect");
                client.disconnect();
                writeLine("btnDisconnect", "sending disconnected", Arrays.toString(disconnected));
                sendData(disconnected);
                uart.unregisterCallback(MainActivity.this);
                uart.disconnect();
                writeLine("btnDisconnect", "after disconnected", Arrays.toString(disconnected));

                //Disable all input except for btnConnect
                enterDest.setEnabled(false);
                enterDest.setVisibility(View.INVISIBLE);
                destinationText.setEnabled(false);
                destinationText.setVisibility(View.INVISIBLE);
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
                    //Collapse keyboard once 'Enter'
                    InputMethodManager inputManager = (InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(MainActivity.this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }

                return handled;
            }
        });

    }

    public void getDirections(double oLat, double oLng, double dLat, double dLng) {
        btnDisconnect.setClickable(true);
        btnDisconnect.setEnabled(true);
        btnDisconnect.setVisibility(View.VISIBLE);

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

        String[] splitAddr = strAddress.split(" ");
        executeAddressFinished = false;

        new BlinkmapGeocoder(MainActivity.this).execute(splitAddr);

        //        Geocoder coder = new Geocoder(this);
        //        List<Address> address;
        //        LatLng dest;
        //
        //        try {
        //            address = coder.getFromLocationName(strAddress, 5);
        //            if (address == null) {
        //                return null;
        //            }
        //            Address location = address.get(0);
        //            for (int i = 0; i < address.size(); i++) {
        //                Address loc2 = address.get(i);
        //                writeLine("address.get(", String.valueOf(i), ") " + loc2.toString());
        //            }
        //            dest = new LatLng(location.getLatitude(), location.getLongitude());
        //
        //            return dest;
        //        }
        //        catch (IOException e) {
        //            e.printStackTrace();
        //        }
        //        //return null if try fails
        //        return null;
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

        originText.setEnabled(true);
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
        writeLine("onConnectionSuspended", "Location Serviced suspended. Please reconnect");
        onDestroy();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
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
        //Only get directions if we have the destination's text address
        if (executeAddressFinished && !executeDirectionsFinished && !requested) {
            getDirections(Double.valueOf(oLat.getText().toString()), Double.valueOf(oLng.getText().toString()), destLat, destLng);
            requested = true;
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

    public void analyzeManeuver(double lat, double lng) {
        Double startLat, startLng;
        Double endLat, endLng;
        String maneuver = "";
        boolean started = false;
        int startIndex = -1;

        if (executeDirectionsFinished) {
            //loop through arrays and see if we get a match
            for (int i = 0; i < stepManeuver.length; i++) {
                //If this step has a maneuver
                if (stepManeuver[i] != null) {
                    //Get the starting and corresponding ending coordinates
                    startLat = Double.valueOf(stepStartLats[i]);
                    startLng = Double.valueOf(stepStartLngs[i]);
                    endLat = Double.valueOf(stepEndLats[i]);
                    endLng = Double.valueOf(stepEndLngs[i]);
                    //if we're at the start of a maneuver, send the maneuver to BLE
                    if (equals(startLat, lat) && equals(startLng, lng)) {
                        //Get the current maneuver
                        maneuver = stepManeuver[i];
                        writeLine("analyzeManeuver",
                                  String.format("(%d) START '%s' (oldManeuver: %s). [EQUAL] Lats: %f | %f -- Lngs: %f | %f", i, maneuver, oldManeuver, startLat, lat, startLng, lng)
                        );
                        writeLine("[EQUAL Starts]", "Sending command", maneuver);
                        //Send to BLE
                        sendManeuver(maneuver, oldManeuver);
                        //Set starter flag
                        started = true;
                        startIndex = i;
                    } else {
                        writeLine("analyzeManeuver", String.format("(%d) START '%s'. [DIFF] Lats: %f | %f -- Lngs: %f | %f.", i, oldManeuver, startLat, lat, startLng, lng));
                        //once they match the end coordinates, send nextCommand to BLE
                    }
                    //If we're at the end of a maneuver
                    if (equals(endLat, lat) && equals(endLng, lng)) {
                        //Make sure we're at the end of the correct maneuver by checking index and starter flag
                        if (started && startIndex == i) {
                            writeLine("analyzeManeuver", String.format("(%d) END '%s'. [EQUAL] lats: %f | %f -- Lngs: %f | %f.", i, oldManeuver, endLat, lat, endLng, lng));
                            writeLine("[SENT START]", "**Clearing Command**");
                            sendData(nextCommand);
                            oldManeuver = maneuver;
                            maneuver = "";
                            started = false;
                        }
                    } else {
                        writeLine("analyzeManeuver", String.format("(%d) END '%s'. [DIFF] Lats: %f | %f -- Lngs: %f | %f.", i, oldManeuver, endLat, lat, endLng, lng), "maneuver not over yet");
                    }
                }
            }
        }
    }

    private void sendManeuver(String maneuver, String previousManeuver) {
        if (!previousManeuver.equals(maneuver)) {     //if this is a different maneuver than the last
            writeLine("sendManeuver", "START", ".......................................");
            //Is this the first maneuver?
            //We need to check so we know when to send nextCommand.
            // nextCommand should only be sent if a maneuver has already happened
            if (!previousManeuver.equals(""))  //oldManeuver != empty means at least one maneuver has already happened
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
                writeLine("sendManeuver", "END", ".......................................");
            }
        }
    }

    /**
     * Compare two doubles within a given epsilon
     */

    public static boolean equals(double a, double b, double eps) {
        if (a == b) { return true; }
        // If the difference is less than epsilon, treat as equal.
        return Math.abs(a - b) < eps;
    }

    /**
     * Compare two doubles, using default epsilon
     */
    public static boolean equals(double a, double b) {
        if (a == b) { return true; }
        // If the difference is less than epsilon, treat as equal.
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
                    //                    toast("Accessing location...");
                    writeLine(TAG, "Accessing location...");
                } else {
                    //                    toast("Location permissions denied");
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
        Log.i("btnConnect", "Clicked to Connect");
        //If adapter isn't initialized, initialize it
        if (!adapterInitialized()) {
            writeLine("setUpBtnConnect", "adapterInitialized()", adapterInitialized());
            initializeAdapter();
        } else {    //if it is initialized, check if BLE is already paired
            writeLine("setUpBtnConnect", "adapterInitialized()", adapterInitialized());
            //if BLE isn't already paired
            if (!isBLEPaired(btAddress)) //only pre-paired if bonded
            {
                writeLine("setUpBtnConnect", "isBLEPaired", isBLEPaired(btAddress));
                //check if adafruit object is null, and initialize it with the BLE device
                if (adafruit == null) {
                    writeLine("setUpBtnConnect", "adafruit is null. getting remote device ", btAddress);
                    adafruit = adapter.getRemoteDevice(btAddress);
                    if (adafruit != null) {
                        connectServices();
                    }
                } else {  //if adafruit object is not null
                    writeLine("setUpBtnConnect", "Adafruit != null, calling uart.connectFirstAvalable");
                    connectServices();
                }
            } else {    //BLE is already paired
                writeLine("setUpBtnConnect", "isBLEPaired", isBLEPaired(btAddress));
                connectServices();
            }
        }

    }

    public void connectServices() {
        //if adafruit object is not null
        if (adafruit != null) {
            if (!isBLEPaired(btAddress)) {
                writeLine("setUpBtnConnect", "adafruit no longer null, calling uart.connectFirstAvailable");
                //connect BLE
                uart.connectFirstAvailable();
            }
            //if already connected to location services, refresh it
            if (client.isConnected()) {
                writeLine("connectServices", "client is connected. disconnecting...");
                client.disconnect();
                writeLine("connectServices", "client is disconnected. connecting...");
                client.connect();
            } else {  //otherwise connect to location services
                writeLine("connectServices", "client is disconnected. connecting...");
                client.connect();
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
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        //        uart.unregisterCallback(this);  //trying to stop BLE from blinking when disconnected
        //        if (client.isConnected()) {
        //            client.disconnect();
        //        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        //        uart.unregisterCallback(this);
        //this is being called when the device rotates and disconnecting
        //        if (client.isConnected()) {
        //            client.disconnect();
        //        }
        //        unregisterReceiver(onReader);
    }
    //endregion //Android
    //region  REGION GATT

    //This method will receive JSON
    public void sendData(byte[] hexCommand) {
        if (uart.isConnected()) {
            writeLine("sendData", "sending", Uart.bytesToHex(hexCommand));
            uart.send(hexCommand);
            writeLine("sendData", "sent", Uart.bytesToHex(hexCommand));
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
                enterDest.setEnabled(true);
                enterDest.setVisibility(View.VISIBLE);
                destinationText.setEnabled(true);
                destinationText.setVisibility(View.VISIBLE);
                btnDisconnect.setEnabled(true);
                btnDisconnect.setClickable(true);
                btnDisconnect.setVisibility(View.VISIBLE);
            }
        });
        while (!uart.isConnected()) ;
        uart.registerCallback(MainActivity.this);
        writeLine(TAG, "Connected!");
        writeLine("setUpBtnConnect", "Calling client.connect()");
        client.connect();
        //        toast("CONNECTED!!!!!!");
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
                //                btnStart = (Button) findViewById(R.id.btnStart);
                //                btnStart.setClickable(false);
                //                btnStart.setEnabled(false);
            }
        });
        toast("CONNECTION FAILED");
    }

    @Override
    public void onDisconnected(Uart uart) {
        // Called when the UART device disconnected.
        //        gatt.disconnect();
        //        gatt.close();
        writeLine(TAG, "Disconnected!");
        if (client.isConnected()) {
            client.disconnect();
        }
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
        //        toast("DISCONNECTED");
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
        //        toast("Waiting for connection...");
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

