#define TEST_MODE

#include <ctime>
#include <time.h>
#include <unistd.h>
#include <stdio.h>

//#include "../src/disparity/disparity.hpp"
//#include "../src/calibration/calibration.hpp"
//#include "../src/test/calibration/calibrationTest.hpp"
//#include "../src/rectification/rectification.hpp"
//#include "../src/disparity/disparity.hpp"
//#include "../src/camera/camera.hpp"

#include "../src/test/camera/cameraTest.hpp"
#include "../src/test/calibration/calibrationTest.hpp"
#include "../src/test/disparity/disparityTest.hpp"
#include "../src/test/rectification/rectificationTest.hpp"
#include "../src/test/byteMatConvert/byteMatConvertTest.hpp"
#include "../src/test/partition/partitionTest.hpp"

bool calibration_mode = false;
bool rectify_mode = !calibration_mode;
bool disparity_mode = !calibration_mode;
bool callFromMatlab = false;
bool test1 = false;
int calibrationThreshold = 30;
int frameInterval = 100;

std::string srcLeft = "/home/wuyang/vr/cpp/src_l.png";
std::string srcRight = "/home/wuyang/vr/cpp/src_r.png";
std::string rectifyLeft = "/home/wuyang/ar/cpp/uleft.png";
std::string rectifyRight = "/home/wuyang/ar/cpp/uright.png";

char matlabScript[] = "/home/wuyang/vr/cpp/calibrationInMatlab.sh";
bool rectifyinit = false;

enum Test {
  camera = 1,
  calibration,
  rectification,
  disparity,
  byteMatConvert,
  partitionTest
};

int testMode = disparity;

#ifdef TEST_MODE
int main() {
  /*
   * test camera read
   */

  switch (testMode) {

    case camera:
      if (cameraReadFrameTest(1)) {
        printf("*******camera test pass************\n");
      } else {
        perror("xxxxxxxcamera test failxxxxxxxxxxxxx\n");
      }
      break;

    case calibration:
      /*
       * test camera calibration.
       */
      testCalibration();
      break;

    case rectification:
      /*
       * test rectification.
       */
      testRectification(0);
      break;

    case disparity:{
    	  /*
    	       * test disparity.
    	       */
    	      //testDisparity();
    	      //testDisparityParameters();
    	    	Mat left = imread(rectifyLeft);
    	    	Mat right = imread(rectifyRight);

    	    	testDisparityGPU(left, right);
    }
      break;

    case byteMatConvert:
      testByteMatConvert();
      break;

    case partitionTest:
      //rectificationPartitionTest();
      disparityPartitionTest();
      break;
  }

  return 0;
}

#endif

#ifndef TEST_MODE
int main() {
  printf("s");
  return 0;
}
#endif

