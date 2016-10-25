package mvherzog.blinkmap_dataflow;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    @SuppressWarnings("deprecation")
    @Override
    public void onNotificationPosted(StatusBarNotification sbn)
    {
        String ticker = "";
        Bundle extras;
        String title = "";
//        String text = "";
        Intent msg = new Intent("Msg");
        String pack = sbn.getPackageName();
        Log.i("Package", "Package=" + pack);
        Log.i("CREATOR", Notification.Action.CREATOR.toString());

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
//            title = extras.getString("android.title");
//            text = extras.getCharSequence("android.text").toString();
//            Log.i("Title", title);
//            Log.i("Text", text);
//            msg.putExtra("ticker", ticker);
//            msg.putExtra("title", title);
//            msg.putExtra("text", text);
            for (String key : extras.keySet()) {
                Log.i("EXTRAS", key + ": " + extras.get(key));
            }
        }

//        AccessibilityEvent event = AccessibilityEvent.obtain();
//        Notification notification = (Notification) event.getParcelableData();
        RemoteViews views = sbn.getNotification().contentView;
        Class secretClass = views.getClass();

        try {
            Map<Integer, String> text = new HashMap<Integer, String>();

            Field outerFields[] = secretClass.getDeclaredFields();
            for (int i = 0; i < outerFields.length; i++) {
                if (!outerFields[i].getName().equals("mActions")) continue;

                outerFields[i].setAccessible(true);

                ArrayList<Object> actions = (ArrayList<Object>) outerFields[i]
                        .get(views);
                for (Object action : actions) {
                    Log.d("OUTERFIELD[i]|ACTION", String.format("%s | %s", outerFields[i], action.toString()));
                    Field innerFields[] = action.getClass().getDeclaredFields();

                    Object value = null;
                    Integer type = -1;
                    Integer viewId = -1;
                    for (Field field : innerFields) {
                        field.setAccessible(true);
                        if (field.getName().equals("value")) {
                            value = field.get(action);
                        } else if (field.getName().equals("type")) {
                            type = field.getInt(action);
                        } else if (field.getName().equals("viewId")) {
                            viewId = field.getInt(action);
                        }
                        Log.d("FIELD|VALUE|TYPE|VIEWID", String.format("%s | %s | %d | %d", field, value, type, viewId));
                    }

                    if (type == 9 || type == 10) {
                        text.put(viewId, value.toString());
                    }
                }

//                System.out.println("title is: " + text.get(16908310));
//                System.out.println("info is: " + text.get(16909082));
//                System.out.println("text is: " + text.get(16908358));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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
