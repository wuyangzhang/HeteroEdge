/*
 * camera.hpp
 *
 *  Created on: Dec 21, 2017
 *      Author: wuyang
 */

#ifndef SRC_CAMERA_CAMERA_HPP_
#define SRC_CAMERA_CAMERA_HPP_
#include <iostream>
#include <string>
#include <sstream>
#include <ctime>

#include <opencv2/opencv.hpp>
#include <opencv2/core/core.hpp>
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "opencv2/highgui.hpp"
#include "opencv2/imgproc.hpp"

using namespace std;
using namespace cv;

class Camera {
 public:
  Camera(int cameraId);
  ~Camera();
  void captureVideo(Mat& mat);
  void display(const String windowName, const Mat& mat);
  int getCameraId();
  double getCameraFPS();
  void setCameraResolution(const int width, const int height);
  void static stereoCameraSplit(Mat& input, Mat& left, Mat& right);
 private:
  VideoCapture* videoCapture;
  int cameraId;
};

#endif /* SRC_CAMERA_CAMERA_HPP_ */
