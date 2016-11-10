package com.bcn.beacon.beacon.Activities;

import android.Manifest;
import android.app.Fragment;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.preference.PreferenceManager;

import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bcn.beacon.beacon.Data.DistanceComparator;
import com.bcn.beacon.beacon.Data.Models.Event;
import com.bcn.beacon.beacon.Data.Models.ListEvent;
import com.bcn.beacon.beacon.Fragments.FavouritesFragment;
import com.bcn.beacon.beacon.Fragments.ListFragment;
import com.bcn.beacon.beacon.Fragments.SettingsFragment;
import com.bcn.beacon.beacon.R;
import com.firebase.client.annotations.Nullable;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.joanzapata.iconify.widget.IconTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AuthBaseActivity
        implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleApiClient.ConnectionCallbacks,
        View.OnClickListener {

    private GoogleApiClient mGoogleApiClient;
    private GoogleMap mMap;

    private MapFragment mMapFragment;
    private ListFragment mListFragment;
    private SettingsFragment mSettingsFragment;
    private FavouritesFragment mFavouritesFragment;
    private List<IconTextView> mTabs;
    private TextView mTitle;
    private Fragment mActiveFragment;

    private FloatingActionButton mCreateEvent;
    private MainActivity mContext;

    private IconTextView mList;
    private IconTextView mWorld;
    private IconTextView mFavourites;
    private IconTextView mSettings;
    private static final String TAG = "MainActivity";

    public static int eventPageClickedFrom = -1;
    public static int REQUEST_CODE_EVENTPAGE = 10;
    public static int REQUEST_CODE_CREATEEVENT = 20;

    private static final int PERMISSION_ACCESS_FINE_LOCATION = 816;

    /**
     * Copied over from BeaconListView
     */
    private DatabaseReference mDatabase;
    private double userLng, userLat, eventLng, eventLat;
    private static final double maxRadius = 100.0;
    // tracker for the temporary fix
    private static int tracker = -1;

    private ArrayList<ListEvent> events = new ArrayList<>();
    private HashMap<String, ListEvent> eventsMap = new HashMap<>();
    private ArrayList<String> favouriteIds = new ArrayList<>();
    private ArrayList<ListEvent> favourites = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //hide the action bar
        getSupportActionBar().hide();

//set default values for preferences if they haven't been modified yet
        PreferenceManager.setDefaultValues(this, R.xml.settings_fragment, false);

        //get the users location using location services
        getUserLocation();
        Log.i("MAIN CREATED", "YES");

        //retrieve all the Views that we would want to modify here
        mList = (IconTextView) findViewById(R.id.list);
        mWorld = (IconTextView) findViewById(R.id.world);
        mFavourites = (IconTextView) findViewById(R.id.favourites);
        mSettings = (IconTextView) findViewById(R.id.settings);
        mCreateEvent = (FloatingActionButton) findViewById(R.id.create_event_fab);

        //set the onClickListener to this activity
        mList.setOnClickListener(this);
        mWorld.setOnClickListener(this);
        mFavourites.setOnClickListener(this);
        mSettings.setOnClickListener(this);
        mCreateEvent.setOnClickListener(this);

        //create an initial map fragment
        mMapFragment = MapFragment.newInstance();

        //create our tab array to keep track of the state of each tab
        mTabs = new ArrayList<>();
        mTabs.add(mList);
        mTabs.add(mWorld);
        mTabs.add(mFavourites);
        mTabs.add(mSettings);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, mGso)
                .build();

        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    mFirebaseUser = user;
                    initMap();
                    Log.d(TAG, "onAuthStateChanged_Main:signed_in:" + mFirebaseUser.getUid());
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged_Main:signed_out");
                }
            }
        };

        // get events from firebase
        getNearbyEvents();
        // get user favourite ids from firebase
        getFavouriteIds();
    }

    protected void onStart() {
        super.onStart();

        mAuth.addAuthStateListener(mAuthListener);

        // added a condition to avoid creating a new instance of map fragment everytime we go back to main activity
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            mMapFragment = MapFragment.newInstance();
            FragmentTransaction fragmentTransaction =
                    getFragmentManager().beginTransaction();
            fragmentTransaction.add(R.id.events_view, mMapFragment, getString(R.string.map_fragment));
            // push to stack so that the fragment transaction is recorded and the fragment will be
            // obtainable from the fragment manager
            fragmentTransaction.addToBackStack(null);
            mActiveFragment = mMapFragment;
            fragmentTransaction.commit();
            Log.i("BACKSTACK COUNT", "0");
        }

        mMapFragment.getMapAsync(this);

    }

    protected void onStop() {
        super.onStop();

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    /**
     * Here we implement the listener for all the views in this activity's view hierarchy
     *
     * @param v
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.list): {
                //change tab colour
                resetTabColours();
                mList.setBackgroundResource(R.color.currentTabColor);

                //show create event button on this page
                mCreateEvent.setEnabled(true);
                mCreateEvent.setVisibility(View.VISIBLE);

                //get List fragment if exists
                Fragment fragment = getFragmentManager().findFragmentByTag(getString(R.string.list_fragment));
                if (fragment == null || !fragment.isVisible()) {
                    if (fragment == null) {
                        //if fragment hasn't been created, get a new one
                        mListFragment = ListFragment.newInstance();
                    } else {
                        //if fragment already exists, use it
                        mListFragment = (ListFragment) fragment;
                    }

                    FragmentTransaction transaction = getFragmentManager().beginTransaction();

                    //attach this fragment to the screen
                    transaction.replace(R.id.events_view, mListFragment, getString(R.string.list_fragment));
                    transaction.addToBackStack(null);
                    mActiveFragment = mListFragment;

                    //allows for smoother transitions between screens
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

                    transaction.commit();
                }
                tracker = 1;
                break;
            }
            case (R.id.world): {

                //change tab colours
                resetTabColours();
                mWorld.setBackgroundResource(R.color.currentTabColor);

                //show create event button on this page
                mCreateEvent.setEnabled(true);
                mCreateEvent.setVisibility(View.VISIBLE);

                Fragment fragment = getFragmentManager().findFragmentByTag(getString(R.string.map_fragment));

                if (fragment == null || !fragment.isVisible()) {
                    //if fragment hasn't been created, create a new instance
                    if (fragment == null) {
                        mMapFragment = MapFragment.newInstance();

                        //else, set map fragment to retrieved fragment
                    } else {
                        mMapFragment = (MapFragment) fragment;
                    }
                    mActiveFragment = mMapFragment;

                    FragmentTransaction fragmentTransaction =
                            getFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.events_view, mMapFragment, getString(R.string.map_fragment));
                    fragmentTransaction.addToBackStack(null);

                    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                    fragmentTransaction.commit();

                    mMapFragment.getMapAsync(this);
                }
                tracker = 0;

                break;
            }

            case (R.id.favourites): {

                resetTabColours();
                mFavourites.setBackgroundResource(R.color.currentTabColor);

                //hide create event button on this page
                mCreateEvent.setEnabled(false);
                mCreateEvent.setVisibility(View.GONE);

                //get List fragment if exists
                Fragment fragment = getFragmentManager().findFragmentByTag(getString(R.string.favourites_fragment));
                if (fragment == null || !fragment.isVisible()) {
                    if (fragment == null) {
                        //if fragment hasn't been created, get a new one
                        mFavouritesFragment = FavouritesFragment.newInstance();
                    } else {
                        //if fragment already exists, use it
                        mFavouritesFragment = (FavouritesFragment) fragment;
                    }

                    FragmentTransaction transaction = getFragmentManager().beginTransaction();

                    //attach this fragment to the screen
                    transaction.replace(R.id.events_view, mFavouritesFragment, getString(R.string.favourites_fragment));
                    transaction.addToBackStack(null);
                    mActiveFragment = mFavouritesFragment;

                    //allows for smoother transitions between screens
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

                    transaction.commit();
                }

                break;
            }
            case (R.id.settings): {
                //change tab colours
                resetTabColours();
                mSettings.setBackgroundResource(R.color.currentTabColor);

                //hide create event button on this page
                mCreateEvent.setEnabled(false);
                mCreateEvent.setVisibility(View.GONE);

                //check if visible fragment is an instance of settings fragment already, if so do nothing
                Fragment fragment = getFragmentManager().findFragmentByTag(getString(R.string.settings_fragment));

                if (fragment == null || !fragment.isVisible()) {
                    if (fragment == null) {
                        mSettingsFragment = SettingsFragment.getInstance();
                    } else {
                        mSettingsFragment = (SettingsFragment) fragment;
                    }

                    FragmentTransaction fragmentTransaction =
                            getFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.events_view, mSettingsFragment, getString(R.string.settings_fragment));
                    fragmentTransaction.addToBackStack(null);

                    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                    fragmentTransaction.commit();

                }
                break;
            }

            //if the user presses the floating button, launch the create event activity
            case (R.id.create_event_fab): {
                Intent intent = new Intent(this, CreateEventActivity.class);
                intent.putExtra("userlat", userLat);
                intent.putExtra("userlng", userLng);
                // for temporary fix
                if (mActiveFragment != null && mActiveFragment == mListFragment) {
                    intent.putExtra("from", 1);
                }
                else if (mActiveFragment != null && mActiveFragment == mMapFragment){
                    // don't really need this, but keep for now
                    //Log.i("ACTIVE", "MAP");
                    intent.putExtra("from", 0);
                }
                else if (tracker == 1) {
                    //Log.i("ACTIVE", "NOT MAP AND MAP NOT NULL");
                    intent.putExtra("from", 1);
                }

                // startActivity(intent);
                // start activity for result using same code for now
                startActivityForResult(intent, REQUEST_CODE_EVENTPAGE);
            }
        }

    }

    private void revokeAccess() {
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {

                    }
                });
    }

    /**
     * Check for GPS permission
     *
     * @return true if user has allowed access to location, false otherwise
     */
    private boolean checkGPSPermission() {
        String permission = "android.permission.ACCESS_FINE_LOCATION";
        int res = getApplicationContext().checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Gets the location of the user
     */
    public void getUserLocation() {
       LocationManager lm = (LocationManager) getSystemService(this.LOCATION_SERVICE);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_FINE_LOCATION);
        if (checkGPSPermission()) {
            List<String> providers = lm.getProviders(true);
            Location bestLocation = null;
            for (String provider : providers) {
                Location l = lm.getLastKnownLocation(provider);
                if (l == null) {
                    continue;
                }
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    // Found best last known location: %s", l);
                    bestLocation = l;
                }
            }
            if (bestLocation != null) {
                userLat = bestLocation.getLatitude();
                userLng = bestLocation.getLongitude();
                //Log.i("PERMISSION:", "ALLOWED");
            }
        }
    }

    /**
     * Call back method: app supposedly calls this again after user allows location services
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                Toast.makeText(getApplicationContext(), "You need to enable location services in order to use Beacon", Toast.LENGTH_LONG);
            }
            return;
        }
    }

    /**
     * Gets nearby events according to the user's location
     */
    private void getNearbyEvents() {
        /* commented this out for now to fix the bug, look into memory leaks! */
        /*if (!events.isEmpty()) {
            events.clear();
        }*/
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mDatabase.child("ListEvents").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!events.isEmpty()) {
                    events.clear();
                }
                double distance;
                for (DataSnapshot event_snapshot : dataSnapshot.getChildren()) {
                    ListEvent event = event_snapshot.getValue(ListEvent.class);

                    double eventLat = event.getLocation().getLatitude();
                    double eventLng = event.getLocation().getLongitude();
                    distance = distFrom(userLat, userLng, eventLat, eventLng);

                    if (distance <= maxRadius) {
                        event.distance = distance;
                        events.add(event);
                        eventsMap.put(event.getEventId(), event);
                    }
                }
                Collections.sort(events, new DistanceComparator());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    /**
     * Function to get the event ids of user's favourites
     */
    public void getFavouriteIds() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference users = database.getReference("Users");
        users.child(userId).child("favourites").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!favouriteIds.isEmpty()) {
                    favouriteIds.clear();
                }
                //HashMap<String, ListEvent> eventsMap = MainActivity.getEventsMap();
                for (DataSnapshot fav_snapshot : dataSnapshot.getChildren()) {
                    //Log.i("FAV_SNAPSHOT", fav_snapshot.getKey());
                    favouriteIds.add(fav_snapshot.getKey());
                    }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /**
     * Java implementation of the Haversine formula for calculating the distance between two locations.
     * Taken from http://stackoverflow.com/questions/120283
     * /how-can-i-measure-distance-and-create-a-bounding-box-based-on-two-latitudelongi/123305#123305
     *
     * @param userLat  - latitude of the user's location
     * @param userLng  - longitude of the user's location
     * @param eventLat - latitude of the event's location
     * @param eventLng - longitude of the event's location
     * @return dist - distance between the two locations
     */
    private static double distFrom(double userLat, double userLng, double eventLat, double eventLng) {
        double earthRadius = 6371.0; // kilometers (or 3958.75 miles)
        double dLat = Math.toRadians(eventLat - userLat);
        double dLng = Math.toRadians(eventLng - userLng);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(eventLat));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double dist = earthRadius * c;
        // for rounding to 3 decimal places
        dist = Math.floor(1000 * dist + 0.5)/1000;

        return dist; // in kilometers
    }

    /**
     * Getter method for that returns the events list
     *
     * @return list of nearby events
     */
    public ArrayList<ListEvent> getEventList() {
        return events;
    }

    public ArrayList<ListEvent> getRefreshedEventList() {
        getNearbyEvents();
        return events;
    }

    public ArrayList<String> getFavouriteIdsList() {
        return favouriteIds;
    }

    public HashMap<String, ListEvent> getEventsMap() {
        return eventsMap;
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        getUserLocation();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    private void signOut() {

        mAuth.signOut();

        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        Toast toast = Toast.makeText(MainActivity.this,
                                "Signed Out Successfully",
                                Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();

                        Intent i = new Intent(MainActivity.this, SignInActivity.class);
                        MainActivity.this.startActivity(i);
                        MainActivity.this.finish();
                    }
                });
    }

    private void initMap() {
        if (mMap != null) {
            mMap.clear();
            if (mAuth.getInstance().getCurrentUser() != null) {
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.pin))
                        .position(new LatLng(userLat, userLng))
                        .title(mAuth.getInstance().getCurrentUser().getDisplayName()));

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 13));
                marker.showInfoWindow();

            } else {
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.pin))
                        .position(new LatLng(49.2606, -123.2460))
                        .title("You ;)"));
                mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()), 250, null);
                marker.showInfoWindow();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        initMap();
    }

    /**
     * resets all the tabs to the unselected color
     */
    private void resetTabColours() {
        for (IconTextView itv : mTabs) {
            itv.setBackgroundResource(R.color.otherTabColor);
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {

    }
    /**
     * This method overrides the default back button functionality
     *
     * If the user is looking at a different tab the world tab will be loaded,
     * otherwise the activity will end and the user will return to the android home screen
     *
     */
    @Override
    public void onBackPressed() {
        //currently viewing the map
        if (mMapFragment != null && mMapFragment.isVisible()) {
            //return to home screen
            finish();

            //map fragment is active but not currently shown
        } else if (mMapFragment != null && !mMapFragment.isVisible()) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            transaction.replace(R.id.events_view, mMapFragment, getString(R.string.map_fragment));
            // we need to handle the back stack so it pops
            transaction.addToBackStack(null);

            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            transaction.commit();
            //Log.i("STATUS", "MAP FRAG ACTIVE, BUT NOT SHOWN");

            //set the world tab as being selected
            resetTabColours();
            mWorld.setBackgroundResource(R.color.currentTabColor);

            mMapFragment.getMapAsync(this);
            mActiveFragment = mMapFragment;

            //ensure that the create event tab is visible again
            mCreateEvent.setEnabled(true);
            mCreateEvent.setVisibility(View.VISIBLE);

        } else {
            finish();
        }
    }

    // To keep track of the view the event page was clicked on
    public static void setEventPageClickedFrom(int from) {
        eventPageClickedFrom = from;
    }


    @Override
    public void onResume() {
        // Temporary fix for going back to list view from event page
        // it actually shows fragment but the action bar goes back to map view
        /*if (eventPageClickedFrom == 1) {
            //set the world tab as being selected
            resetTabColours();
            mList.setBackgroundResource(R.color.currentTabColor);
            eventPageClickedFrom = 0;
        }*/
        super.onResume();
    }

    /**
     * This is the temporary fix for displaying the current tab correctly
     * @param requestCode
     * @param resultCode
     * @param intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CODE_EVENTPAGE && resultCode == RESULT_CANCELED) {
            //Fragment fragment =
            //Log.i("ON RESULT", "YES");
            switch (eventPageClickedFrom) {
                case (1): {
                    resetTabColours();
                    mList.setBackgroundResource(R.color.currentTabColor);
                    //eventPageClickedFrom = 0;
                    break;
                }
                case (2): {
                    resetTabColours();
                    mFavourites.setBackgroundResource(R.color.currentTabColor);
                    mCreateEvent.setEnabled(false);
                    mCreateEvent.setVisibility(View.GONE);
                    //eventPageClickedFrom = 0;
                    break;
                }
                /*default: {
                    resetTabColours();
                    mWorld.setBackgroundResource(R.color.currentTabColor);
                    break;
                }*/
            }

        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.i("SAVE STATE", "YES");
        //outState.putParcelable("lastFragment", getFragmentManager().saveFragmentInstanceState(mActiveFragment));
        //getFragmentManager().putFragment(outState, "lastFragment", mActiveFragment);
        outState.putInt("tracker", tracker);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        tracker = savedInstanceState.getInt("tracker");

        //getFragmentManager().getFragment(savedInstanceState, "lastFragment");
        //savedInstanceState.getParcelable("lastFragment");

    }

    @Override
    public void onDestroy() {
        Log.i("DESTROYED", "YES");
        Log.i("BACK STACK COUNT", Integer.toString(getFragmentManager().getBackStackEntryCount()));
        super.onDestroy();
        Log.i("FINISHING?", Boolean.toString(this.isFinishing()));
    }
}
