/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

package com.example.watch2.presentation

import Utilities
import Utilities.TargetDistances
import Utilities.TargetWidths
import android.util.Log

class Trials {
    private val tag = "Trials"
    var trialsDone: ArrayList<Trial>? = null // all the completed trials
    var trials: ArrayList<Trial?>? = null // all the trials


    constructor() {
        trials = ArrayList()
        trialsDone = ArrayList()
    }

    fun initTrialBlock(width: Int, distance: Int, reps: Int): Boolean {
        trials = ArrayList()

        if (Utilities.BlockCounter > Utilities.NofBlocks) {
            return false
        }

        for (r in 0 until reps)
            for (w in 0 until width)
                for (d in 0 until distance)
                    trials!!.add( Trial(TargetWidths[w], TargetDistances[d]-TargetWidths[w]) )
        shuffleTrials()
        return true
    }

    private fun shuffleTrials() {
        if (trials != null)
            trials?.shuffle()
    }

    fun getCurrentWidth(): Int {
        val currentTrial = trials!![0]
        return currentTrial!!.width
    }

    fun getCurrentDistance(): Int {
        val currentTrial = trials!![0]
        return currentTrial!!.distance
    }

    fun startTrial(t: Long) {
        val currentTrial = trials!![0]
        currentTrial!!.trialStartTime = t
        Utilities.TrialCounter += 1
        Log.i(tag, "Log/ START TIME: $t")
    }

    fun endTrial(t: Long) {
        val currentTrial = trials!![0]
        currentTrial!!.trialEndTime = t
        Utilities.TrialEndCounter += 1
        Log.i(tag, "Log/ END TIME: $t")
//        trialsDone!!.add(Trial(currentTrial))
    }

    fun addSelectionTIme(t: Long) {
        val currentTrial = trials!![0]
        currentTrial!!.selectionTime.add(t)
    }

    fun addErrorTIme(t: Long) {
        val currentTrial = trials!![0]
        currentTrial!!.errorTime.add(t)
    }

    fun addPts(pt: Point) {
        val currentTrial = trials!![0]
        currentTrial!!.pts.add(pt)
    }

    fun addAcc(data: Sensor){
        val currentTrial = trials!![0]
        currentTrial!!.AccList?.add(data)
    }
}

class Trial {
    // trial data
    var width: Int
    var distance: Int

    // measures
    var trialStartTime: Long = 0    // starttime (processing)
    var trialEndTime: Long = 0      // endtime   (processing - usually shortly after touchup)

    var selectionTime: ArrayList<Long>
    var errorTime: ArrayList<Long>
    var pts: ArrayList<Point>       // the array of touched points

    var AccList: ArrayList<Sensor>? = null
    var GyrList: ArrayList<Sensor>? = null
    var MagList: ArrayList<Sensor>? = null

    constructor(w: Int, d: Int) {
        width = w
        distance = d
        trialStartTime = trialEndTime - 1
        selectionTime = ArrayList()
        errorTime = ArrayList()
        pts = ArrayList()
        AccList = ArrayList()
        GyrList = ArrayList()
        MagList = ArrayList()
    }
}

class Point(var rawSonar: Float, var filteredSonar: Float, var point: Float, var time: Long)

class Sensor(var x: Float, var y: Float, var z: Float, var t: Long)