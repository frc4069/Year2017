package org.usfirst.frc.team4069.robot;

import edu.wpi.first.wpilibj.SampleRobot;
import edu.wpi.first.wpilibj.RobotDrive;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

import edu.wpi.first.wpilibj.vision.*;


public class Robot extends SampleRobot {
	RobotDrive myRobot = new RobotDrive(0, 1); // class that handles basic drive
	 private Encoder leftDriveEncoder;
	    private Encoder rightDriveEncoder;
	  
												// operations
	Joystick leftStick = new Joystick(0); // set to ID 1 in DriverStation
	Joystick rightStick = new Joystick(1); // set to ID 2 in DriverStation
	VideoCapture vcap;
//	vcap.set(CV_CAP_PROP_CONTRAST, 0);
	
	
	public Robot() {

		//vcap.set(propId, value)
		myRobot.setExpiration(0.1);
	    leftDriveEncoder = new Encoder(IOMapping.LEFT_DRIVE_ENCODER_1, IOMapping.LEFT_DRIVE_ENCODER_2);
	    rightDriveEncoder = new Encoder(IOMapping.RIGHT_DRIVE_ENCODER_1, IOMapping.RIGHT_DRIVE_ENCODER_2);
	    
	    Thread thread = new Thread(new VisionThread());
	    thread.start();
	   
	}
	 void SendDataToSmartDashboard()
	    {

	      SmartDashboard.putNumber("LEFTDRIVE", leftDriveEncoder.get());
	      SmartDashboard.putNumber("RIGHTDRIVE", leftDriveEncoder.get());
	    }
	/**
	 * Runs the motors with tank steering.
	 */
	@Override
	public void operatorControl() {
		myRobot.setSafetyEnabled(true);
		while (isOperatorControl() && isEnabled()) {
			SendDataToSmartDashboard();
			myRobot.tankDrive(leftStick, rightStick);
			
			Timer.delay(0.005); // wait for a motor update time
		}
	}
	
	
	
	
	
}

