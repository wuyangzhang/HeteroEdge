/*
 * disparityTest.hpp
 *
 *  Created on: Dec 22, 2017
 *      Author: wuyang
 */

#ifndef SRC_DISPARITY_DISPARITYTEST_HPP_
#define SRC_DISPARITY_DISPARITYTEST_HPP_

#include <opencv2/core/utility.hpp>
#include "opencv2/highgui.hpp"
#include "opencv2/imgproc.hpp"

void testDisparity();
void testDisparityParameters();
void testDisparityGPU(cv::Mat& left, cv::Mat& right);

#endif /* SRC_DISPARITY_DISPARITYTEST_HPP_ */
