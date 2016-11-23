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
    public Response r = null;

    public HttpRequest(Response delegate) {
        r = delegate;
    }

    public interface Response {
        void populateDirectionArrays(String[] rManeuvers, /*Double[][] rStarts,*/ String[][] rEnds);

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
//        JSONObject start_location;
        JSONObject end_location; //This is where moves happen

        //response arrays & manipulation string
        String maneuver, trimmedManeuver;
        String[] maneuvers;
        String[][] /*starts,*/ ends;    //dimension #1: latitudes, dimension #2: longitudes

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
                writeLine("STEPS.LENGTH", steps.length());

                maneuvers = new String[steps.length()];
//                starts = new Double[steps.length()][steps.length()];
                ends = new String[steps.length()][steps.length()];

                writeLine("steps", steps.toString());
                for (int i = 0; i < steps.length(); i++) {
                    step = steps.getJSONObject(i);
                    //                    writeLine(String.format("steps[%d]", i), step.toString());
                    if (step.has("maneuver")) {
                        maneuver = step.getString("maneuver");
                        writeLine("maneuver before array insertion", maneuver.isEmpty());
                        //if the step has a maneuver, populate arrays
                        if (!maneuver.isEmpty()/* && !maneuver.trim().isEmpty()*/) {
                            maneuvers[i] = maneuver;

                            //initialize location JSON objects
//                            start_location = step.getJSONObject("start_location");
                            end_location = step.getJSONObject("end_location");

                            //insert latitudes
//                            starts[i][0] = Double.valueOf(start_location.get("lat").toString());
                            ends[i][0] = String.valueOf(end_location.get("lng").toString());

                            //insert longitudes
                            for (int j = i; j < steps.length(); j++) {
//                                starts[0][j] = Double.valueOf(start_location.get("lng").toString());
                                ends[0][j] = String.valueOf(end_location.get("lng").toString());
                            }
                        }

                    }
                }

                //Sending the arrays back to MainActivity
                r.populateDirectionArrays(maneuvers/*, starts*/, ends);
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
