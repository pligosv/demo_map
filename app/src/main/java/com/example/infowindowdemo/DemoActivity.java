package com.example.infowindowdemo;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.app.ActionBarDrawerToggle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;


import java.text.DecimalFormat;

public class DemoActivity extends Activity implements MapFragment.Callbacks {
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private LinearLayout mDrawerLinear;
    private ListView mDrawerList;


    private CharSequence mDrawerTitle;
    private CharSequence mTitle;
    private String[] mDrawerTitles;

    private float distance;
    private String timeToDest;
    private float totalDistance;
    private float distanceError;
    private long startTime;
    private long endTime;

    private TextView timeToPoint;
    private TextView walkedTime;
    private TextView distanceToPoint;

    private TextView walkedDistance;
    private TextView errorDistance;
    private Button endNavigationButton;

    private static final int PERMISSIONS_REQUEST = 1;

    public void getPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST);
            }
        }
    }


    @Override
    public void dataSending(String dist, String time, long millis) {
        distance = Float.parseFloat(dist.replaceAll("[\\D]", "")) / 10;
        timeToDest = time;
        startTime = millis;
        new Thread(new Runnable() {
            @Override
            public void run() {
                timeToPoint.post(new Runnable() {
                    @Override
                    public void run() {
                        timeToPoint.setText("Время до точки: " + timeToDest);
                        distanceToPoint.setText("Расстояние до точки: " + distance + " км");
                        endNavigationButton.setEnabled(true);
                    }
                });

            }
        }).start();
    }

    @Override
    public void distanceCount(String d) {
        Log.d("distance", "Пройденное расстояние " + d);
        totalDistance += Float.parseFloat(d.replaceAll("[\\D]", "")) / 1000;
        Log.d("distance", "Пройденное расстояние " + totalDistance);
        distanceError = totalDistance - distance;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity);

        mTitle = mDrawerTitle = getTitle();
        mDrawerTitles = getResources().getStringArray(R.array.list_items);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLinear = (LinearLayout) findViewById(R.id.left_drawer);
        mDrawerList = (ListView) findViewById(R.id.left_menu);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        mDrawerList.setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_list_item, mDrawerTitles));
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        timeToPoint = (TextView) findViewById(R.id.time_to_point);
        timeToPoint.setText("Время до точки: ");

        walkedTime = (TextView) findViewById(R.id.walked_time);
        walkedTime.setText("Затраченное время: ");

        distanceToPoint = (TextView) findViewById(R.id.distance_to_point);
        distanceToPoint.setText("Расстояние до точки: ");

        walkedDistance = (TextView) findViewById(R.id.walked_dist);
        walkedDistance.setText("Пройденное расстояние: ");

        errorDistance = (TextView) findViewById(R.id.error_dist);
        errorDistance.setText("Отклонение: ");

        endNavigationButton = (Button) findViewById(R.id.endNavigationButton);
        endNavigationButton.setEnabled(false);
        endNavigationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endTime = System.currentTimeMillis();
                long diff = endTime - startTime;
                walkedTime.setText("Затраченное время: " + diff / 1000 + " сек");
                walkedDistance.setText("Пройденное расстояние: " + new DecimalFormat("#0.00").format(totalDistance) + " км");
                errorDistance.setText("Отклонение в км: " + new DecimalFormat("#0.00").format(distanceError) + " км");
                endNavigationButton.setEnabled(false);
            }
        });

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.drawable.ic_drawer,
                R.string.drawer_open,
                R.string.drawer_close
        ) {
            public void onDrawerClosed(View view) {
                getActionBar().setTitle(mTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle("Меню");
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        getPermissions();

        if (savedInstanceState == null) {
            selectItem(0);
        }

    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If the nav drawer is open, hide action items related to the content view
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerLinear);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle action buttons
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        MapFragment.isComing = false;
    }


    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    private void selectItem(int position) {
        // update the main content by replacing fragments

        FragmentManager fragmentManager = getFragmentManager();

        fragmentManager.beginTransaction().add(R.id.container_map_fragment, new MapFragment())
                .commit();
        // update selected item and title, then close the drawer
        mDrawerList.setItemChecked(position, true);
        setTitle(mDrawerTitles[position]);
        mDrawerLayout.closeDrawer(mDrawerLinear);
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getActionBar().setTitle(mTitle);
    }
}
