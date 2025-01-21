/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

package com.example.watch2.presentation
import DataRecorder.dataRecorder
import OneEuroFilter
import Utilities.CurrentSelectionMethod
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.wear.widget.SwipeDismissFrameLayout
import com.example.watch2.R
import com.example.watch2.databinding.ActivityGameBinding
import com.example.watch2.presentation.BlockActivity.Companion.trialSet
import com.example.watch2.presentation.MainActivity.Companion.player
import com.github.psambit9791.jdsp.filter.Butterworth
import kotlin.math.absoluteValue
import kotlin.math.pow


class GameActivity : AppCompatActivity(), SensorEventListener {

    private val TAG = "GameActivity"

    companion object Static {
        @JvmStatic
        var XfromSONAR = 0f
    }

    private var nBinding: ActivityGameBinding? = null
    private val binding get() = nBinding!!

    private lateinit var dot: TextView
    private lateinit var square1: TextView
    private lateinit var square2: TextView


    private var num = mutableListOf<Int>(0,0)
    private var count = mutableListOf<Int>(0,0)
    private var prev = mutableListOf<Int>(0,0)
    private val btnL = mutableListOf(0,0)
    private val btnR= mutableListOf(0,0)
    private var isLeft = true
    private val greenColor = Color.parseColor("#27AE60")
    private val shadedGreenColor = Color.parseColor("#1E8449")
    private val redColor = Color.parseColor("#C0392B")

    private var butterworth: Butterworth = Butterworth(100.0)
    private var accX_arrayList: ArrayList<Double> = arrayListOf()
    private var accY_arrayList: ArrayList<Double> = arrayListOf()
    private var accZ_arrayList: ArrayList<Double> = arrayListOf()
    private var norm_acc_arrayList: ArrayList<Double> = arrayListOf()
    private var window_size = 30
    private var current_tap = false
    private var prev_tap = false
    private var already_tapped = false
    private var tap_starttime: Long = 0
    private val tap_threshold = 15

    private val total_w = 480 // 480
    private val dwellThreshold = 500 //ms
    private val nOfSelection = 6

    private lateinit var sensorManager: SensorManager

    private var onDwell = false
    private lateinit var dwellProgress: ProgressBar
    private var dwellingInitTime: Long = 0
    private var onlyCalledTwoTime = 0
    private var isEnded = false

    private lateinit var layout: SwipeDismissFrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {

        Log.w(TAG, "Log/ Game Activity STARTED")

        super.onCreate(savedInstanceState)
        nBinding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val w = trialSet.getCurrentWidth()
        val h = 80
        val d = trialSet.getCurrentDistance()

        layout = findViewById<androidx.wear.widget.SwipeDismissFrameLayout>(R.id.root)

        val euroFilter = OneEuroFilter(frequency,
            mincutoff,
            beta,
            dcutoff)

        square1 = TextView(this)
        square2 = TextView(this)
        val bar = TextView(this)
        dot = TextView(this)

        square1.setLayoutParams(ViewGroup.LayoutParams(w, h))  //set width height of square
        square1.setBackgroundColor(redColor)
        square1.setX((total_w / 2 - d / 2 - w).toFloat())
        square1.setY((total_w / 2 - h / 2).toFloat())

        square2.setLayoutParams(ViewGroup.LayoutParams(w, h))
        square2.setBackgroundColor(redColor)
        square2.setX((total_w / 2 + d / 2).toFloat())
        square2.setY((total_w / 2 - h / 2).toFloat())

        dot.setLayoutParams(ViewGroup.LayoutParams(10, 10))
        dot.setBackgroundColor(Color.RED)
        dot.setX((total_w / 2 - dot.width / 2).toFloat())
        dot.setY((total_w / 2 - 10/2).toFloat())

        // Add to parent view
        layout?.addView(square1)
        layout?.addView(square2)
        layout?.addView(bar)
        layout?.addView(dot)

        dwellProgress = findViewById(R.id.dwellProgress)

        //Listener for listening the changes of XfromSONAR
        val listen = MutableLiveData<Float>()
        var updatedX = (total_w/8/2).toFloat()
        var euroFilteredSonar = 0f

        // set accelerometer sensor for IMU tap action
        setUpSensorStuff()
        val trial_starttime = System.currentTimeMillis()
        trialSet.startTrial(trial_starttime)

        //when 'listen' is changed take action
        listen.observe(this@GameActivity, object : Observer<Float> {
            override fun onChanged(changedValue: Float) {
                if (onlyCalledTwoTime < 2){
                    getButtonLoc(0, square1)
                    getButtonLoc(1, square2)
                    onlyCalledTwoTime += 1
                }

                if ((System.currentTimeMillis() - trial_starttime) > 10000 && !isEnded) {
                    isEnded = true
                    endActivity()
                }

                trialSet.addPts(Point(XfromSONAR, euroFilteredSonar, updatedX, System.currentTimeMillis()))

                val newX = updatedX * 8f

                dot.animate()
                    .x(newX)
                    .setDuration(0)
                    .start()

                when (CurrentSelectionMethod) {
                    0 -> {
                        if (isLeft) doubleCross(0)
                        else doubleCross(1)
                    } // USE CASE 0: Double Cross
                    1 -> {
                        if (isLeft) dwell(0)
                        else dwell(1)
                    } // USE CASE 1: Dwell
                    2 -> {
                        if (isLeft) tap_region(0)
                        else tap_region(1)
                    } // USE CASE 2: TAP
                }

                if (isLeft) changeCol(0, square1)
                else changeCol(1, square2)
            }
        })

        //Changing 'listen' value every 0.02sec
        object : CountDownTimer(Long.MAX_VALUE, 20) {
            override fun onTick(millisUntilFinished: Long) {
                if (listen.value != XfromSONAR) {
                    euroFilteredSonar = euroFilter.filter(XfromSONAR.toDouble(), System.currentTimeMillis()/1000.toDouble()).toFloat()
                    updatedX += euroFilteredSonar

                    if (updatedX <= 0){
                        updatedX = 0f
                    } else if (updatedX > total_w/8) {
                        updatedX = (total_w/8).toFloat()
                    }

                    listen.setValue(XfromSONAR)
                }
            }

            override fun onFinish() {
            }
        }.start()

    }

    fun endActivity() {
        initvar()
        setOffSensorStuff()
        stopData()
        dataRecorder.stopRecordingAudio()
        trialSet.endTrial(System.currentTimeMillis())

        val intent = Intent(this@GameActivity, SavingActivity::class.java)
        startActivity(intent)

        finish()
    }

    fun stopData() {
        player.stop()
    } // stop playback

    fun getButtonLoc(i: Int, btn: TextView) {
        val w = btn.width
        val tune = dot.width / 2
        val loc = IntArray(2)
        btn.getLocationInWindow(loc)
        btnL.set(i, (loc[0] - tune))
        btnR.set(i, (loc[0] + w - tune))
        Log.w(TAG, "Log/ btn${i} loc: ${(loc[0] - tune)}, ${(loc[0] + w - tune)}")
    }

    fun doubleCross(i: Int) {
        if (count[i] == 0) {
            if (dot.x.toInt() in btnL[i]..btnR[i]) {
                setCount(i,1)
            }
            if ((dot.x.toInt() < btnL[i]) || (dot.x.toInt() > btnR[i])) {
                prev.set(i, (dot.x.toInt()))
            }
        }
        else if (count[i] == 1) {
            if ((dot.x.toInt() < btnL[i]) && (prev[i] > btnR[i])) {
                setCount(i, 3)
                prev.set(i, (dot.x.toInt()))
            } else if ((dot.x.toInt() > btnR[i]) && (prev[i] < btnL[i])) {
                setCount(i, 3)
                prev.set(i, (dot.x.toInt()))
            } else if ((dot.x.toInt() < btnL[i]) && (prev[i] < btnL[i])) {
                setCount(i, 2)
            } else if ((dot.x.toInt() > btnR[i]) && (prev[i] > btnR[i])) {
                setCount(i, 2)
            }
        }
    }

    fun dwell(i:Int){
        if (dot.x.toInt() in btnL[i]..btnR[i]) {
            if (i==0) dwellProgress.setX(square1.x + (square1.width - dwellProgress.width)/2)
            else dwellProgress.setX(square2.x + (square2.width - dwellProgress.width)/2)

            if (!onDwell) {
                dwellingInitTime = System.currentTimeMillis()
                onDwell = true
                dwellProgress.visibility = View.VISIBLE
            } else {
                if ((System.currentTimeMillis() - dwellingInitTime) > dwellThreshold) {
                    setCount(i, 2)
                    dwellProgress.visibility = View.INVISIBLE
                } else {
                    dwellProgress.progress =
                        ((System.currentTimeMillis() - dwellingInitTime) / (dwellThreshold / 100)).toInt()
                    setCount(i, 1)
                }
            }
        }
        else {
            if (onDwell){
                onDwell=false
                dwellProgress.visibility = View.INVISIBLE
                if (count[i] == 1) {
                    trialSet.addErrorTIme(System.currentTimeMillis())
                }
                setCount(i, 0)
            }
        }
    }

    fun tap_region(i:Int){
        if (dot.x.toInt() in btnL[i]..btnR[i]) {
            setCount(i, 1)
        } else {
            setCount(i, 0)
        }
    }

    fun tap(i:Int){
        if (dot.x.toInt() in btnL[i]..btnR[i]) {
            if (!already_tapped && current_tap) { setCount(i, 2) }
            else { setCount(i, 1) }
        } else {
            if (!already_tapped && current_tap) { setCount(i, 3) }
            else { setCount(i, 0) }
        }
    }

    fun changeCol(i: Int, btn: TextView) {
        if (count[i] == 0) {
            btn.setBackgroundColor(greenColor)
        } else if (count[i] == 1) {
            btn.setBackgroundColor(shadedGreenColor)
        } else if (count[i] == 2) {
            btn.setBackgroundColor(redColor)
            num[i]++
            btn.text = num[i].toString()
            setCount(i, 0)
            prev.set(i, (dot.x.toInt()))
            trialSet.addSelectionTIme(System.currentTimeMillis())
            if ((num[0] + num[1]) >= nOfSelection) {
                endActivity()
                isEnded = true
            }
            if (isLeft) isLeft = false
            else isLeft = true
        } else if (count[i] == 3) {
            btn.setBackgroundColor(redColor)
            num[i]++
            btn.text = num[i].toString()
            setCount(i, 0)

            prev.set(i, (dot.x.toInt()))
            trialSet.addSelectionTIme(System.currentTimeMillis())
            trialSet.addErrorTIme(System.currentTimeMillis())
            if ((num[0] + num[1]) >= nOfSelection) {
                endActivity()
                isEnded = true
            }

            if (isLeft) isLeft = false
            else isLeft = true
        }
    }

    fun setCount(i: Int, n: Int) {
        for (j in 0..1) {
            if (j == i) {
                count.set(j, n)
            } else {
                count.set(j, 0)
            }
        }
    }

    fun initvar(){
        num = mutableListOf<Int>(0,0,0,0,0)
        count = mutableListOf<Int>(0,0,0,0,0)
        prev = mutableListOf<Int>(0,0,0,0,0)
    }

    private fun setUpSensorStuff(){
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also{
            sensorManager.registerListener(this,
                it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    private fun setOffSensorStuff(){
        sensorManager.unregisterListener(this)
    }


    override fun onSensorChanged(event: SensorEvent?) {
        val timestamp = System.currentTimeMillis()
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER){
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            trialSet.addAcc(Sensor(x, y, z, timestamp))

            if ((CurrentSelectionMethod) == 2) {
                update_window(x.toDouble(), y.toDouble(), z.toDouble())
                if (accZ_arrayList.size == window_size && !already_tapped) {
                    norm_pow_window()
                    val flt = butterworth.highPassFilter(norm_acc_arrayList.toDoubleArray(), 3, 10.0)
                    val flt_max = arrayOf(flt.max().absoluteValue, flt.min().absoluteValue).max()
                    norm_acc_arrayList.clear()

                    if (flt_max > tap_threshold) {
                        current_tap = true
                    } else {
                        current_tap = false
                    }

                    if (!prev_tap) {
                        if (current_tap && (!already_tapped)) {
                            Log.w(TAG, "Log/ Tapped!, flt_value: ${flt_max}")

                            if (isLeft) {
                                tap(0)
                                changeCol(0, square1)
                            } else {
                                tap(1)
                                changeCol(1, square2)
                            }

                            already_tapped = true
                            tap_starttime = System.currentTimeMillis()
                        }
                    }
                    if (current_tap) prev_tap = true
                    else prev_tap = false
                }

                if ((System.currentTimeMillis() - tap_starttime) > 500)
                    already_tapped = false
            }
        }
    }

    fun update_window(x: Double, y: Double, z: Double){
        if (accZ_arrayList.size == window_size) {
            accX_arrayList.removeAt(0)
            accY_arrayList.removeAt(0)
            accZ_arrayList.removeAt(0)
        }
        accX_arrayList.add(x)
        accY_arrayList.add(y)
        accZ_arrayList.add(z)
    }

    fun norm_pow_window(){
        val avg_x = accX_arrayList.average()
        val avg_y = accY_arrayList.average()
        val avg_z = accZ_arrayList.average()

        val sd_x = Math.sqrt(accX_arrayList.map{ (it-avg_x).pow(2) }.average())
        val sd_y = Math.sqrt(accY_arrayList.map{ (it-avg_y).pow(2) }.average())
        val sd_z = Math.sqrt(accZ_arrayList.map{ (it-avg_z).pow(2) }.average())

        val norm_x = accX_arrayList.map{(it-avg_x)/sd_x}
        val norm_y = accY_arrayList.map{(it-avg_y)/sd_y}
        val norm_z = accZ_arrayList.map{(it-avg_z)/sd_z}

        for (i in 0..window_size-1) {
            norm_acc_arrayList.add(norm_x[i].pow(2) + norm_y[i].pow(2) + norm_z[i].pow(2))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}

