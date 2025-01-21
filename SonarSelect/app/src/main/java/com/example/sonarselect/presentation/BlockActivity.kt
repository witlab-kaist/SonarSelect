/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

package com.example.watch2.presentation
import Utilities
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.watch2.R
import com.example.watch2.databinding.ActivityBlockBinding

class BlockActivity : AppCompatActivity() {
    private val TAG = "BlockActivity"
    private var nBinding: ActivityBlockBinding? = null
    // 매번 null 체크를 할 필요 없이 편의성을 위해 바인딩 변수 재 선언
    private val binding get() = nBinding!!
    companion object {
        var trialSet = Trials()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nBinding = ActivityBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mode: Button = findViewById(R.id.main_button)

        when (Utilities.SUB_ID.toInt()%6) {
            0 -> Utilities.CurrentSelectionMethod = arrayOf(0,1,2,3)[(Utilities.BlockCounter / Utilities.NofBlocksReps)]
            1 -> Utilities.CurrentSelectionMethod = arrayOf(0,2,1,3)[(Utilities.BlockCounter / Utilities.NofBlocksReps)]
            2 -> Utilities.CurrentSelectionMethod = arrayOf(1,0,2,3)[(Utilities.BlockCounter / Utilities.NofBlocksReps)]
            3 -> Utilities.CurrentSelectionMethod = arrayOf(1,2,0,3)[(Utilities.BlockCounter / Utilities.NofBlocksReps)]
            4 -> Utilities.CurrentSelectionMethod = arrayOf(2,0,1,3)[(Utilities.BlockCounter / Utilities.NofBlocksReps)]
            5 -> Utilities.CurrentSelectionMethod = arrayOf(2,1,0,3)[(Utilities.BlockCounter / Utilities.NofBlocksReps)]
        }

        when (Utilities.CurrentSelectionMethod) {
            0 -> mode.text = "Crossing \n\n Block ${Utilities.BlockCounter +1} Start"
            1 -> mode.text = "Dwelling \n\n Block ${Utilities.BlockCounter +1} Start"
            2 -> mode.text = "Pinching \n\n Block ${Utilities.BlockCounter +1} Start"
            3 -> mode.text = "Block Finished \n\n Thank you 😊"
        }

        mode.setOnClickListener {
            Log.w(TAG, "Log/ LeftBlocks: " + (Utilities.NofBlocks - Utilities.BlockCounter).toString())

            if (Utilities.BlockCounter < Utilities.NofBlocks) {
                Utilities.TrialCounter = 0
                if (!trialSet.initTrialBlock(
                        Utilities.TargetWidth,
                        Utilities.TargetDistance,
                        Utilities.TargetReps
                    )
                ) {
                    Log.w(TAG, "Log/ No more trials")
                } else {
                    Log.w(TAG, "Log/ Trials are generated: #" + trialSet.trials!!.size )
                    val intent = Intent(this@BlockActivity, StartActivity::class.java)
                    startActivity(intent)

                    // 현재 액티비티 닫기
                    finish()
                }
            }
        }
    }
}