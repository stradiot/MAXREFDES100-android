package com.example.maxrefdes100

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer

class SetupActivity : AppCompatActivity() {

    private lateinit var connectionService: ConnectionService
    private var serviceBound: Boolean = false

    val accelObserver = Observer<List<Int>> {
        val accelXValue = findViewById<TextView>(R.id.accelXValue)
        val accelYValue = findViewById<TextView>(R.id.accelYValue)
        val accelZValue = findViewById<TextView>(R.id.accelZValue)

        accelXValue.text = it[0].toString()
        accelYValue.text = it[1].toString()
        accelZValue.text = it[2].toString()
    }

    val heartbeatObserver = Observer<Int> {
        val heartbeatValue = findViewById<TextView>(R.id.heartbeatValue)

        heartbeatValue.text = it.toString()
    }

    val connectionObserver = Observer<String> {
        val connectionValue = findViewById<TextView>(R.id.connectionValue)

        connectionValue.text = it
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ConnectionService.LocalBinder
            connectionService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_setup)

        Intent(this, ConnectionService::class.java).also {
            startService(it)
        }

        val applyButton = findViewById<Button>(R.id.buttonApply)
        val connectButton = findViewById<Button>(R.id.buttonConnect)
        val disconnectButton = findViewById<Button>(R.id.buttonDisconnect)

        applyButton.setOnClickListener { onApplyClick() }
        connectButton.setOnClickListener { onConnectClick() }
        disconnectButton.setOnClickListener { onDisconnectClick() }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, ConnectionService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        serviceBound = false
    }

    private fun onApplyClick() {
        val mac = findViewById<EditText>(R.id.editTextMAC)

//        "03:00:00:51:3B:A5"
        if (serviceBound) {
            connectionService.mac = mac.text.toString()
        }
    }

    private fun onConnectClick() {
        connectionService.connect()
        connectionService.accelData.observe(this, accelObserver)
        connectionService.heartbeatData.observe(this, heartbeatObserver)
        connectionService.connectionData.observe(this, connectionObserver)
    }

    private fun onDisconnectClick() {
        connectionService.disconnect()
    }
}