package mvherzog.blinkmap_dataflow;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static mvherzog.blinkmap_dataflow.MainActivity.writeLine;

public class NotificationReader extends NotificationListenerService
{
    String TAG = this.getClass().getSimpleName();
    Context context;
    public static boolean isNotificationAccessEnabled = false;

    @Override
    public void onCreate()
    {
        writeLine("On Create");
        super.onCreate();
        context = getApplicationContext();
        writeLine("Context" + context);
    }

//    @Override
//    public IBinder onBind(Intent mIntent)
//    {
//        IBinder mIBinder = super.onBind(mIntent);
//        isNotificationAccessEnabled = true;
//        return mIBinder;
//    }
//
//    @Override
//    public boolean onUnbind(Intent mIntent)
//    {
//        boolean mOnUnbind = super.onUnbind(mIntent);
//        isNotificationAccessEnabled = false;
//        return mOnUnbind;
//    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn)
    {
        writeLine("Posted", "getNotification()=" + sbn.getNotification());

        if (sbn.getPackageName().equalsIgnoreCase("com.google.android.apps.maps"))
        {
            try
            {
                RemoteViews views = sbn.getNotification().bigContentView;
                Class<?> rvClass = views.getClass();
                Field field = rvClass/*.getSuperclass()*/.getDeclaredField("mActions");
                field.setAccessible(true);
                ArrayList<Parcelable> actions = (ArrayList<Parcelable>) field
                        .get(views);

                for (Parcelable action : actions)
                {
                    // create parcel from action
                    Parcel parcel = Parcel.obtain();
                    action.writeToParcel(parcel, 0);
                    parcel.setDataPosition(0);
                    writeLine("actions: ", action);

                    // check if is 2 / ReflectionAction
                    int tag = parcel.readInt();
//                    if (tag != 2)
//                    {
//                        continue;
//                    }

                    int viewId = parcel.readInt();

                    String methodName = parcel.readString();
                    writeLine("methodName: ", methodName);
                    if (methodName == null || !methodName.equals("setText"))
                    {
                        writeLine("#Big Not setText: " + methodName);
                        continue;
                    }

                }

                Intent msg = new Intent("Msg");

                String ticker = "";
                Bundle extras;
                String title = "";
                String text = "";
                String pack = sbn.getPackageName();
                writeLine("Package", pack);

                if (sbn.getNotification().tickerText != null)
                {
                    ticker = sbn.getNotification().tickerText.toString();
                    writeLine("Ticker", ticker);
                }
                if (sbn.getNotification().actions != null)
                {
                    for (Notification.Action action : sbn.getNotification().actions)
                    {
                        writeLine("ACTION Title", action.title.toString());
                    }
                }
                if (sbn.getNotification().extras != null)
                {
                    extras = sbn.getNotification().extras;
                    for (String key : extras.keySet())
                    {
                        writeLine("EXTRAS", key + ": " + extras.get(key));
                    }
//            title = extras.getString("android.title");
//            text = extras.getCharSequence("android.text").toString();
//            Log.i("Title", title);
//            Log.i("Text", text);
                    writeLine("msg.getDataStrng()", msg.getDataString());
                    msg.putExtra("ticker", ticker);
                    msg.putExtra("title", title);
                    msg.putExtra("text", text);
                }

                msg.putExtra("package", pack);

                LocalBroadcastManager.getInstance(context).sendBroadcast(msg);

            }
            catch (IllegalAccessException e)
            {
                e.printStackTrace();
            }
            catch (NoSuchFieldException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn)
    {
        writeLine("Msg", "Notification Removed");

    }

    private void writeLine(final Object text)
    {
        MainActivity.writeLine(TAG, text);
    }

    private void writeLine(final String prompt, final Object text)
    {
        MainActivity.writeLine(TAG, prompt, text);
    }

//    @Override
//    public void onNotificationPosted(StatusBarNotification sbn) {
//        Intent msg = new Intent("Msg");
//        if (sbn.getPackageName().shouldStart("com.google.android.apps.maps")) {
//            writeLine("GM", "got Google Maps notification");
//            Bundle extras = sbn.getNotification().extras;
//            String text;
//            if (extras.get(Notification.EXTRA_TEXT) instanceof String) {
//                text = (String) extras.get(Notification.EXTRA_TEXT);
//                Log.i("if TEXT", text);
//                msg.putExtra("text", text);
//            } else {
//                text = extras.get(Notification.EXTRA_TEXT).toString();
//                Log.i("else TEXT", text);
//                msg.putExtra("text", text);
//            }
//            Log.i(TAG, text);
//
//        LocalBroadcastManager.getInstance(context).sendBroadcast(msg);
//            Intent intent = new Intent(this, MainActivity.class);
//            if (text.contains("right")) {
//                intent.putExtra("right", true);
//            } else if (text.contains("left")) {
//                intent.putExtra("right", false);
//            }
//            startService(intent);
//        }
//
//    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        writeLine("onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onListenerConnected()
    {
        writeLine("Got onListenerConnected");
    }

//    @SuppressLint("NewApi")
//    public class DefaultBigView /*implements MessageParser*/ {
//        private final String TAG = DefaultBigView.class.getName();
//
//        private final int ID_FIRST_LINE = 16909023; // bigContentView

    public Message parse(Notification notification)
    {
        // use simple method if bigContentView is not supported
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
//                return new DefaultView().parse(notification);

        Message result = new Message();

        try
        {
            RemoteViews views = notification.bigContentView;

            Class<?> rvClass = views.getClass();

//                Field field = rvClass.getDeclaredField("mActions");
            Field field = rvClass.getSuperclass().getDeclaredField("mActions");
            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            ArrayList<Parcelable> actions = (ArrayList<Parcelable>) field
                    .get(views);

            for (Parcelable action : actions)
            {
                try
                {
                    // create parcel from action
                    Parcel parcel = Parcel.obtain();
                    action.writeToParcel(parcel, 0);
                    parcel.setDataPosition(0);

                    // check if is 2 / ReflectionAction
                    int tag = parcel.readInt();
                    if (tag != 2)
                    {
                        continue;
                    }

                    int viewId = parcel.readInt();

                    String methodName = parcel.readString();
                    if (methodName == null || !methodName.equals("setText"))
                    {
                        Log.w(TAG, "#Big Not setText: " + methodName);
                        continue;
                    }

                    // should be 10 / Character Sequence, here
                    parcel.readInt();

                    // Store the actual string
                    String value = String.valueOf(parcel);

                    Log.d(TAG, "Big viewId is " + viewId);
                    Log.d(TAG, "Big Found value: " + value);

                    //  if (viewId == ID_FIRST_LINE) {
                    int indexDelimiter = value.indexOf(':');

//                        if (indexDelimiter != -1) {
//                            result.sender = value.substring(0, indexDelimiter);
//                            result.message = value.substring(indexDelimiter + 2);
//                        }
//                          }

                    parcel.recycle();
                }
                catch (Exception e)
                {
                    Log.e(TAG, "Big Error accessing object!", e);
                }
            }

//                if (result.sender == null || result.message == null)
//                    return null;

            return result;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Big Could not access mActions!", e);

            return null;
        }
    }
//    }
}