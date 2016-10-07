package mvherzog.blinkmap_dataflow;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by mherzog on 10/5/2016.
 */

class ConnectionThread extends Thread
{
    private static final String TAG = MainActivity.class.getSimpleName();
    private final BluetoothSocket socket;
    private final BluetoothDevice device;
    private UUID CONNECTION_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    protected ConnectedThread connected;
    BluetoothAdapter adapter;
    ParcelUuid[] uuids;
    Class<?>[] parcels = {};

    public ConnectionThread(BluetoothDevice device)
    {
        Log.i(TAG, "CONNECTION.CONSTRUCTOR");
        BluetoothSocket tmp_socket = null;
//            CONNECTION_UUID = UUID.fromString(device.ACTION_UUID);
        this.device = device;
        Log.i(TAG, "Device = " + this.device.getName());
        try
        {
            adapter = BluetoothAdapter.getDefaultAdapter();
            Method getUuidsMethod = BluetoothAdapter.class.getDeclaredMethod("getUuids", parcels);

            uuids = (ParcelUuid[]) getUuidsMethod.invoke(adapter, parcels);
            for (ParcelUuid uuid : uuids)
            {
                Log.d(TAG, "UUID: " + uuid.getUuid().toString());
            }
            tmp_socket = this.device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(uuids[1].toString()));//this.device.createRfcommSocketToServiceRecord(CONNECTION_UUID);
            Log.d(TAG, "tmp_socket= " + tmp_socket);
        }
        catch (IOException e)
        {
            Log.d(TAG, e.getMessage());
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
        socket = tmp_socket;
    }

    public void run()
    {
        Log.i(TAG, "CONNECTION.RUN");
        adapter.cancelDiscovery();
        Log.i(TAG, "Socket.isConnected(): " + socket.isConnected());
        // TODO: 10/6/2016 START HERE AND GET SOCKET CONNECTED 

        try
        {
            socket.connect();
            Log.i(TAG, "Socket.connect()");
        }
        catch (IOException connectException)
        {
            Log.i(TAG, "catch1");
            cancel();
            return;
        }

        connected = new ConnectedThread(socket);
        Log.d(TAG, "connecting");
        connected.start();
    }

    public void cancel()
    {
        try
        {
            socket.close();
            Log.i(TAG, "socket.close()");
        }
        catch (IOException io)
        {
            Log.d(TAG, io.getMessage());
        }
    }
}
