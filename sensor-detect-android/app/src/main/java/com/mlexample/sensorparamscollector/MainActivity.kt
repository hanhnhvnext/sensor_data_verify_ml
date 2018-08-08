package com.mlexample.sensorparamscollector

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.content.LocalBroadcastManager
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import java.security.Permissions

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
            startBtn.setText("Stop Collecting")
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            requestPermission()

        LocalBroadcastManager.getInstance(this).registerReceiver(cyclingReceiver, IntentFilter("cyclingIntent"))
    }

    private val cyclingReceiver  = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val Cycling = intent!!.getFloatExtra("Cycling", 0.0f)
            val Downstairs        = intent!!.getFloatExtra("Downstairs",0.0f)
            val Jogging        = intent!!.getFloatExtra("Jogging",0.0f)
            val Sitting        = intent!!.getFloatExtra("Sitting",0.0f)
            val Standing        = intent!!.getFloatExtra("Standing",0.0f)
            val Upstairs        = intent!!.getFloatExtra("Upstairs",0.0f)
            val Walking        = intent!!.getFloatExtra("Walking",0.0f)


            if(Cycling > 0.9) {
                cyclingConfidence.text = "Cycling:  Yes. ";
            }else {
                cyclingConfidence.text = "Cycling:  No. " ;

            }
            service!!.saveActivity(cyclingConfidence.text.toString() + ", Confidence: "+ Cycling+"\n" )

//            +
//             "Downstairs Confidence:"+ Downstairs.toString()+"\n"+
//                    "Jogging Confidence:"+ Jogging.toString()+"\n"+
//                    "Sitting Confidence:"+ Sitting.toString()+"\n"+
//                    "Standing Confidence"+ Standing.toString()+"\n"+
//                    "Upstair Confidence:"+ Upstairs.toString()+"\n"+
//                    "Walking Confindence:"+ Walking.toString()+"\n";
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
            if (userId.text.isEmpty()) {
                Toast.makeText(this, "Please input user id first", Toast.LENGTH_SHORT).show()
            }else if(caseId.text.isEmpty()){
                Toast.makeText(this, "Please input case id", Toast.LENGTH_SHORT).show()

            } else {
                preferences.edit().putString("user_id", userId.text.toString()).apply()
                preferences.edit().putString("case_id",caseId.text.toString()).apply()
                preferences.edit().putString("activity_type", activityType.selectedItem.toString()).apply()
                startService(Intent(this, SensorService::class.java))
                bindService(Intent(this, SensorService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
                startBtn.setText("Stop Collecting")
                preferences.edit().putBoolean("service_running", true).apply()
            }
        } else {
            preferences.edit().putBoolean("service_running", false).apply()
            service?.stopForeground(true)
            if(service != null) {
                try {
                    unbindService(serviceConnection)
                } catch (e : IllegalArgumentException){

                }
            }
            stopService(Intent(this, SensorService::class.java))
            startBtn.setText("Start Collecting")
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
