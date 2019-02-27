/*
 * rectification.hpp
 *
 *  Created on: Dec 20, 2017
 *      Author: wuyang
 */

#ifndef SRC_RECTIFICATION_RECTIFICATION_HPP_
#define SRC_RECTIFICATION_RECTIFICATION_HPP_

#include <iostream>
#include <string>
#include <sstream>
#include <ctime>

#include <opencv2/opencv.hpp>
#include <opencv2/core/core.hpp>
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/calib3d.hpp"
#include "opencv2/imgcodecs.hpp"
#include "opencv2/highgui.hpp"
#include "opencv2/imgproc.hpp"

using namespace std;
using namespace cv;

void rectifyStereoImages(Mat& leftImg, Mat& rightImg, Mat& leftUndistortedImg,
                         Mat& rightUndistortedImg, Mat map1x, Mat map1y,
                         Mat map2x, Mat map2y);

class Rectification {
 public:
  void init(const String calibrationFilename,
            const String rectificationFilename, const Size imgSize);
  void rectifyStereoImages(Mat& leftImg, Mat& rightImg, Mat& leftUndistortedImg,
                           Mat& rightUndistortedImg);
  void getQMatrix(Mat& Q);
  Mat map1x, map1y, map2x, map2y;

 private:
  void stereoRecitification();
  void readCalibrationParamFromFile(const String filename);
  void readRectificationParamFromFile(const String filename);
  void writeRectificationParamToFile(const String filename);
  cv::Size imgSize;
  Mat cameraMatrix1, cameraMatrix2;
  Mat distCoeffs1, distCoeffs2;
  Mat R, T, E, F;
  Mat R1, R2, T1, T2, P1, P2, Q;

};

#endif /* SRC_RECTIFICATION_RECTIFICATION_HPP_ */
