package mvherzog.blinkmap_dataflow;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InterfaceAddress;
import java.net.URL;
import java.util.Iterator;

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
        JSONObject jsonResponse;
        try {
            URL url = new URL(requestString);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {

                //Make disconnect button enabled, visible and clickable
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(httpURLConnection.getInputStream()));
                String line;
                while ((line = input.readLine()) != null) {
                    //                    response.append(line);
                    response += line;
                }
                input.close();
                //Gets all steps
                jsonResponse = new JSONObject(response);
                //Gets routes array
                JSONArray routes = jsonResponse.getJSONArray("routes");
                //Gets bounds object
                JSONObject bounds = routes.getJSONObject(0);

                return bounds.getJSONArray("legs");
            }
        }
        catch (IOException | JSONException e) {
            this.ex = e;
            e.printStackTrace();
        }
        return null;
    }

    protected void onPostExecute(JSONArray legs) {
        writeLine("onPostExecute");
        JSONObject leg;
        JSONObject step;
        JSONArray steps;
        JSONObject end_location;
        JSONObject start_location;
        String maneuver;
        if (legs != null) {
            try {
                leg = legs.getJSONObject(0);
                //                    writeLine(String.format("leg.Object[%d]", i), leg.toString());
                //                    Iterator<String> legsObjs = leg.keys();
                //                    while(legsObjs.hasNext()){
                //                        String key = (String) legsObjs.next();
                //                        String value = leg.getString(key);
                //                        writeLine("objects in legs", value);
                //                    }
                steps = leg.getJSONArray("steps");

                MainActivity.stepManeuver = new String[steps.length()];
                MainActivity.stepStartLocationCoordinates = new String[steps.length()];
                MainActivity.stepEndLocationCoordinates = new String[steps.length()];

                writeLine("steps", steps.toString());
                for (int i = 0; i < steps.length(); i++) {
                    step = steps.getJSONObject(i);
                    writeLine(String.format("steps[%d]", i), step.toString());
                    if (step.has("maneuver")) {
                        //if the maneuver isn't null, fill out the arrays
                        if (!step.get("maneuver").equals("")){
                            maneuver = step.getString("maneuver");
                            MainActivity.stepManeuver[i] = maneuver;
                            //                        writeLine("maneuver/stepManeuver", MainActivity.stepManeuver);
                            start_location = step.getJSONObject("start_location");
                            MainActivity.stepStartLocationCoordinates[i] = start_location.toString();
                            //                        writeLine("start_location/stepStart", MainActivity.stepStartLocationCoordinates);
                            end_location = step.getJSONObject("end_location");
                            MainActivity.stepEndLocationCoordinates[i] = end_location.toString();
                            //                        writeLine("end_location/stepEnd", MainActivity.stepEndLocationCoordinates);
                        }
                    }
                }
                for (int i = 0; i < MainActivity.stepManeuver.length; i++) {
                    writeLine(String.format("maneuver[%d]", i), MainActivity.stepManeuver[i]);
                }

                for (int i = 0; i < MainActivity.stepStartLocationCoordinates.length; i++) {
                    writeLine(String.format("start[%d]", i), MainActivity.stepStartLocationCoordinates[i]);
                }

                for (int i = 0; i < MainActivity.stepEndLocationCoordinates.length; i++) {
                    writeLine(String.format("end[%d]", i), MainActivity.stepEndLocationCoordinates[i]);
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }//try-catch
        } else {
            writeLine("legs is null");
        }//if-else
    }//onPostExecute

    private void writeLine(final Object text) {
        MainActivity.writeLine(TAG, text);
    }

    private void writeLine(final String prompt, final Object text) {
        MainActivity.writeLine(TAG, prompt, text);
    }
}
