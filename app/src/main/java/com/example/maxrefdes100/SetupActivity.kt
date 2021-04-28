package com.example.maxrefdes100

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer

class SetupActivity : AppCompatActivity() {

    private lateinit var connectionService: ConnectionService
    private var serviceBound: Boolean = false

    val movementObserver = Observer<Boolean> {
        val movementValue = findViewById<TextView>(R.id.movementValue)

        movementValue.text = if (it) "MOVING" else "IDLE"

        val color = if (it) "#BF0101" else "#077A42"
        movementValue.setTextColor(Color.parseColor(color))
    }

    val heartrateObserver = Observer<Int> {
        val heartrateValue = findViewById<TextView>(R.id.heartrateValue)

        heartrateValue.text = it.toString()

        val color = if (it == 0) "#BF0101" else "#FF757575"
        heartrateValue.setTextColor(Color.parseColor(color))
    }

    val connectionObserver = Observer<String> {
        val connectionValue = findViewById<TextView>(R.id.connectionValue)

        connectionValue.text = it

        val color = if (it == "CONNECTED") "#077A42" else "#BF0101"
        connectionValue.setTextColor(Color.parseColor(color))
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
        val host = findViewById<EditText>(R.id.editTextHost)

//        "03:00:00:51:3B:A5"
        if (mac.text.isEmpty() || host.text.isEmpty()) {
            Toast.makeText(applicationContext,"Fill in mac and host fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (serviceBound) {
            connectionService.mac = mac.text.toString()
            connectionService.host = host.text.toString()
        }
    }

    private fun onConnectClick() {
        if (connectionService.mac.isEmpty() || connectionService.host.isEmpty()) {
            Toast.makeText(applicationContext,"MAC and HOST need to be set", Toast.LENGTH_SHORT).show()
            return
        }

        connectionService.connect()
        connectionService.movementData.observe(this, movementObserver)
        connectionService.heartrateData.observe(this, heartrateObserver)
        connectionService.connectionData.observe(this, connectionObserver)
    }

    private fun onDisconnectClick() {
        connectionService.disconnect()
    }
}