package mvherzog.blinkmap_dataflow;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;

public class NotificationReader extends NotificationListenerService
{
    Context context;

    @Override
    public void onCreate()
    {
        Log.i("NOTIFICATION", "On Create");
        super.onCreate();
        context = getApplicationContext();
        Log.i("NOTIFICATION", "Context=" + context);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn)
    {
        String ticker = "";
        Bundle extras;
        String title = "";
        String text = "";
        Intent msg = new Intent("Msg");
        String pack = sbn.getPackageName();
        Log.i("Package", "Package=" + pack);

        Log.i("POSTED:", "describeContents()=" + sbn.describeContents());
        if (sbn.getNotification().tickerText != null)
        {
            ticker = sbn.getNotification().tickerText.toString();
            Log.i("Ticker", ticker);
        }
        if (sbn.getNotification().actions != null)
        {
            for (Notification.Action action : sbn.getNotification().actions)
            {
                Log.d("ACTION Title", action.title.toString());
            }
        }
        Log.i("POSTED:", "getNotification()=" + sbn.getNotification());
        if (sbn.getNotification().extras != null)
        {
            extras = sbn.getNotification().extras;
            for (String key : extras.keySet()) {
                Log.i("EXTRAS", key + ": " + extras.get(key));
            }
//            title = extras.getString("android.title");
//            text = extras.getCharSequence("android.text").toString();
//            Log.i("Title", title);
//            Log.i("Text", text);
//            msg.putExtra("ticker", ticker);
//            msg.putExtra("title", title);
//            msg.putExtra("text", text);
        }

        RemoteViews view = sbn.getNotification().tickerView;
        Log.i("VIEW", view.getPackage().toString());

        msg.putExtra("package", pack);

        LocalBroadcastManager.getInstance(context).sendBroadcast(msg);

//        }

    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn)
    {
        Log.i("Msg", "Notification Removed");

    }
}
