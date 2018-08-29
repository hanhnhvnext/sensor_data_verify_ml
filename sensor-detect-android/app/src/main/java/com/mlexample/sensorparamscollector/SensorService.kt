package com.mlexample.sensorparamscollector

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
import java.io.File
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
import android.view.View
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.lang.reflect.Array
import kotlin.collections.ArrayList


class SensorService : Service(), SensorEventListener {
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
    private val OUTPUT_SIZE = 7

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
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        userId = preferences.getString("user_id", "")
        caseId = preferences.getString("case_id","")
        activityType = preferences.getString("activity_type", "")
        running = true
        if(!outputRoot.exists() && !outputRoot.mkdirs()){
            return
        }

//        val outputName = fileFormat.format(Date())
        val outputName = "sensor_data_detect.txt"

        outputFile = File(outputRoot, outputName).absolutePath
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }


    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }
    var accX = 0.0f
    var accY= 0.0f
    var accZ= 0.0f
    var gyroX= 0.0f
    var gyroY= 0.0f
    var gyroZ= 0.0f
    var accLemeter = false
    var gyroScope = false

    override fun onSensorChanged(sensorEvent: SensorEvent?) {

//        val gyro: Sensor
        val sensor = sensorEvent?.sensor
//        Log.e("sensor", "type".plus(sensor?.type ).plus("---").plus(System.currentTimeMillis()));
        if(sensor?.type == Sensor.TYPE_ACCELEROMETER){
            accLemeter = true
//            saveData(sensorEvent)
            accX = sensorEvent!!.values[0]
            accY = sensorEvent!!.values[1]
            accZ = sensorEvent!!.values[2]
            saveData(accX, accY, accZ)
            processData(sensorEvent)

        } else if(sensor?.type == Sensor.TYPE_GYROSCOPE){
            gyroScope = true
            gyroX = sensorEvent!!.values[0]
            gyroY = sensorEvent!!.values[1]
            gyroZ = sensorEvent!!.values[2]
        }
        if(accLemeter && gyroScope) {
            accLemeter = false
            gyroScope = false
        }

    }

//    var array = Array(200, {FloatArray(3)})
    var array = Array(200, {FloatArray(3)})
    var str = ""
    var i = 0

    private fun processData(sensorEvent: SensorEvent?){
        val x = sensorEvent!!.values[0]
        val y = sensorEvent!!.values[1]
        val z = sensorEvent!!.values[2]

        xArray.add(x)
        yArray.add(y)
        zArray.add(z)

//        var item = FloatArray(3)
//        item[0] = x
//        item[1] = y
//        item[2] = z
        if(i < 200) {
//            array[i] = arrayOf(x, y, z);
            array[i] = floatArrayOf(x,y,z)

        }

        Log.e("---",Arrays.toString(floatArrayOf(x,y,z)))
        i++
        str = str +","+ Arrays.toString(floatArrayOf(x,y,z))




        if(xArray.size == N_SAMPLES && yArray.size == N_SAMPLES && zArray.size == N_SAMPLES) {
//            saveData(str)


            val data = arrayListOf<Float>()
            data.addAll(xArray)
            data.addAll(yArray)
            data.addAll(zArray)

            val result = FloatArray(OUTPUT_SIZE)
            Log.e("start",Arrays.toString(toFloatArray(data)))
//            saveData(Arrays.toString(toFloatArray(data)))
            classifier.feed(INPUT_NODE, toFloatArray(data), 1, 200, 3)
            classifier.run(OUTPUT_NODES)
            classifier.fetch(OUTPUT_NODE, result)
            sendResultToActivity(result)
            Log.e("result:",Arrays.toString(result));

            xArray.clear()
            yArray.clear()
            zArray.clear()
            str = ""
        }
    }

    private fun sendResultToActivity(result: FloatArray) {
        Log.e("end",System.currentTimeMillis().toString())

//        Log.e("cycling confident", confidence.toString());
        val intent = Intent("cyclingIntent")
        intent.putExtra("Cycling", result[0])
        intent.putExtra("Downstairs",result[1])
        intent.putExtra("Jogging",result[2])
        intent.putExtra("Sitting",result[3])
        intent.putExtra("Standing",result[4])
        intent.putExtra("Upstairs",result[5])
        intent.putExtra("Walking",result[6])
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
    public fun saveActivity(data: String){
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
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))
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