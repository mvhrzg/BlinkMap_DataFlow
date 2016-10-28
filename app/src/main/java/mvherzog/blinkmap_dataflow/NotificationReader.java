package mvherzog.blinkmap_dataflow;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;

public class NotificationReader extends NotificationListenerService
{
    String TAG = this.getClass().getSimpleName();
    Context context;

    @Override
    public void onCreate()
    {
        Log.i("NOTIFICATION", "On Create");
        super.onCreate();
        context = getApplicationContext();
        Log.i("NOTIFICATION", "Context=" + context);
    }

//    @Override
//    public void onNotificationPosted(StatusBarNotification sbn)
//    {
//        String ticker = "";
//        Bundle extras;
//        String title = "";
//        String text = "";
//        Intent msg = new Intent("Msg");
//        String pack = sbn.getPackageName();
//        Log.i("Package", "Package=" + pack);
//
//        Log.i("POSTED:", "describeContents()=" + sbn.describeContents());
//        if (sbn.getNotification().tickerText != null)
//        {
//            ticker = sbn.getNotification().tickerText.toString();
//            Log.i("Ticker", ticker);
//        }
//        if (sbn.getNotification().actions != null)
//        {
//            for (Notification.Action action : sbn.getNotification().actions)
//            {
//                Log.d("ACTION Title", action.title.toString());
//            }
//        }
//        Log.i("POSTED:", "getNotification()=" + sbn.getNotification());
//        if (sbn.getNotification().extras != null)
//        {
//            extras = sbn.getNotification().extras;
//            for (String key : extras.keySet())
//            {
//                Log.i("EXTRAS", key + ": " + extras.get(key));
//            }
////            title = extras.getString("android.title");
////            text = extras.getCharSequence("android.text").toString();
////            Log.i("Title", title);
////            Log.i("Text", text);
////            msg.putExtra("ticker", ticker);
////            msg.putExtra("title", title);
////            msg.putExtra("text", text);
//        }
//
//        msg.putExtra("package", pack);
//
//        LocalBroadcastManager.getInstance(context).sendBroadcast(msg);
//
//    }
//    @Override
//    public void onNotificationRemoved(StatusBarNotification sbn)
//    {
//        Log.i("Msg", "Notification Removed");
//
//    }
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Intent msg = new Intent("Msg");
        if (sbn.getPackageName().equals("com.google.android.apps.maps")) {
            Log.i("GM", "got Google Maps notification");
            Bundle extras = sbn.getNotification().extras;
            String text;
            if (extras.get(Notification.EXTRA_TEXT) instanceof String) {
                text = (String) extras.get(Notification.EXTRA_TEXT);
                Log.i("if TEXT", text);
                msg.putExtra("text", text);
            } else {
                text = extras.get(Notification.EXTRA_TEXT).toString();
                Log.i("else TEXT", text);
                msg.putExtra("text", text);
            }
            Log.i(TAG, text);

        LocalBroadcastManager.getInstance(context).sendBroadcast(msg);
            Intent intent = new Intent(this, MainActivity.class);
            if (text.contains("right")) {
                intent.putExtra("right", true);
            } else if (text.contains("left")) {
                intent.putExtra("right", false);
            }
            startService(intent);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "Got onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onListenerConnected()
    {
        Log.d(TAG, "Got onListenerConnected");
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn)
    {
        if (sbn.getPackageName().equals("com.google.android.apps.maps"))
        {
            Intent intent = new Intent(this, MainActivity.class);
            stopService(intent);
        }
    }
}