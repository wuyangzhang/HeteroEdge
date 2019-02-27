/*
 * partition.hpp
 *
 *  Created on: Jan 3, 2018
 *      Author: wuyang
 */

#ifndef SRC_PARTITION_PARTITION_HPP_
#define SRC_PARTITION_PARTITION_HPP_

#include <iostream>
#include <string>
#include <sstream>
#include <iomanip>
#include <stdexcept>
#include <vector>
#include <ctime>
#include <time.h>

#include <opencv2/core/utility.hpp>
#include "opencv2/highgui.hpp"
#include "opencv2/imgproc.hpp"

#include <jni.h>

using namespace cv;
using namespace std;

jobjectArray convertMatArrayToByte2dArray(JNIEnv* env,
                                                 const Mat* matArray,
                                                 const int partitionNum);

jobjectArray partition(JNIEnv*, const jbyteArray& imgArray,
                       const jint partitionNum, const jint imgRows,
                       const jint imgCols);

jobjectArray partition(JNIEnv*, const jbyteArray& imgArray,
                       const jint partitionNum, const jint imgRows,
                       const jint imgCols, const jint compensationRowNum);

void partition(Mat* partitionResults, const Mat& src, const int partitionNum);

void partition(Mat* partitionResults, const Mat& src, const int partitionNum,
               const int compensationRowNum);

void partition(Mat* partitionResults, const Mat& src, const int partitionNum,
               const int upCompensationRowNum,
               const int downCompensationRowNum);

void merge(Mat& mergedResult, Mat* partitionArray, const int partitionNum,
           const int imgRows, const int imgCols,
           const int downCompensation);

#endif /* SRC_PARTITION_PARTITION_HPP_ */
