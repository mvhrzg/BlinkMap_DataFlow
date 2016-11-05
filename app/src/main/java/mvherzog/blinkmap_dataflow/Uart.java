package mvherzog.blinkmap_dataflow;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import java.util.LinkedList;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;

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

    // UUIDs for UART service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID   = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID   = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    // UUID for the UART BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Device Information service and associated characeristics.
    public static UUID DIS_UUID       = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
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

    // Queues for characteristic read (synchronous)
    private Queue<BluetoothGattCharacteristic> readQueue;

    // Interface for a BluetoothLeUart client to be notified of UART actions.
    public interface Callback {
        public void onConnected(Uart uart);
        public void onConnectFailed(Uart uart);
        public void onDisconnected(Uart uart);
        public void onReceive(Uart uart, BluetoothGattCharacteristic rx);
        public void onDeviceFound(BluetoothDevice device);
        public void onDeviceInfoAvailable();
    }

    public Uart(Context context) {
        super();
        this.context = context;
        this.callbacks = new WeakHashMap<Callback, Object>();
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
        this.readQueue = new ConcurrentLinkedQueue<BluetoothGattCharacteristic>();
    }

    // Return instance of BluetoothGatt.
    public BluetoothGatt getGatt() {
        return gatt;
    }

    // Return true if connected to UART device, false otherwise.
    public boolean isConnected() {
        return (tx != null && rx != null);
    }

    public String getDeviceInfo() {
        if (tx == null || !disAvailable ) {
            // Do nothing if there is no connection.
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Manufacturer : " + disManuf.getStringValue(0) + "\n");
        sb.append("Model        : " + disModel.getStringValue(0) + "\n");
        sb.append("Firmware     : " + disSWRev.getStringValue(0) + "\n");
        return sb.toString();
    };

    public boolean deviceInfoAvailable() { return disAvailable; }

    // Send data to connected UART device.
    public void send(byte[] data) {
        if (tx == null || data == null || data.length == 0) {
            // Do nothing if there is no connection or message to send.
            return;
        }
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(data);
        writeInProgress = true; // Set the write in progress flag
        gatt.writeCharacteristic(tx);
        // ToDo: Update to include a timeout in case this goes into the weeds
        while (writeInProgress); // Wait for the flag to clear in onCharacteristicWrite
    }

    // Send data to connected UART device.
    public void send(String data) {
        if (data != null && !data.isEmpty()) {
            send(data.getBytes(Charset.forName("UTF-8")));
            Log.i("Uart send", String.format("Data sent: %s", data));
        }
    }

    // Register the specified callback to receive UART callbacks.
    public void registerCallback(Callback callback) {
        callbacks.put(callback, null);
    }

    // Unregister the specified callback.
    public void unregisterCallback(Callback callback) {
        callbacks.remove(callback);
    }

    // Disconnect to a device if currently connected.
    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
        }
        gatt = null;
        tx = null;
        rx = null;
    }

    // Stop any in progress UART device scan.
    public void stopScan() {
        if (adapter != null) {
            adapter.stopLeScan(this);
        }
    }

    // Start scanning for BLE UART devices.  Registered callback's onDeviceFound method will be called
    // when devices are found during scanning.
    public void startScan() {
        if (adapter != null) {
            adapter.startLeScan(this);
        }
    }

    // Connect to the first available UART device.
    public void connectFirstAvailable() {
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
                // Connected to device, start discovering services.
                if (!gatt.discoverServices()) {
                    // Error starting service discovery.
                    connectFailure();
                }
            }
            else {
                // Error connecting to device.
                connectFailure();
            }
        }
        else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            // Disconnected, notify callbacks of disconnection.
            rx = null;
            tx = null;
            notifyOnDisconnected(this);
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        // Notify connection failure if service discovery failed.
        if (status == BluetoothGatt.GATT_FAILURE) {
            connectFailure();
            return;
        }

        // Save reference to each UART characteristic.
        tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
        rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);

        // Save reference to each DIS characteristic.
        disManuf = gatt.getService(DIS_UUID).getCharacteristic(DIS_MANUF_UUID);
        disModel = gatt.getService(DIS_UUID).getCharacteristic(DIS_MODEL_UUID);
        disHWRev = gatt.getService(DIS_UUID).getCharacteristic(DIS_HWREV_UUID);
        disSWRev = gatt.getService(DIS_UUID).getCharacteristic(DIS_SWREV_UUID);

        // Add device information characteristics to the read queue
        // These need to be queued because we have to wait for the response to the first
        // read request before a second one can be processed (which makes you wonder why they
        // implemented this with async logic to begin with???)
        readQueue.offer(disManuf);
        readQueue.offer(disModel);
        readQueue.offer(disHWRev);
        readQueue.offer(disSWRev);

        // Request a dummy read to get the device information queue going
        gatt.readCharacteristic(disManuf);

        // Setup notifications on RX characteristic changes (i.e. data received).
        // First call setCharacteristicNotification to enable notification.
        if (!gatt.setCharacteristicNotification(rx, true)) {
            // Stop if the characteristic notification setup failed.
            connectFailure();
            return;
        }
        // Next update the RX characteristic's client descriptor to enable notifications.
        BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
        if (desc == null) {
            // Stop if the RX characteristic has no client descriptor.
            connectFailure();
            return;
        }
        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(desc)) {
            Log.i("Uart", "couldn't write desc");
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
    public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            //Log.w("DIS", characteristic.getStringValue(0));
            // Check if there is anything left in the queue
            BluetoothGattCharacteristic nextRequest = readQueue.poll();
            if(nextRequest != null){
                // Send a read request for the next item in the queue
                gatt.readCharacteristic(nextRequest);
            }
            else {
                // We've reached the end of the queue
                disAvailable = true;
                notifyOnDeviceInfoAvailable();
            }
        }
        else {
            //Log.w("DIS", "Failed reading characteristic " + characteristic.getUuid().toString());
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
             Log.d("Uart","Characteristic write successful");
        }
        writeInProgress = false;
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        // Stop if the device doesn't have the UART service.
        if (!parseUUIDs(scanRecord).contains(UART_UUID)) {
            return;
        }
        // Notify registered callbacks of found device.
        notifyOnDeviceFound(device);
        // Connect to first found device if required.
        if (connectFirst) {
            // Stop scanning for devices.
            stopScan();
            // Prevent connections to future found devices.
            connectFirst = false;
            // Connect to device.
            gatt = device.connectGatt(context, true, this);
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
            if (cb != null ) {
                cb.onReceive(uart, rx);
            }
        }
    }

    private void notifyOnDeviceFound(BluetoothDevice device) {
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

    // Notify callbacks of connection failure, and reset connection state.
    private void connectFailure() {
        rx = null;
        tx = null;
        notifyOnConnectFailed(this);
    }

    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

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
                            uuids.add(new UUID(leastSignificantBit,
                                               mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                            continue;
                        } finally {
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


/**
 * CREATED BY ME, Commenting it out so I can try out the BLE UART class
 */

// Encapsulate a list of actions to execute. Actions should be queued and executed sequentially to avoid problems
//public class Gatt extends BluetoothGattCallback
//{
//    // Log
//    private final static String TAG = Gatt.class.getSimpleName();
//
//    // Constants
//    private static String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
//
//    public interface ServiceAction
//    {
//        ServiceAction NULL = new ServiceAction()
//        {
//            @Override
//            public boolean execute(BluetoothGatt bluetoothGatt)
//            {
//                // it is null action. do nothing.
//                return true;
//            }
//        };
//
//        /**
//         * Executes action.
//         *
//         * @param bluetoothGatt
//         * @return true - if action was executed instantly. false if action is waiting for feedback.
//         */
//        public boolean execute(BluetoothGatt bluetoothGatt);
//    }
//
//    private final LinkedList<Gatt.ServiceAction> mQueue = new LinkedList<ServiceAction>();        // list of actions to execute
//    private volatile ServiceAction mCurrentAction;
//
//    protected void read(BluetoothGattService gattService, String characteristicUUID, String descriptorUUID)
//    {
//        ServiceAction action = serviceReadAction(gattService, characteristicUUID, descriptorUUID);
//        mQueue.add(action);
//    }
//
//    private Gatt.ServiceAction serviceReadAction(final BluetoothGattService gattService, final String characteristicUuidString, final String descriptorUuidString)
//    {
//        return new Gatt.ServiceAction()
//        {
//            @Override
//            public boolean execute(BluetoothGatt bluetoothGatt)
//            {
//                final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
//                final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUuid);
//                if (characteristic != null)
//                {
//                    if (descriptorUuidString == null)
//                    {
//                        // Read Characteristic
//                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0)
//                        {
//                            bluetoothGatt.readCharacteristic(characteristic);
//                            return false;
//                        }
//                        else
//                        {
//                            Log.w(TAG, "read: characteristic not readable: " + characteristicUuidString);
//                            return true;
//                        }
//                    }
//                    else
//                    {
//                        // Read Descriptor
//                        final UUID descriptorUuid = UUID.fromString(descriptorUuidString);
//                        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUuid);
//                        if (descriptor != null)
//                        {
//                            bluetoothGatt.readDescriptor(descriptor);
//                            return false;
//                        }
//                        else
//                        {
//                            Log.w(TAG, "read: descriptor not found: " + descriptorUuidString);
//                            return true;
//                        }
//                    }
//                }
//                else
//                {
//                    Log.w(TAG, "read: characteristic not found: " + characteristicUuidString);
//                    return true;
//                }
//            }
//        };
//    }
//
//    protected void enableNotification(BluetoothGattService gattService, String characteristicUUID, boolean enable)
//    {
//        ServiceAction action = serviceNotifyAction(gattService, characteristicUUID, enable);
//        mQueue.add(action);
//    }
//
//    private Gatt.ServiceAction serviceNotifyAction(final BluetoothGattService gattService, final String characteristicUuidString, final boolean enable)
//    {
//        return new Gatt.ServiceAction()
//        {
//            @Override
//            public boolean execute(BluetoothGatt bluetoothGatt)
//            {
//                if (characteristicUuidString != null)
//                {
//                    final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
//                    final BluetoothGattCharacteristic dataCharacteristic = gattService.getCharacteristic(characteristicUuid);
//
//                    if (dataCharacteristic == null)
//                    {
//                        Log.w(TAG, "Characteristic with UUID " + characteristicUuidString + " not found");
//                        return true;
//                    }
//
//                    final UUID clientCharacteristicConfiguration = UUID.fromString(CHARACTERISTIC_CONFIG);
//                    final BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(clientCharacteristicConfiguration);
//                    if (config == null)
//                    {
//                        return true;
//                    }
//
//                    // enableNotification/disable locally
//                    bluetoothGatt.setCharacteristicNotification(dataCharacteristic, enable);
//                    // enableNotification/disable remotely
//                    config.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
//                    bluetoothGatt.writeDescriptor(config);
//
//                    return false;
//                }
//                else
//                {
//                    Log.w(TAG, "Characteristic UUID is null");
//                    return true;
//                }
//            }
//        };
//    }
//
//    protected void enableIndication(BluetoothGattService gattService, String characteristicUUID, boolean enable)
//    {
//        ServiceAction action = serviceIndicateAction(gattService, characteristicUUID, enable);
//        mQueue.add(action);
//    }
//
//    private Gatt.ServiceAction serviceIndicateAction(final BluetoothGattService gattService, final String characteristicUuidString, final boolean enable)
//    {
//        return new Gatt.ServiceAction()
//        {
//            @Override
//            public boolean execute(BluetoothGatt bluetoothGatt)
//            {
//                if (characteristicUuidString != null)
//                {
//                    final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
//                    final BluetoothGattCharacteristic dataCharacteristic = gattService.getCharacteristic(characteristicUuid);
//
//                    if (dataCharacteristic == null)
//                    {
//                        Log.w(TAG, "Characteristic with UUID " + characteristicUuidString + " not found");
//                        return true;
//                    }
//
//                    final UUID clientCharacteristicConfiguration = UUID.fromString(CHARACTERISTIC_CONFIG);
//                    final BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(clientCharacteristicConfiguration);
//                    if (config == null)
//                    {
//                        return true;
//                    }
//
//                    // enableNotification/disable remotely
//                    config.setValue(enable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
//                    bluetoothGatt.writeDescriptor(config);
//
//                    return false;
//                }
//                else
//                {
//                    Log.w(TAG, "Characteristic UUID is null");
//                    return true;
//                }
//            }
//        };
//    }
//
//    protected void write(BluetoothGattService gattService, String uuid, byte[] value)
//    {
//        ServiceAction action = serviceWriteAction(gattService, uuid, value);
//        mQueue.add(action);
//    }
//
//    private Gatt.ServiceAction serviceWriteAction(final BluetoothGattService gattService, final String uuid, final byte[] value)
//    {
//        return new Gatt.ServiceAction()
//        {
//            @Override
//            public boolean execute(BluetoothGatt bluetoothGatt)
//            {
//                final UUID characteristicUuid = UUID.fromString(uuid);
//                final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUuid);
//                if (characteristic != null)
//                {
//                    characteristic.setValue(value);
//                    bluetoothGatt.writeCharacteristic(characteristic);
//                    return false;
//                }
//                else
//                {
//                    Log.w(TAG, "write: characteristic not found: " + uuid);
//                    return true;
//                }
//            }
//        };
//    }
//
//    protected void clear()
//    {
//        mCurrentAction = null;
//        mQueue.clear();
//    }
//
//    protected void execute(BluetoothGatt gatt)
//    {
//        if (mCurrentAction == null)
//        {
//            while (!mQueue.isEmpty())
//            {
//                final Gatt.ServiceAction action = mQueue.pop();
//                mCurrentAction = action;
//                if (!action.execute(gatt))
//                {
//                    break;
//                }
//                mCurrentAction = null;
//            }
//        }
//    }
//
//    @Override
//    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
//    {
//        super.onDescriptorRead(gatt, descriptor, status);
//
//        mCurrentAction = null;
//        execute(gatt);
//    }
//
//    @Override
//    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
//    {
//        super.onDescriptorWrite(gatt, descriptor, status);
//
//        mCurrentAction = null;
//        execute(gatt);
//    }
//
//    @Override
//    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
//    {
//        super.onCharacteristicWrite(gatt, characteristic, status);
//
//        mCurrentAction = null;
//        execute(gatt);
//    }
//
//    @Override
//    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
//    {
//        if (newState == BluetoothProfile.STATE_DISCONNECTED)
//        {
//            mQueue.clear();
//            mCurrentAction = null;
//        }
//    }
//
//    @Override
//    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
//    {
//        super.onCharacteristicRead(gatt, characteristic, status);
//
//        mCurrentAction = null;
//        execute(gatt);
//    }
//
//    @Override
//    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
//    {
//    }
//
//    // Helper function to create a Gatt Executor with a custom listener
//    protected static Gatt createExecutor(final BleExecutorListener listener)
//    {
//        return new Gatt()
//        {
//            @Override
//            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
//            {
//                super.onConnectionStateChange(gatt, status, newState);
//                listener.onConnectionStateChange(gatt, status, newState);
//            }
//
//            @Override
//            public void onServicesDiscovered(BluetoothGatt gatt, int status)
//            {
//                super.onServicesDiscovered(gatt, status);
//                listener.onServicesDiscovered(gatt, status);
//            }
//
//            @Override
//            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
//            {
//                super.onCharacteristicRead(gatt, characteristic, status);
//                listener.onCharacteristicRead(gatt, characteristic, status);
//            }
//
//            @Override
//            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
//            {
//                super.onCharacteristicChanged(gatt, characteristic);
//                listener.onCharacteristicChanged(gatt, characteristic);
//            }
//
//            @Override
//            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
//            {
//                super.onDescriptorRead(gatt, descriptor, status);
//                listener.onDescriptorRead(gatt, descriptor, status);
//            }
//
//            @Override
//            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
//            {
//                super.onReadRemoteRssi(gatt, rssi, status);
//                listener.onReadRemoteRssi(gatt, rssi, status);
//            }
//
//        };
//    }
//
//    public interface BleExecutorListener
//    {
//
//        void onConnectionStateChange(BluetoothGatt gatt, int status, int newState);
//
//        void onServicesDiscovered(BluetoothGatt gatt, int status);
//
//        void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);
//
//        void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
//
//        void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);
//
//        void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status);
//
//    }
//}