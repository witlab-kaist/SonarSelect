/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

package com.example.watch2.presentation

import Utilities
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.watch2.databinding.ActivitySavingBinding


class SavingActivity : AppCompatActivity() {

    private var nBinding: ActivitySavingBinding? = null
    private val binding get() = nBinding!!
    private val TAG = "Saving Activity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nBinding = ActivitySavingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.w(TAG, "Log/ Left Trials: #${Utilities.NofTrials-Utilities.TrialCounter}, done Trials: #${Utilities.TrialCounter}, total done: #${Utilities.TrialEndCounter}")

        Handler().postDelayed(Runnable {
            if (Utilities.NofTrials-Utilities.TrialCounter == 0){
                Utilities.BlockCounter += 1
                val i = Intent(this@SavingActivity, BlockActivity::class.java)
                startActivity(i)

                finish()
            }
            else {
                BlockActivity.trialSet.trials?.removeAt(0)
                val i = Intent(this@SavingActivity, StartActivity::class.java)
                startActivity(i)

                finish()
            }
        }, 500) // 1초

    }
}