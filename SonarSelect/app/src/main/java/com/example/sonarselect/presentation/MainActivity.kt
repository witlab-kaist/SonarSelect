/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

package com.example.watch2.presentation
import DataPlayer
import DataPlayer.generateCombTone
import Utilities.BlockCounter
import Utilities.TrialCounter
import Utilities.TrialEndCounter
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import com.example.watch2.R
import com.example.watch2.databinding.ActivityMainBinding

var frequency = 100.0 // Hz
var mincutoff = 0.5 // Hz
var beta = 0.1
var dcutoff = 1.0 // this one should be ok

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private var nBinding: ActivityMainBinding? = null


    companion object {
        var combWave: DoubleArray = generateCombTone(48000, 17500, 350, 8, 1)
        var player: DataPlayer = DataPlayer(combWave, 15)
    }

    private val binding get() = nBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        nBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        combWave = generateCombTone(48000, 17500, 350, 8, 1)
        player = DataPlayer(combWave, 15)
        player.play()

        BlockCounter = 0;
        TrialCounter = 0;
        TrialEndCounter = 0;
        val mode: Button = findViewById(R.id.btn_start)

        mode.setOnClickListener {
            player.stop()
            val intent = Intent(this@MainActivity, BlockActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}