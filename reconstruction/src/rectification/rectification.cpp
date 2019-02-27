/*
 * rectification.cpp
 *
 *  Created on: Dec 20, 2017
 *      Author: wuyang
 */

#include "rectification.hpp"

void Rectification::init(const String calibrationFilename,
                         const String rectificationFilename,
                         const Size imgSize) {
  this->imgSize = imgSize;
  Rectification::readCalibrationParamFromFile(calibrationFilename);
  Rectification::stereoRecitification();
  Rectification::writeRectificationParamToFile(rectificationFilename);
}

void Rectification::stereoRecitification() {
  cv::stereoRectify(cameraMatrix1, distCoeffs1, cameraMatrix2, distCoeffs2,
                    imgSize, R, T, R1, R2, P1, P2, Q, CALIB_ZERO_DISPARITY, 0);
  initUndistortRectifyMap(cameraMatrix1, distCoeffs1, R1, P1, imgSize, CV_32FC1,
                          map1x, map1y);
  initUndistortRectifyMap(cameraMatrix2, distCoeffs2, R2, P2, imgSize, CV_32FC1,
                          map2x, map2y);
}

void Rectification::rectifyStereoImages(Mat& leftImg, Mat& rightImg,
                                        Mat& leftUndistortedImg,
                                        Mat& rightUndistortedImg) {
  assert(
      !leftImg.empty() && !rightImg.empty()
          && leftImg.size() == rightImg.size());
  remap(leftImg, leftUndistortedImg, map1x, map1y, INTER_LINEAR);
  remap(rightImg, rightUndistortedImg, map2x, map2y, INTER_LINEAR);
}

void rectifyStereoImages(Mat& leftImg, Mat& rightImg, Mat& leftUndistortedImg,
                         Mat& rightUndistortedImg, Mat map1x, Mat map1y,
                         Mat map2x, Mat map2y) {
  assert(
      !leftImg.empty() && !rightImg.empty()
          && leftImg.size() == rightImg.size());
  remap(leftImg, leftUndistortedImg, map1x, map1y, INTER_LINEAR);
  remap(rightImg, rightUndistortedImg, map2x, map2y, INTER_LINEAR);
}

void Rectification::readRectificationParamFromFile(const String filename) {
  FileStorage fs(filename, FileStorage::READ);

  if (!fs.isOpened()) {
    std::cerr << "failed to open " << filename << endl;
    return;
  }

  fs["map1x"] >> map1x;
  fs["map1y"] >> map1y;
  fs["map2x"] >> map2x;
  fs["map2y"] >> map2y;

  fs.release();
}

void Rectification::readCalibrationParamFromFile(const String filename) {
  FileStorage fs(filename, FileStorage::READ);

  if (!fs.isOpened()) {
    std::cerr << "failed to open " << filename << endl;
    return;
  }

  fs["cameraMatrix1"] >> cameraMatrix1;
  fs["distCoeffs1"] >> distCoeffs1;
  fs["cameraMatrix2"] >> cameraMatrix2;
  fs["distCoeffs2"] >> distCoeffs2;
  fs["R"] >> R;
  fs["T"] >> T;

  fs.release();
}

void Rectification::writeRectificationParamToFile(const String filename) {
  FileStorage fs(filename, FileStorage::WRITE);

  if (!fs.isOpened()) {
    std::cerr << "failed to open " << filename << endl;
    return;
  }

  fs << "map1x" << map1x << "map1y" << map1y << "map2x" << map2x << "map2y"
     << map2y;
  fs << "Q" << Q;
  fs.release();
}

void Rectification::getQMatrix(Mat& Q) {
  Q = this->Q.clone();
}

