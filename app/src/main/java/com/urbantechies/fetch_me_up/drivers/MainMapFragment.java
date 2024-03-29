package com.urbantechies.fetch_me_up.drivers;


import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.urbantechies.fetch_me_up.R;
import com.urbantechies.fetch_me_up.model.ClusterMarker;
import com.urbantechies.fetch_me_up.model.JobData;
import com.urbantechies.fetch_me_up.model.PolylineData;
import com.urbantechies.fetch_me_up.model.UserLocation;
import com.urbantechies.fetch_me_up.util.MyClusterManagerRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A simple {@link Fragment} subclass.
 */
public class MainMapFragment extends Fragment implements
        OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener,
        GoogleMap.OnPolylineClickListener, View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "MainMapFragment";
    private static final int LOCATION_UPDATE_INTERVAL = 3000;

    private MapView mMapView;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    private ArrayList<UserLocation> mUserLocations = new ArrayList<>();
    private GoogleMap mGoogleMap;
    private LatLngBounds mMapBoundary;
    private UserLocation mUserPosition;
    private ClusterManager mClusterManager;
    private MyClusterManagerRenderer mClusterManagerRenderer;
    private ArrayList<ClusterMarker> mClusterMarkers = new ArrayList<>();
    private Handler mHandler = new Handler();
    private Runnable mRunnable;
    private GeoApiContext mGeoApiContext = null;
    private ArrayList<PolylineData> mPolyLinesData = new ArrayList<>();
    private Marker mSelectedMarker = null;
    private ArrayList<Marker> mTripMarkers = new ArrayList<>();
    private FirebaseFirestore mDb;
    private ListenerRegistration mJobReadyEventListener;
    private ArrayList<JobData> mJobReadyList = new ArrayList<>();

    private Switch mSwitchOnline;
    private LinearLayout mTripLayout;
    private EditText mFromTextTrip, mToTextTrip;
    private TextView mTripStatus, mFareText, mPassengerNameText;
    private Button mTripButton;
    private JobData mJobData;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {

            final ArrayList<UserLocation> locations = getArguments().getParcelableArrayList(getString(R.string.intent_user_locations));
            mUserLocations.clear();
            mUserLocations.addAll(locations);
        }

        mDb = FirebaseFirestore.getInstance();
        setUserPosition();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_map, container, false);
        mMapView = view.findViewById(R.id.navigation_map);
        view.findViewById(R.id.btn_reset_map).setOnClickListener(this);

        mSwitchOnline = view.findViewById(R.id.switchOnline);
        mSwitchOnline.setOnCheckedChangeListener(this);

        mTripLayout = view.findViewById(R.id.trip_layout);
        mTripLayout.setVisibility(View.GONE);
        mFromTextTrip = view.findViewById(R.id.from_text_trip);
        mToTextTrip = view.findViewById(R.id.to_text_trip);
        mPassengerNameText = view.findViewById(R.id.passengerNameTxt);
        mTripStatus = view.findViewById(R.id.status_text_trip_layout);
        mFareText = view.findViewById(R.id.faretxt);
        mTripButton = view.findViewById(R.id.btnTrip);
        mTripButton.setOnClickListener(this);


        initGoogleMap(savedInstanceState);


        return view;
    }

    private void resetMap() {
        if (mGoogleMap != null) {
            mGoogleMap.clear();

            if (mClusterManager != null) {
                mClusterManager.clearItems();
            }

            if (mClusterMarkers.size() > 0) {
                mClusterMarkers.clear();
                mClusterMarkers = new ArrayList<>();
            }

            if (mPolyLinesData.size() > 0) {
                mPolyLinesData.clear();
                mPolyLinesData = new ArrayList<>();
            }
        }
    }

    public void zoomRoute(List<LatLng> lstLatLngRoute) {

        if (mGoogleMap == null || lstLatLngRoute == null || lstLatLngRoute.isEmpty()) return;

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng latLngPoint : lstLatLngRoute)
            boundsBuilder.include(latLngPoint);

        int routePadding = 120;
        LatLngBounds latLngBounds = boundsBuilder.build();

        mGoogleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(latLngBounds, routePadding),
                600,
                null
        );
    }

    private void removeTripMarkers() {
        for (Marker marker : mTripMarkers) {
            marker.remove();
        }
    }

    private void resetSelectedMarker() {
        if (mSelectedMarker != null) {
            mSelectedMarker.setVisible(true);
            mSelectedMarker = null;
            removeTripMarkers();
        }
    }

    private void retrieveJobs() {

        CollectionReference usersRef = mDb.collection(getString(R.string.collection_job_ready));

        mJobReadyEventListener = usersRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@javax.annotation.Nullable QuerySnapshot queryDocumentSnapshots, @javax.annotation.Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, "onEvent: Listen failed.", e);
                    return;
                }

                if (queryDocumentSnapshots != null) {

                    // Clear the list and add all the users again
                    mJobReadyList.clear();
                    mJobReadyList = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        JobData jobData = doc.toObject(JobData.class);
                        if (jobData.getStatus().equals("ready")) {
                            mSwitchOnline.setChecked(false);
                            createAlertJobBox(jobData);
                            mJobData = jobData;
                            break;
                            // mJobReadyList.add(jobData);
                        }
                    }

                }
            }
        });

    }


    private void createAlertJobBox(JobData jobData) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage("You got a job!")
                .setCancelable(true)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {

                        JobData tempjobData = new JobData(jobData.getPassenger(), mUserPosition.getUser(), jobData.getId(), "accepted",
                                jobData.getPassenger_location(), (new com.google.maps.model.LatLng(mUserPosition.getGeo_point().getLatitude(),
                                mUserPosition.getGeo_point().getLongitude())), jobData.getDestination_location(), jobData.getDestination(),
                                jobData.getFare());

                        DocumentReference endRef = mDb.collection(getString(R.string.collection_job_ready))
                                .document(jobData.getId());

                        endRef.set(tempjobData)
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(TAG, "onComplete: Success Getting the job!");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(TAG, "Error deleting document", e);
                                    }
                                });





                        DocumentReference jobRef = mDb.collection(getString(R.string.collection_job_accepted))
                                .document(mUserPosition.getUser().getUser_id())
                                .collection("Past Jobs")
                                .document(jobData.getId());


                        jobRef.set(tempjobData)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(TAG, "onComplete: Added to database!");
                                        mTripLayout.setVisibility(View.VISIBLE);
                                        String status = mTripStatus.getText().toString();
                                        mFromTextTrip.setText("My Location");
                                        mToTextTrip.setText(tempjobData.getDestination());
                                        mFareText.setText(tempjobData.getFare());
                                        mPassengerNameText.setText(tempjobData.getPassenger().getFirst_name());
                                        calculateDirections2(tempjobData, status);
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(TAG, "Error writing document", e);
                                    }
                                });
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();

    }

    private void finishTrip(){
        DocumentReference endRef = mDb.collection(getString(R.string.collection_job_ready))
                .document(mJobData.getId());

                        endRef.update("status", "completed")
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(TAG, "onComplete: Success Getting the job!");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(TAG, "Error deleting document", e);
                                    }
                                });


        DocumentReference jobRef = mDb.collection(getString(R.string.collection_job_accepted))
                .document(mUserPosition.getUser().getUser_id())
                .collection("Past Jobs")
                .document(mJobData.getId());


        jobRef.update("status", "completed")
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });
    }

    private void midTrip(){
        DocumentReference endRef = mDb.collection(getString(R.string.collection_job_ready))
                .document(mJobData.getId());

                        endRef.update("status", "ongoing")
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Log.d(TAG, "onComplete: Success Getting the job!");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(TAG, "Error deleting document", e);
                                    }
                                });


        DocumentReference jobRef = mDb.collection(getString(R.string.collection_job_accepted))
                .document(mUserPosition.getUser().getUser_id())
                .collection("Past Jobs")
                .document(mJobData.getId());


        jobRef.update("status", "ongoing")
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });
    }


    //    private void calculateDirections2(com.google.maps.model.LatLng passengerLatLng, com.google.maps.model.LatLng destinationLatLng, com.google.maps.model.LatLng driver_location) {
    private void calculateDirections2(JobData jobData, String status) {
        Log.d(TAG, "calculateDirections: calculating directions.");

        com.google.maps.model.LatLng destination;

        if (status.equals("Pick Up")) {
            destination = new com.google.maps.model.LatLng(
                    jobData.getPassenger_location().lat,
                    jobData.getPassenger_location().lng
            );
        } else {
            destination = new com.google.maps.model.LatLng(
                    jobData.getDestination_location().lat,
                    jobData.getDestination_location().lng
            );
        }

        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);

        // show all possible routes
        directions.alternatives(true);

        if (status.equals("Pick Up")) {
            directions.origin(
                    new com.google.maps.model.LatLng(
                            mUserPosition.getGeo_point().getLatitude(),
                            mUserPosition.getGeo_point().getLongitude()
                    )
            );
        } else {
            directions.origin(
                    new com.google.maps.model.LatLng(
                            jobData.getPassenger_location().lat,
                            jobData.getPassenger_location().lng
                    )
            );
        }


        Log.d(TAG, "calculateDirections: destination: " + destination.toString());
        directions.destination(destination).setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.d(TAG, "calculateDirections: routes: " + result.routes[0].toString());
                Log.d(TAG, "calculateDirections: duration: " + result.routes[0].legs[0].duration);
                Log.d(TAG, "calculateDirections: distance: " + result.routes[0].legs[0].distance);
                Log.d(TAG, "calculateDirections: geocodedWayPoints: " + result.geocodedWaypoints[0].toString());

                addPolylinesToMap(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.e(TAG, "calculateDirections: Failed to get directions: " + e.getMessage());

            }
        });
    }


    private void calculateDirections(Marker marker) {
        Log.d(TAG, "calculateDirections: calculating directions.");

        com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(
                marker.getPosition().latitude,
                marker.getPosition().longitude
        );
        DirectionsApiRequest directions = new DirectionsApiRequest(mGeoApiContext);

        // show all possible routes
        directions.alternatives(true);
        directions.origin(
                new com.google.maps.model.LatLng(
                        mUserPosition.getGeo_point().getLatitude(),
                        mUserPosition.getGeo_point().getLongitude()
                )
        );
        Log.d(TAG, "calculateDirections: destination: " + destination.toString());
        directions.destination(destination).setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.d(TAG, "calculateDirections: routes: " + result.routes[0].toString());
                Log.d(TAG, "calculateDirections: duration: " + result.routes[0].legs[0].duration);
                Log.d(TAG, "calculateDirections: distance: " + result.routes[0].legs[0].distance);
                Log.d(TAG, "calculateDirections: geocodedWayPoints: " + result.geocodedWaypoints[0].toString());

                addPolylinesToMap(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.e(TAG, "calculateDirections: Failed to get directions: " + e.getMessage());

            }
        });
    }

    private void addPolylinesToMap(final DirectionsResult result) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: result routes: " + result.routes.length);
                if (mPolyLinesData.size() > 0) {
                    for (PolylineData polylineData : mPolyLinesData) {
                        polylineData.getPolyline().remove();
                    }
                    mPolyLinesData.clear();
                    mPolyLinesData = new ArrayList<>();
                }

                double duration = 99999999;
                for (DirectionsRoute route : result.routes) {
                    Log.d(TAG, "run: leg: " + route.legs[0].toString());
                    List<com.google.maps.model.LatLng> decodedPath = PolylineEncoding.decode(route.overviewPolyline.getEncodedPath());

                    List<LatLng> newDecodedPath = new ArrayList<>();

                    // This loops through all the LatLng coordinates of ONE polyline.
                    for (com.google.maps.model.LatLng latLng : decodedPath) {

//                        Log.d(TAG, "run: latlng: " + latLng.toString());

                        newDecodedPath.add(new LatLng(
                                latLng.lat,
                                latLng.lng
                        ));
                    }
                    Polyline polyline = mGoogleMap.addPolyline(new PolylineOptions().addAll(newDecodedPath));
                    polyline.setColor(ContextCompat.getColor(getActivity(), R.color.darkGrey));
                    polyline.setClickable(true);
                    mPolyLinesData.add(new PolylineData(polyline, route.legs[0]));

                    double tempDuration = route.legs[0].duration.inSeconds;
                    if (tempDuration < duration) {
                        duration = tempDuration;
                        onPolylineClick(polyline);
                        zoomRoute(polyline.getPoints());
                    }

//                    mSelectedMarker.setVisible(false);
                }
            }
        });
    }


    private void startUserLocationsRunnable() {
        Log.d(TAG, "startUserLocationsRunnable: starting runnable for retrieving updated locations.");
        mHandler.postDelayed(mRunnable = new Runnable() {
            @Override
            public void run() {
                retrieveUserLocations();
                //retrieveJobs();
                mHandler.postDelayed(mRunnable, LOCATION_UPDATE_INTERVAL);
            }
        }, LOCATION_UPDATE_INTERVAL);
    }

    private void stopLocationUpdates() {
        mHandler.removeCallbacks(mRunnable);
    }

    private void retrieveUserLocations() {

        try {
            for (final ClusterMarker clusterMarker : mClusterMarkers) {

                DocumentReference userLocationRef = FirebaseFirestore.getInstance()
                        .collection(getString(R.string.collection_user_locations))
                        .document(clusterMarker.getUser().getUser_id());

                userLocationRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {

                            final UserLocation updatedUserLocation = task.getResult().toObject(UserLocation.class);

                            // update the location
                            for (int i = 0; i < mClusterMarkers.size(); i++) {
                                try {
                                    if (mClusterMarkers.get(i).getUser().getUser_id().equals(updatedUserLocation.getUser().getUser_id())) {

                                        LatLng updatedLatLng = new LatLng(
                                                updatedUserLocation.getGeo_point().getLatitude(),
                                                updatedUserLocation.getGeo_point().getLongitude()
                                        );

                                        mClusterMarkers.get(i).setPosition(updatedLatLng);
                                        mClusterManagerRenderer.setUpdateMarker(mClusterMarkers.get(i));
                                    }


                                } catch (NullPointerException e) {
                                    Log.e(TAG, "retrieveUserLocations: NullPointerException: " + e.getMessage());
                                }
                            }
                        }
                    }
                });
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "retrieveUserLocations: Fragment was destroyed during Firestore query. Ending query." + e.getMessage());
        }

    }


    private void addMapMarkers() {

        if (mGoogleMap != null) {

            resetMap();

            if (mClusterManager == null) {
                mClusterManager = new ClusterManager<ClusterMarker>(getActivity().getApplicationContext(), mGoogleMap);
            }
            if (mClusterManagerRenderer == null) {
                mClusterManagerRenderer = new MyClusterManagerRenderer(
                        getActivity(),
                        mGoogleMap,
                        mClusterManager
                );
                mClusterManager.setRenderer(mClusterManagerRenderer);
            }

            Log.d(TAG, "size user locations: " + mUserLocations.size());

            if (mUserLocations.size() != 0) {
                for (UserLocation userLocation : mUserLocations) {
                    try {
                        String snippet = "";
//                        if (userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
//                            snippet = "This is you";
//                        } else {
//                            snippet = "Determine route to " + userLocation.getUser().getUsername() + "?";
//                        }

                        int avatar = 0;

                        if (userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
                            snippet = "This is you";
                            avatar = R.drawable.ic_directions_car_black_24dp;
                        } else if (userLocation.getUser().getStatus().equals("Driver")) {
                            snippet = "Driver";
                        } else if (userLocation.getUser().getStatus().equals("PlaceNear")) {
                            snippet = "Go to " + userLocation.getUser().getUsername() + "?";
                        } else if (userLocation.getUser().getStatus().equals("Place")) {
                            snippet = "Go to " + userLocation.getUser().getUsername() + "?";
                        } else if (userLocation.getUser().getStatus().equals("Passenger")) {
                            snippet = "Passenger";
                            avatar = R.drawable.ic_person_black_24dp;
                        } else {
                            snippet = "ignore";
                        }

//                        int avatar = R.drawable.cartman_cop; // set the default avatar
//                        try {
//                            avatar = Integer.parseInt(userLocation.getUser().getAvatar());
//                        } catch (NumberFormatException e) {
//                            Log.d(TAG, "addMapMarkers: no avatar for " + userLocation.getUser().getUsername() + ", setting default.");
//                        }
                        ClusterMarker newClusterMarker = new ClusterMarker(
                                new LatLng(userLocation.getGeo_point().getLatitude(), userLocation.getGeo_point().getLongitude()),
                                userLocation.getUser().getUsername(),
                                snippet,
                                avatar,
                                userLocation.getUser()
                        );

                        if (userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
                            mClusterManager.addItem(newClusterMarker);
                            mClusterMarkers.add(newClusterMarker);
                        }

                    } catch (NullPointerException e) {
                        Log.e(TAG, "addMapMarkers: NullPointerException: " + e.getMessage());
                    }

                }
                mClusterManager.cluster();

                setCameraView();
            }
        }
    }


    private void setCameraView() {


        double bottomBoundary = mUserPosition.getGeo_point().getLatitude() - .01;
        double leftBoundary = mUserPosition.getGeo_point().getLongitude() - .01;
        double topBoundary = mUserPosition.getGeo_point().getLatitude() + .01;
        double rightBoundary = mUserPosition.getGeo_point().getLongitude() + .01;

//        double bottomBoundary = 4.3877663 - .1;
//        double leftBoundary = 100.9639676 - .1;
//        double topBoundary = 4.3877663 + .1;
//        double rightBoundary = 100.9639676 + .1;

        mMapBoundary = new LatLngBounds(
                new LatLng(bottomBoundary, leftBoundary),
                new LatLng(topBoundary, rightBoundary)
        );

        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mMapBoundary, 0));
    }

    private void setUserPosition() {
        for (UserLocation userLocation : mUserLocations) {
            if (userLocation.getUser().getUser_id().equals(FirebaseAuth.getInstance().getUid())) {
                mUserPosition = userLocation;
            }
        }

    }


    public static MainMapFragment newInstance() {
        return new MainMapFragment();
    }


    private void initGoogleMap(Bundle savedInstanceState) {
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }

        mMapView.onCreate(mapViewBundle);

        mMapView.getMapAsync(this);

        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(getString(R.string.google_maps_api_key))
                    .build();
        }

    }




    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
        startUserLocationsRunnable();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        // map.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //map.setMyLocationEnabled(true);
        mGoogleMap = map;
        mGoogleMap.setOnPolylineClickListener(this);
        addMapMarkers();
        mGoogleMap.setOnInfoWindowClickListener(this);
        // setCameraView();
    }

    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
        stopLocationUpdates();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }


    @Override
    public void onInfoWindowClick(final Marker marker) {
        if (marker.getTitle().contains("Trip #")) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage("Open Google Maps?")
                    .setCancelable(true)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            String latitude = String.valueOf(marker.getPosition().latitude);
                            String longitude = String.valueOf(marker.getPosition().longitude);
                            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + latitude + "," + longitude);
                            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                            mapIntent.setPackage("com.google.android.apps.maps");

                            try {
                                if (mapIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                                    startActivity(mapIntent);
                                }
                            } catch (NullPointerException e) {
                                Log.e(TAG, "onClick: NullPointerException: Couldn't open map." + e.getMessage());
                                Toast.makeText(getActivity(), "Couldn't open map", Toast.LENGTH_SHORT).show();
                            }

                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            dialog.cancel();
                        }
                    });
            final AlertDialog alert = builder.create();
            alert.show();
        } else {
            if ((marker.getSnippet().equals("This is you")) || (marker.getSnippet().equals("Driver"))
                    || (marker.getSnippet().equals("Passenger")) || (marker.getSnippet().equals("ignore"))) {
                marker.hideInfoWindow();
            } else {

                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(marker.getSnippet())
                        .setCancelable(true)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                                resetSelectedMarker();
                                mSelectedMarker = marker;
                                calculateDirections(marker);
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                                dialog.cancel();
                            }
                        });
                final AlertDialog alert = builder.create();
                alert.show();
            }
        }

    }

    @Override
    public void onPolylineClick(Polyline polyline) {

        int index = 0;
        for (PolylineData polylineData : mPolyLinesData) {
            index++;
            Log.d(TAG, "onPolylineClick: toString: " + polylineData.toString());
            if (polyline.getId().equals(polylineData.getPolyline().getId())) {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.blue1));
                polylineData.getPolyline().setZIndex(1);

                LatLng endLocation = new LatLng(
                        polylineData.getLeg().endLocation.lat,
                        polylineData.getLeg().endLocation.lng
                );

                Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                        .position(endLocation)
                        .title("Trip #" + index)
                        .snippet("Duration: " + polylineData.getLeg().duration)

                );

                marker.showInfoWindow();
                mTripMarkers.add(marker);

            } else {
                polylineData.getPolyline().setColor(ContextCompat.getColor(getActivity(), R.color.darkGrey));
                polylineData.getPolyline().setZIndex(0);
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_reset_map: {
                addMapMarkers();
                startUserLocationsRunnable();
                break;
            }
            case R.id.btnTrip: {


                if(mTripButton.getText().toString().equals("Pick Up Passenger")){
                    //mTripLayout.setVisibility(View.VISIBLE);
                    mTripStatus.setText("On Trip");
                    String status = mTripStatus.getText().toString();
                    mFromTextTrip.setText("My Location");
                    mToTextTrip.setText(mJobData.getDestination());
                    mFareText.setText(mJobData.getFare());
                    mTripButton.setText("Finish Trip");
                    midTrip();
                    calculateDirections2(mJobData, status);
                }else{
                    mTripLayout.setVisibility(View.GONE);
                    mTripStatus.setText("Pick Up");
                    mFromTextTrip.setText("");
                    mToTextTrip.setText("");
                    mFareText.setText("RM 2.00");
                    mPassengerNameText.setText("Ahmad Syahir");
                    mTripButton.setText("Pick Up Passenger");
                    mSwitchOnline.setChecked(true);
                    finishTrip();
                    addMapMarkers();
                    startUserLocationsRunnable();
                }

                break;
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {

        if (isChecked) {
            retrieveJobs();
        } else {
            //do stuff when Switch if OFF
        }
    }
}
