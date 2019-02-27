/*
 * cameraTest.cpp
 *
 *  Created on: Dec 21, 2017
 *      Author: wuyang
 */

#include "cameraTest.hpp"
#include "../../camera/camera.hpp"

bool cameraReadFrameTest(int cameraId) {
  Camera camera(cameraId);
  Mat mat;
  Mat leftImg;
  Mat rightImg;

  double fps = camera.getCameraFPS();
  printf("camera fps: %f\n", fps);

  camera.setCameraResolution(1280,480);
  while (1) {
    camera.captureVideo(mat);
    printf("Frame Size [Height %d, Width %d]\n", mat.rows, mat.cols);
    cv::imshow("output", mat);
    camera.stereoCameraSplit(mat, leftImg, rightImg);
    imshow("left", leftImg);
    imshow("right", rightImg);

    if(waitKey(1) == 27){
      break;
    }

  }

  if (!mat.empty())
    return true;
  else
    return false;

}
