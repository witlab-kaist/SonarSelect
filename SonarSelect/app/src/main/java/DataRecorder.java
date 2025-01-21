/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import com.example.watch2.presentation.GameActivity;

public class DataRecorder {

    // LLAP
    public static final int  AUDIO_SAMPLE_RATE = 48000;     //Record sample rate
    public static final double  START_FREQ = 17500.0;       //Start audio frequency
    public static final double FREQ_INTERVAL = 350.0;       //Frequency interval
    public static final int NUM_FREQ = 8;                   //Number of frequency
    public static final int MAX_FRAME_SIZE = 1920;          //Number of frame size
    public static final double SPEED_ADJ = 1.5;             //Speed adjust

    int maxFramesPerSlice = MAX_FRAME_SIZE;
    RangeFinder myRangeFinder = new RangeFinder(maxFramesPerSlice, NUM_FREQ, START_FREQ, FREQ_INTERVAL);

    private static final String TAG = "Data Recorder";

    // Recorder
    public static DataRecorder dataRecorder = new DataRecorder();
    static int device                       = MediaRecorder.AudioSource.MIC;   // for S21
    private static final int CHANNEL        = AudioFormat.CHANNEL_IN_MONO;   // use stereo to get top and bottom mics (one/channel)
    private static final int FORMAT         = AudioFormat.ENCODING_PCM_16BIT;  // standard encoding
    private static final int RECORDING_RATE = 48000;                           // DVD quality (max)
    private AudioRecord recorder;

    // Distance Variables
    double distance = 0;
    double distanceChange = 0;

    // the start and stop times
    long recordingStartTime;
    long recordingStopTime;

    // the minimum buffer size needed for audio recording - 7680 for 48000, STEREO(2), PCM_16BIT, or 1/25 of a second (40ms).
    private int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL, FORMAT);

    // are we currently sending audio data
    boolean currentlyRecordingAudio = false;

    // Sound Analysis
    public static double[] soundFile;

    public void startRecordingAudio(int duration) {
        currentlyRecordingAudio = true;
        startRecording(duration);
    }

    public void stopRecordingAudio() {
        currentlyRecordingAudio = false;
    }

    private void startRecording(final int duration) {

        recordingStartTime = recordingStopTime = -1; // blank it.

        Thread streamThread = new Thread(new Runnable() {

            @RequiresApi(api = Build.VERSION_CODES.O)
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                BUFFER_SIZE = MAX_FRAME_SIZE * 2;
                int maxPackets = duration * (AUDIO_SAMPLE_RATE / MAX_FRAME_SIZE);
                byte[] buffer = new byte[(int) (BUFFER_SIZE)];

                int count = 0;

                try {
                    recorder = new AudioRecord(device, RECORDING_RATE, CHANNEL, FORMAT, (int)(BUFFER_SIZE));
                    recorder.startRecording();
                    recordingStartTime = System.currentTimeMillis();
                    Log.w(TAG, "Recording Start Time: " + recordingStartTime);

                    while (currentlyRecordingAudio) {
                        recorder.read(buffer, 0, BUFFER_SIZE);
                        if (count==maxPackets) {
                            currentlyRecordingAudio = false;
                        }
                        else {
                            try {
                                int sz = (int) (BUFFER_SIZE / 2);
                                soundFile = new double[sz];
                                for (int index = 0; index < sz; index++) {
                                    int i = index * 2;
                                    soundFile[index] = (short) ((buffer[i + 1] << 8) + buffer[i]);
                                }

                                myRangeFinder.mRecDataBuffer_p = myRangeFinder.GetRecDataBuffer(maxFramesPerSlice);
                                myRangeFinder.mRecDataBuffer = soundFile;
                                distanceChange = myRangeFinder.GetDistanceChange();
                                distance = distance + distanceChange * SPEED_ADJ;
                                GameActivity.setXfromSONAR( (float) (distanceChange * SPEED_ADJ));
                            }
                            catch (Exception e) {
                                Log.w(TAG, "STFT Exception: " + e );
                            }
                            buffer = new byte[(int)(BUFFER_SIZE)];
                        }
                        count++;
                    }
                    recordingStopTime = System.currentTimeMillis(); // not reading after this point, so should be fairly accurate....
                    Log.w(TAG, "Recording Stop Time: " + recordingStopTime);
                }
                catch (Exception e) {
                    Log.w(TAG, "RECORDER Exception: " + e);
                }
                recorder.stop();
            }
        }
        );

        // start the thread
        streamThread.start();
    }
}