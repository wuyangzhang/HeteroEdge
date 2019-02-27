/*
 * rectificationTest.cpp
 *
 *  Created on: Dec 21, 2017
 *      Author: wuyang
 */

#include "rectificationTest.hpp"
#include <sys/time.h>

void testRectification(int useCamera) {
  //test initialize rectification parameters
  Rectification rectification;
  struct timeval tpstart, tpend;
  gettimeofday(&tpstart, NULL);
  rectification.init("calibration.xml", "rectification.xml", Size(640, 480));
  //test remap function
  gettimeofday(&tpend, NULL);
  long timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
      + (tpend.tv_usec - tpstart.tv_usec) / 1000;
  printf("Time of rectification initialization %ld ms\n", timuse);

  if (useCamera) {
    Camera camera(0);
    camera.setCameraResolution(1280, 480);

    Mat srcFromStereoCamera;
    Mat leftImg, rightImg;
    Mat uLeftImg, uRightImg;

    printf("Start to rectify stereo images!\n");

    while (1) {
      camera.captureVideo(srcFromStereoCamera);
      camera.display("show", srcFromStereoCamera);
      camera.stereoCameraSplit(srcFromStereoCamera, leftImg, rightImg);
      rectification.rectifyStereoImages(leftImg, rightImg, uLeftImg, uRightImg);
      imshow("rectified left", uLeftImg);
      imshow("rectified right", uRightImg);
      char key = (char) waitKey(100);
      if (key == 27) {
        break;
      }
    }

    gettimeofday(&tpstart, NULL);
    rectification.rectifyStereoImages(leftImg, rightImg, uLeftImg, uRightImg);
    gettimeofday(&tpend, NULL);
    timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
        + (tpend.tv_usec - tpstart.tv_usec) / 1000;
    printf("Time of rectification %ld ms\n", timuse);
  }

}
