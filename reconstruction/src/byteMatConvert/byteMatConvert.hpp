/*
 * byteMatConvert.hpp
 *
 *  Created on: Jan 3, 2018
 *      Author: wuyang
 */

#ifndef SRC_BYTEMATCONVERT_BYTEMATCONVERT_HPP_
#define SRC_BYTEMATCONVERT_BYTEMATCONVERT_HPP_

#include <iostream>
#include <opencv2/core/core.hpp>
#include "opencv2/highgui.hpp"
#include "opencv2/imgproc.hpp"
#include <vector>
#include <jni.h>

using namespace std;
using namespace cv;
void ByteToMat(const vector<uchar>&, Mat&, const int&, const int&);
void ByteToMat(uchar[], Mat&, const int&, const int&);
void ByteToMat(JNIEnv *env, const jbyteArray& inputImg, Mat&, const int&,
               const int&);
void ByteToMat(JNIEnv *env, const jbyteArray& inputImg, Mat&, const int&,
               const int&, const int&);
void MatToByte(vector<uchar>&, const Mat&);
void MatToByte(uchar*, const Mat&);
void MatToByte(jbyte*, const Mat&);
void MatToByte(JNIEnv*, jbyteArray&, const Mat&);
void MatToByte(JNIEnv*, vector<vector<uchar> >*, jobjectArray&);

#endif /* SRC_BYTEMATCONVERT_BYTEMATCONVERT_HPP_ */
