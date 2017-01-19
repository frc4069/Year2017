package org.usfirst.frc.team4069.robot;

import edu.wpi.first.wpilibj.SampleRobot;
import edu.wpi.first.wpilibj.RobotDrive;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Timer;

public class Robot extends SampleRobot
{
  RobotDrive myRobot = new RobotDrive(0, 1); // class that handles basic drive
  // operations
  Joystick leftStick = new Joystick(0); // set to ID 1 in DriverStation
  Joystick rightStick = new Joystick(1); // set to ID 2 in DriverStation

  public Robot()
  {
    myRobot.setExpiration(0.1);
  }

  /**
   * Runs the motors with tank steering.
   */
  @Override
  public void operatorControl()
  {
    myRobot.setSafetyEnabled(true);
    while (isOperatorControl() && isEnabled())
    {
      myRobot.tankDrive(leftStick, rightStick);
      Timer.delay(0.005); // wait for a motor update time
    }
  }
}