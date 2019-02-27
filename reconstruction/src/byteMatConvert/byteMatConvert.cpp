/*
 * byteMatConvert.cpp
 *
 *  Created on: Jan 3, 2018
 *      Author: wuyang
 */

#include "byteMatConvert.hpp"
#include <memory>

void ByteToMat(const vector<uchar>& imgByte, Mat& imgMat, const int& matHeight,
               const int& matWidth) {
  const int size = imgByte.size();
  uchar array[size];
  std::copy(imgByte.begin(), imgByte.end(), array);
  imgMat = Mat(matWidth, matHeight, CV_8UC3, array).clone();
}

void ByteToMat(uchar imgByte[], Mat& imgMat, const int& matHeight,
               const int& matWidth) {
  imgMat = Mat(matWidth, matHeight, CV_8UC3, imgByte).clone();

}

/*
 * byteToMat used for JNI environment
 */
void ByteToMat(JNIEnv *env, const jbyteArray& inputImg, Mat& imgMat,
               const int& imgRows, const int& imgCols) {
  jbyte* firstVal = env->GetByteArrayElements(inputImg, 0);
  imgMat = Mat(imgRows, imgCols, CV_8UC3, (uchar*) firstVal).clone();
  env->ReleaseByteArrayElements(inputImg, firstVal, 0);

}

void ByteToMat(JNIEnv *env, const jbyteArray& inputImg, Mat& imgMat,
               const int& imgRows, const int& imgCols, const int& type) {

  jbyte* firstVal = env->GetByteArrayElements(inputImg, 0);
  imgMat = Mat(imgRows, imgCols, type, (uchar*) firstVal).clone();
  env->ReleaseByteArrayElements(inputImg, firstVal, 0);

}

void MatToByte(uchar* imgByte, const Mat& imgMat) {
  const int size = imgMat.total() * imgMat.elemSize();
  memcpy(imgByte, imgMat.data, size);
}

void MatToByte(jbyte* imgByte, const Mat& imgMat) {
  const long size = imgMat.total() * imgMat.elemSize();
  memcpy(imgByte, imgMat.data, size);
}

void MatToByte(JNIEnv* env, jbyteArray& imgArray, const Mat& imgMat) {
  jbyte* firstVal = env->GetByteArrayElements(imgArray, 0);
  const long size = imgMat.total() * imgMat.elemSize();
  memcpy(firstVal, imgMat.data, size);
  env->ReleaseByteArrayElements(imgArray, firstVal, 0);
}

void MatToByte(JNIEnv* env, vector<vector<uchar> >*, jobjectArray&) {
}

