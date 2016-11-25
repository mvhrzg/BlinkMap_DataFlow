package mvherzog.blinkmap_dataflow;

import android.os.AsyncTask;
import android.provider.ContactsContract;

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
    public Response r = null;

    public HttpRequest(Response delegate) {
        r = delegate;
    }

    public interface Response {
        void populateDirectionArrays(String[] rManeuvers, String[] startLats, String[] startLngsEnd, String[] endLats, String[] endLngs);

        void onExecuteFinished();

    }

    @Override
    protected JSONArray doInBackground(String... request) {
        String key = "AIzaSyDx19YRUPUR38pUId34rkR7b8L3z61RTGA";
        String requestString = "https://maps.googleapis.com/maps/api/directions/json?";
        requestString += "origin=" + request[0] + "," + request[1];
        requestString += "&destination=" + request[2] + "," + request[3];
        requestString += "&key=" + key;
        writeLine("requestString", requestString);
        String response = "";
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
            e.printStackTrace();
        }
        return null;
    }

    protected void onPostExecute(JSONArray legs) {
        writeLine("onPostExecute");
        //JSON parse arrays
        JSONObject leg;
        JSONObject step;
        JSONArray steps;
        JSONObject start_location;
        JSONObject end_location; //This is where moves happen

        //response arrays & manipulation string
        String maneuver, trimmedManeuver;
        String[] maneuvers;
        String[] startLats, startLngs, endLats, endLngs;

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

                //initialize arrays
                maneuvers = new String[steps.length()];
                startLats = new String[steps.length()];
                startLngs = new String[steps.length()];
                endLats = new String[steps.length()];
                endLngs = new String[steps.length()];

//                writeLine("steps", steps.toString());
                for (int i = 0; i < steps.length(); i++) {
                    step = steps.getJSONObject(i);


                    //Commenting this out so I can see how accurate the current coordinates are (since I am not moving)
                    if (step.has("maneuver")) {
                        maneuver = step.getString("maneuver");
                        //if the step has a maneuver, populate arrays
                        maneuvers[i] = maneuver;
                    }

                        //initialize location JSON objects
                        start_location = step.getJSONObject("start_location");
                        end_location = step.getJSONObject("end_location");

                        //insert start locations
                        startLats[i] = String.valueOf(start_location.get("lat"));
                        startLngs[i] = String.valueOf(start_location.get("lng"));

                        //insert end locations
                        endLats[i] = String.valueOf(end_location.get("lat"));
                        endLngs[i] = String.valueOf(end_location.get("lng"));
//                    }

                }

                //Sending the arrays back to MainActivity
                r.populateDirectionArrays(maneuvers, startLats, startLngs, endLats, endLngs);
                r.onExecuteFinished();
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
