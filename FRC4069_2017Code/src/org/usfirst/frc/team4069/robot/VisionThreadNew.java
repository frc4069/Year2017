package org.usfirst.frc.team4069.robot;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.Preferences;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class VisionThreadNew implements Runnable
{
  public static final int MIN_WIDTH = 120;
  public static final int Y_IMAGE_RES = 240;
  public static final double VIEW_ANGLE = 34.8665269;
  public static final double AUTO_STEADY_STATE = 1.9;

  public static final int minR = 0, minG = 70, minB = 0;
  public static final int maxR = 95, maxG = 255, maxB = 255; //75;

  public static final double minHRatio = 1.5, minVRatio = 1.5;
  public static final double maxHRatio = 6.6, maxVRatio = 8.5;

  public static final int MAX_SIZE = 255;
  public static final Scalar RED = new Scalar(0, 0, 255), BLUE = new Scalar(255, 0, 0), GREEN = new Scalar(0, 255, 0), ORANGE = new Scalar(0, 128, 255), YELLOW = new Scalar(0, 255, 255), PINK = new Scalar(255, 0, 255), WHITE = new Scalar(255, 255, 255);

  /*
   * CMY ranges C from 52 to 17%
   *  Magenta of 0%
   *  Yellow of  80 to 5
   * 
   */
  
  
  
  
  private boolean mDebug = false;
  private boolean mShowContours = true;

  private Target targets = new Target();
  private Mat frame = new Mat();
  private Mat outputframe = new Mat();

  private boolean mExitThread = false;
  private boolean mProcessFrames = true;

  private VideoCaptureThread vcap_thread_instance;
  private Thread vcap_thread_handle;

  private static double xcenter = 0.0;
  private double mapped = 0.0;

  public double lastXCenter = 0.0;
  public double lastMapped = 0.0;

  // OpenCV constants
  public static final int CV_CAP_PROP_BRIGHTNESS = 10;
  public static final int CV_CAP_PROP_CONTRAST = 11;
  public static final int CV_CAP_PROP_EXPOSURE_ABSOLUTE = 39;
  public static final int CV_CAP_PROP_FRAME_WIDTH = 3;
  public static final int CV_CAP_PROP_FRAME_HEIGHT = 4;
  Preferences prefs = Preferences.getInstance();
  CvSource outputStream;

  Scalar minRange,bminRange,cminRange;
  Scalar maxRange,bmaxRange,cmaxRange;
  LowPassFilter xLowPass;
  

  public VisionThreadNew(VideoCaptureThread vidcapinstance, Thread vcap_handle)
  {
    vcap_thread_instance = vidcapinstance; // to access getframe
    vcap_thread_handle = vcap_handle; // for thread control
    
    
    
    //minRange = getRGBFromCMY(.17,0,.05);  //new Scalar(minB, minG, minR); RGB = 212,255,242
    //maxRange = getRGBFromCMY(.52,0,.80); // //new Scalar(maxB, maxG, maxR); RGB=122,255,51
    minRange = new Scalar(240,239,70); //119); //51,70,122);
    maxRange = new Scalar(255,255,133); //242,255,212);
    bminRange = new Scalar(194,236,28); //31, 245, 194);
    bmaxRange = new Scalar(204,252,32); ////32, 252, 204);
    
    cminRange = new Scalar(240,239,22); //51,70,122);
    cmaxRange = new Scalar(255,255,46); //242,255,212);

    
  }

  public void run()
  {
    Mat img = new Mat();
    Mat lastvalidimg = null;

    Mat thresholded = new Mat();
    Mat thresholdedb= new Mat();
    Mat thresholdedc= new Mat();
    outputStream = CameraServer.getInstance().putVideo("ProcessorOutput", 640, 480);

    targets.matchStart = false;
    targets.validFrame = false;
    targets.hotLeftOrRight = 0;
    xLowPass = new LowPassFilter(200);
    
    while ((true) && (mExitThread == false))
    {
      if (mProcessFrames)
      {
        img = vcap_thread_instance.GetFrame();

        if (img != null)
        {
          lastvalidimg = img;
           Scalar minScalar = new Scalar(prefs.getDouble("minB", minB), prefs.getDouble("minG", minG), prefs.getDouble("minR", minR));
           Scalar maxScalar = new Scalar(prefs.getDouble("maxB", maxB), prefs.getDouble("maxG", maxG), prefs.getDouble("maxR", maxR));
          Core.inRange(img, minRange, maxRange, thresholded); // Scalar(minB, minG, minR), Scalar(maxB, maxG, maxR), thresholded);
          Core.inRange(img, bminRange, bmaxRange, thresholdedb);
          Core.inRange(img, cminRange, cmaxRange, thresholdedc);
          
          Imgproc.blur(thresholded, thresholded, new Size(3, 3));
          Imgproc.blur(thresholdedb,thresholdedb,new Size(3,3));
          Imgproc.blur(thresholdedc,thresholdedc,new Size(3,3));

          synchronized (targets)
          {
            findTargetNew(img, thresholded);
            findTargetNew(img, thresholdedb);
            findTargetNew(img,thresholdedc);
            CalculateDist();
          } // synchronized
          // outputStream.putFrame(img);
        } // if img!=null
        if (lastvalidimg != null)
        {
          outputStream.putFrame(lastvalidimg);
        }
      } // if processframes true
      try
      {
        Thread.sleep(5);
      }
      catch (InterruptedException e)
      {
        e.printStackTrace();
      }
    } // while true
  } // run

  /**
   * Main Thread method, calls start which results in run() being called above
   * 
   * @param args
   */
  // public static void main(String args[])
  // {
  // (new Thread(new VisionThreadNew())).start();
  // }

  private void CalculateDist()
  {
    double targetHeight = 32;
    if (targets.VerticalTarget != null)
    {
      int height = targets.VerticalTarget.height;
      targets.targetDistance = Y_IMAGE_RES * targetHeight / (height * 12 * 2 * Math.tan(VIEW_ANGLE * Math.PI / (180 * 2)));
    }
  }

  private double Lerp(double a1, double a2, double b1, double b2, double num)
  {
    return ((num - a1) / (a2 - a1)) * (b2 - b1) - b2;
  }

  private void findTarget(Mat original, Mat thresholded)
  {
    xcenter = 0;
    int contourMin = 6;
    MatOfInt4 hierarchy = new MatOfInt4();
    ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
    // RETR_EXTERNAL : If you use this flag, it returns only extreme outer contours. All child contours are left behind.
    // CHAIN_APPROX_SIMPLE : removes all redundant points so a line would only return 2 points, rectangle 4 etc. CHAIN_APPROX_NONE returns dozens/hundreds
    Imgproc.findContours(thresholded, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
    // FIRST, lets get rid of any contours not of proper size...
    // sum all the contours center x positions into xcenter
    // Use iterator so we don't have to loop twice while removing, faster.
    Iterator<MatOfPoint> it = contours.iterator();
    while (it.hasNext())
    {
      MatOfPoint mop = (MatOfPoint) it.next();

      if (mop.size().width < contourMin && mop.size().height < contourMin)
      {
        it.remove();
      }
      else
      {
        xcenter += mop.get(0, 0)[0] + mop.size().width / 2; // don't add to xcenter for removed contours
      }
    } // for mop

    // Now contours is 'cleaned' of ones too small

    if (contours.size() > 0)
    {
      if (mDebug)
      {
        System.out.println("# Contours: " + contours.size());
      }

      xcenter /= contours.size();

      mapped = Lerp(0, 640, -1, 1, xcenter); //
      lastXCenter = xcenter;
      lastMapped = mapped;

      RotatedRect[] minRect = new RotatedRect[contours.size()];

      // Mat drawing = Mat.zeros(original.size(), CvType.CV_8UC3);

      NullTargets();

      for (int i = 0; i < contours.size(); i++)
      {
        MatOfPoint2f mop2f = new MatOfPoint2f(contours.get(i).toArray());
        minRect[i] = Imgproc.minAreaRect(mop2f);

        if (mShowContours)
        {
          Point[] rect_points = new Point[4];
          minRect[i].points(rect_points);

          for (int j = 0; j < 4; j++)
          {
            Imgproc.line(original, rect_points[j], rect_points[(j + 1) % 4], BLUE, 3);
          }
        } // if visualize

        Rect box = minRect[i].boundingRect();

        double WHRatio = box.width / ((double) box.height);

        double HWRatio = ((double) box.height) / box.width;

        // check if contour is vert, we use HWRatio because it is greater that 0 // for vert target
        if ((HWRatio > minVRatio) && (HWRatio < maxVRatio))
        {
          targets.VertGoal = true;
          targets.VerticalTarget = box;
          targets.VerticalAngle = minRect[i].angle;
          targets.VerticalCenter = new Point(box.x + box.width / 2, box.y + box.height / 2);
          targets.Vertical_H_W_Ratio = HWRatio;
          targets.Vertical_W_H_Ratio = WHRatio;

        }
        // check if contour is horiz, we use WHRatio because it is greater that
        // 0 for vert target
        else if ((WHRatio > minHRatio) && (WHRatio < maxHRatio))
        {
          targets.HorizGoal = true;
          targets.HorizontalTarget = box;
          targets.HorizontalAngle = minRect[i].angle;
          targets.HorizontalCenter = new Point(box.x + box.width / 2, box.y + box.height / 2);
          targets.Horizontal_H_W_Ratio = HWRatio;
          targets.Horizontal_W_H_Ratio = WHRatio;
        }

        if (targets.HorizGoal && targets.VertGoal)
        {
          targets.HotGoal = true;

          // determine left or right
          if (targets.VerticalCenter.x < targets.HorizontalCenter.x) // target is right
          {
            targets.targetLeftOrRight = 1;
          }
          else
          {
            if (targets.VerticalCenter.x > targets.HorizontalCenter.x) // target is left
            {
              targets.targetLeftOrRight = -1;
            }
          }
        }
        targets.lastTargerLorR = targets.targetLeftOrRight;
        // }

        // System.out.println("MAPPED: " + mapped); // NAN if xcenter is NAN
        if (mDebug)
        {
          System.out.println("---------------------------");

          System.out.println("Contour: " + i);
          System.out.println("X: " + box.x);
          System.out.println("Y: " + box.y);
          System.out.println("Height: " + box.height);
          System.out.println("Width: " + box.width);
          System.out.println("Angle: " + minRect[i].angle);
          // System.out.println("Ratio (W/H): " + WHRatio);
          // System.out.println("Ratio (H/W): " + HWRatio);
          System.out.println("Area: " + (box.height * box.width));
        }
        Point center = new Point(box.x + box.width / 2, box.y + box.height / 2);
        Imgproc.line(original, center, center, YELLOW, 3);
        // Imgproc.line(original, new Point(320 / 2, 240 / 2), new Point(320 / 2, 240 / 2), YELLOW, 3);
      } // for i <contours.size
    } // if contours.size >0
    else
    { // no contours if here
      // System.out.println("No Contours");
      targets.targetLeftOrRight = 0;
    }

    // outputStream.putFrame(original); // output);
    if (mShowContours)
    {
      /*
       * BufferedImage toShow = matToImage(original);
       * 
       * if (toShow != null) { window.getDisplayIcon().setImage(toShow); window.repaint(); }
       */
    } // if showcontours

    synchronized (targets)
    {
      if (!targets.matchStart)
      {
        targets.hotLeftOrRight = targets.targetLeftOrRight;
      }
    } // synchronized
  }// findTarget

  // ----------------------------------------------------------------

  private void CleanContours(ArrayList<MatOfPoint> contours)
  {

  }

  private void findTargetNew(Mat original, Mat thresholded)
  {
    xcenter = 0;
    int contourMin = 6;
    MatOfInt4 hierarchy = new MatOfInt4();

    ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();

    Imgproc.findContours(thresholded, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

    

    Iterator<MatOfPoint> it = contours.iterator();
    double xAverage = 0.0;
    double yAverage = 0.0;
    int avgCtr = 0;
    double contMaxArea=0.0;
    MatOfPoint contLargestArea;
    
    while (it.hasNext())
    {
      MatOfPoint mop = (MatOfPoint) it.next();
      double area = Imgproc.contourArea(mop);
      if (area < 80)
      //if (mop.size().width < contourMin && mop.size().height < contourMin)
      {
        it.remove();
      }
      else
      {
        xcenter += mop.get(0, 0)[0] + mop.size().width / 2; // don't add to xcenter for removed contours
        Rect rect = Imgproc.boundingRect(mop);
        //double area = Imgproc.contourArea(mop);
        if (area > contMaxArea)
        {
          area=contMaxArea;
          contLargestArea = mop;
        }
      }
    } // for mop
    // Now contours is 'cleaned' of ones too small
    
    if (contours.size() > 0)
    {
      xcenter /= contours.size();  //average to get x center
      mapped = Lerp(0, 640, -1, 1, xcenter); //

      lastXCenter = xcenter;
      lastMapped = mapped;

      RotatedRect[] minRect = new RotatedRect[contours.size()];

      avgCtr = 0;
      xAverage = 0.0;
      yAverage = 0.0;
      for (int i = 0; i < contours.size(); i++)
      {
        MatOfPoint2f mop2f = new MatOfPoint2f(contours.get(i).toArray());
        minRect[i] = Imgproc.minAreaRect(mop2f);
        double curarea = Imgproc.contourArea(contours.get(i));
        
        double contourWeight =curarea / contMaxArea;  //0-1.0 for weight of this contour
        
        if ((mShowContours)&&(contourWeight > .5))
        {
          Point[] rect_points = new Point[4];
          minRect[i].points(rect_points);

          for (int j = 0; j < 4; j++)
          {
            Imgproc.line(original, rect_points[j], rect_points[(j + 1) % 4], BLUE, 2);
            xAverage += rect_points[j].x;
            yAverage += rect_points[j].y;
            avgCtr++;
          }
        } // if visualize

        Rect box = minRect[i].boundingRect();

        Point center = new Point(box.x + box.width / 2, box.y + box.height / 2);
        Point center2 = new Point(3+(box.x + box.width / 2),3+( box.y + box.height / 2));
        Imgproc.line(original, center, center2, YELLOW, 3);

        // Imgproc.line(original, new Point(320 / 2, 240 / 2), new Point(320 / 2, 240 / 2), YELLOW, 3);
      } // for i <contours.size
      xAverage /= avgCtr;
      yAverage /= avgCtr;
      double nx = xLowPass.calculate(xAverage);
      Point avgpt = new Point(nx,0); //yAverage);
      Point avgpt2 = new Point(nx,320); //yAverage+2); //give some size
        Imgproc.line(original,avgpt,avgpt2,GREEN,1);



    } // if contours.size >0
    else
    { // no contours if here
      // System.out.println("No Contours");
    }
  }// findTargetNew

  private void NullTargets()
  {
    targets.HorizontalAngle = 0.0;
    targets.VerticalAngle = 0.0;
    targets.Horizontal_W_H_Ratio = 0.0;
    targets.Horizontal_H_W_Ratio = 0.0;
    targets.Vertical_W_H_Ratio = 0.0;
    targets.Vertical_H_W_Ratio = 0.0;
    targets.targetDistance = 0.0;
    targets.targetLeftOrRight = 0;
    targets.lastTargerLorR = 0;

    targets.HorizGoal = false;
    targets.VertGoal = false;
    targets.HotGoal = false;
  } // NullTargets

  /**
   * matToImage : Convert a Mat to a png
   * 
   * @param mat
   * @return
   */
  private BufferedImage matToImage(Mat mat)
  {
    MatOfByte buffer = new MatOfByte();
    Imgcodecs.imencode(".png", mat, buffer);
    BufferedImage image = null;
    try
    {
      image = ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    return image;
  } // matToImage
  
  Scalar getRGBFromCMY(double c,double m,double y)
  {
    Double  Red = 255 * (1-c);
    Double Green = 255 * (1-m);
    Double Blue = 255 * (1-y);
    
    int r = Red.intValue();
    int g = Green.intValue();
    int b = Blue.intValue();
        
    Scalar rgb = new Scalar(b,g,r);
    return rgb;
  }
  
  
}//class VisionThreadNew
