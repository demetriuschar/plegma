package com.yodiwo.androidnode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.Address;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.Geocoder;
import android.provider.Settings;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity implements LocationListener {

    private final static String TAG = MainActivity.class.getSimpleName();

    private SettingsProvider settingsProvider = null;
    private NfcAdapter mNfcAdapter = null;

    public static final String MIME_TEXT_PLAIN = "text/plain";

    private LocationManager locationManager;
    private String bestGPSProvider;
    private BluetoothAdapter bluetoothAdapter;

    private static final int REQUEST_ENABLE_BT = 666;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if we are pair the device
        if (settingsProvider == null)
            settingsProvider = SettingsProvider.getInstance(this);

        if (settingsProvider.getNodeKey() != null && settingsProvider.getNodeSecretKey() != null) {

            // Check for nfc
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter == null) {
                Toast.makeText(this, "This device doesn't support NFC", Toast.LENGTH_LONG).show();
            } else {
                if (!mNfcAdapter.isEnabled()) {
                    Toast.makeText(this, "NFC is disabled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "NFC is enabled", Toast.LENGTH_SHORT).show();
                }
            }

            // Check for torch availability
            ThingsModuleService.hasTorch = getApplicationContext().getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
            if (!ThingsModuleService.hasTorch) {

                Toast.makeText(this, "This device doesn't support Torch", Toast.LENGTH_LONG).show();
            }
            else {
                // Start service
                Intent intent = new Intent(this, ThingsModuleService.class);
                startService(intent);
            }

            // Check for Bluetooth availability
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "This device doesn't support Bluetooth", Toast.LENGTH_LONG).show();
            }
            else {
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            }

            // Get the location manager
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (!isGPSEnabled) {
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                    alertDialog.setTitle("GPS settings")
                            .setMessage("GPS is currently disabled. Do you want to go to settings menu?")
                            .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            })
                            .show();
                }
                else {
                    bestGPSProvider = this.getBestGPSProviderForChosenCriteria();
                }
            }
            else
            {
                Toast.makeText(this, "GPS not supported", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getBestGPSProviderForChosenCriteria() {

        // List all providers (for debug purposes)
        List<String> providers = locationManager.getAllProviders();
        for (String provider : providers) {
            LocationProvider info = locationManager.getProvider(provider);
            Log.d(TAG, "GPS provider:" + info.toString());
        }

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_MEDIUM);
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
        String provider = locationManager.getBestProvider(criteria, false);
        LocationProvider info = locationManager.getProvider(provider);
        Log.d(TAG, "GPS best provider: " + info.toString());

        Location location = locationManager.getLastKnownLocation(provider);
        if (location == null)
            Log.d(TAG, "GPS Locations (starting with last known): [unknown]\n\n");
        else
            Log.d(TAG, "GPS Locations (starting with last known): " + location.toString());

        return provider;
    }

    // ---------------------------------------------------------------------------------------------
    // TEARDOWN PROCEDURE

    private int backPressCounter = 0;

    private class BackPressCounterTimeout extends TimerTask {
        @Override
        public void run() { backPressCounter = 0; }
    }

    private void Teardown() {
        NodeService.Teardown(this);
        super.onBackPressed();
    }

    @Override
    public void onBackPressed() {

        if(backPressCounter == 0) {
            Toast.makeText(this, "Press back once more to disconnect and exit", Toast.LENGTH_SHORT).show();
            backPressCounter++;
            (new Timer()).schedule(new BackPressCounterTimeout(), 2 * 1000);  //2 sec
        } else {
            Teardown();
        }
        Log.d(TAG, "Back button tapped");
    }

    // ---------------------------------------------------------------------------------------------
    @Override
    public void onResume() {
        super.onResume();

        if (settingsProvider.getNodeKey() == null || settingsProvider.getNodeSecretKey() == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Pair now?")
                    .setMessage("Application is not paired to Yodiwo Services. Pair now?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(MainActivity.this, PairingActivity.class);
                                startActivity(intent);
                            }
                    })
                    .setNegativeButton("No", null)
                    .setCancelable(true)
                    .show();
        }
        else {

            // Tell NodeService to handle Resuming itself
            NodeService.Resume(this);

            // Get from defines what we need to enable
            NodeService.StartService(this, SensorsListener.SensorType.Accelerometer);
            NodeService.StartService(this, SensorsListener.SensorType.Brightness);
            NodeService.StartService(this, SensorsListener.SensorType.Proximity);

            // Resume NFC
            setupForegroundNFCDispatch(this, mNfcAdapter);

            // Get hold of camera resource for torch
            if(ThingsModuleService.hasTorch) {
                ThingsModuleService.getCamera();
            }

            // Request update location
            if(locationManager != null) {
                try {
                    if(bestGPSProvider == null) {
                        bestGPSProvider = this.getBestGPSProviderForChosenCriteria();
                    }

                    // TODO: Set detailed default criteria and expose them through thing's configuration
                    locationManager.requestLocationUpdates(bestGPSProvider, 20000, 500, this);
                }
                catch (Exception e) {
                    Log.e(TAG, "Request location updates failed", e);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public void onPause() {

        // Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
        if(mNfcAdapter!=null)
            stopForegroundNFCDispatch(this, mNfcAdapter);

        if (settingsProvider.getNodeKey() != null && settingsProvider.getNodeSecretKey() != null) {
            // Get from defines what we need to enable
            NodeService.StopService(this, SensorsListener.SensorType.Accelerometer);
            NodeService.StopService(this, SensorsListener.SensorType.Brightness);
            NodeService.StopService(this, SensorsListener.SensorType.Proximity);

            // Tell NodeService to handle Pausing itself
            NodeService.Pause(this);

            // Release camera resource
            if(ThingsModuleService.hasTorch) {
                ThingsModuleService.releaseCamera();
            }

            if (settingsProvider.getNodeKey() != null && settingsProvider.getNodeSecretKey() != null) {
                locationManager.removeUpdates(this);
            }
        }

        super.onPause();
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Intent intent = null;

        // Select the opened activity
        switch (id) {
            // Start settings activity
            case R.id.action_settings:
                intent = new Intent(this, SettingsActivity.class);
                break;
            // Start Pairing activity
            case R.id.action_repairing:
                intent = new Intent(this, PairingActivity.class);
                break;
        }

        if (intent != null) {
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onNewIntent(Intent intent) {
        handleNFCIntent(intent);
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Bluetooth Thing is disabled", Toast.LENGTH_LONG).show();
            }
        }
    }

    // =============================================================================================
    // NFC

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundNFCDispatch(final Activity activity, NfcAdapter adapter) {
        if(adapter!=null) {
            final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

            IntentFilter[] filters = new IntentFilter[1];
            String[][] techList = new String[][]{};

            // Notice that this is the same filter as in our manifest.
            filters[0] = new IntentFilter();
            filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
            filters[0].addCategory(Intent.CATEGORY_DEFAULT);
            try {
                filters[0].addDataType(MIME_TEXT_PLAIN);
            } catch (IntentFilter.MalformedMimeTypeException e) {
                throw new RuntimeException("Check your mime type.");
            }

            adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * @param activity The corresponding requesting to stop the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundNFCDispatch(final Activity activity, NfcAdapter adapter) {
        if(adapter!=null)
            adapter.disableForegroundDispatch(activity);
    }

    // ---------------------------------------------------------------------------------------------

    private void handleNFCIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);

            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
        }
    }
    // ---------------------------------------------------------------------------------------------

    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     */
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                Log.e(TAG, "NFC:" + result);
                NodeService.SendPortMsg(getApplicationContext(),
                        ThingManager.OutputNFC,
                        ThingManager.OutputNFCPort,
                        result);
            }
        }
    }


    // =============================================================================================
    // GPS

    private static final int REVERSEGEOCODING_RESULT_SUCCESS = 1;
    private static final String[] S = { "Out of Service", "Temporarily Unavailable", "Available" };

    @Override
    public void onLocationChanged(Location location) {

        location = locationManager.getLastKnownLocation(bestGPSProvider);
        if (location == null) {
            Log.d(TAG, "GPS Locations (starting with last known): [unknown]\n\n");
        }
        else {
            Log.d(TAG, "GPS Locations (starting with last known):" + location.toString());

            Double latitude = location.getLatitude();
            Double longitude = location.getLongitude();

            this.getAddressFromLocation(latitude, longitude,
                    getApplicationContext(), new ReverseGeocodingHandler());
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "GPS Provider Status Changed: " + provider + ", Status="
                + S[status] + ", Extras=" + extras);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "GPS Provider Enabled: " + provider);

        if(bestGPSProvider == null) {
            bestGPSProvider = this.getBestGPSProviderForChosenCriteria();
        }

        // TODO: Set detailed default criteria and expose them through thing's configuration
        locationManager.requestLocationUpdates(bestGPSProvider, 20000, 500, this);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "GPS Provider Disabled: " + provider);
    }

    // ---------------------------------------------------------------------------------------------

    // Reverse geocoding related private members

    private void getAddressFromLocation(final double latitude, final double longitude,
                                        final Context context, final Handler cbHandler) {

        Thread thread = new Thread() {

            @Override
            public void run() {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                try {
                    List<Address> addressList = geocoder.getFromLocation(latitude, longitude, 1);
                    if (addressList != null && addressList.size() > 0) {
                        Address address = addressList.get(0);

                        // Construct message
                        Message message = Message.obtain();
                        message.setTarget(cbHandler);
                        message.what = REVERSEGEOCODING_RESULT_SUCCESS;
                        Bundle bundle = new Bundle();
                        bundle.putDouble("latitude", latitude);
                        bundle.putDouble("longitude", longitude);
                        bundle.putString("address", address.getThoroughfare());
                        bundle.putString("country", address.getCountryName());
                        bundle.putString("postal", address.getPostalCode());
                        message.setData(bundle);

                        // Send message to handler
                        message.sendToTarget();
                    }
                }
                catch (IOException e) {
                    Log.e(TAG, "Reverse geocoding failed", e);
                }
            }
        };

        thread.start();
    }

    private class ReverseGeocodingHandler extends Handler {

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case REVERSEGEOCODING_RESULT_SUCCESS:
                {
                    Bundle bundle = message.getData();

                    // Notify NodeService
                    NodeService.SendPortMsg(getApplicationContext(),
                            ThingManager.GPS,
                            new String[] { Double.toString(bundle.getDouble("latitude")),
                                           Double.toString(bundle.getDouble("longitude")),
                                           bundle.getString("address"),
                                           bundle.getString("country"),
                                           bundle.getString("postal")});

                    break;
                }
                default:
                {
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
}
