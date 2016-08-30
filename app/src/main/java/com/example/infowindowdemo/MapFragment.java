package com.example.infowindowdemo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsoluteLayout;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.*;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static android.view.View.*;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class MapFragment
        extends
        com.google.android.gms.maps.MapFragment
        implements
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        OnClickListener {

    public static final String DESTINATION = "destination";

    private static Spot[] SPOTS_ARRAY = new Spot[]{
            new Spot("Нижне-Волжская Набережная, 2", new LatLng(56.3312727, 43.99937380000006)),
            new Spot("Улица Рождественская, 36", new LatLng(56.327087, 43.9837112)),
            new Spot("Нижне-Волжская Набережная, 21", new LatLng(56.3271138, 43.9809261)),
            new Spot("Улица Рождественская, 17", new LatLng(56.33012100000001, 43.99504200000001)),
    };

    //интервал обновления положения всплывающего окна.
    //для плавности необходимо 60 fps, то есть 1000 ms / 60 = 16 ms между обновлениями.
    private static final int POPUP_POSITION_REFRESH_INTERVAL = 16;
    //длительность анимации перемещения карты
    private static final int ANIMATION_DURATION = 500;

    private Map<Marker, Spot> spots;

    //точка на карте, соответственно перемещению которой перемещается всплывающее окно
    private LatLng trackedPosition;

    //Handler, запускающий обновление окна с заданным интервалом
    private Handler handler;

    //Runnable, который обновляет положение окна
    private Runnable positionUpdaterRunnable;

    //смещения всплывающего окна, позволяющее
    //скорректировать его положение относительно маркера
    private int popupXOffset;
    private int popupYOffset;
    //высота маркера
    private int markerHeight;
    private AbsoluteLayout.LayoutParams overlayLayoutParams;

    //слушатель, который будет обновлять смещения
    private ViewTreeObserver.OnGlobalLayoutListener infoWindowLayoutListener;

    //контейнер всплывающего окна
    private View infoWindowContainer;
    private TextView textView;
    private Button comeButton;
    private Button cancelButton;

    private GoogleMap mMap;
    private ArrayList<LatLng> markerPoints;
    private LatLng myPosition;

    private Callbacks mCallbacks;

    private static final String MAPCYCLE = "MAPCYCLE";

    Handler myHandler;
    Thread t;
    Marker clickedMarker;
    private LatLng firstPosition;
    private boolean secondRoute;



    public static boolean isComing;

    public interface Callbacks {
        void dataSending(String distance, String timeToDest, long millis);

        void distanceCount(String distance);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        markerPoints = new ArrayList<>();
        spots = new HashMap<>();
        markerHeight = getResources().getDrawable(R.drawable.pin).getIntrinsicHeight();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment, null);

        FrameLayout containerMap = (FrameLayout) rootView.findViewById(R.id.container_map);
        View mapView = super.onCreateView(inflater, container, savedInstanceState);
        containerMap.addView(mapView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        mMap = getMap();
        mMap.setMyLocationEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(56.328738, 43.986520), 16f));
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerClickListener(this);

        //mMap.getUiSettings().setZoomControlsEnabled(true);



        addMarkers();

        infoWindowContainer = rootView.findViewById(R.id.container_popup);
        //подписываемся на изменения размеров всплывающего окна
        infoWindowLayoutListener = new InfoWindowLayoutListener();
        infoWindowContainer.getViewTreeObserver().addOnGlobalLayoutListener(infoWindowLayoutListener);
        overlayLayoutParams = (AbsoluteLayout.LayoutParams) infoWindowContainer.getLayoutParams();

        textView = (TextView) infoWindowContainer.findViewById(R.id.textview_title);
        comeButton = (Button) infoWindowContainer.findViewById(R.id.come_button);
        cancelButton = (Button) infoWindowContainer.findViewById(R.id.cancel_button);

        return rootView;

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        infoWindowContainer.getViewTreeObserver().removeGlobalOnLayoutListener(infoWindowLayoutListener);
        handler.removeCallbacks(positionUpdaterRunnable);
        handler = null;
    }


    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //очистка
        handler = new Handler(Looper.getMainLooper());
        positionUpdaterRunnable = new PositionUpdaterRunnable();
        handler.post(positionUpdaterRunnable);
    }




    @Override
    public void onClick(View v) {

    }


    private void addMarkers() {
        mMap.clear();
        spots.clear();
        for (Spot spot : SPOTS_ARRAY) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(spot.getPosition())
                    .title("Title")
                    .snippet("Subtitle"));
            spots.put(marker, spot);
        }
    }



    private LatLng getMyLocation() {
        double latitude = mMap.getMyLocation().getLatitude();
        double longitude = mMap.getMyLocation().getLongitude();
        return new LatLng(latitude, longitude);
    }


    @Override
    public void onMapClick(LatLng latLng) {
        infoWindowContainer.setVisibility(INVISIBLE);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        GoogleMap map = getMap();
        Projection projection = map.getProjection();
        trackedPosition = marker.getPosition();
        Point trackedPoint = projection.toScreenLocation(trackedPosition);
        trackedPoint.y -= popupYOffset / 2;
        LatLng newCameraLocation = projection.fromScreenLocation(trackedPoint);
        map.animateCamera(CameraUpdateFactory.newLatLng(newCameraLocation), ANIMATION_DURATION, null);

        Spot spot = spots.get(marker);
        textView.setText(spot.getName());
        clickedMarker = marker;

        infoWindowContainer.setVisibility(VISIBLE);

        comeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isComing){
                    isComing=false;
                    t.interrupt();
                }
                secondRoute = false;
                myPosition = getMyLocation();
                firstPosition = getMyLocation();
                if (markerPoints.size() > 1) {
                    markerPoints.clear();
                    addMarkers();
                }
                // Adding new item to the ArrayList

                markerPoints.add(myPosition);
                markerPoints.add(clickedMarker.getPosition());

                if (markerPoints.size() >= 2) {
                    LatLng origin = markerPoints.get(0);
                    LatLng dest = markerPoints.get(1);

                    // Getting URL to the Google Directions API
                    String url = getDirectionsUrl(origin, dest);

                    DownloadTask downloadTask = new DownloadTask();

                    // Start downloading json data from Google Directions API

                    downloadTask.execute(url);
                    Location location;

                    myHandler = new Handler();
                    isComing = true;
                    t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while(isComing) {
                                    TimeUnit.SECONDS.sleep(10);
                                    myHandler.post(g);
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    t.start();
                }
            }
        });

        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                infoWindowContainer.setVisibility(INVISIBLE);
            }
        });
        return true;
    }

    Runnable g = new Runnable() {
        @Override
        public void run() {
            try{
            myPosition = getMyLocation();
            }catch (IllegalStateException e){
            }
            ArrayList<LatLng> tmpPoints = new ArrayList<>();
            if (tmpPoints.size() > 1) {
                tmpPoints.clear();
            }
            // Adding new item to the ArrayList
            tmpPoints.add(firstPosition);
            tmpPoints.add(myPosition);

            if (tmpPoints.size() >= 2) {
                LatLng origin = tmpPoints.get(0);
                LatLng dest = tmpPoints.get(1);

                // Getting URL to the Google Directions API
                secondRoute = true;
                String url = getDirectionsUrl(origin, dest);

                DownloadTask downloadTask = new DownloadTask();
                // Start downloading json data from Google Directions API
                downloadTask.execute(url);
            }

            firstPosition = myPosition;

            float distance[] = new float[1];
            Location.distanceBetween(myPosition.latitude, myPosition.longitude, clickedMarker.getPosition().latitude, clickedMarker.getPosition().longitude, distance);
            Log.d("distance", String.valueOf(distance[0]));
            if (distance[0] < 7) {
                isComing = false;
            }
        }


    };


    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";

        String mode = "mode=walking";

        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
        //Log.d("distance", url);

        return url;
    }

    /**
     * A method to download json data from url
     */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception download url", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    // Fetches data from url passed
    private class DownloadTask extends AsyncTask<String, Void, String> {

        // Downloading data in non-ui thread
        @Override
        protected String doInBackground(String... url) {

            // For storing data from web service
            String data = "";

            try {
                // Fetching the data from web service
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        // Executes in UI thread, after the execution of
        // doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();

            // Invokes the thread for parsing the JSON data
            parserTask.execute(result);
        }
    }

    /**
     * A class to parse the Google Places in JSON format
     */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                // Starts parsing data

                routes = parser.parse(jObject);
                if (secondRoute) {
                    String walkedDistance = parser.getDistance(jObject);
                    //Log.d("distance", "Пройденное расстояние " + walkedDistance);
                    mCallbacks.distanceCount(walkedDistance);
                } else {
                    String distance = parser.getDistance(jObject);
                    //Log.d("distance", "Расстояние до точки " + distance);
                    String timeToDest = parser.getTime(jObject);
                    //Log.d("distance", "Время до точки " + timeToDest);
                    mCallbacks.dataSending(distance, timeToDest, System.currentTimeMillis());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        // Executes in UI thread, after the parsing process

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            // Traversing through all the routes
            try {
                for (int i = 0; i < result.size(); i++) {
                    points = new ArrayList<LatLng>();
                    lineOptions = new PolylineOptions();

                    // Fetching i-th route
                    List<HashMap<String, String>> path = result.get(i);

                    // Fetching all the points in i-th route
                    for (int j = 0; j < path.size(); j++) {
                        HashMap<String, String> point = path.get(j);

                        double lat = Double.parseDouble(point.get("lat"));
                        double lng = Double.parseDouble(point.get("lng"));
                        LatLng position = new LatLng(lat, lng);

                        points.add(position);
                    }

                    // Adding all the points in the route to LineOptions
                    lineOptions.addAll(points);
                    lineOptions.width(8);
                    if (secondRoute) {
                        lineOptions.color(Color.RED);
                    } else {
                        lineOptions.color(Color.BLUE);
                    }

                }

                // Drawing polyline in the Google Map for the i-th route
                mMap.addPolyline(lineOptions);

            } catch (Exception e) {
                Toast.makeText(getActivity(), "Отсутствует подключение к интернету", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private class InfoWindowLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            //размеры окна изменились, обновляем смещения
            popupXOffset = infoWindowContainer.getWidth() / 2;
            popupYOffset = infoWindowContainer.getHeight();
        }
    }

    private class PositionUpdaterRunnable implements Runnable {
        private int lastXPosition = Integer.MIN_VALUE;
        private int lastYPosition = Integer.MIN_VALUE;

        @Override
        public void run() {
            //помещаем в очередь следующий цикл обновления
            handler.postDelayed(this, POPUP_POSITION_REFRESH_INTERVAL);

            //если всплывающее окно скрыто, ничего не делаем
            if (trackedPosition != null && infoWindowContainer.getVisibility() == VISIBLE) {
                Point targetPosition = getMap().getProjection().toScreenLocation(trackedPosition);

                //если положение окна не изменилось, ничего не делаем
                if (lastXPosition != targetPosition.x || lastYPosition != targetPosition.y) {
                    //обновляем положение
                    overlayLayoutParams.x = targetPosition.x - popupXOffset;
                    overlayLayoutParams.y = targetPosition.y - popupYOffset - markerHeight - 30;
                    infoWindowContainer.setLayoutParams(overlayLayoutParams);

                    //запоминаем текущие координаты
                    lastXPosition = targetPosition.x;
                    lastYPosition = targetPosition.y;
                }
            }
        }
    }
}
