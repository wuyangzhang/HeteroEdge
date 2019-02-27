/*
 * partitionTest.cpp
 *
 *  Created on: Jan 6, 2018
 *      Author: wuyang
 */

#include "partitionTest.hpp"
#include "../../rectification/rectification.hpp"
#include "../../partition/partition.hpp"
#include "../../disparity/disparity.hpp"

void rectificationPartitionTest() {

  const int partitionNum = 4;
  const int ROWS(480);
  const int COLS(640);
  const int downCompensatitionRowNum = 20;

  Mat srcL = imread("left.png");
  Mat srcR = imread("right.png");

  Rectification rectification;
  rectification.init("calibration.xml", "rectification.xml", Size(COLS, ROWS));

  Mat* partitionL = new Mat[partitionNum];
  Mat* partitionR = new Mat[partitionNum];

  partition(partitionL, srcL, partitionNum, 20);
  partition(partitionR, srcR, partitionNum, 20);

  Mat* uLeftImgPartition = new Mat[partitionNum];
  Mat* uRightImgPartition = new Mat[partitionNum];

  for (int i = 0; i < partitionNum; i++) {
    rectification.rectifyStereoImages(partitionL[i], partitionR[i],
                                      uLeftImgPartition[i],
                                      uRightImgPartition[i]);
  }

  Mat uLeft, uRight;
  merge(uLeft, uLeftImgPartition, partitionNum, ROWS, COLS, 0);
  merge(uRight, uRightImgPartition, partitionNum, ROWS, COLS, 0);

  imshow("uRight", uRight);
  waitKey();

}

void disparityPartitionTest() {

  const int partitionNum = 4;
  const int upCompensationRowNum = 35;
  const int downCompensationRowNum = 35;

  const int ROWS(480);
  const int COLS(640);

  Mat srcL = imread("uleft.png");
  Mat srcR = imread("uright.png");

  Mat* partitionL = new Mat[partitionNum];
  Mat* partitionR = new Mat[partitionNum];

  partition(partitionL, srcL, partitionNum, upCompensationRowNum,
            downCompensationRowNum);
  partition(partitionR, srcR, partitionNum, upCompensationRowNum,
            downCompensationRowNum);

  DisparityMap disparityMap(640, 480, DisparityMap::BM);
  disparityMap.init();

  Mat* disparityPartition = new Mat[partitionNum];
  for (int i = 0; i < partitionNum; i++) {
    disparityMap.calDisparityMap(partitionL[i], partitionR[i],
                                 disparityPartition[i]);

    Mat disp8;
    disparityPartition[i].convertTo(
        disp8, CV_8U, 255 / (disparityMap.getNumberOfDisparity() * 16.));
    imshow("partOfParallel", disp8);
    waitKey();
  }

  Mat disparity;
  merge(disparity, disparityPartition, partitionNum, ROWS, COLS,
        upCompensationRowNum);

  Mat disp8;
  disparity.convertTo(disp8, CV_8U,
                      255 / (disparityMap.getNumberOfDisparity() * 16.));
  imshow("disparity", disp8);
  waitKey();
  //non-parallel version
  disparityMap.calDisparityMap(srcL, srcR, disparity);
  disparity.convertTo(disp8, CV_8U,
                      255 / (disparityMap.getNumberOfDisparity() * 16.));
  imshow("non-parallel-disparity", disp8);
  waitKey();

}
