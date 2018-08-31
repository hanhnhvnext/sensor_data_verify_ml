package com.mlexample.sensorparamscollector

import android.app.IntentService
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import android.app.Notification
import android.app.PendingIntent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.support.v4.content.LocalBroadcastManager
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.*
import java.lang.reflect.Array
import kotlin.collections.ArrayList


class SensorService: IntentService("MyService"){
//    val graphFilePath = "file:///android_asset/frozen_model.pb"
    val graphFilePath = "file:///android_asset/final_unity_har_20180823.pb"

    val N_SAMPLES = 200
    val outputPath = "Sensor_Collector"
    val outputRoot = File(Environment.getExternalStorageDirectory(), outputPath)
    var fileFormat = SimpleDateFormat("'sensor_'yyyy-MM-dd-HH-mm-ss'.txt'", Locale.US)
    var outputFile = ""
    var activityFile = ""

    var xArray = arrayListOf<Float>()
    var yArray = arrayListOf<Float>()
    var zArray = arrayListOf<Float>()

    private val INPUT_NODE = "input"
    private val OUTPUT_NODES = arrayOf("y_")
    private val OUTPUT_NODE = "y_"
    private val INPUT_SIZE = longArrayOf(1, 200, 3)
    private val OUTPUT_SIZE = 2

    val sensorManager: SensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    val classifier : TensorFlowInferenceInterface by lazy {
        TensorFlowInferenceInterface(assets, graphFilePath)
    }

    var running = false
    lateinit var preferences: SharedPreferences
    lateinit var userId : String
    lateinit var caseId: String
    lateinit var activityType: String

    override fun onCreate() {
        super.onCreate()
        running = true
        if(!outputRoot.exists() && !outputRoot.mkdirs()){
            return
        }

        val outputName = "sensor_data_detect.txt"

        outputFile = File(outputRoot, outputName).absolutePath
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        if(intent != null){
            val action = intent.action
            if(action.equals("start_detect_activity")){
                processData()
            }
        }
        startForeground()
    }


    private fun processData(){
        val bufferReader = File(Environment.getExternalStorageDirectory(), "data_set_raw.txt").bufferedReader()
        var line  : String? = null
        while ({ line = bufferReader.readLine(); line }() != null) {
            val words = line!!.split(',')
            val x = words[3].toFloat()
            val y = words[4].toFloat()
            val z = words[5].toFloat()

            xArray.add(x)
            yArray.add(y)
            zArray.add(z)

            if(xArray.size == N_SAMPLES && yArray.size == N_SAMPLES && zArray.size == N_SAMPLES) {

                val data = arrayListOf<Float>()
                data.addAll(xArray)
                data.addAll(yArray)
                data.addAll(zArray)

                val result = FloatArray(OUTPUT_SIZE)
                Log.e("start",Arrays.toString(toFloatArray(data)))
                classifier.feed(INPUT_NODE, toFloatArray(data), 1, 200, 3)
                classifier.run(OUTPUT_NODES)
                classifier.fetch(OUTPUT_NODE, result)
                sendResultToActivity(result)
                Log.e("result:",Arrays.toString(result));

                xArray.clear()
                yArray.clear()
                zArray.clear()
            }
        }
        Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show()
        enableButton()
    }

    private fun sendResultToActivity(result: FloatArray) {
        Log.e("end",System.currentTimeMillis().toString())

//        Log.e("cycling confident", confidence.toString());
        val intent = Intent("cyclingIntent")
        intent.putExtra("Cycling", result[0])
        intent.putExtra("No_Cycling",result[1])

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun enableButton(){
        val intent = Intent("enableButton")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun toFloatArray(dataList: ArrayList<Float>) : FloatArray{
        var i = 0
        var array = FloatArray(dataList.size)

        for (item in dataList){
            array[i++] = item?:Float.NaN
        }
        return array
    }

    private fun saveData(accX:Float,accY:Float,accZ:Float){
        val currentTime = System.currentTimeMillis()
        val data = userId + "," + activityType + "," + currentTime.toString() + "," + accX + "," + accY + "," + accZ +","+caseId+"\n"
        try {
            val outputStreamWriter = OutputStreamWriter(FileOutputStream(outputFile, true))
            outputStreamWriter.write(data)
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: " + e.toString())
        }

    }

    private fun saveData(data:String){
        try {
            val outputStreamWriter = OutputStreamWriter(FileOutputStream(outputFile, true))
            outputStreamWriter.write(data)
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: " + e.toString())
        }

    }
    fun saveActivity(data: String){
        try {
            val outputStreamWriter = OutputStreamWriter(FileOutputStream(outputFile, true))
            outputStreamWriter.write(data)
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: " + e.toString())
        }
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder(){
        fun getService() : SensorService{
            return this@SensorService
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
    }

    fun startForeground() {
        var notification = Notification()
        val intent = Intent(this, MainActivity::class.java)
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(intent)
        val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, "channel")
        notification = builder.setContentIntent(resultPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),
                        R.mipmap.ic_launcher)).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT != Build.VERSION_CODES.N
                && Build.VERSION.SDK_INT != Build.VERSION_CODES.N_MR1) {
            val smallIconViewId = this.getResources().getIdentifier("right_icon", "id", android.R::class.java.`package`.name)
            if (smallIconViewId != 0) {
                if (notification.contentView != null)
                    notification.contentView.setViewVisibility(smallIconViewId, View.INVISIBLE)

                if (notification.headsUpContentView != null)
                    notification.headsUpContentView.setViewVisibility(smallIconViewId, View.INVISIBLE)

                if (notification.bigContentView != null)
                    notification.bigContentView.setViewVisibility(smallIconViewId, View.INVISIBLE)
            }
        }

        notification.flags = Notification.FLAG_NO_CLEAR
        this.startForeground(12345, notification)
    }
}