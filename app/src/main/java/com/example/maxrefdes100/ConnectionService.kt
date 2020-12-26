package com.example.maxrefdes100

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.polidea.rxandroidble2.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.lifecycle.MutableLiveData


class ConnectionService : Service()  {

    var accelData = MutableLiveData<List<Int>>()
    var heartbeatData = MutableLiveData<Int>()
    var connectionData = MutableLiveData<String>()

    private lateinit var rxBleClient: RxBleClient
    private var connection: Disposable? = null
    private val binder = LocalBinder()
    private var connected: Boolean = false
    var mac = String()
        set(value) { field = value }

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
    }

    private fun processAccelerometer(data: ByteArray) {
        val accelX = (data[1].toInt() shl 8) + data[0].toInt()
        val accelY = (data[3].toInt() shl 8) + data[2].toInt()
        val accelZ = (data[5].toInt() shl 8) + data[4].toInt()

        val accumulated = sqrt(
         accelX.toFloat().pow(2)
          + accelY.toFloat().pow(2)
          + accelZ.toFloat().pow(2)
        )

        val accelDataList: List<Int> = listOf<Int>(accelX, accelY, accelZ)
        accelData.postValue(accelDataList)

        Log.i("ACCEL_X", accelX.toString())
        Log.i("ACCEL_Y", accelY.toString())
        Log.i("ACCEL_Z", accelZ.toString())
        Log.i("ACCUMULATED", accumulated.toString())
    }

    private fun processHeartbeat(data: ByteArray) {
        val hearthbeat = (data[1].toInt() shl 8) + data[0].toInt()

        heartbeatData.postValue(hearthbeat)

        Log.i("HEARTHBEAT", hearthbeat.toString())
    }

    private fun connectionSetup(bleDevice: RxBleDevice) {
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

    fun connect() {
        if (connected) {
            return
        }

        val bleDevice: RxBleDevice = rxBleClient.getBleDevice(mac)

        bleDevice.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            if (it == RxBleConnection.RxBleConnectionState.CONNECTED) {
                                Log.i("CONNECTION STATE", "CONNECTED")
                                connectionData.postValue("CONNECTED")
                                connected = true
                            } else if (it == RxBleConnection.RxBleConnectionState.DISCONNECTED) {
                                Log.i("CONNECTION STATE", "DISCONNECTED")
                                connectionData.postValue("DISCONNECTED")
                                connection?.dispose()
                                connected = false
                            }
                        },
                        {
                            Log.e("CONNECTION FAILURE", it.message.toString())
                        }
                )

        connectionSetup(bleDevice)
    }

    fun disconnect() {
        if (connected) {
            connection?.dispose()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("SSSSSSSSSSSSSSSSSSSSSSSSSSSSS", "SSSSSSSSSSS")
        connection?.dispose()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
}