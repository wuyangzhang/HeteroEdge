/*
 * ReconstructionJNIWrapper.cpp
 *
 *  Created on: Jan 3, 2018
 *      Author: wuyang
 */

#include "../byteMatConvert/byteMatConvert.hpp"
#include "../calibration/calibration.hpp"
#include "../camera/camera.hpp"
#include "../disparity/disparity.hpp"
#include "../partition/partition.hpp"
#include "../rectification/rectification.hpp"
#include "../reprojection/reprojection.hpp"
#include "ReconstructionJava.h"

/*
 * Class:     ReconstructionJNIWrapper
 * Method:    readImage
 * Signature: (Ljava/lang/String;[B)V
 */
JNIEXPORT void JNICALL Java_reconstructionAPI_ReconstructionAPI_readImage(JNIEnv * env, jclass,
                                                         jstring imgAddr,
                                                         jbyteArray imgArray) {
  const char* _imgAddr = env->GetStringUTFChars(imgAddr, 0);
  Mat imgMat = imread(_imgAddr);
  printf("load image addr %s\n", _imgAddr);
  assert(!imgMat.empty() && "[Error] cannot load image !");
  
  env->ReleaseStringUTFChars(imgAddr, _imgAddr);
  MatToByte(env, imgArray, imgMat);
}

/*
 * Class:     ReconstructionJava
 * Method:    cameraInit
 * Signature: (IB)V
 */
JNIEXPORT long JNICALL Java_reconstructionAPI_ReconstructionAPI_cameraInit(JNIEnv *env, jclass,
                                                          jint cameraId) {
  Camera* camera = new Camera(cameraId);
  camera->setCameraResolution(1280, 480);

  return (long) camera;
}

/*
 * Class:     ReconstructionJava
 * Method:    releaseCamera
 * Signature: (B)V
 */
JNIEXPORT void JNICALL Java_reconstructionAPI_ReconstructionAPI_releaseCamera(
    JNIEnv * env, jclass, jlong cameraPointer) {
  Camera* camera = (Camera*) cameraPointer;
  delete camera;
}

/*
 * Class:     ReconstructionJava
 * Method:    readFromCamera
 * Signature: (I[B)V
 */
JNIEXPORT void JNICALL Java_reconstructionAPI_ReconstructionAPI_readFromCamera(
    JNIEnv *env, jclass, jlong cameraPointer, jbyteArray leftImgArray,
    jbyteArray rightImgArray) {

  Camera* camera = (Camera*) cameraPointer;
  Mat stereo, left, right;
  camera->captureVideo(stereo);

  Camera::stereoCameraSplit(stereo, left, right);

  MatToByte(env, leftImgArray, left);
  MatToByte(env, rightImgArray, right);
}

/*
 * Class:     ReconstructionJava
 * Method:    displayImage
 * Signature: (Ljava/lang/String;[BII)V
 */
JNIEXPORT void JNICALL Java_reconstructionAPI_ReconstructionAPI_displayImage(JNIEnv * env,
                                                            jclass,
                                                            jstring imgName,
                                                            jbyteArray imgArray,
                                                            jint imgCols,
                                                            jboolean isWaitKey) {
  const char* _imgName = env->GetStringUTFChars(imgName, 0);
  Mat imgMat;
  int imgRows = env->GetArrayLength(imgArray) / imgCols / 3;
  ByteToMat(env, imgArray, imgMat, imgRows, (int) imgCols);
  assert(imgMat.rows == imgRows && imgMat.cols == imgCols);
  imshow(_imgName, imgMat);
  env->ReleaseStringUTFChars(imgName, _imgName);
  if (isWaitKey) {
    waitKey();
  }
}

/*
 * Class:     ReconstructionJava
 * Method:    partitionImage
 * Signature: ([BIII)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_reconstructionAPI_ReconstructionAPI_partitionImage___3BIII(
    JNIEnv *env, jclass, jbyteArray imgArray, jint partitionNum, jint imgRows,
    jint imgCols) {

  return partition(env, imgArray, partitionNum, imgRows, imgCols);
}

/*
 * Class:     ReconstructionJava
 * Method:    partitionImage
 * Signature: ([BIIII)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_reconstructionAPI_ReconstructionAPI_partitionImage___3BIIII(
    JNIEnv * env, jclass, jbyteArray imgArray, jint partitionNum, jint imgRows,
    jint imgCols, jint compensationRowNum) {
  return partition(env, imgArray, partitionNum, imgRows, imgCols,
                   compensationRowNum);
}

/*
 * Class:     ReconstructionJava
 * Method:    partitionImage
 * Signature: ([BIIIII)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_reconstructionAPI_ReconstructionAPI_partitionImage___3BIIIII(
    JNIEnv * env, jclass, jbyteArray imgArray, jint partitionNum, jint imgRows,
    jint imgCols, jint upCompensationRowNum, jint downCompensationRowNum) {

  Mat src;
  ByteToMat(env, imgArray, src, (int) imgRows, (int) imgCols);

  Mat partitionResults[(int) partitionNum];
  partition(partitionResults, src, (int) partitionNum,
            (int) upCompensationRowNum, (int) downCompensationRowNum);


  return convertMatArrayToByte2dArray(env, partitionResults, (int) partitionNum);
}

/*
 * Class:     ReconstructionJava
 * Method:    mergeImage
 * Signature: ([[BIII)[B
 */
JNIEXPORT jbyteArray JNICALL Java_reconstructionAPI_ReconstructionAPI_mergeImage(
    JNIEnv *env, jclass, jobjectArray partition2dArray, jint imgRows,
    jint imgCols, jint upCompensationRowNum, jint colorWay, jint matType) {

  jsize partitionNum = env->GetArrayLength(partition2dArray);

  Mat subImg[(int) partitionNum];
  for (int i = 0; i < (int) partitionNum; i++) {
    jbyteArray tmp = (jbyteArray) env->GetObjectArrayElement(partition2dArray,
                                                             i);
    int eachRows = env->GetArrayLength(tmp) / imgCols / colorWay;
    ByteToMat(env, tmp, subImg[i], eachRows, (int) imgCols, matType);
  }

  Mat mergeMat;
  merge(mergeMat, subImg, (int) partitionNum, (int) imgRows, (int) imgCols,
        (int) upCompensationRowNum);

  const jsize totalElementSize =
      (jsize) (mergeMat.total() * mergeMat.elemSize());
  jbyteArray mergedArray = env->NewByteArray(totalElementSize);
  MatToByte(env, mergedArray, mergeMat);

  return mergedArray;
}

/*
 * Class:     ReconstructionJava
 * Method:    initImageRectification
 * Signature: (Ljava/lang/String;Ljava/lang/String;II)V
 */
JNIEXPORT jobjectArray JNICALL Java_reconstructionAPI_ReconstructionAPI_initImageRectification(
    JNIEnv * env, jclass, jstring calibrationFilename,
    jstring rectificationFilename, jint imgRows, jint imgCols) {

  Rectification rectification;
  const char* CalibrationFile = env->GetStringUTFChars(calibrationFilename, 0);
  const char* RectificationFile = env->GetStringUTFChars(rectificationFilename,
                                                         0);
  rectification.init(CalibrationFile, RectificationFile,
                     Size((int) imgCols, (int) imgRows));

  env->ReleaseStringUTFChars(calibrationFilename, CalibrationFile);
  env->ReleaseStringUTFChars(rectificationFilename, RectificationFile);

  //convert map MAT results to byte array and to a 2d array
  jsize totalMapElement = (jsize) rectification.map1x.total()
      * rectification.map1x.elemSize();
  jbyteArray map1x = env->NewByteArray(totalMapElement);
  jbyteArray map1y = env->NewByteArray(totalMapElement);
  jbyteArray map2x = env->NewByteArray(totalMapElement);
  jbyteArray map2y = env->NewByteArray(totalMapElement);

  Mat Q;
  rectification.getQMatrix(Q);
  jsize QSize = (jsize) (Q.total() * Q.elemSize());
  jbyteArray Qarray = env->NewByteArray(QSize);

  MatToByte(env, map1x, rectification.map1x);
  MatToByte(env, map1y, rectification.map1y);
  MatToByte(env, map2x, rectification.map2x);
  MatToByte(env, map2y, rectification.map2y);

  MatToByte(env, Qarray, Q);
  jclass byteArrayClass = env->FindClass("[B");
  jobjectArray byte2dArray = env->NewObjectArray(5, byteArrayClass, NULL);
  env->SetObjectArrayElement(byte2dArray, 0, map1x);
  env->SetObjectArrayElement(byte2dArray, 1, map1y);
  env->SetObjectArrayElement(byte2dArray, 2, map2x);
  env->SetObjectArrayElement(byte2dArray, 3, map2y);

  env->SetObjectArrayElement(byte2dArray, 4, Qarray);

  env->DeleteLocalRef(map1x);
  env->DeleteLocalRef(map1y);
  env->DeleteLocalRef(map2x);
  env->DeleteLocalRef(map2y);
  env->DeleteLocalRef(Qarray);

  return byte2dArray;

}

/*
 * Class:     ReconstructionJava
 * Method:    rectifyStereoImages
 * Signature: ([B[B[B[B)V
 */
JNIEXPORT void JNICALL Java_reconstructionAPI_ReconstructionAPI_rectifyStereoImages(
    JNIEnv * env, jclass, jbyteArray leftArray, jbyteArray rightArray,
    jbyteArray uLeftArray, jbyteArray uRightArray, jint imgRows, jint imgCols,
    jbyteArray map1xArray, jbyteArray map1yArray, jbyteArray map2xArray,
    jbyteArray map2yArray) {

  Mat leftImg, rightImg, uLeftImg, uRightImg, map1x, map1y, map2x, map2y;
  ByteToMat(env, leftArray, leftImg, (int) imgRows, (int) imgCols);
  ByteToMat(env, rightArray, rightImg, (int) imgRows, (int) imgCols);

  ByteToMat(env, map1xArray, map1x, (int) imgRows, (int) imgCols, 5);
  ByteToMat(env, map1yArray, map1y, (int) imgRows, (int) imgCols, 5);
  ByteToMat(env, map2xArray, map2x, (int) imgRows, (int) imgCols, 5);
  ByteToMat(env, map2yArray, map2y, (int) imgRows, (int) imgCols, 5);

  rectifyStereoImages(leftImg, rightImg, uLeftImg, uRightImg, map1x, map1y,
                      map2x, map2y);

  MatToByte(env, uLeftArray, uLeftImg);
  MatToByte(env, uRightArray, uRightImg);
}

/*
 * Class:     ReconstructionJava
 * Method:    calculateDisparityMap
 * Signature: ([B[B[BIII)V
 */
JNIEXPORT jbyteArray JNICALL Java_reconstructionAPI_ReconstructionAPI_calculateDisparityMap(
    JNIEnv * env, jclass, jbyteArray leftArray, jbyteArray rightArray,
    jint imgCols, jint disparityAlgorithm) {

  int imgRows = env->GetArrayLength(leftArray) / (int) imgCols / 3;

  DisparityMap disparityMap(imgCols, imgRows, (int) disparityAlgorithm);
  disparityMap.init();

  Mat uLeftImg, uRightImg, disparity;
  ByteToMat(env, leftArray, uLeftImg, (int) imgRows, (int) imgCols);
  ByteToMat(env, rightArray, uRightImg, (int) imgRows, (int) imgCols);

  disparityMap.calDisparityMap(uLeftImg, uRightImg, disparity);

  jsize totalDisparityElement = (jsize) (disparity.total()
      * disparity.elemSize());
  jbyteArray dispairtyArray = env->NewByteArray(totalDisparityElement);
  MatToByte(env, dispairtyArray, disparity);

  return dispairtyArray;

}

/*
 * Class:     reconstructionAPI_ReconstructionAPI
 * Method:    calculateDisparityMapGPU
 * Signature: ([B[BII)[B
 */
JNIEXPORT jbyteArray JNICALL Java_reconstructionAPI_ReconstructionAPI_calculateDisparityMapGPU
  (JNIEnv * env, jclass, jbyteArray leftArray, jbyteArray rightArray,
		    jint imgCols, jint disparityAlgorithm){
	int imgRows = env->GetArrayLength(leftArray) / (int) imgCols / 3;

	  DisparityMap disparityMap(imgCols, imgRows, (int) disparityAlgorithm);
	  disparityMap.init();

	  Mat uLeftImg, uRightImg, disparity;
	  ByteToMat(env, leftArray, uLeftImg, (int) imgRows, (int) imgCols);
	  ByteToMat(env, rightArray, uRightImg, (int) imgRows, (int) imgCols);

	  disparityMap.calDisparityMapGPU(uLeftImg, uRightImg, disparity);

	  jsize totalDisparityElement = (jsize) (disparity.total()
	      * disparity.elemSize());
	  jbyteArray dispairtyArray = env->NewByteArray(totalDisparityElement);
	  MatToByte(env, dispairtyArray, disparity);

	  return dispairtyArray;
}

/*
 * Class:     ReconstructionJava
 * Method:    displayDisparityMap
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_reconstructionAPI_ReconstructionAPI_displayDisparityMap(
    JNIEnv * env, jclass, jbyteArray disparityArray, jint imgRows, jint imgCols,
    jint disparityNum, jboolean isWaitKey) {

  Mat disparity;
  ByteToMat(env, disparityArray, disparity, (int) imgRows, (int) imgCols, 3);

  Mat disp8;
  disparity.convertTo(disp8, CV_8U, 255 / ((int) 160 * 16.));
  imshow("disparity", disp8);
  if (isWaitKey) {
    waitKey(50);
  }
}

/*
 * Class:     ReconstructionJava
 * Method:    reprojectTo3D
 * Signature: ([B[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_reconstructionAPI_ReconstructionAPI_reprojectTo3D(
    JNIEnv * env, jclass, jbyteArray disparity, jbyteArray Q, jint rows,
    jint cols) {

  Mat disparityMat, xyzMat, QMat;
  ByteToMat(env, disparity, disparityMat, (int) rows, (int) cols, 3);
  ByteToMat(env, Q, QMat, 4, 4, 6);

  Reprojection reprojection;
  reprojection.reprojectionTo3D(disparityMat, xyzMat, QMat);
  xyzMat *= 16;

  jsize totalXYZSize = (jsize) (xyzMat.total() * xyzMat.elemSize());
  jbyteArray xyz = env->NewByteArray(totalXYZSize);
  MatToByte(env, xyz, xyzMat);
  return xyz;
}

/*
 * Class:     ReconstructionJava
 * Method:    queryDepth
 * Signature: (II)V
 */
JNIEXPORT jfloatArray JNICALL Java_reconstructionAPI_ReconstructionAPI_queryDepth(
    JNIEnv *env, jclass, jbyteArray xyzByte, jint x, jint y, jint rows,
    jint cols) {

  Mat xyz;
  ByteToMat(env, xyzByte, xyz, (int) rows, (int) cols);
  jfloatArray xyzResult = env->NewFloatArray(3);
  Point origin = Point(int(x), int(y));
  float tmp[3];
  tmp[0] = xyz.at<Vec3f>(origin).val[0];
  tmp[1] = xyz.at<Vec3f>(origin).val[1];
  tmp[2] = xyz.at<Vec3f>(origin).val[2];
  env->SetFloatArrayRegion(xyzResult, 0, 1, tmp);
  env->SetFloatArrayRegion(xyzResult, 1, 1, tmp);
  env->SetFloatArrayRegion(xyzResult, 2, 1, tmp);

  return xyzResult;
}

/*
 * Class:     ReconstructionJava
 * Method:    waitKey
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_reconstructionAPI_ReconstructionAPI_waitKey
  (JNIEnv *, jclass, jlong time){
  waitKey(time);
}
