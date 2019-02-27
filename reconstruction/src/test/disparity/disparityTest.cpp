/*
 * disparityTest.cpp
 *
 *  Created on: Dec 22, 2017
 *      Author: wuyang
 */
#include <sys/time.h>

#include "disparityTest.hpp"
#include "../../camera/camera.hpp"
#include "../../rectification/rectification.hpp"
#include "../../disparity/disparity.hpp"
DisparityMap disparityMap(640, 480, DisparityMap::BM);
Mat disparity;
Mat leftImg, rightImg;
Mat uLeftImg, uRightImg;
Mat xyz;
int PreFilterType = 0, PreFilterCap = 50, MinDisparity = 7, UniqnessRatio = 1,
		TextureThreshold = 0, SpeckleRange = 0, SADWindowSize = 68,
		smallBlockSize = 10, SpackleWindowSize = 0, numDisparities = 159,
		PreFilterSize = 23, lambda(8000), sigma(1.5), vis(5), disp12MaxDiff(10);

Rectification rectification;

void onTrackBar(int);

void checkZ(int event, int x, int y, int, void*) {
	Point origin;
	switch (event) {
	case EVENT_LBUTTONDOWN:
		cout << "mouse event\n" << x << y;
		origin = Point(x, y);
		cout << origin << "world coordinate is : " << xyz.at<Vec3f>(origin)
				<< endl;
//      Mat tmp = leftImg.clone();
		circle(uLeftImg, origin, 5, (0, 0, 255), 3);
		char re[50];
		sprintf(re, "[Depth] : %.0f mm", -xyz.at<Vec3f>(origin).val[2]);
		putText(uLeftImg, re, Point(origin.x - 100, origin.y + 30),
				FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2, LINE_AA);
		imshow("rectified left", uLeftImg);
		break;
	}
}

void savepoint(const char* filename, const Mat& mat) {

	const double max_z = 1.0e4;
	FILE* fp = fopen(filename, "wt");
	for (int y = 0; y < mat.rows; y++) {
		for (int x = 0; x < mat.cols; x++) {
			Vec3f point = mat.at<Vec3f>(y, x);
			if (fabs(point[2] - max_z) < FLT_EPSILON || fabs(point[2]) > max_z)
				continue;
			fprintf(fp, "%f %f %f\n", point[0], point[1], point[2]);
		}
	}
	fclose(fp);
}

void testDisparity() {

	Camera camera(1);
	camera.setCameraResolution(1280, 480);

	Rectification rectification;
	struct timeval tpstart, tpend;
	gettimeofday(&tpstart, NULL);
	rectification.init("calibration.xml", "rectification.xml", Size(640, 480));
	gettimeofday(&tpend, NULL);
	long timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
			+ (tpend.tv_usec - tpstart.tv_usec) / 1000;
	printf("Time of rectification initialization %ld ms\n", timuse);

	DisparityMap disparityMap(640, 480, DisparityMap::BM);
	disparityMap.init();

	Mat srcFromStereoCamera;
	Mat disparity;

	while (1) {
		camera.captureVideo(srcFromStereoCamera);
		camera.display("show", srcFromStereoCamera);
		camera.stereoCameraSplit(srcFromStereoCamera, leftImg, rightImg);

		//rectification results
		rectification.rectifyStereoImages(leftImg, rightImg, uLeftImg,
				uRightImg);
		imshow("rectified left", uLeftImg);
		imshow("rectified right", uRightImg);

		//find disparity map
		struct timeval tpstart, tpend;
		gettimeofday(&tpstart, NULL);
		disparityMap.calDisparityMap(uLeftImg, uRightImg, disparity);
		gettimeofday(&tpend, NULL);
		long timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
				+ (tpend.tv_usec - tpstart.tv_usec) / 1000;
		printf("Time of disparity calculation %ld ms\n", timuse);

		Mat disp8;
		disparity.convertTo(disp8, CV_8U,
				255 / (disparityMap.getNumberOfDisparity() * 16.));
		Mat Q;
		rectification.getQMatrix(Q);
		printf("disparity type %d, Q type %d\n", disparity.type(), Q.type());
		cv::reprojectImageTo3D(disparity, xyz, Q, true);
		xyz = xyz * 16;
		imshow("disparity", disp8);
		moveWindow("disparity", 20, 20);

		setMouseCallback("rectified left", checkZ, 0);

		cout << "event done\n";
		char key = (char) waitKey(500);
		if (key == 27) {
			break;
		}

		if (key == 's') {
			printf("save stereo images!\n");
			imwrite("./left.png", leftImg);
			imwrite("./right.png", rightImg);
			imwrite("./uleft.png", uLeftImg);
			imwrite("./uright.png", uRightImg);
			imwrite("disparity.png", disp8);
		}
	}
	return;

}

void testDisparityGPU(Mat& left, Mat& right) {
	struct timeval tpstart, tpend;
	gettimeofday(&tpstart, NULL);
	Mat disparity;
	disparityMap.calDisparityMapGPU(left, right, disparity);
	gettimeofday(&tpend, NULL);
	long timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
			+ (tpend.tv_usec - tpstart.tv_usec) / 1000;
	printf("Time of disparity gpu calculation %ld ms\n", timuse);

}
void disparityFilter(Mat_<float> disp, int numberOfDisparity,
		int miniDisparity) {

	Mat_<float> disp1;
	float lastPixel = 10;

	for (int i = 0; i < disp.rows; i++) {
		for (int j = numberOfDisparity; j < disp.cols; j++) {
			if (disp.at<float>(i, j) < miniDisparity) {
				disp.at<float>(i, j) = lastPixel;
			} else {
				lastPixel = disp.at<float>(i, j);
			}
		}
	}

	int an = 4;
	copyMakeBorder(disp, disp1, an, an, an, an, BORDER_REPLICATE);
	Mat element = getStructuringElement(MORPH_ELLIPSE,
			Size(an * 2 + 1, an * 2 + 1));
	morphologyEx(disp1, disp1, CV_MOP_OPEN, element);
	morphologyEx(disp1, disp1, CV_MOP_CLOSE, element);
	disp = disp1(Range(an, disp.rows - an), Range(an, disp.cols - an)).clone();
	imshow("filter", disp);
}

//void WLSFilter(Mat& leftDisp, Mat& rightDisp, Mat& filter) {
//  Ptr<ximgproc::DisparityWLSFilter> wlsFilter =
//      ximgproc::createDisparityWLSFilter(disparityMap.bm);
//  wlsFilter->setLambda(lambda);
//  double sigmaVal = sigma / 100. * 10;
//  wlsFilter->setSigmaColor(sigmaVal);
//  wlsFilter->filter(leftDisp, leftImg, filter, rightDisp);
//}

void onTrackBar(int) {

	if (PreFilterSize % 2 == 0) {
		PreFilterSize = PreFilterSize + 1;
	}

	if (PreFilterSize < 5) {
		PreFilterSize = 5;
	}

	if (SADWindowSize % 2 == 0) {
		SADWindowSize = SADWindowSize + 1;
	}

	if (numDisparities % 16 != 0) {
		numDisparities = numDisparities + (16 - numDisparities % 16);
	}

	if (SADWindowSize < 5) {
		SADWindowSize = 5;
	}

	if (numDisparities < 16) {
		numDisparities = 16;
	}

	printf(
			"Tune Parameters\n numDisparity %d, blockSize %d, filterSize %d, filterCap %d, minDisparity %d, texture %d, speckleWindowSize %d, uniqueness %d, maxDiff %d\n",
			numDisparities, SADWindowSize, PreFilterSize, PreFilterCap,
			MinDisparity - 30, TextureThreshold, SpackleWindowSize,
			UniqnessRatio, disp12MaxDiff);
	disparityMap.bm->setNumDisparities(numDisparities);
	disparityMap.bm->setBlockSize(SADWindowSize);
	disparityMap.bm->setPreFilterType(0);
	disparityMap.bm->setPreFilterSize(PreFilterSize);
	disparityMap.bm->setPreFilterCap(PreFilterCap);
	disparityMap.bm->setMinDisparity(MinDisparity - 30);
	disparityMap.bm->setTextureThreshold(TextureThreshold);
	disparityMap.bm->setSpeckleRange(SpeckleRange);
	disparityMap.bm->setSpeckleWindowSize(SpackleWindowSize);
	disparityMap.bm->setUniquenessRatio(UniqnessRatio);
	disparityMap.bm->setSmallerBlockSize(smallBlockSize);
	disparityMap.bm->setDisp12MaxDiff(disp12MaxDiff);
	disparityMap.calDisparityMap(leftImg, rightImg, disparity);
	Mat disparityRight;
	disparityMap.calDisparityMap(rightImg, leftImg, disparityRight);

	Mat disp8, disp8Right;
	disparity.convertTo(disp8, CV_8U, 255 / (numDisparities * 16.));
	imshow("disparityTune", disp8);
	//disp8.convertTo(disp8, CV_32F);
	//disparityFilter(disparity, numDisparities, MinDisparity);
	//reprojection..generate xyz
	Mat Q;
	rectification.getQMatrix(Q);
	cv::reprojectImageTo3D(disparity, xyz, Q, true);
	xyz = xyz * 16;

}

