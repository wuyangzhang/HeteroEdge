/*
 * reprojection.cpp
 *
 *  Created on: Dec 20, 2017
 *      Author: wuyang
 */

#include "reprojection.hpp"

void saveXYZ(const char* filename, const Mat& mat) {

  const double max_z = 1.0e4;
  FILE* fp = fopen(filename, "wt");
  for (int y = 0; y < mat.rows; y++) {
    for (int x = 0; x < mat.cols; x++) {
      Vec3f point = mat.at<Vec3f>(y, x);
      if (fabs(point[2] - max_z) < FLT_EPSILON || fabs(point[2]) > max_z)
        continue;
      fprintf(fp, "%f %f %f\n", point[0], point[1], point[2]);
    }
  }
  fclose(fp);
}

void Reprojection::reprojectionTo3D(Mat& disparityMap, Mat& xyz, Mat& Q) {
  cv::reprojectImageTo3D(disparityMap, xyz, Q, true);
}
