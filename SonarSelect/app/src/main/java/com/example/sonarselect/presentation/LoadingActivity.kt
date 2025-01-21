/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

package com.example.watch2.presentation

import DataRecorder.dataRecorder
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.example.watch2.databinding.ActivityLoadingBinding


class LoadingActivity : AppCompatActivity() {

    private var nBinding: ActivityLoadingBinding? = null
    private val binding get() = nBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nBinding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        playDataRepeat()
        dataRecorder.startRecordingAudio(25*60)

        Handler().postDelayed(Runnable {
            val i = Intent(this@LoadingActivity,GameActivity::class.java)
            startActivity(i)
            finish()
        }, 500) // 0.5sec
    }

    fun playDataRepeat() {
        MainActivity.player.play()
    }
}