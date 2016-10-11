//package mvherzog.blinkmap_dataflow;
//
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.bluetooth.BluetoothSocket;
//import android.bluetooth.le.BluetoothLeScanner;
//import android.bluetooth.le.ScanCallback;
//import android.os.ParcelUuid;
//import android.util.Log;
//
//import java.io.IOException;
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//
///**
// * Created by mherzog on 10/5/2016.
// */
//
//class ConnectionThread extends Thread
//{
//    private static final String TAG = MainActivity.class.getSimpleName();
//    private BluetoothSocket socket;
//    private final BluetoothDevice device;
//    private UUID CONNECTION_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
//    protected ConnectedThread connected;
//    BluetoothAdapter adapter;
//    BluetoothLeScanner scanner;
//    ParcelUuid[] parcelUuids;
//    UUID[] uuids;
//    Class<?>[] parcels = {};
//    BluetoothAdapter.LeScanCallback callback;
//
//    public ConnectionThread(BluetoothDevice device)
//    {
//        Log.i(TAG, "CONNECTION.CONSTRUCTOR");
//        this.device = device;
//        Log.i(TAG, "Device = " + this.device.getName());
//        try
//        {
//            adapter = BluetoothAdapter.getDefaultAdapter();
//            //Added by me 10/10/16
//            scanner = adapter.getBluetoothLeScanner();
//            callback = new BluetoothAdapter.LeScanCallback()
//            {
//                @Override
//                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord)
//                {
//
//                }
//            };
//            Method getUuidsMethod = BluetoothAdapter.class.getDeclaredMethod("getUuids", parcels);
//
//            parcelUuids = (ParcelUuid[]) getUuidsMethod.invoke(adapter, parcels);
//            uuids = new UUID[parcelUuids.length];
//            int i = 0;
//            for (ParcelUuid uuid : parcelUuids)
//            {
////                Log.d(TAG, "UUID: " + uuid.getUuid().toString());
//                uuids[i] = uuid.getUuid();
//                i++;
//            }
//            socket = this.device.createRfcommSocketToServiceRecord(UUID.fromString(parcelUuids[1].toString()));//this.device.createInsecureRfcommSocketToServiceRecord(CONNECTION_UUID);
//            BleDevicesScanner bd = new BleDevicesScanner(adapter, scanner, uuids, callback);
//            bd.start();
//        }
//        catch (IOException e)
//        {
//            Log.d(TAG, e.getMessage());
//        }
//        catch (NoSuchMethodException e)
//        {
//            e.printStackTrace();
//        }
//        catch (IllegalAccessException e)
//        {
//            e.printStackTrace();
//        }
//        catch (InvocationTargetException e)
//        {
//            e.printStackTrace();
//        }
//    }
//
//    public void run()
//    {
//        Log.i(TAG, "CONNECTION.RUN");
//        adapter.cancelDiscovery();
//        Log.i(TAG, "Socket.isConnected(): " + socket.isConnected());
//        try
//        {
//            socket.connect();
//            Log.i(TAG, "Socket.connect()");
//        }
//        catch (IOException connectException)
//        {
//            Log.i(TAG, "catch1");
//            cancel();
//            return;
//        }
//        if (socket != null && !socket.isConnected())
//        {
//            connected = new ConnectedThread(socket);
//            Log.d(TAG, "connecting");
//            connected.start();
//        }
//
//    }
//
//    public void cancel()
//    {
//        try
//        {
//            socket.close();
//            Log.i(TAG, "socket.close()");
//        }
//        catch (IOException io)
//        {
//            Log.d(TAG, io.getMessage());
//        }
//    }
//}
