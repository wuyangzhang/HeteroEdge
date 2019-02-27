/*
 * reprojection.hpp
 *
 *  Created on: Dec 20, 2017
 *      Author: wuyang
 */

#ifndef SRC_REPROJECTION_REPROJECTION_HPP_
#define SRC_REPROJECTION_REPROJECTION_HPP_

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

class Reprojection {
 public:
  static void saveXYZ(const char* filename, const Mat& mat);
  void reprojectionTo3D(Mat& disparityMap, Mat& xyz, Mat& Q);

 private:
  Mat Q;

};

#endif /* SRC_REPROJECTION_REPROJECTION_HPP_ */
