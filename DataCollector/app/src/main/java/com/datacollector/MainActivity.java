package com.datacollector;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class MainActivity extends Activity implements SensorEventListener, View.OnClickListener {
    private static final int RECORDS_PER_LETTER = 1;
    private static final long LENGTH = 2000;
    private static final long RESOLUTION = 20;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Button mButtonStart, mButtonStop, mButtonRedo;
    private TextView mTextView;
    private ImageView mImageView;
    private Drawable[] mImages;
    private ToneGenerator mToneGen;

    private boolean mCollectingData = false;
    private ArrayList<AccelData>[] mSensorData;
    private int mCurRecord;
    private char mCurLetter;
    private long mStartTime;
    private long mLastRecordTime;
    private double mSumX, mSumY, mSumZ, mSumRX, mSumRY, mSumRZ;
    private int mNumAccelData;
    private int mNumGyroData;

    private File mLastFile;

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
        mButtonRedo = (Button) findViewById(R.id.button_redo);
        mButtonStart.setOnClickListener(this);
        mButtonStop.setOnClickListener(this);
        mButtonRedo.setOnClickListener(this);
        mButtonStop.setEnabled(false);
        mButtonRedo.setEnabled(false);

        // textview
        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setText("Write the letter 'a':");

        // imageview (and drawables)
        mImages = new Drawable[26];
        for (int i = 0; i < 26; i++) {
            String id = String.valueOf((char) ('a' + i));
            mImages[i] = getDrawable(getResources().getIdentifier(id, "drawable", getPackageName()));
        }
        mImageView = (ImageView) findViewById(R.id.image);
        mImageView.setImageDrawable(mImages[0]);

        // tone generator
        mToneGen = new ToneGenerator(AudioManager.STREAM_DTMF, ToneGenerator.MAX_VOLUME);

        // vars
        mSensorData = new ArrayList[10];
        mCurRecord = 0;
        mCurLetter = 'a';
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
                    mSensorData[mCurRecord].add(data);
                } else {
                    // bad data; start over
                    onClick(mButtonStop);
                    onClick(mButtonRedo);
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
                mButtonRedo.setEnabled(false);

                // set up array
                mSensorData[mCurRecord] = new ArrayList<>();
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

                // make loud beep
                mToneGen.startTone(ToneGenerator.TONE_DTMF_5, 50);

                // start collecting data
                mCollectingData = true;
                break;

            case R.id.button_stop:
                // stop collecting data
                mCollectingData = false;

                // enable/disable buttons
                mButtonStop.setEnabled(false);
                mButtonStart.setEnabled(true);
                mButtonRedo.setEnabled(true);

                // save data
                saveData();

                // update vars
                mCurRecord++;
                if (mCurRecord == RECORDS_PER_LETTER) {
                    mCurRecord = 0;
                    mCurLetter++;
                    if (mCurLetter > 'z') {
                        // update text and image
                        mTextView.setText("Thank you!");
                        mImageView.setImageDrawable(null);

                        // make loud beep
                        mToneGen.startTone(ToneGenerator.TONE_DTMF_0, 100);
                        break;
                    }

                    // update text and image; do not start again
                    mTextView.setText("Write the letter '" + mCurLetter + "':");
                    mImageView.setImageDrawable(mImages[mCurLetter - 'a']);
                }

                // make loud beep
                mToneGen.startTone(ToneGenerator.TONE_DTMF_0, 100);

                break;

            case R.id.button_redo:
                mButtonRedo.setEnabled(false);

                mCurRecord--;
                if (mCurRecord < 0) {
                    mCurRecord = RECORDS_PER_LETTER - 1;
                    mCurLetter--;

                    if (!deleteLastFile()) {
                        Toast.makeText(this, "Warning: old file not deleted", Toast.LENGTH_LONG).show();
                    }

                    // update text and image
                    mTextView.setText("Write the letter '" + mCurLetter + "':");
                    mImageView.setImageDrawable(mImages[mCurLetter - 'a']);
                }
                break;

            default:
                break;
        }
    }

    private void saveData() {
        String filename = mCurLetter + "_" + System.currentTimeMillis() + ".csv";
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DataCollector/";
        File dir = new File(path);
        dir.mkdirs();
        File file = new File(dir, filename);
        mLastFile = file;

        try {
            file.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(file);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            for (AccelData data : mSensorData[mCurRecord]) {
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

    private boolean deleteLastFile() {
        return (mLastFile != null) && mLastFile.exists() && mLastFile.delete();
    }
}
