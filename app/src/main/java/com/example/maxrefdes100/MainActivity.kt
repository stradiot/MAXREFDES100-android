package com.example.maxrefdes100

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val intent = Intent(this, ConnectionService::class.java)
        intent.putExtra("mac", "03:00:00:51:3B:A5")
        startService(intent)
    }
}