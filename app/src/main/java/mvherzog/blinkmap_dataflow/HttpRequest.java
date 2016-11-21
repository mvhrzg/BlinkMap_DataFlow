package mvherzog.blinkmap_dataflow;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class HttpRequest extends AsyncTask<String, JSONArray, JSONArray> {
    private static final String TAG = HttpRequest.class.getSimpleName();
    private Exception ex;
    JSONObject maneuver;

    @Override
    protected JSONArray doInBackground(String... request) {
        String key = "AIzaSyDx19YRUPUR38pUId34rkR7b8L3z61RTGA";
        String requestString = "https://maps.googleapis.com/maps/api/directions/json?";
        requestString += "origin=" + request[0] + "," + request[1];
        requestString += "&destination=" + request[2] + "," + request[3];
        requestString += "&key=" + key;
        writeLine("requestString", requestString);
        String/*Builder*/ response = "";//new StringBuilder();
        JSONObject objResponse;
        try {
            URL url = new URL(requestString);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {

                //Make disconnect button enabled, visible and clickable
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(httpURLConnection.getInputStream()));
                String line = null;
                while ((line = input.readLine()) != null) {
                    //                    response.append(line);
                    response += line;
                }
                input.close();
                //Gets all steps
                objResponse = new JSONObject(response);
                //should be legs
                return objResponse.getJSONArray("routes");
            }
        }
        catch (IOException | JSONException e) {
            this.ex = e;
            e.printStackTrace();
        }
        return null;
    }

    protected void onPostExecute(JSONArray response) {
        writeLine("onPostExecute");
        if (response != null) {
            for (int i = 0; i < response.length(); i++) {
                try {
                    JSONObject end_location = response.getJSONObject(i).getJSONObject("end_location");
                    writeLine("end_location", end_location.toString());
                    JSONObject maneuver = response.getJSONObject(i).getJSONObject("maneuver");
                    writeLine("maneuver", maneuver.toString());
                    JSONObject start_location = response.getJSONObject(i).getJSONObject("start_location");
                    writeLine("start_location", start_location.toString());
                    //                    maneuver = steps.getJSONObject(i);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } else {
            writeLine("steps is null");
        }
    }

    private void writeLine(final Object text) {
        MainActivity.writeLine(TAG, text);
    }

    private void writeLine(final String prompt, final Object text) {
        MainActivity.writeLine(TAG, prompt, text);
    }
}
