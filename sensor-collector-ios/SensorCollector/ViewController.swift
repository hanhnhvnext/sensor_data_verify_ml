//
//  ViewController.swift
//  SensorCollector
//
//  Created by hanh nguyen on 7/25/18.
//  Copyright © 2018 hanh nguyen. All rights reserved.
//

import UIKit
import CoreMotion
import Dispatch
import ProgressHUD
import CoreLocation

class ViewController: UIViewController, UIPickerViewDelegate, UIPickerViewDataSource, UITextFieldDelegate {

    let motionManager = CMMotionManager()
    var timer: Timer!
    
    private lazy var locationManager: CLLocationManager = {
        let manager = CLLocationManager()
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.delegate = self
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true
        manager.requestAlwaysAuthorization()
        return manager
    }()
    private let activityManager = CMMotionActivityManager()
    private let pedometer = CMPedometer()
    private var shouldStartUpdating: Bool = false
    private var startDate: Date? = nil
    
    private var pickerData = [String]()
    var userDefault = UserDefaults.standard
    private var isStart = false
    
    @IBOutlet weak var userID: UITextField!
    @IBOutlet weak var picker: UIPickerView!
    
    @IBAction func startCollect(_ sender: UIButton) {
        if !isStart {
            if userID.text == "" {
                ProgressHUD.showError("Please input User ID first")
            } else {
                locationManager.startUpdatingLocation()
                userDefault.set(userID.text, forKey: "userID")
                timer = Timer.scheduledTimer(timeInterval: 1, target: self, selector: #selector(ViewController.update), userInfo: nil, repeats: true)
                isStart = true
                sender.setTitle("Stop", for: .normal)
            }
        } else {
            locationManager.stopUpdatingLocation()
            motionManager.stopAccelerometerUpdates()
            motionManager.stopGyroUpdates()
            motionManager.stopDeviceMotionUpdates()
            if let timer = timer {
                timer.invalidate()
            }
            isStart = false
            sender.setTitle("Start", for: .normal)
        }
    }
    
    private func getPickerIndex() -> Int {
        for item in pickerData {
            if item == userDefault.string(forKey: "selectedActivity") {
                return pickerData.index(of: item)!
            }
        }
        return 0
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        pickerData = ["Walking", "Running", "Cycling"]
        userID.text = userDefault.string(forKey: "userID")
        
        self.picker.delegate = self
        self.picker.dataSource = self
        
        self.userID.delegate = self
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        self.view.endEditing(true)
    }
    
    func numberOfComponents(in pickerView: UIPickerView) -> Int {
        return 1
    }
    
    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        return pickerData.count
    }
    
    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        return pickerData[row]
    }
    
    func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
        userDefault.set(pickerData[row], forKey: "selectedActivity")
    }
    
    @objc func update() {
        motionManager.startAccelerometerUpdates()
        motionManager.startGyroUpdates()
        //        motionManager.startMagnetometerUpdates()
        motionManager.startDeviceMotionUpdates()
        
        let file = "file.txt" //this is the file. we will write to and read from it
        
        if let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            let fileURL = dir.appendingPathComponent(file)
            
            let userId = self.userDefault.string(forKey: "userID")!
            let activityType = pickerData[picker.selectedRow(inComponent: 0)]
            let timeline = String(Date().millisecondsSince1970)
            
            var dataRow = "\(userId),\(activityType),\(timeline),"
        
            if let accelerometerData = motionManager.accelerometerData {
                dataRow.append("\(accelerometerData.acceleration.x),\(accelerometerData.acceleration.y) \(accelerometerData.acceleration.z),")
            }
            
            if let gyroData = motionManager.gyroData {
                dataRow.append("\(gyroData.rotationRate.x),\(gyroData.rotationRate.y),\(gyroData.rotationRate.z)")
            }
            
//            if let magnetometerData = motionManager.magnetometerData {
//                dataRow.append("\(magnetometerData.magneticField.x),\(magnetometerData.magneticField.y),\(magnetometerData.magneticField.z)")
//            }
           
            print(dataRow)
            writeFile(text: dataRow, fileURL: fileURL)
        }
    }
    
    func writeFile(text:String,fileURL:URL)  {
        
        //test
        do {
            let dir: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).last! as URL
            let url = dir.appendingPathComponent("sensor_data.txt")
            try text.appendLineToURL(fileURL: url as URL)
            let result = try String(contentsOf: url as URL, encoding: String.Encoding.utf8)
        }
        catch {
            print("Could not write to file")
        }
    }
    
    
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        
    }
    
    @objc private func didTapStartButton() {
        shouldStartUpdating = !shouldStartUpdating
        
    }
}


extension Date {
    var millisecondsSince1970:Int64 {
        return Int64((self.timeIntervalSince1970 * 1000.0).rounded())
    }
    
    init(milliseconds:Int) {
        self = Date(timeIntervalSince1970: TimeInterval(milliseconds / 1000))
    }
}

// MARK: - CLLocationManagerDelegate
extension ViewController: CLLocationManagerDelegate {
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let mostRecentLocation = locations.last else {
            return
        }
        
        if UIApplication.shared.applicationState == .active {
           
        } else {
            print("App is backgrounded. New location is %@", mostRecentLocation)
        }
    }
    
}
