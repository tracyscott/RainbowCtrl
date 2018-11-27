package com.noomechanism.rainbowctrl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCListener;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPort;
import com.illposed.osc.OSCPortIn;
import com.illposed.osc.OSCPortOut;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    public static final String TAG = "MainActivity";

    private Button updateButton;
    private EditText rainbowEditText;
    private SeekBar gammaRedBar;
    private CheckBox sendSensorsOSC;
    private Toolbar myToolbar;
    private ActionBar myActionBar;
    private String rainbowIP = "192.168.2.226";
    private String myIp = "192.168.2.136";
    private int rainbowPort = 7979;
    private int rainbowOscRecvPort = 7980;
    // This is used to send messages
    private OSCPortOut oscPortOut;
    private OSCPortIn oscReceiver;
    private String rainbowStudio1IP;
    private String rainbowStudio2IP;
    private String rainbowStudioIP;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Sensor accelerometer;
    private Sensor gyroscopeSensor;
    private Sensor rotationVectorSensor;
    private int sensorDelay = SensorManager.SENSOR_DELAY_NORMAL;
    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;
    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;
    private float lastX, lastY, lastZ;
    private float lastGyroX, lastGyroY, lastGyroZ;

    private float[] lastOrientations = new float[3];
    public boolean sendSensorsViaOSC = false;

    private static final int RAINBOW_STUDIO_NONE = 0;
    private static final int RAINBOW_STUDIO_ONE = 1;
    private static final int RAINBOW_STUDIO_TWO = 2;
    private int whichRainbowStudio = RAINBOW_STUDIO_NONE;


    private float vibrateThreshold = 0f;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    //mTextMessage.setText(R.string.title_home);
                    return true;
                case R.id.navigation_dashboard:
                    //mTextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    //mTextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        myActionBar = getSupportActionBar();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        rainbowStudio1IP = sharedPref.getString("rainbowstudio_1_ip", "192.168.1.100");
        rainbowStudio2IP = sharedPref.getString("rainbowstudio_2_ip", "192.168.1.101");
        Log.d(TAG, "Rainbow Studio 1 IP: " + rainbowStudio1IP);
        Log.d(TAG, "Rainbow Studio 2 IP: " + rainbowStudio2IP);

        updateButton = (Button) findViewById(R.id.updateBtn);
        rainbowEditText = (EditText) findViewById(R.id.rainbowEditText);
        gammaRedBar = (SeekBar) findViewById(R.id.gammaRedBar);

        updateButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                messageToSend = rainbowEditText.getText().toString();
                rainbowEditText.setText("");
                Log.d(TAG, "clicked button to send message");
            }
        });

        sendSensorsOSC = (CheckBox) findViewById(R.id.enableSensorsOSCBtn);
        sendSensorsOSC.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    sendSensorsViaOSC = true;
                } else {
                    sendSensorsViaOSC = false;
                }
            }
        });
        //BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        //navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, accelerometer, sensorDelay);
            vibrateThreshold = accelerometer.getMaximumRange() / 2;
        } else {
            Log.d(TAG, "Accelerometer sensor not available.");
        }

        gyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscopeSensor != null) {
            mSensorManager.registerListener(this, gyroscopeSensor, sensorDelay);
        } else {
            Log.d(TAG, "No gyroscope sensor available");
        }

        SensorEventListener rvListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float[] rotationMatrix = new float[16];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values);
                // Remap coordinate system
                float[] remappedRotationMatrix = new float[16];
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedRotationMatrix);
                // Convert to orientations
                float[] orientations = new float[3];
                SensorManager.getOrientation(remappedRotationMatrix, orientations);
                // Convert from radians to degrees.
                for(int i = 0; i < 3; i++) {
                    orientations[i] = (float)(Math.toDegrees(orientations[i]));
                    lastOrientations[i] = orientations[i];
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };

        rotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);;
        if (rotationVectorSensor != null) {
            mSensorManager.registerListener(rvListener, rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        Log.d(TAG, "onCreate, starting network threads");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_one:
                // Connect to RainbowStudio 1
                myActionBar.setTitle("RainbowCtrl - 1");
                if (whichRainbowStudio != RAINBOW_STUDIO_ONE) {
                    whichRainbowStudio = RAINBOW_STUDIO_ONE;
                    rainbowStudioIP = rainbowStudio1IP;
                    if (oscPortOut != null) {
                        oscPortOut.close();
                        oscPortOut = null;
                    }
                }
                return true;
            case R.id.action_two:
                // Connect to RainbowStudio 2
                myActionBar.setTitle("RainbowCtrl - 2");
                if (whichRainbowStudio != RAINBOW_STUDIO_TWO) {
                    whichRainbowStudio = RAINBOW_STUDIO_TWO;
                    rainbowStudioIP = rainbowStudio2IP;
                    oscSendThread.interrupt();
                    if (oscPortOut != null) {
                        oscPortOut.close();
                        oscPortOut = null;
                    }
                }
                return true;
            default:
                super.onOptionsItemSelected(item);
        }
        return false;
    }

    //onResume() register the accelerometer for listening the events
    protected void onResume() {
        Log.d(TAG, "resuming!");
        super.onResume();
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        oscRecvThread.start();
        oscSendThread.start();
    }

    // onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        Log.d(TAG, "pausing!");
        super.onPause();
        mSensorManager.unregisterListener(this);
        if (oscReceiver != null) {
            oscReceiver.stopListening();
            oscReceiver.close();
        }
        if (oscPortOut != null) {
            oscPortOut.close();
            oscPortOut = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Process updates from the accelerometer and gyroscope.
     *
     * @param event SensorEvent containing updated values.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // clean current values
        //        displayCleanValues();
        // display the current x,y,z accelerometer values
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // displayCurrentValues();
            // display the max x,y,z accelerometer values
            //displayMaxValues();
            // get the change of the x,y,z values of the accelerometer
            deltaX = Math.abs(lastX - event.values[0]);
            deltaY = Math.abs(lastY - event.values[1]);
            deltaZ = Math.abs(lastZ - event.values[2]);
            // if the change is below 2, it is just plain noise
            if (deltaX < 2)
                deltaX = 0;
            if (deltaY < 2)
                deltaY = 0;
            lastX = event.values[0];
            lastY = event.values[1];
            lastZ = event.values[2];
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            lastGyroX = event.values[0];
            lastGyroY = event.values[1];
            lastGyroZ = event.values[2];
        }
    }

    public void displayCleanValues() {
        /*
        currentX.setText("0.0");
        currentY.setText("0.0");
        currentZ.setText("0.0");
        */
    }

    // display the current x,y,z accelerometer values
    public void displayCurrentValues() {
        Log.d(TAG, "deltaX=" + Float.toString(deltaX));
        Log.d(TAG, "deltaY=" + Float.toString(deltaY));
        Log.d(TAG, "deltaZ=" + Float.toString(deltaZ));
    }

    private String messageToSend;

    private Thread oscSendThread = new Thread() {
        @Override
        public void run() {
            // We need to open and close these ports as we switch between 1 and 2.
            while (true) {
                if (oscPortOut == null && whichRainbowStudio != RAINBOW_STUDIO_NONE) {
                    try {
                        // Connect to some IP address and port
                        Log.d(TAG, "opening OSC port to send to: " + rainbowStudioIP);
                        //rainbowIP = myIp;
                        // We need to grab the correct IP/Port depending on whether we are
                        // connecting to RainbowStudio-1 or RainbowStudio-2.
                        oscPortOut = new OSCPortOut(InetAddress.getByName(rainbowStudioIP), rainbowPort);
                        Log.d(TAG, "port opened.");
                    } catch (UnknownHostException unhex) {
                        Log.e(TAG, "UnknownHostException: " + unhex.getMessage());
                    } catch (SocketException sex) {
                        Log.e(TAG, "SocketException: " + sex.getMessage());
                    }
                }
                if (oscPortOut != null) {
                    if (messageToSend != null) {
                        try {
                            String[] thingsToSend = new String[1];
                            thingsToSend[0] = "/rainbow/textupdate";
                            Object[] valuesToSend = new String[1];
                            valuesToSend[0] = messageToSend;
                            OSCMessage message = new OSCMessage((String) thingsToSend[0], Arrays.asList(valuesToSend[0]));
                            Log.d(TAG, "Sending message.");
                            oscPortOut.send(message);
                            messageToSend = null;
                        } catch (UnknownHostException e) {
                            // Error handling when your IP isn't found
                            Log.d(TAG, "UnknownHostException");
                            return;
                        } catch (Exception e) {
                            // Error handling for any other errors
                            Log.d(TAG, "some other error: " + e.getMessage());
                            Log.e(TAG, "error", e);
                            return;
                        }
                    }

                    if (sendSensorsViaOSC) {
                        try {
                            OSCBundle sensorData = new OSCBundle();
                            Object[] args = new Object[1];
                            args[0] = lastX;
                            OSCMessage message = new OSCMessage("/rainbow/mobile/accelx", Arrays.asList(args));
                            sensorData.addPacket(message);
                            args[0] = lastY;
                            message = new OSCMessage("/rainbow/mobile/accely", Arrays.asList(args));
                            sensorData.addPacket(message);
                            args[0] = lastZ;
                            message = new OSCMessage("/rainbow/mobile/accelz", Arrays.asList(args));
                            sensorData.addPacket(message);
                            Log.d(TAG, "Sending sensor data: " + lastX);
                            oscPortOut.send(sensorData);
                        } catch (UnknownHostException unhex) {
                            Log.e(TAG, "UnknownHostException: " + unhex.getMessage());
                        } catch (Exception e) {
                            Log.e(TAG, "Exception: " + e.getMessage());
                        }
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException iex) {
                    // When we switch between RAINBOW_STUDIO_ONE and RAINBOW_STUDIO_TWO, we close
                    // our oscOutputPort, set it to null, and then interrupt this thread.  This
                    // Thread should re-initialize oscOutputPort with the correct IP based on our
                    // preferences.
                }
            }

        }
    };

    private Thread oscRecvThread = new Thread() {
        @Override
        public void run() {
            try {
                Log.d(TAG, "Initializing OSC Receiver");
                oscReceiver = new OSCPortIn(rainbowOscRecvPort);
                OSCListener listener = new OSCListener() {
                    public void acceptMessage(java.util.Date time, OSCMessage message) {
                        Log.d(TAG, "Message received!");
                        List<Object> args = message.getArguments();
                        Log.d(TAG, "received: " + message.getAddress());
                        float value =  (Float)args.get(0);
                        Log.d(TAG, "value=" + value);
                        gammaRedBar.setProgress((int)(100.0f * (value-1.0f)/2.0f));
                    }
                };
                oscReceiver.addListener("//*", listener);
                oscReceiver.startListening();
                Log.d(TAG, "Done listening!");
            } catch (SocketException sex) {
                Log.e(TAG, "SocketException receiving: " + sex.getMessage());
            }
        }
    };
}
