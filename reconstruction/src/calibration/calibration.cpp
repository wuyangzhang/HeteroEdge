/*
 * calibration.cpp
 *
 *  Created on: Dec 19, 2017
 *      Author: wuyang
 */

#include "calibration.hpp"

/*
 *  Initialize the calibration class
 *  @param imgWidth, imgHieght
 *  @param chessboardWidth, chessboardHeight
 *  @return
 */

Calibration::Calibration(const int imgWidth, const int imgHeight,
		const int chessboardWidth, const int chessboardHeight,
		const float chessboardGridSize, const int MAX_CALIBRATED_IMAGE_COUNT) :
		MAX_CALIBRATED_IMAGE_COUNT(MAX_CALIBRATED_IMAGE_COUNT), IMG_WIDTH(
				imgWidth), IMG_HEIGHT(imgHeight), IMG_SIZE(
				cv::Size(imgWidth, imgHeight)), CHESSBOARD_WIDTH(
				chessboardWidth), CHESSBOARD_HEIGHT(chessboardHeight), CHESSBOARD_GRID_SIZE(
				chessboardGridSize), CHESSBOARD_SIZE(
				cv::Size(chessboardWidth, chessboardHeight)) {
	totalCalibratedImageCount = 0;
	singleCameracriteria = TermCriteria(TermCriteria::COUNT + TermCriteria::EPS,
			30, DBL_EPSILON);
	stereoCameraCriteria = TermCriteria(TermCriteria::COUNT + TermCriteria::EPS,
			30, 1e-5);
	singleCameraFlag = 0x00001 + 0x00008 + 0x00800 + 0x01000;
	stereoCameraFlag = 0x00001 + 0x00008 + 0x00800 + 0x01000;
}

/*
 *  Core function in stereo camera calibration
 *  @param left image & right image
 *  @return
 */
bool Calibration::findChessboard(const Mat& img1, const Mat& img2,
		bool writeChessBoardImgToFile) {

	/* input images check */
	assert(!img1.empty() && !img2.empty() && img1.size() == img2.size());

	bool found1 = false, found2 = false;

	/* find all grid corners of a chess board */
	found1 = findChessboardCorners(img1, CHESSBOARD_SIZE, corners1, 1 | 4);
	found2 = findChessboardCorners(img2, CHESSBOARD_SIZE, corners2, 1 | 4);

	/* if we can find those corners, we use cornerSubpix function to further refine the coordinates of corners */
	/* convert image to gray scale */
	Mat gray1, gray2;
	cvtColor(img1, gray1, 6);
	cvtColor(img2, gray2, 6);

	if (found1) {
//    cornerSubPix(gray1, corners1, Size(5, 5), Size(-1, -1),
//                 TermCriteria(CV_TERMCRIT_EPS | CV_TERMCRIT_ITER, 30, 0.01));
		cornerSubPix(gray1, corners1, Size(5, 5), Size(-1, -1),
				TermCriteria(cv::TermCriteria::EPS, 30, 0.01));
		drawChessboardCorners(gray1, CHESSBOARD_SIZE, corners1, found1);
	}

	if (found2) {
//    cornerSubPix(gray2, corners2, Size(5, 5), Size(-1, -1),
//                 TermCriteria(CV_TERMCRIT_EPS | CV_TERMCRIT_ITER, 30, 0.01));
		cornerSubPix(gray1, corners1, Size(5, 5), Size(-1, -1),
				TermCriteria(cv::TermCriteria::EPS, 30, 0.01));
		drawChessboardCorners(gray2, CHESSBOARD_SIZE, corners2, found2);
	}

	/* we further store 3d coordinates of corners */
	std::vector<Point3f> obj;
	for (int i = 0; i < CHESSBOARD_HEIGHT; i++) {
		for (int j = 0; j < CHESSBOARD_WIDTH; j++) {
			obj.push_back(
					Point3f((float) j * CHESSBOARD_GRID_SIZE,
							(float) i * CHESSBOARD_GRID_SIZE, 0));
		}
	}

	if (found1 and found2) {

		imshow("Left chess board", gray1);
		imshow("Right chess board", gray2);
		waitKey(1);

		imgPoints1.push_back(corners1);
		imgPoints2.push_back(corners2);
		objectPoints.push_back(obj);

		if (writeChessBoardImgToFile) {
			printf("Save captured no. [%d] chess board stereo images \n",
					totalCalibratedImageCount);
			stringstream ss;
			ss << totalCalibratedImageCount;
			String index = ss.str();
			String l_path = "chessBoard_Left_" + index + ".png";
			String r_path = "chessBoard_Right_" + index + ".png";
			imwrite(l_path, img1);
			imwrite(r_path, img2);
		}

		printf("No. [%d] chess board stereo images have been processed!\n",
				totalCalibratedImageCount);
		totalCalibratedImageCount++;
		return true;
	}

	printf("Fail to find any chess board from inputs!\n");
	return false;
}

bool Calibration::findSufficientChessBoard() {
	return totalCalibratedImageCount >= MAX_CALIBRATED_IMAGE_COUNT;
}

void Calibration::finishFindChessboard() {
	for (unsigned int i = 0; i < imgPoints1.size(); i++) {
		vector<Point2f> v1, v2;
		for (unsigned int j = 0; j < imgPoints1[i].size(); j++) {
			v1.push_back(
					Point2f((double) imgPoints1[i][j].x,
							(double) imgPoints1[i][j].y));
			v2.push_back(
					Point2f((double) imgPoints2[i][j].x,
							(double) imgPoints2[i][j].y));
		}
		leftPoints.push_back(v1);
		rightPoints.push_back(v2);
	}
}

double Calibration::singleCameraCalibration(const int whichCamera) {
	assert((whichCamera == LEFT) or (whichCamera == RIGHT));

	if (whichCamera == LEFT) {
		return (leftRet = calibrateCamera(objectPoints, leftPoints, IMG_SIZE,
				cameraMatrix1, distCoeffs1, R1, T1, singleCameraFlag,
				singleCameracriteria));
	} else {
		return (rightRet = calibrateCamera(objectPoints, rightPoints, IMG_SIZE,
				cameraMatrix2, distCoeffs2, R2, T2, singleCameraFlag,
				singleCameracriteria));
	}

}

double Calibration::stereoCameraCalibration() {
	return stereoRet = cv::stereoCalibrate(objectPoints, leftPoints,
			rightPoints, cameraMatrix1, distCoeffs1, cameraMatrix2, distCoeffs2,
			IMG_SIZE, R, T, E, F, stereoCameraFlag, stereoCameraCriteria);
}

double Calibration::calibrationQualityCheck() {
	double err = 0;
	int npoints = 0;
	vector<Vec3f> lines[2];

	for (int i = 0; i < totalCalibratedImageCount; i++) {
		int npt = (int) leftPoints[i].size();
		Mat imgpt[2];
		imgpt[0] = Mat(leftPoints[i]);
		imgpt[1] = Mat(rightPoints[i]);
		undistortPoints(imgpt[0], imgpt[0], cameraMatrix1, distCoeffs1, Mat(),
				cameraMatrix1);
		undistortPoints(imgpt[1], imgpt[1], cameraMatrix2, distCoeffs2, Mat(),
				cameraMatrix2);
		computeCorrespondEpilines(imgpt[0], 1, F, lines[0]);
		computeCorrespondEpilines(imgpt[1], 2, F, lines[1]);

		for (int j = 0; j < npt; j++) {
			double errij = fabs(
					leftPoints[i][j].x * lines[1][j][0]
							+ leftPoints[i][j].y * lines[1][j][1]
							+ lines[1][j][2])
					+ fabs(
							rightPoints[i][j].x * lines[0][j][0]
									+ rightPoints[i][j].y * lines[0][j][1]
									+ lines[0][j][2]);
			err += errij;
		}

		npoints += npt;
	}
	/* average epiolar err */
	return err / npoints;
}

void Calibration::readCalibratedParamFromFile(const String filename) {
	FileStorage fs(filename, FileStorage::READ);

	if (!fs.isOpened()) {
		std::cerr << "failed to open " << filename << endl;
		return;
	}

	std::cout << "start to read calibrated parameters" << endl;

	fs["cameraMatrix1"] >> cameraMatrix1;
	fs["distCoeffs1"] >> distCoeffs1;
	fs["cameraMatrix2"] >> cameraMatrix2;
	fs["distCoeffs2"] >> distCoeffs2;
	fs["R"] >> R;
	fs["T"] >> T;

	fs.release();
}

void Calibration::writeCalibratedParamToFile(const String filename) {
	FileStorage fs(filename, FileStorage::WRITE);

	if (!fs.isOpened()) {
		std::cerr << "failed to open " << filename << endl;
		return;
	}

	fs << "cameraMatrix1" << cameraMatrix1 << "distCoeffs1" << distCoeffs1
			<< "cameraMatrix2" << cameraMatrix2 << "distCoeffs2" << distCoeffs2;
	fs << "R" << R << "T" << T;
	fs.release();
}

