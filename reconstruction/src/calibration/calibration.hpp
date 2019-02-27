/*
 * calibration.hpp
 *
 * This class intends to find the calibrated parameters of a stereo camera.
 * @input a set of chess board images
 * @output intrinsic parameters
 * 		   extrinsic parameters
 * 		   distortion parameters
 *
 *  Created on: Dec 19, 2017
 *      Author: wuyang
 */

#ifndef SRC_CALIBRATION_CALIBRATION_HPP_
#define SRC_CALIBRATION_CALIBRATION_HPP_

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

class Calibration {

 public:
  Calibration(const int imgWidth, const int imgHeight, const int w_chessboard,
              const int h_chessboard, const float chessboardGridSize,
              const int MAX_CALIBRATED_IMAGE_COUNT);

  bool findChessboard(const Mat& left, const Mat& right,
                      bool writeChessBoardImgToFile);
  bool findSufficientChessBoard();
  void finishFindChessboard();

  void readCalibratedParamFromFile(const String filename);
  void writeCalibratedParamToFile(const String filename);

  double singleCameraCalibration(const int whichiCamera);
  double stereoCameraCalibration();
  double calibrationQualityCheck();

  void printCalibrationError() {
    printf("calibration error: [left]: %f, [right]: %f, [stereo]: %f", leftRet,
           rightRet, stereoRet);
  }

  void printCalibratedParam() {
    std::cout << cameraMatrix1 << endl;
    std::cout << distCoeffs1 << endl;
    std::cout << cameraMatrix2 << endl;
    std::cout << distCoeffs2 << endl;
    std::cout << R << endl;
    std::cout << T << endl;
  }

  int MAX_CALIBRATED_IMAGE_COUNT;
  int totalCalibratedImageCount;
  enum {
    LEFT,
    RIGHT
  };

 private:

  //image data

  const int IMG_WIDTH;
  const int IMG_HEIGHT;
  const cv::Size IMG_SIZE;

  //chesess board data

  const int CHESSBOARD_WIDTH, CHESSBOARD_HEIGHT;
  const float CHESSBOARD_GRID_SIZE;
  cv::Size CHESSBOARD_SIZE;

  // calibration parameters

  TermCriteria singleCameracriteria;
  TermCriteria stereoCameraCriteria;
  int singleCameraFlag;
  int stereoCameraFlag;

  Mat cameraMatrix1, cameraMatrix2;
  Mat distCoeffs1, distCoeffs2;
  Mat R, T, E, F;
  Mat R1, R2, T1, T2, P1, P2;

  vector<vector<Point3f> > objectPoints;
  vector<vector<Point2f> > imgPoints1, imgPoints2;
  vector<vector<Point2f> > leftPoints, rightPoints;
  vector<Point2f> corners1, corners2;
  // calibration error
  double leftRet, rightRet, stereoRet;

};

#endif /* SRC_CALIBRATION_CALIBRATION_HPP_ */
