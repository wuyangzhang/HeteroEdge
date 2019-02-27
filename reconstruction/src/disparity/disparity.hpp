/*
 * disparity.hpp
 *
 *  Created on: Dec 20, 2017
 *      Author: wuyang
 */

//#define GPU_MODE

#ifndef SRC_DISPARITY_DISPARITY_HPP_
#define SRC_DISPARITY_DISPARITY_HPP_

#include <iostream>
#include <iostream>
#include <string>
#include <sstream>
#include <iomanip>
#include <stdexcept>
#include <ctime>
#include <time.h>

#include <opencv2/opencv.hpp>
#include <opencv2/core/core.hpp>

#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/ximgproc/disparity_filter.hpp"

#include <opencv/cv.h>
#include "opencv2/calib3d.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "opencv2/highgui.hpp"
#include "opencv2/core/utility.hpp"

#ifdef GPU_MODE
#include "opencv2/cudastereo.hpp"
#endif

using namespace std;
using namespace cv;

/*
 *
 * This class intends to do the disparity calculation.
 * @input stereo images
 * @output disparity map
 */
class DisparityMap {

 public:
  DisparityMap(int imgWidth, int imgHeight, int algorithm);

  void init();
  void calDisparityMap(Mat& leftImg, Mat& rightImg, Mat& disparityMap);
  void calDisparityMapGPU(Mat& leftImg, Mat& rightImg, Mat&disparityMap);
  void drawColorDisp();
  void show_webCamera();
  void readParam(std::string filename, int algorithm);
  void printParam(int algorithm);

  void showDisparityMap(bool color);
  void setQMatrix(Mat& Q) {
    this->Q = Q;
  }
  Mat getQMatrix() {
    return this->Q;
  }

  void findOptimalDisparity();

  void setLeftImage(Mat& img) {
    this->leftImg = img;
  }

  int getNumberOfDisparity(){
    return numberOfDisparity;
  }
#ifdef GPU_MODE
  void cudaDrawColorDisparityMap();
#endif

  /*
   * avaiable disparity algorithm
   */
  enum {
    BM,
    SGBM
  };

#ifdef GPU_MODE
  enum {BMGPU, BP, CSBP};
#endif

  static void print_help();
  Ptr<StereoBM> bm;

 private:

  Ptr<StereoSGBM> sgbm;

#ifdef GPU_MODE
  Ptr<cuda::StereoBM> bmGPU;
  Ptr<cuda::StereoBeliefPropagation> bp;
  Ptr<cuda::StereoConstantSpaceBP> csbp;
  cv::Ptr<cv::cuda::DisparityBilateralFilter> d_filter;
  cuda::GpuMat d_disp;
  cuda::GpuMat gpu_colorDisparityMap;
  cuda::GpuMat gpu_xyz;
  Mat colorDisparityMap;
#endif

  int numberOfDisparity;
  int SADWindowSize;
  Mat xyz;
  Mat Q;
  int algorithm;
  int imgWidth, imgHeight;
  Mat leftImg;

};

#endif /* SRC_DISPARITY_DISPARITY_HPP_ */
