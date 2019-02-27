/*
 * paritition.cpp
 *
 *  Created on: Jan 3, 2018
 *      Author: wuyang
 */

#include "partition.hpp"
#include <sys/time.h>

#include "../byteMatConvert/byteMatConvert.hpp"

jobjectArray convertMatArrayToByte2dArray(JNIEnv* env,
                                                 const Mat* matArray,
                                                 const int partitionNum) {

  //convert each mat to an array
  jbyteArray byteArray[(int) partitionNum];
  for (int i = 0; i < (int) partitionNum; i++) {
    jsize size = (jsize) (matArray[i].total() * matArray[i].elemSize());
    byteArray[i] = env->NewByteArray(size);
    MatToByte(env, byteArray[i], matArray[i]);
  }

  //place those array to a 2d array
  //garbage collection will handle the returned reference... do not worry about the memory management
  jclass byteArrayClass = env->FindClass("[B");
  jobjectArray byte2dArray = env->NewObjectArray((jsize) partitionNum,
                                                 byteArrayClass, NULL);

  for (int i = 0; i < (int) partitionNum; i++) {
    jsize eachSubArraySize = env->GetArrayLength(byteArray[i]);
    env->SetObjectArrayElement(byte2dArray, i, byteArray[i]);
    env->DeleteLocalRef(byteArray[i]);
  }

  return byte2dArray;
}

/*
 * partition an image into a set of sub images
 */
jobjectArray partition(JNIEnv* env, const jbyteArray& imgArray,
                       const jint partitionNum, const jint imgRows,
                       const jint imgCols) {

  Mat imgMat;
  ByteToMat(env, imgArray, imgMat, (int) imgRows, (int) imgCols);

  Mat partitionResults[(int) partitionNum];
  partition(partitionResults, imgMat, (int) partitionNum);

  return convertMatArrayToByte2dArray(env, partitionResults, (int) partitionNum);
}

/*
 * partition an image into a set of sub images
 */
jobjectArray partition(JNIEnv* env, const jbyteArray& imgArray,
                       const jint partitionNum, const jint imgRows,
                       const jint imgCols, const jint compensationRowNum) {

  Mat imgMat;
  ByteToMat(env, imgArray, imgMat, (int) imgRows, (int) imgCols);

  Mat partitionResults[(int) partitionNum];
  partition(partitionResults, imgMat, (int) partitionNum,
            (int) compensationRowNum);

  return convertMatArrayToByte2dArray(env, partitionResults, (int) partitionNum);
}

void partition(Mat* partitionResults, const Mat& src, const int partitionNum) {

  const int partitionRowSum = src.rows / partitionNum;

  for (int i = 0; i < partitionNum; i++) {
    cv::Rect rect(0, i * partitionRowSum, src.cols, partitionRowSum);
    partitionResults[i] = src(rect).clone();
  }

}

void partition(Mat* partitionResults, const Mat& src, const int partitionNum,
               const int compensationRowNum) {

  const int partitionRowSum = src.rows / partitionNum;
  const int partitionRowSumWithCompensation = partitionRowSum
      + compensationRowNum;

  for (int i = 0; i < partitionNum - 1; i++) {
    cv::Rect rect(0, i * partitionRowSum, src.cols,
                  partitionRowSumWithCompensation);
    partitionResults[i] = src(rect).clone();
  }

  cv::Rect rect(0, (partitionNum - 1) * partitionRowSum, src.cols,
                partitionRowSum);
  partitionResults[(partitionNum - 1)] = src(rect).clone();
}

void partition(Mat* partitionResults, const Mat& src, const int partitionNum,
               const int upCompensationRowNum,
               const int downCompensationRowNum) {

  const int partitionRowSum = src.rows / partitionNum;

  //down compensation for the first partition
  int firstTotalRow = partitionRowSum + downCompensationRowNum;
  if (firstTotalRow < upCompensationRowNum + downCompensationRowNum) {
    firstTotalRow = upCompensationRowNum + downCompensationRowNum;
  }

  cv::Rect rectUp(0, 0, src.cols, firstTotalRow);
  partitionResults[0] = src(rectUp).clone();

  //up compensation for the last partition
  int lastTotalRow = partitionRowSum + upCompensationRowNum;
  int lastStartRow = (partitionNum - 1) * partitionRowSum
      - upCompensationRowNum;
  if (lastTotalRow < upCompensationRowNum + downCompensationRowNum) {
    lastStartRow = lastStartRow
        - (upCompensationRowNum + downCompensationRowNum - lastTotalRow);
    lastTotalRow = upCompensationRowNum + downCompensationRowNum;
  }

  cv::Rect rectDown(0, lastStartRow, src.cols, lastTotalRow);
  partitionResults[partitionNum - 1] = src(rectDown).clone();

  if (partitionNum < 3) {
    return;
  }

  //compensate on both up and down partition
  for (int i = 1; i < partitionNum - 1; i++) {
    int startRow = 0;
    if (i * partitionRowSum - upCompensationRowNum >= 0) {
      startRow = i * partitionRowSum - upCompensationRowNum;
    }

    int totalRows = partitionRowSum + upCompensationRowNum
        + downCompensationRowNum;

    if (startRow + totalRows > src.rows) {
      startRow = src.rows - totalRows;
    }

    cv::Rect rect(0, startRow, src.cols, totalRows);
    partitionResults[i] = src(rect).clone();
  }

}

void merge(Mat& mergedResult, Mat* partitionMatSet, const int partitionNum,
           const int imgRows, const int imgCols,
           const int upCompensationRowNum) {

  int eachRow = imgRows / partitionNum;
  cv::Rect firstrect(0, 0, imgCols, eachRow);
  partitionMatSet[0] = partitionMatSet[0](firstrect).clone();

  for (int i = 1; i < partitionNum; i++) {
    cv::Rect rect(0, upCompensationRowNum, imgCols, eachRow);
    partitionMatSet[i] = partitionMatSet[i](rect).clone();
  }

  mergedResult = partitionMatSet[0].clone();
  for (int i = 1; i < (int) partitionNum; i++) {
    cv::vconcat(mergedResult, partitionMatSet[i], mergedResult);
  }

//  for (int i = 0; i < partitionNum; i++) {
//    Mat disp8;
//    partitionMatSet[i].convertTo(disp8, CV_8U, 255 / (160 * 16.));
//    imshow("disparityfff", disp8);
//    waitKey();
//  }
//

}
