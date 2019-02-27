#include <unistd.h>
#include <string>
#include <fstream>

#include "disparity.hpp"
#include <sys/time.h>

//#define GPU_MODE

DisparityMap::DisparityMap(int imgWidth, int imgHeight, int algorithm) :
		imgWidth(imgWidth), imgHeight(imgHeight), algorithm(algorithm) {
	numberOfDisparity = 160;

}

void print_help() {
	printf(
			"\nDisparity map calculation converting left and right images into disparity\n");
}

void DisparityMap::init() {

	//disparity algorithm initialization
	switch (algorithm) {
	case SGBM: {
		sgbm = StereoSGBM::create(10, 128, 5, 24 * 11 * 11, 4 * 24 * 11 * 11);
		sgbm->setNumDisparities(numberOfDisparity);  //112
		sgbm->setBlockSize(5);  // 9
		sgbm->setPreFilterCap(271);  //31
		sgbm->setP1(322);  //31
		sgbm->setP2(8800);  //31
		sgbm->setMinDisparity(10);
		sgbm->setUniquenessRatio(15);  // 15
		sgbm->setSpeckleWindowSize(0);  //100
		sgbm->setSpeckleRange(8);  //32
		sgbm->setDisp12MaxDiff(1);  //1
		sgbm->setUniquenessRatio(10);
		sgbm->setDisp12MaxDiff(1);
		sgbm->setMode(StereoSGBM::MODE_SGBM);
		break;
	}

	case BM: {
//      int PreFilterType = 0, PreFilterCap = 50, MinDisparity = 7,
//          UniqnessRatio = 1, TextureThreshold = 0, SpeckleRange = 0,
//          SADWindowSize = 68, smallBlockSize = 10, SpackleWindowSize = 0,
//          numDisparities = 160, PreFilterSize = 23, disp12MaxDiff(10);

		bm = StereoBM::create(160, 25);
		bm->setPreFilterSize(23);
		bm->setPreFilterCap(50);
		bm->setBlockSize(69);
		bm->setMinDisparity(-23);
		bm->setNumDisparities(160);
		bm->setTextureThreshold(0);
		bm->setUniquenessRatio(6);
		bm->setSpeckleWindowSize(0);
		bm->setDisp12MaxDiff(10);
		break;
	}

	}

}

/*
 * core function to calculate disparity.
 * input : left and right images
 * output: disparity
 *
 */
void DisparityMap::calDisparityMap(Mat& leftImg, Mat& rightImg,
		Mat& disparityMap) {

	//check image validation
	assert(
			!leftImg.empty() && !rightImg.empty()
					&& leftImg.size() == rightImg.size());

	//convert to gray
	Mat left, right;
	cvtColor(leftImg, left, COLOR_BGR2GRAY);
	cvtColor(rightImg, right, COLOR_BGR2GRAY);

	Mat disp(left.size(), CV_8U);

	switch (this->algorithm) {
	case SGBM:
		sgbm->compute(leftImg, rightImg, disparityMap);
		break;

	case BM:
		bm->compute(left, right, disparityMap);
		break;
	}
}

#ifdef GPU_MODE
void DisparityMap::calDisparityMapGPU(Mat& leftImg, Mat& rightImg,
		Mat& disparityMap) {

	assert(
			!leftImg.empty() && !rightImg.empty()
					&& leftImg.size() == rightImg.size());

	cuda::GpuMat d_left, d_right;
	//upload mat to gpu

	Mat left, right;
	struct timeval tpstart, tpend;
	this->bmGPU = cuda::createStereoBM(112);

	cvtColor(leftImg, left, COLOR_BGR2GRAY);
	cvtColor(rightImg, right, COLOR_BGR2GRAY);

	gettimeofday(&tpstart, NULL);
	d_left.upload(left);
	d_right.upload(right);

	gettimeofday(&tpend, NULL);
	long timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
			+ (tpend.tv_usec - tpstart.tv_usec) / 1000;
	printf("Time of upload disparity in gpu %ld ms\n", timuse);

	gettimeofday(&tpstart, NULL);

	bmGPU->compute(d_left, d_right, d_disp);

	gettimeofday(&tpend, NULL);
	timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
			+ (tpend.tv_usec - tpstart.tv_usec) / 1000;
	printf("Time of compute disparity in gpu %ld ms\n", timuse);

	gettimeofday(&tpstart, NULL);
	d_disp.download(disparityMap);
	gettimeofday(&tpend, NULL);
	timuse = 1000 * (tpend.tv_sec - tpstart.tv_sec)
			+ (tpend.tv_usec - tpstart.tv_usec) / 1000;
	printf("Time of download disparity in gpu %ld ms\n", timuse);

//    case BP:
//    cuda::StereoBeliefPropagation::estimateRecommendedParams(this->imgWidth, this->imgHeight, ndisp, iters, levels);
//    cout << ndisp << iters << levels;
//    this->bp = cuda::createStereoBeliefPropagation(ndisp, iters, levels, CV_32F);
//    bp->compute(d_left, d_right, d_disp);
//    d_disp.download(this->disparityMap);
//    break;
//
//    case CSBP:
//    cuda::StereoConstantSpaceBP::estimateRecommendedParams(this->imgWidth, this->imgHeight, ndisp, iters, levels, nr_plane);
//    cout << ndisp << iters << levels;
//    this->csbp = cuda::createStereoConstantSpaceBP(1024, iters, levels, CV_32F);
//    csbp->compute(d_left, d_right, d_disp);
//    d_disp.download(this->disparityMap);
//    break;

}

//void
//DisparityMap::showDisparityMap(bool color) {
//  Mat result;
//  if(color) {
//    drawColorDisp();
//    result = this->colorDisparityMap;
//  } else {
//    cv::ximgproc::getDisparityVis(disparityMap, filter_Disparity_vis, vis_mult);
//  }
//
//  imshow("disparity map", filter_Disparity_vis);
//  waitKey(1);
//}

#ifdef GPU_MODE
void DisparityMap::cudaDrawColorDisparityMap() {
cuda::drawColorDisp(this->d_disp, this->gpu_colorDisparityMap, 2048);
this->gpu_colorDisparityMap.download(this->colorDisparityMap);
}
#endif

void DisparityMap::readParam(std::string filename, int algorithm) {
ifstream readFile;
readFile.open(filename);
if (readFile.is_open()) {
	switch (algorithm) {
	case BM:
		std::string numDisparities, blockSize, minDisparity, textureThreshold,
				uniquenessRatio, speckleWindowSize, speckleRange, disp12maxDiff;
		while (!readFile.eof()) {
			getline(readFile, numDisparities);
			getline(readFile, blockSize);
			bm->setNumDisparities(stoi(numDisparities));
			bm->setBlockSize(stoi(blockSize));
			break;
		}

	}
}

readFile.close();
}

void DisparityMap::printParam(int algorithm) {
switch (algorithm) {
case BM:
	cout << "print bm parameters" << endl;
	cout << "numDisparities: " << bm->getNumDisparities(); // << " blockSize: " << bm->getBlockSize() << endl;
	break;
}
}
#endif

void DisparityMap::show_webCamera() {
VideoCapture left_cap(0);
VideoCapture right_cap(1);
Mat left_frame, right_frame;

int captureIndex = 0;

while (1) {
	left_cap >> left_frame;
	right_cap >> right_frame;

	imshow("right camera", right_frame);
	imshow("left camera", left_frame);

	char key = (char) waitKey(1);
	if (key == 27) {
		break;  //esc to exit
	} else if (key == ' ') {
		captureIndex++;
		cout << "save captured images" << endl;
		stringstream ss;
		ss << captureIndex;
		String index = ss.str();
		String l_path = "l_" + index + ".png";
		String r_path = "r_" + index + ".png";
		imwrite(l_path, left_frame);
		imwrite(r_path, right_frame);
	} else if (key == 's') {
		cout << "analyze disparity map" << endl;
	}
}

left_cap.release();
right_cap.release();
destroyAllWindows();
}

