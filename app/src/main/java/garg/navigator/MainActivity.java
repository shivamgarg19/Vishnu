package garg.navigator;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.HashSet;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;


public class MainActivity extends AppCompatActivity {

    private EditText mDestination;
    private TextView mDeviceConnected;
    private String mStartingPoint;
    private MyDatagramReceiver myDatagramReceiver = null;
    TrackingService mMyService;
    HashSet<String> mIPAddresses = new HashSet<>();
    private static final int PERMISSION_REQUEST_CODE = 200;
    private boolean mBound = false, gps = false;
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    ArrayList<String> mRecentLocations;

    private ListView mRecentsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        if (!checkPermission()) {
            requestPermission();
        } else if (!checkGPS()) {
            turnGPSOn();
        }

        mDestination = (EditText) findViewById(R.id.destination);
        mDeviceConnected = (TextView) findViewById(R.id.device_connected);

        mDestination.setImeActionLabel("Navigate", EditorInfo.IME_ACTION_GO);
        mDestination.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId != EditorInfo.IME_ACTION_GO) {
                    return false;
                }
                navigate(v.getText().toString());
                return false;
            }
        });

        final ImageButton navigate = (ImageButton) findViewById(R.id.navigate);
        navigate.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                navigate(mDestination.getText().toString());
            }
        });

        mRecentsView = (ListView) findViewById(R.id.recent_list);
        mRecentsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mDestination.setText(mRecentLocations.get(i));
            }
        });

        final ImageButton clearRecent = (ImageButton) findViewById(R.id.clearRecent);
        clearRecent.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("clear", "cleared");
                clearLocations();
            }
        });
    }

    private boolean checkPermission() {
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_FINE_LOCATION);
        int result2 = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_COARSE_LOCATION);
        return result1 == PackageManager.PERMISSION_GRANTED && result2 == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE);
    }

    private void navigate(String destination) {
        if (!isMyServiceRunning(TrackingService.class)) {
            Log.e("Service", "Not Running");
            startService(new Intent(MainActivity.this, TrackingService.class));
            if (!mBound) doBindService();
        }
        if (mMyService.location != null) {
            Location location = mMyService.location.getLastLocation();
            mStartingPoint = String.valueOf(location.getLatitude()) + "," + String.valueOf(location.getLongitude());
        }
        if (!checkGPS()) {
            Toast.makeText(MainActivity.this, "You need to enable to GPS", Toast.LENGTH_SHORT).show();
            turnGPSOn();
        } else if (destination.length() == 0) {
            Toast.makeText(MainActivity.this, "Please enter the destination", Toast.LENGTH_SHORT).show();
        } else if (!checkConnectivity()) {
            Toast.makeText(MainActivity.this, "No Internet connection available", Toast.LENGTH_SHORT).show();
        } else if (mStartingPoint == null) {
            Toast.makeText(MainActivity.this, "Unable to fetch Current location. Please try again", Toast.LENGTH_SHORT).show();
        } else {
            addLocation(destination);
            Intent i = new Intent(MainActivity.this, Navigation.class);
            i.putExtra("Destination", destination);
            i.putExtra("Origin", mStartingPoint);
            i.putExtra("IPAddress", new ArrayList<String>(mIPAddresses));
            startActivity(i);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {

                    boolean locationAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (locationAccepted) {
                        Log.e("per", "Permission Granted");
                        if (!checkGPS()) turnGPSOn();
                    } else {
                        Log.e("per", "Permission Denied");

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION)) {
                                showMessageOKCancel("You need to allow access to the permissions",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    requestPermissions(new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION},
                                                            PERMISSION_REQUEST_CODE);
                                                }
                                            }
                                        },
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                onStop();
                                                finish();
                                            }
                                        }
                                );
                                return;
                            }
                        }
                    }
                }
                break;
        }
    }


    private void showMessageOKCancel(String message, DialogInterface.OnClickListener request, DialogInterface.OnClickListener finish) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", request)
                .setNegativeButton("Cancel", finish)
                .create()
                .show();
    }

    private boolean checkGPS() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void turnGPSOn() {
        if (checkGPS()) return;
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                .addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(10000 / 2);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        Task<LocationSettingsResponse> result =
                LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());
        result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
            @Override
            public void onComplete(Task<LocationSettingsResponse> task) {
                try {
                    LocationSettingsResponse response = task.getResult(ApiException.class);
                    // All location settings are satisfied. The client can initialize location
                    // requests here.
                    Log.e("GPS", "Enabled");
                    gps = true;
                    startService(new Intent(MainActivity.this, TrackingService.class));


                } catch (ApiException exception) {
                    switch (exception.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the
                            // user a dialog.
                            try {
                                // Cast to a resolvable exception.
                                ResolvableApiException resolvable = (ResolvableApiException) exception;
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                resolvable.startResolutionForResult(
                                        MainActivity.this,
                                        REQUEST_CHECK_SETTINGS);
                            } catch (IntentSender.SendIntentException | ClassCastException e) {
                                // Ignore the error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.

                            break;
                    }
                }
            }
        });

    }

    public boolean checkConnectivity() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (connMgr != null) {
            networkInfo = connMgr.getActiveNetworkInfo();
        }
        return networkInfo != null && networkInfo.isConnected();
    }

    public void addLocation(String location) {
        if (mRecentLocations == null) {
            mRecentLocations = new ArrayList<String>();
        }
        int index = mRecentLocations.indexOf(location);
        if (index == -1) {
            mRecentLocations.add(0, location);
        } else {
            mRecentLocations.remove(index); // delete and move to top
            mRecentLocations.add(0, location);
        }
        
        syncLocationPreferences();
    }

    public void clearLocations() {
        if (mRecentLocations == null || mRecentLocations.isEmpty()) {
            return;
        }

        mRecentLocations.clear();
        syncLocationPreferences();
    }

    public void syncLocationPreferences() {
        mRecentsView.invalidateViews();

        SharedPreferences prefs = getSharedPreferences("recent_locations", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        try {
            editor.putString("location", ObjectSerializer.serialize(mRecentLocations));
        } catch (IOException e) {
            e.printStackTrace();
        }
        editor.apply();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startService(new Intent(this, TrackingService.class));
        if (!mBound) doBindService();
        if (null == mRecentLocations) {
            mRecentLocations = new ArrayList<String>();
        }

        SharedPreferences prefs = getSharedPreferences("recent_locations", Context.MODE_PRIVATE);
        try {
            mRecentLocations = (ArrayList<String>) ObjectSerializer.deserialize(prefs.getString("location", ObjectSerializer.serialize(new ArrayList<String>())));
            Log.i("Locations", mRecentLocations.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        mRecentsView.setAdapter(new ArrayAdapter<String>(
                this, R.layout.recent_list_item, R.id.recent_text, mRecentLocations));
    }

    @Override
    protected void onResume() {
        super.onResume();
        myDatagramReceiver = new MyDatagramReceiver();
        myDatagramReceiver.start();
        if (!mBound) doBindService();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        myDatagramReceiver.kill();
        if (mBound) doUnbindService();
        stopService(new Intent(this, TrackingService.class));
        Log.e("Service", "Service Stopped");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.about) {
            showAboutDialogBox();
            return true;
        }  else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void showAboutDialogBox() {
        final AlertDialog.Builder aboutDialogBox = new AlertDialog.Builder(MainActivity.this);
        aboutDialogBox.setTitle("About");
        aboutDialogBox.setMessage(getResources().getString(R.string.about));
        aboutDialogBox.setNegativeButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        aboutDialogBox.show();

    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mMyService = ((TrackingService.ServiceBinder) service).getService();
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            mMyService = null;
            mBound = false;
        }
    };

    void doBindService() {
        bindService(new Intent(this, TrackingService.class), mConnection, Context.BIND_AUTO_CREATE);
        mBound = true;
    }

    void doUnbindService() {
        // Detach our existing connection.
        unbindService(mConnection);
        mBound = false;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    private class MyDatagramReceiver extends Thread {
        private boolean bKeepRunning = true;

        public void run() {
            Log.i("broadcast-listener", "udp listener started");
            String message;
            DatagramSocket socket = null;
            byte[] lmessage = new byte[10000];
            try {
                socket = new DatagramSocket(23001);
                while (bKeepRunning) {
                    Log.i("broadcast-listener", "waiting for broadcast");
                    DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);
                    socket.receive(packet);
                    message = new String(lmessage, 0, packet.getLength());
                    String IPAddress = packet.getAddress().getHostAddress();
                    //runOnUiThread(updateTextMessage);
                    Log.i("boardcast-listener", message);

                    byte[] reply = "ack".getBytes();
                    socket.send(new DatagramPacket(reply, reply.length, packet.getSocketAddress()));

                    boolean added = mIPAddresses.add(IPAddress);
                    if (added) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mDeviceConnected.setText(String.format("Devices Connected: %d", mIPAddresses.size()));
                            }
                        });
                    }
                    Log.i("broadcast-listener", "Added: " + added);
                    Log.i("boardcast-listener", "Address: " + IPAddress);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }

            if (socket != null) {
                socket.close();
            }

        }

        public void kill() {
            Log.i("broadcast-listener", "killed");
            bKeepRunning = false;
            Thread.currentThread().interrupt();
        }
    }
}
