package com.example.maxrefdes100

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.polidea.rxandroidble2.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import com.jakewharton.rx.ReplayingShare
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt
import org.json.JSONObject
import androidx.lifecycle.MutableLiveData
import com.android.volley.toolbox.Volley
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.Response

class ConnectionService : Service()  {

    private lateinit var bleDevice: RxBleDevice

    var movementData = MutableLiveData<Boolean>()
    var heartrateData = MutableLiveData<Int>()
    var connectionData = MutableLiveData<String>()

    private lateinit var queue: RequestQueue

    private lateinit var rxBleClient: RxBleClient
    private lateinit var connectionObservable: Observable<RxBleConnection>
    private val connectionDisposable = CompositeDisposable()
    private val binder = LocalBinder()
    private var connected: Boolean = false
    var mac = String()
        set(value) { field = value }
    var host = String()
        set(value) { field = value }

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
    }

    private var hrLastVal: Int = 0x00;

    private fun sendHTTPData() {
        val jsonobj = JSONObject()
        val url = "http://" + host + "/value_update"

        jsonobj.put("activity", movementData.value)
        jsonobj.put("heartrate", heartrateData.value)

        val request = JsonObjectRequest(Request.Method.POST, url, jsonobj,
            Response.Listener {
                response ->
                    Log.i("HTTP RESPONSE", response.toString())
            }, Response.ErrorListener {
                error ->
                    Log.e("HTTP RESPONSE", error.toString())
            }
        )
        queue.add(request)
    }

    private fun processMovement(data: ByteArray) {
        val movement = if (data[0].toInt() == 0) false else true

        movementData.postValue(movement)

        Log.i("MOVEMENT", movement.toString())

        val rpcVal = if (movement == false) 0x01.toByte() else 0x00.toByte()

        Log.i("RPC value", rpcVal.toString())

        if (rpcVal.toInt() == hrLastVal) return
        if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
            connectionObservable
                .firstOrError()
                .flatMap {
                    it.writeCharacteristic(
                        UUID.fromString("36e55e37-6b5b-420b-9107-0d34a0e8675a"),
                        byteArrayOf(rpcVal)
                    )
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { Log.i("RPC write SUCCESS", "A"); hrLastVal = rpcVal.toInt() },
                    { Log.e("RPC write FAILURE", it.toString()) }
                )
                .let { connectionDisposable.add(it) }
        }
    }

    private fun processHeartrate(data: ByteArray) {
        val heartrate = data[0].toInt()

        heartrateData.postValue(heartrate)

        Log.i("HEARTRATE", heartrate.toString())

        sendHTTPData()
    }

    private fun prepareConnectionObservable(): Observable<RxBleConnection> =
        bleDevice
            .establishConnection(false)
            .takeUntil(PublishSubject.create<Unit>())
            .compose(ReplayingShare.instance())

    private fun connectionSetup(bleDevice: RxBleDevice) {
        connectionObservable = prepareConnectionObservable()

        connectionObservable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    it.setupNotification(UUID.fromString("e6c9da1a-8096-48bc-83a4-3fca383705af"))
                        .flatMap { it }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                                processMovement(it)
                            },
                            {
                                Log.e("MOVEMENT NOTIFICATION SETUP FAILURE", it.message.toString())
                            }
                        )

                    it.setupNotification(UUID.fromString("621a00e3-b093-46bf-aadc-abe4c648c569"))
                        .flatMap { it }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                                processHeartrate(it)
                            },
                            {
                                Log.e("HEARTRATE NOTIFICATION SETUP FAILURE", it.message.toString())
                            }
                        )
                },
                {
                    Log.e("CONNECTION FAILURE", it.message.toString())
                }
            )
            .let { connectionDisposable.add(it) }
    }

    override fun onCreate() {
        super.onCreate()

        queue = Volley.newRequestQueue(this)

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

        bleDevice = rxBleClient.getBleDevice(mac)

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
                                connectionDisposable?.dispose()
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
            connectionDisposable?.dispose()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionDisposable?.dispose()
        //queue.cancelAll()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
}