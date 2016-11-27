package mvherzog.blinkmap_dataflow;

import android.os.AsyncTask;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class BlinkmapGeocoder extends AsyncTask<String, LatLng, LatLng> {
    private static final String TAG = ObtainDirections.class.getSimpleName();
    final String key = "AIzaSyDx19YRUPUR38pUId34rkR7b8L3z61RTGA";
    public BlinkmapGeocoder.Response r = null;

    public interface Response {
        void populateCoordinates(Double lat, Double lng);

        void onExecuteAddressFinished();
    }

    public BlinkmapGeocoder(Response delegate) {
        r = delegate;
    }

    @Override
    protected LatLng doInBackground(String... addr) {

        LatLng dest;
        String requestString = "https://maps.googleapis.com/maps/api/geocode/json?address=";
        for (String i : addr) {
            requestString += "+";
            requestString += i;
        }
        requestString += "&key=" + key;

        writeLine("requestString", requestString);

        String response = "";
        JSONObject jsonResponse;
        try {
            URL url = new URL(requestString);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {

                //Make disconnect button enabled, visible and clickable
                BufferedReader buffer = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                String line;
                while ((line = buffer.readLine()) != null) {
                    response += line;
                }
                buffer.close();
                //Gets all steps
                jsonResponse = new JSONObject(response);
                JSONArray results = jsonResponse.getJSONArray("results");
                JSONObject coordinates = results.getJSONObject(0);
                JSONObject geometry = coordinates.getJSONObject("geometry");
                JSONObject location = geometry.getJSONObject("location");
                dest = new LatLng(location.getDouble("lat"), location.getDouble("lng"));
                return dest;
            }

        }
        catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected void onPostExecute(LatLng dest) {
        writeLine("onPostExecute");

        //Sending coordinates back to MainActivity
        r.populateCoordinates(dest.latitude, dest.longitude);
        r.onExecuteAddressFinished();
    }//onPostExecute

    private void writeLine(final Object text) {
        MainActivity.writeLine(TAG, text);
    }

    private void writeLine(final String prompt, final Object text) {
        MainActivity.writeLine(TAG, prompt, text);
    }
}

