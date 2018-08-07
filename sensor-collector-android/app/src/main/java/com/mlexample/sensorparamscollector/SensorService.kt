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
import android.R.attr.data
import android.app.Notification
import android.app.PendingIntent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.view.View
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter


class SensorService : Service(), SensorEventListener {

    val outputPath = "Sensor_Collector"
    val outputRoot = File(Environment.getExternalStorageDirectory(), outputPath)
    var fileFormat = SimpleDateFormat("'sensor_'yyyy-MM-dd-HH-mm-ss'.txt'", Locale.US)
    var outputFile = ""

    val sensorManager: SensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    var running = false
    lateinit var preferences: SharedPreferences
    lateinit var userId : String
    lateinit var activityType: String

    override fun onCreate() {
        super.onCreate()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL)
//        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        userId = preferences.getString("user_id", "")
        activityType = preferences.getString("activity_type", "")
        running = true
        if(!outputRoot.exists() && !outputRoot.mkdirs()){
            return
        }

        val outputName = "sensor_data.txt"
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
        if(sensor?.type == Sensor.TYPE_ACCELEROMETER){
            accLemeter = true
//            saveData(sensorEvent)
            accX = sensorEvent!!.values[0]
            accY = sensorEvent!!.values[1]
            accZ = sensorEvent!!.values[2]

        } else if(sensor?.type == Sensor.TYPE_GYROSCOPE){
            gyroScope = true
            gyroX = sensorEvent!!.values[0]
            gyroY = sensorEvent!!.values[1]
            gyroZ = sensorEvent!!.values[2]
        }
        if(accLemeter && gyroScope) {
            saveData(accX, accY, accZ, gyroX, gyroY, gyroZ)
            accLemeter = false
            gyroScope = false
        }


    }

    private fun saveData(sensorEvent: SensorEvent?){
        val x = sensorEvent!!.values[0]
        val y = sensorEvent!!.values[1]
        val z = sensorEvent!!.values[2]
        val currentTime = System.currentTimeMillis()
        val data = userId + "," + activityType + "," + currentTime.toString() + "," + x + "," + y + "," + z + "\n"

        try {
            val outputStreamWriter = OutputStreamWriter(FileOutputStream(outputFile, true))
            outputStreamWriter.write(data)
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: " + e.toString())
        }

    }

    private fun saveData(accX:Float,accY:Float,accZ:Float,gyroX:Float,gyroY:Float,gyroZ:Float){
//        val x = sensorEvent!!.values[0]
//        val y = sensorEvent!!.values[1]
//        val z = sensorEvent!!.values[2]
        val currentTime = System.currentTimeMillis()
        val data = userId + "," + activityType + "," + currentTime.toString() + "," + accX + "," + accY + "," + accZ +
                ","+gyroX+","+ gyroY+","+gyroZ + "\n"
        Log.e("data",data);
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