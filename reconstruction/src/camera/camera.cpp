/*
 * camera.cpp
 *
 *  Created on: Dec 21, 2017
 *      Author: wuyang
 */

#include "camera.hpp"

Camera::Camera(int cameraId)
    : cameraId(cameraId) {
  this->videoCapture = new VideoCapture(cameraId);
  assert(videoCapture->isOpened());
}

Camera::~Camera() {
  delete this->videoCapture;
}

void Camera::captureVideo(Mat& mat) {
  *this->videoCapture >> mat;
}

void Camera::display(const String windowName, const Mat& mat) {
  imshow(windowName, mat);
}

int Camera::getCameraId() {
  return cameraId;
}


double Camera::getCameraFPS() {
  return videoCapture->get(cv::CAP_PROP_FPS);
}



void Camera::setCameraResolution(const int width, const int height) {
  videoCapture->set(cv::CAP_PROP_FRAME_WIDTH, width);
  videoCapture->set(cv::CAP_PROP_FRAME_HEIGHT, height);
}


void Camera::stereoCameraSplit(Mat& input, Mat& left, Mat& right) {
  int rows = input.rows;
  int cols = input.cols;
  left = input(Rect(0, 0, cols / 2, rows)).clone();
  right = input(Rect(cols / 2, 0, cols / 2, rows)).clone();
}
