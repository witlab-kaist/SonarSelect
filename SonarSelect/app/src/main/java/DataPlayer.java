/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class DataPlayer {
    double[] sample = null;    // sound as double vals
    private static AudioTrack audioTrack = null;
    byte generatedSnd[]   = null;    // sound as PCM data
    boolean dataLoaded    = false;   // whether or not we have any contents.
    int sampleRate        = 48000;   // sample rate of signal - default is 48000

    private static final String TAG = "DataPlayer";

    public DataPlayer(double[] in, int repetition)
    {
        setData(in, repetition);
    }

    void setData(double[] in, int repetition)
    {
        sample = new double[in.length*repetition];
        for (int i=0; i<in.length; i++)
            for (int r=0; r<repetition; r++)
                sample[i + in.length*r] = in[i];
        genTone();

        Log.i(TAG, "Generated a PCM file of " + in.length);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                2 * in.length * repetition,
                AudioTrack.MODE_STATIC);

        dataLoaded = true;
    }

    // try to stop the sound playback manually.
    synchronized public void stop() {
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.setPlaybackHeadPosition(0); // back to the start
        }
    }

    // start the sound playback.
    synchronized public void play() {
        if (!dataLoaded) {
            Log.i(TAG, "DataPlayer: no data loaded");
            return;
        }

        try {
            audioTrack.write(generatedSnd, 0, generatedSnd.length);
            audioTrack.play();
        }
        catch (Exception e) {
            Log.i(TAG, "DataPlayer: " + e.toString());
        }
        catch (OutOfMemoryError e) {
            Log.i(TAG, "DataPlayer: " + e.toString());
        }
    }

    // Generate tone data for ~1 seconds - we round up to the length of the underlying data
    // why don't we just calculate this in the first place....
    synchronized void genTone() {
        // convert to 16 bit pcm sound array
        generatedSnd = new byte[2 * sample.length];
        Log.i(TAG, "GeneratedSnd: " + generatedSnd.length);
        int idx = 0;
        for (final double dVal : sample)
        {
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) ( val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
    }

    static public double[] generateCombTone(int fileFreq, int startFreq, int interFreq, int numFreq, int sampleDur)
    {
        sampleDur *= fileFreq;

        double[] y  = new double[sampleDur];
        for (double currentFreq = startFreq; currentFreq < startFreq + interFreq*numFreq; currentFreq += interFreq){
            if (currentFreq == startFreq){
                for (int i=0;i<sampleDur;i++){
                    y[i] = Math.cos(2 * Math.PI * i / (fileFreq/currentFreq)) / numFreq; // for continuous wave
                }
            }
            else {
                for (int i = 0; i < sampleDur; i++) {
                    y[i] += Math.cos(2 * Math.PI * i / (fileFreq / currentFreq)) / numFreq; // for continuous wave
                }
            }
        }
        return y;
    }
}
