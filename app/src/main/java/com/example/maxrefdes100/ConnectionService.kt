package com.example.maxrefdes100

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.polidea.rxandroidble2.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt


class ConnectionService : Service()  {

    private lateinit var rxBleClient: RxBleClient
    private lateinit var bleDevice: RxBleDevice
    private lateinit var connection: Disposable
    private lateinit var mac: String

    private fun processAccelerometer(data: ByteArray) {
        val accelX = ((data[1].toInt() shl 8) + data[0].toInt()).toFloat()
        val accelY = ((data[3].toInt() shl 8) + data[2].toInt()).toFloat()
        val accelZ = ((data[5].toInt() shl 8) + data[4].toInt()).toFloat()

        val accumulated = sqrt(accelX.pow(2) + accelY.pow(2) + accelZ.pow(2))
        Log.i("ACCEL_X", accelX.toString())
        Log.i("ACCEL_Y", accelY.toString())
        Log.i("ACCEL_Z", accelZ.toString())
        Log.i("ACCUMULATED", accumulated.toString())
    }

    private fun processHeartbeat(data: ByteArray) {

    }


    private fun connectionSetup() {
        connection = bleDevice.establishConnection(false)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    it.setupNotification(UUID.fromString("e6c9da1a-8096-48bc-83a4-3fca383705af"))
                        .flatMap { it }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                                processAccelerometer(it)
                            },
                            {
                                Log.e("ACCELERATION NOTIFICATION SETUP FAILURE", it.message.toString())
                            }
                        )

                    it.setupNotification(UUID.fromString("621a00e3-b093-46bf-aadc-abe4c648c569"))
                        .flatMap { it }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                                processHeartbeat(it)
                            },
                            {
                                Log.e("HEARTBEAT NOTIFICATION SETUP FAILURE", it.message.toString())
                            }
                        )
                },
                {
                    Log.e("CONNECTION FAILURE", it.message.toString())
                }
            )
    }

    override fun onCreate() {
        super.onCreate()

        rxBleClient = RxBleClient.create(this)
        RxBleClient.updateLogOptions(
            LogOptions.Builder()
                .setLogLevel(LogConstants.INFO)
                .setMacAddressLogSetting(LogConstants.MAC_ADDRESS_FULL)
                .setUuidsLogSetting(LogConstants.UUIDS_FULL)
                .setShouldLogAttributeValues(true)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mac = intent?.extras?.getString("mac")!!

        bleDevice = rxBleClient.getBleDevice(mac)

        bleDevice.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    if (it == RxBleConnection.RxBleConnectionState.CONNECTED) {
                        Log.i("CONNECTION STATE", "CONNECTED")
                    } else if (it == RxBleConnection.RxBleConnectionState.DISCONNECTED) {
                        Log.i("CONNECTION STATE", "DISCONNECTED")
                        connection.dispose()
                        connectionSetup()
                    }
                },
                {
                    Log.e("CONNECTION FAILURE", it.message.toString())
                }
            )

        connectionSetup()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.dispose()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}