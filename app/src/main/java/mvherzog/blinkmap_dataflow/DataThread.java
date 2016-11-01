package mvherzog.blinkmap_dataflow;

import android.bluetooth.BluetoothSocket;
import android.database.CursorJoiner;
import android.icu.util.Output;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ResultReceiver;
import android.provider.ContactsContract;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

public class DataThread extends Thread
{
    public static final String TAG = DataThread.class.getSimpleName();
    private Handler handler;
    public static final int SEND_CODE = 1;
    public static final int QUIT_CODE = 2;
    private final Looper looper;
    private BluetoothSocket socket;
    private InputStream input;
    private OutputStream output;

    public DataThread(BluetoothSocket socket)
    {
        Log.i(TAG, "new DataThread");
        this.socket = socket;
        looper = Looper.getMainLooper();
    }

    @Override
    public void run()
    {

        Log.i("RUN", "calling Looper.prepare");

        looper.prepare();
        Log.i("RUN", "making new DataHandler");
        handler = new Handler()
        {

            @Override
            public void handleMessage(Message msg)
            {
                Log.i("DataThread", "in private handleMessage");
                if (msg.what == SEND_CODE)
                {
//            try
//            {
                    Log.i("Message=", String.format("%d", SEND_CODE));
//                output.write(SEND_CODE);
//            }
//            catch (IOException e)
//            {
//                e.printStackTrace();
//            }
                }
                else if (msg.what == QUIT_CODE)
                {
                    Looper.myLooper().quitSafely();
                }
            }

        };
        try
        {
            Log.i("RUN", "calling Looper.loop");
            looper.loop();
        }
        catch (RuntimeException re)
        {
            Log.i("RuntimeException", re.getMessage());
        }

    }
        public Handler getThreadHandler()
        {
            return handler;
        }

}