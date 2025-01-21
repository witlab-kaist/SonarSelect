/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */

package com.example.watch2.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.watch2.R
import com.example.watch2.databinding.ActivityStartBinding


class StartActivity : AppCompatActivity() {

    private var nBinding: ActivityStartBinding? = null

    private val binding get() = nBinding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nBinding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tap: Button = findViewById(R.id.sub_button)
        tap.setOnClickListener{
            val intent = Intent(this, LoadingActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}