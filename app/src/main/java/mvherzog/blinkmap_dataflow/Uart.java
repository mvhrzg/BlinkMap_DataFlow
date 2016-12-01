package mvherzog.blinkmap_dataflow;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.security.Timestamp;
import java.text.BreakIterator;
import java.util.Date;
import java.util.LinkedList;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.widget.Toast;

import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatchEntity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.String;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Uart extends BluetoothGattCallback implements BluetoothAdapter.LeScanCallback {

    private static final int MAX_PACKET_SIZE = 20;
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

    // Internal UART state.
    private Context context;
    private WeakHashMap<Callback, Object> callbacks;
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    public boolean connectFirst;
    private boolean writeInProgress; // Flag to indicate a write is currently in progress

    // Device Information state.
    private BluetoothGattCharacteristic disManuf;
    private BluetoothGattCharacteristic disModel;
    private BluetoothGattCharacteristic disHWRev;
    private BluetoothGattCharacteristic disSWRev;
    private boolean disAvailable;

    //TAG
    private static final String TAG = Uart.class.getSimpleName();

    // Queues for characteristic read (synchronous)
    private Queue<BluetoothGattCharacteristic> readQueue;

    // Interface for a BluetoothLeUart client to be notified of UART actions.
    interface Callback {
        void onConnected(Uart uart);
        void onConnectFailed(Uart uart);
        void onDisconnected(Uart uart);
        void onReceive(Uart uart, BluetoothGattCharacteristic rx);
        void onDeviceFound(BluetoothDevice device);
        void onDeviceInfoAvailable();
    }

    Uart(Context context) {
        super();
        this.context = context;
        this.callbacks = new WeakHashMap<>();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.gatt = null;
        this.tx = null;
        this.rx = null;
        this.disManuf = null;
        this.disModel = null;
        this.disHWRev = null;
        this.disSWRev = null;
        this.disAvailable = false;
        this.connectFirst = false;
        this.writeInProgress = false;
        this.readQueue = new ConcurrentLinkedQueue<>();
    }

    public BluetoothGatt getGatt() {
        return gatt;
    }

    // Return true if connected to UART device, false otherwise.
    boolean isConnected() {
        return (tx != null && rx != null);
    }

    String getDeviceInfo() {
        if (tx == null || !disAvailable) {
            // Do nothing if there is no connection.
            return "";
        }
        return "Manufacturer : " + disManuf.getStringValue(0) + "\n" +
                "Model        : " + disModel.getStringValue(0) + "\n" +
                "Firmware     : " + disSWRev.getStringValue(0) + "\n";
    }

    public boolean deviceInfoAvailable() {
        return disAvailable;
    }

    void send(byte[] data) {
        if (tx == null || data == null || data.length == 0) {
            return;
        }

        tx.setValue(data);
        writeInProgress = true;
        writeLine(String.format("Writing [tx]. Bytes: '%s' | String: '%s'", bytesToHex(tx.getValue()), tx.getStringValue(0)));
        tx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(tx);

        while (writeInProgress) {
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        writeLine("After characteristic write cleared");
    }

    // Register the specified callback to receive UART callbacks.
    void registerCallback(Callback callback) {
        callbacks.put(callback, null);
    }

    // Unregister the specified callback.
    void unregisterCallback(Callback callback) {
        callbacks.remove(callback);
    }

    // Disconnect to a device if currently connected.
    void disconnect() {
        writeLine("Disconnect()");
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
        }
        gatt = null;
        tx = null;
        rx = null;
    }

    // Stop any in progress UART device scan.
    private void stopScan() {
        if (adapter != null) {
            adapter.stopLeScan(this);
        }
    }

    private void startScan() {
        if (adapter != null) {
            adapter.startLeScan(this);
        }
    }

    // Connect to the first available UART device.
    void connectFirstAvailable() {
        // Disconnect to any connected device.
        disconnect();
        // Stop any in progress device scan.
        stopScan();
        // Start scan and connect to first available device.
        connectFirst = true;
        startScan();
    }

    // Handlers for BluetoothGatt and LeScan events.
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeLine("Connected to Adafruit. Start discovering services");
                // Connected to device, start discovering services.
                if (!gatt.discoverServices()) {
                    // Error starting service discovery.
                    connectFailure();
                }
            } else {
                // Error connecting to device.
                connectFailure();
            }
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            rx = null;
            tx = null;
            gatt.disconnect();
            gatt.close();
            notifyOnDisconnected(this);
        }
    }

    private void writeLine(final Object text) {
        MainActivity.writeLine(TAG, text);
    }

    private void writeLine(final String prompt, final Object text) {
        MainActivity.writeLine(TAG, prompt, text);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        writeLine("OnServicesDiscovered Status", parseConnectionError(status));
        super.onServicesDiscovered(gatt, status);
        // Notify connection failure if service discovery failed.
        if (status == BluetoothGatt.GATT_FAILURE) {
            connectFailure();
            return;
        }

        // Save reference to each UART characteristic.
        tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
        rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);

        if (!gatt.setCharacteristicNotification(rx, true)) {
            writeLine("Couldn't set characteristic notification for rx = " + rx.getStringValue(0));
            connectFailure();
            return;
        }
        BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
        if (desc == null) {
            writeLine("[rx] = null");
            // Stop if the RX characteristic has no client descriptor.
            connectFailure();
            return;
        }
        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(desc)) {
            writeLine("Couldn't write descriptor", bytesToHex(desc.getValue()));
            // Stop if the client descriptor could not be written.
            connectFailure();
            return;
        }

        // Notify of connection completion.
        notifyOnConnected(this);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        notifyOnReceive(this, characteristic);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            writeLine("Read characteristic", characteristic.getStringValue(0));
            // Check if there is anything left in the queue
            BluetoothGattCharacteristic nextRequest = readQueue.poll();
            if (nextRequest != null) {
                // Send a read request for the next item in the queue
                gatt.readCharacteristic(nextRequest);
            } else {
                disAvailable = true;
                notifyOnDeviceInfoAvailable();
            }
        } else {
            writeLine("Failed reading characteristic", characteristic.getUuid().toString());
        }

    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            writeInProgress = false;
            writeLine(String.format("Wrote characteristic '%s'", characteristic.getStringValue(0)));
        }
        writeInProgress = false;
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        writeLine("onLeScan()", "Scanning...");
        // Stop if the device doesn't have the UART service.
        if (!parseUUIDs(scanRecord).contains(UART_UUID)) {
            return;
        }
        // Notify registered callbacks of found device.
        notifyOnDeviceFound(device);
        // Connect to first found device if required.
        if (connectFirst) {
            // Stop scanning for devices.
            writeLine("onLeScan()", "Stopping scan...");
            stopScan();
            // Prevent connections to future found devices.
            connectFirst = false;
            // Connect to device.
            gatt = device.connectGatt(context, true, this);
        }
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    private static String parseConnectionError(final int error) {
        switch (error) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS/STATE_DISCONNECTED";
            case BluetoothGatt.STATE_CONNECTED:
                return "STATE_CONNECTED";
            case 0x01:
                return "GATT CONN L2C FAILURE";
            case 0x08:
                return "GATT CONN TIMEOUT";
            case 0x13:
                return "GATT CONN TERMINATE PEER USER";
            case 0x16:
                return "GATT CONN TERMINATE LOCAL HOST";
            case 0x3E:
                return "GATT CONN FAIL ESTABLISH";
            case 0x22:
                return "GATT CONN LMP TIMEOUT";
            case 0x0100:
                return "GATT CONN CANCEL ";
            case 0x0085:
                return "GATT ERROR"; // Device not reachable
            default:
                return "UNKNOWN (" + error + ")";
        }
    }

    // Private functions to simplify the notification of all callbacks of a certain event.
    private void notifyOnConnected(Uart uart) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onConnected(uart);
            }
        }
    }

    private void notifyOnConnectFailed(Uart uart) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onConnectFailed(uart);
            }
        }
    }

    private void notifyOnDisconnected(Uart uart) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onDisconnected(uart);
            }
        }
    }

    private void notifyOnReceive(Uart uart, BluetoothGattCharacteristic rx) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onReceive(uart, rx);
            }
        }
    }

    private void notifyOnDeviceFound(BluetoothDevice device) {
        writeLine("Found device", String.format("%s (%s)", device.getAddress(), device.getName()));
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onDeviceFound(device);
            }
        }
    }

    private void notifyOnDeviceInfoAvailable() {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onDeviceInfoAvailable();
            }
        }
    }

    private void connectFailure() {
        rx = null;
        tx = null;
        notifyOnConnectFailed(this);
    }

    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0) {
                break;
            }

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit, mostSignificantBit));
                        }
                        catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                        finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }
        return uuids;
    }
}