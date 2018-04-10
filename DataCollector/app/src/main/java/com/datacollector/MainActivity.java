package com.datacollector;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener, View.OnClickListener {

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Button mButtonStart, mButtonStop, mButtonSave;
    private LineChart mChart;
    private ToneGenerator mToneGen;

    private boolean mCollectingData = false;
    private ArrayList<AccelData> mSensorData;
    private long mStartTime;
    private long mLastRecordTime;
    private static final long LENGTH = 2000;
    private static final long RESOLUTION = 20;
    private double mSumX, mSumY, mSumZ, mSumRX, mSumRY, mSumRZ;
    private int mNumAccelData;
    private int mNumGyroData;
    private TextView mTextView;
    private TextView mTextView2;

    private TensorFlowInferenceInterface inferenceInterface;
    private float[] inVals;
    private float[] outVals;
    private static final String INPUT_NAME = "Reshape";
    private static final String OUTPUT_NAME = "softmax_tensor";
    private String[] outputNames = new String[] {OUTPUT_NAME};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // sensor
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        // buttons
        mButtonStart = (Button) findViewById(R.id.button_start);
        mButtonStop = (Button) findViewById(R.id.button_stop);
        mButtonSave = (Button) findViewById(R.id.button_save);
        mButtonStart.setOnClickListener(this);
        mButtonStop.setOnClickListener(this);
        mButtonSave.setOnClickListener(this);
        mButtonStop.setEnabled(false);

        // chart
        mChart = (LineChart) findViewById(R.id.chart);

        // tone generator
        mToneGen = new ToneGenerator(AudioManager.STREAM_DTMF, ToneGenerator.MAX_VOLUME);

        mTextView = (TextView) findViewById(R.id.textview);
        mTextView2 = (TextView) findViewById(R.id.textview2);

        // tflite
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), "frozen_graph_all2.pb");
        inVals = new float[600];
        outVals = new float[26];
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // save data to ArrayList
        if (mCollectingData) {
            long curTime = System.currentTimeMillis();
            long timestamp = curTime - mStartTime;
            long timeElapsed = timestamp - mLastRecordTime;

            // time to start a new record?
            if (timeElapsed >= RESOLUTION) {
                if (mNumAccelData > 0 && mNumGyroData > 0) {
                    AccelData data = new AccelData(mLastRecordTime, mSumX / mNumAccelData,
                            mSumY / mNumAccelData, mSumZ / mNumAccelData, mSumRX / mNumGyroData,
                            mSumRY / mNumGyroData, mSumRZ / mNumGyroData);
                    mSensorData.add(data);
                } else {
                    Toast.makeText(this, "DON'T USE THIS DATA", Toast.LENGTH_LONG).show();
                }
                mLastRecordTime += RESOLUTION;
                // finish after a certain length of time
                if (timestamp >= LENGTH) {
                    onClick(mButtonStop);
                }
            }

            if (event.sensor.equals(mAccelerometer)) {
                mSumX += event.values[0];
                mSumY += event.values[1];
                mSumZ += event.values[2];
                mNumAccelData++;
            } else {
                mSumRX += event.values[0];
                mSumRY += event.values[1];
                mSumRZ += event.values[2];
                mNumGyroData++;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.button_start:
                // enable/disable buttons
                mButtonStop.setEnabled(true);
                mButtonStart.setEnabled(false);

                // set up array
                mSensorData = new ArrayList<>();
                mStartTime = System.currentTimeMillis();
                mLastRecordTime = 0;
                mSumX = 0;
                mSumY = 0;
                mSumZ = 0;
                mSumRX = 0;
                mSumRY = 0;
                mSumRZ = 0;
                mNumAccelData = 0;
                mNumGyroData = 0;

                // start collecting data
                mCollectingData = true;
                break;

            case R.id.button_stop:
                // stop collecting data
                mCollectingData = false;

                // enable/disable buttons
                mButtonStop.setEnabled(false);
                mButtonStart.setEnabled(true);

                // make loud beep
                //mToneGen.startTone(ToneGenerator.TONE_DTMF_0, 100);

                // plot data
                plotData();
                dostuff();
                break;

            case R.id.button_save:
                dostuff();
                //Toast.makeText(this, "DO STUFF HERE", Toast.LENGTH_SHORT).show();
                break;

            default:
                break;
        }
    }

    private void dostuff() {
        // normalize data
        double mean[] = {0,0,0,0,0,0};
        double stdev[] = {0,0,0,0,0,0};

        for (AccelData data : mSensorData) {
            mean[0] += data.getX();
            mean[1] += data.getY();
            mean[2] += data.getZ();
            mean[3] += data.getRx();
            mean[4] += data.getRy();
            mean[5] += data.getRz();
        }

        for (int i = 0; i < 6; i++) {
            mean[i] /= 100;
        }

        for (AccelData data : mSensorData) {
            stdev[0] += (data.getX() - mean[0]) * (data.getX() - mean[0]);
            stdev[1] += (data.getY() - mean[1]) * (data.getY() - mean[1]);
            stdev[2] += (data.getZ() - mean[2]) * (data.getZ() - mean[2]);
            stdev[3] += (data.getRx() - mean[3]) * (data.getRx() - mean[3]);
            stdev[4] += (data.getRy() - mean[4]) * (data.getRy() - mean[4]);
            stdev[5] += (data.getRz() - mean[5]) * (data.getRz() - mean[5]);
        }

        for (int i = 0; i < 6; i++) {
            stdev[i] = Math.sqrt(stdev[i] / 100);
        }

        // put data into inVals
        for (int i = 0; i < 600; i += 6) {
            AccelData data = mSensorData.get(i / 6);
            inVals[i    ] = (float) ((data.getX()  - mean[0]) / stdev[0]);
            inVals[i + 1] = (float) ((data.getY()  - mean[1]) / stdev[1]);
            inVals[i + 2] = (float) ((data.getZ()  - mean[2]) / stdev[2]);
            inVals[i + 3] = (float) ((data.getRx() - mean[3]) / stdev[3]);
            inVals[i + 4] = (float) ((data.getRy() - mean[4]) / stdev[4]);
            inVals[i + 5] = (float) ((data.getRz() - mean[5]) / stdev[5]);
        }

        // inference
        inferenceInterface.feed(INPUT_NAME, inVals, 1, 100, 1, 6);
        inferenceInterface.run(outputNames);
        inferenceInterface.fetch(OUTPUT_NAME, outVals);

        float maxOut = outVals[0];
        int maxIndex = 0;
        for (int i = 1; i < 26; i++) {
            if (Float.compare(outVals[i], maxOut) > 0) {
                maxOut = outVals[i];
                maxIndex = i;
            }
        }
        char prediction = (char) ('a' + maxIndex);
        //Toast.makeText(this, "You wrote the letter '" + prediction + "'!", Toast.LENGTH_SHORT).show();

        mTextView.setText("Confidence: " + Integer.toString((int) (maxOut * 100 + 0.5)) + "%");
        mTextView2.setText("" + prediction);
    }

    private void plotData() {
        List<Entry> xPoints = new ArrayList<>();
        List<Entry> yPoints = new ArrayList<>();
        List<Entry> zPoints = new ArrayList<>();
        List<Entry> rxPoints = new ArrayList<>();
        List<Entry> ryPoints = new ArrayList<>();
        List<Entry> rzPoints = new ArrayList<>();

        for (AccelData data : mSensorData) {
            xPoints.add(new Entry(data.getTimestamp(), (float) data.getX()));
            yPoints.add(new Entry(data.getTimestamp(), (float) data.getY()));
            zPoints.add(new Entry(data.getTimestamp(), (float) data.getZ()));
            rxPoints.add(new Entry(data.getTimestamp(), (float) data.getRx()));
            ryPoints.add(new Entry(data.getTimestamp(), (float) data.getRy()));
            rzPoints.add(new Entry(data.getTimestamp(), (float) data.getRz()));
        }

        LineDataSet xDataSet = new LineDataSet(xPoints, "X");
        xDataSet.setColor(Color.RED);
        xDataSet.setDrawCircles(false);
        LineDataSet yDataSet = new LineDataSet(yPoints, "Y");
        yDataSet.setColor(Color.GREEN);
        yDataSet.setDrawCircles(false);
        LineDataSet zDataSet = new LineDataSet(zPoints, "Z");
        zDataSet.setColor(Color.BLUE);
        zDataSet.setDrawCircles(false);
        LineDataSet rxDataSet = new LineDataSet(rxPoints, "RX");
        rxDataSet.setColor(Color.DKGRAY);
        rxDataSet.setDrawCircles(false);
        LineDataSet ryDataSet = new LineDataSet(ryPoints, "RY");
        ryDataSet.setColor(Color.GRAY);
        ryDataSet.setDrawCircles(false);
        LineDataSet rzDataSet = new LineDataSet(rzPoints, "RZ");
        rzDataSet.setColor(Color.LTGRAY);
        rzDataSet.setDrawCircles(false);

        LineData lineData = new LineData(xDataSet, yDataSet, zDataSet, rxDataSet, ryDataSet, rzDataSet);
        mChart.setData(lineData);

        Description desc = new Description();
        desc.setText("");
        mChart.setDescription(desc);

        mChart.invalidate();
    }
}
