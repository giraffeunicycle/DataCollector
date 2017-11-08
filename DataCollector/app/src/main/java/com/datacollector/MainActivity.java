package com.datacollector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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

    private String mLastFilename;

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
                mToneGen.startTone(ToneGenerator.TONE_DTMF_0, 100);

                // plot data
                plotData();
                break;

            case R.id.button_save:
                // pop up Dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = this.getLayoutInflater();
                final View view = inflater.inflate(R.layout.save_dialog, null);
                ((EditText) view.findViewById(R.id.save_text)).setText(mLastFilename);
                builder.setMessage(R.string.save_message)
                        .setView(view)
                        .setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                EditText et = (EditText) view.findViewById(R.id.save_text);
                                saveData(et.getText().toString());
                            }
                        });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                break;

            default:
                break;
        }
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

    private void saveData(String label) {
        mLastFilename = label;
        String filename = label.toLowerCase() + "_" + System.currentTimeMillis() + ".csv";
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DataCollector/";
        File dir = new File(path);
        dir.mkdirs();
        File file = new File(dir, filename);

        try {
            file.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(file);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            for (AccelData data : mSensorData) {
                outputStreamWriter.append(data.toString()).append("\n");
            }
            outputStreamWriter.close();
            outputStream.flush();
            outputStream.close();
            Toast.makeText(this, "File \"" + filename + "\" saved.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
            Toast.makeText(this, "Error! File not saved.", Toast.LENGTH_SHORT).show();
        }

    }
}
