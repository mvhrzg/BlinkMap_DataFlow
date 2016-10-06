package mvherzog.blinkmap_dataflow;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by mherzog on 10/5/2016.
 */

public class ConnectedThread extends Thread
{

    private static final String TAG = MainActivity.class.getSimpleName();;
    private final BluetoothSocket socket;
    private final InputStream input;
    private final OutputStream output;

    ConnectedThread(BluetoothSocket socket)
    {
        Log.i(TAG, "CONNECTED.CONSTRUCTOR");
        this.socket = socket;
        InputStream is = null;
        OutputStream os = null;
        try
        {
            is = socket.getInputStream();
            Log.i(TAG, "****IS: " + is.toString());
            os = socket.getOutputStream();
            Log.i(TAG, "****OS: " + os.toString());
        }
        catch (IOException io)
        {
            Log.d(TAG, io.getMessage());
        }
        input = is;
        output = os;
    }

    public void run()
    {
        Log.i(TAG, "CONNECTED.RUN");
        byte[] buffer = new byte[1024];
        int begin = 0;
        int bytes = 0;
        while (true)
        {
            try
            {
                bytes += input.read(buffer, bytes, buffer.length - bytes);
                for (int i = begin; i < bytes; i++)
                {
                    if (buffer[i] == "#".getBytes()[0])
                    {
                        handler.obtainMessage(1, begin, i, buffer).sendToTarget();
                        begin = i + 1;
                        Log.i(TAG, "***writing");
                        write(buffer);
                        Log.i(TAG, "***end_writing");
                        if (i == bytes - 1)
                        {
                            bytes = 0;
                            begin = 0;
                        }
                    }
                }
            }
            catch (IOException io)
            {
                break;
            }
        }
    }

    void write(byte[] bytes)
    {
        Log.i(TAG, "CONNECTED.WRITE");
        try
        {
            output.write(bytes);
        }
        catch (IOException io)
        {
            Log.d(TAG, io.getMessage());
        }
    }

    public void cancel()
    {
        Log.i(TAG, "CONNECTED.CANCEL");
        try
        {
            socket.close();
        }
        catch (IOException io)
        {
            Log.d(TAG, io.getMessage());
        }
    }

    Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            Log.i(TAG, "HANDLE_MESSAGE");
            byte[] buffer = (byte[]) msg.obj;
            int begin = msg.arg1;
            int end = msg.arg2;
            Log.d(TAG, "*****Message: " + msg.toString());

            switch (msg.what)
            {
                case 1:
                    String writeMsg = new String(buffer);
                    writeMsg.substring(begin, end);
                    break;
            }
            super.handleMessage(msg);
        }
    };
}
