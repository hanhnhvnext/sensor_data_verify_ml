package com.mlexample.sensorparamscollector

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var startBtn : Button
    lateinit var userId : EditText
    lateinit var activityType : Spinner
    lateinit var cyclingConfidence : TextView
    lateinit var caseId: EditText
    val PERMISSION_REQUEST_CODE = 1343
    companion object {
        var service: SensorService? = null
    }
    lateinit var preferences: SharedPreferences
    var running : Boolean = false
    var outputFile = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        running = preferences.getBoolean("service_running", false)
        startBtn = start_btn
        userId = user_id
        cyclingConfidence = cycling_confidence
        caseId = case_id

        userId.setText(preferences.getString("user_id", ""))
        caseId.setText(preferences.getString("case_id",""))
        activityType = activity_type
        activityType.setSelection(getIndex(activityType, preferences.getString("activity_type","")))
        startBtn.setOnClickListener { startSensorService() }
        if(running){
            startBtn.isClickable = false
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            requestPermission()

        LocalBroadcastManager.getInstance(this).registerReceiver(cyclingReceiver, IntentFilter("cyclingIntent"))
        LocalBroadcastManager.getInstance(this).registerReceiver(enableButtonReceiver, IntentFilter("enableButton"))

        val outputName = "sensor_data_detect.txt"

        outputFile = File(Environment.getExternalStorageDirectory(), outputName).absolutePath
    }

    private val cyclingReceiver  = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val cycling = intent!!.getFloatExtra("Cycling", 0.0f)
            val noCycling = intent.getFloatExtra("No_Cycling",0.0f)

            if(cycling > 0.95) {
                cyclingConfidence.text = "Cycling " + ", Confidence : " + cycling + "\n"
            }else {
                cyclingConfidence.text = "No_Cycling " +", Confidence : " + noCycling + "\n"
            }

            val text = cyclingConfidence.text.toString()

            saveActivity(text)
        }
    }

    private val enableButtonReceiver = object  : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            startBtn.isClickable = true
            preferences.edit().putBoolean("service_running", false).apply()
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

    private fun getIndex(spinner: Spinner, value: String): Int{
        for (i in 0..spinner.count - 1) {
            if(spinner.getItemAtPosition(i).toString().equals(value))
                return i
        }
        return 0
    }

    private fun startSensorService(){
        if(!preferences.getBoolean("service_running", false)) {
            val intent = Intent(this, SensorService::class.java)
            intent.setAction("start_detect_activity")
            startService(intent)
            bindService(Intent(this, SensorService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
            startBtn.isClickable = false
            preferences.edit().putBoolean("service_running", true).apply()
        }
    }

    val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
            service = null
            unbindService(this)
        }

        override fun onServiceConnected(p0: ComponentName?, binder: IBinder?) {
            service = (binder as SensorService.LocalBinder).getService()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestPermission(){
        if(checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            this.requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA),
                    PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permission: Array<String>, grantResult: IntArray) {
        super.onRequestPermissionsResult(requestCode, permission, grantResult)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permission.indices) {
                if (permission[i] == android.Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    if (grantResult[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if(service != null) {
            try {
                unbindService(serviceConnection)
            } catch (e : IllegalArgumentException){

            }
        }
        super.onDestroy()
    }

}
