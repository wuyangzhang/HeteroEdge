package reconstructionAPI;

import topologyResourceConsumption.Timer;

public class ReconstructionAPI {
	final static String libraryName = "Reconstruction";
	static {
		try {
			System.loadLibrary(libraryName);
		} catch (UnsatisfiedLinkError e) {
			System.err.println("Fail to load library " + libraryName);
		}
	}

	/* native method */

	/*
	 * read image to a byte array
	 * 
	 * @param imageAddr, image file address
	 * 
	 * @param imageByte image byte array to store the image
	 */
	public static native void readImage(String imageAddr, byte[] imageByte);

	public static native long cameraInit(int cameraId);

	public static native void releaseCamera(long cameraPointer);

	/*
	 * read image from camera to a byte array
	 * 
	 * @param imageAddr, image file address
	 * 
	 * @param imageByte image byte array to store the image
	 */
	public static native void readFromCamera(long cameraPointer, byte[] leftImgByte, byte[] rightImgByte);

	/*
	 * partition an image byte array to multiple sub image arrays
	 * 
	 * @param inputImg input image array
	 * 
	 * @param partitionNum
	 * 
	 * @param imgRows
	 * 
	 * @param imgCols
	 */
	public static native byte[][] partitionImage(byte[] imageByte, int partitionNum, int imgRows, int imgCols);

	/*
	 * partition an image byte array to multiple sub image arrays
	 * 
	 * @param inputImg input image array
	 * 
	 * @param partitionNum
	 * 
	 * @param imgRows
	 * 
	 * @param imgCols
	 */
	public static native byte[][] partitionImage(byte[] imageByte, int partitionNum, int imgRows, int imgCols,
			int compensationRowNum);

	/*
	 * partition an image byte array to multiple sub image arrays
	 * 
	 * @param inputImg input image array
	 * 
	 * @param partitionNum
	 * 
	 * @param imgRows
	 * 
	 * @param imgCols
	 */
	public static native byte[][] partitionImage(byte[] imageByte, int partitionNum, int imgRows, int imgCols,
			int upCompensationRowNum, int downCompensationRowNum);

	/*
	 * merge multiple sub image arrays to a single image array
	 * 
	 * @param partitionImgSet, partition image array
	 * 
	 * @param mergedImg
	 * 
	 * @param imgRows : total rows of each partition image
	 * 
	 * @param imgCols : total columns of each partition image
	 * 
	 * @param upCompensation
	 */
	public static native byte[] mergeImage(byte[][] partitionImgSet, int imgRows, int imgCols, int upCompensation,
			int colorWay, int matType);

	/*
	 * initialize rectification
	 * 
	 * @param calibrationFileAddr file to store calibration parameters
	 * 
	 * @param rectificationFileAddr file to output rectification parameters
	 * 
	 * @imgRows
	 * 
	 * @imgCols
	 */
	public static native byte[][] initImageRectification(String calibrationFileAddr, String rectificationFileAddr,
			int imgRows, int imgCols);

	/*
	 * rectify an image
	 * 
	 * @param leftImg
	 * 
	 * @param rightImg
	 * 
	 * @param uLeftImg undistorted left image
	 * 
	 * @param uRightImg undistorted right image
	 */
	public static native void rectifyStereoImages(byte[] leftImg, byte[] rightImg, byte[] uLeftImg, byte[] uRightImg,
			int imgRows, int imgCols, byte[] map1x, byte[] map1y, byte[] map2x, byte[] map2y);

	/*
	 * initialize disparity calculation
	 * 
	 * @param imgRows
	 * 
	 * @param imgCols
	 * 
	 * @param disparityAlgorithm bm 0, sgbm 1
	 */
	public static native void initDisparityCalculation(int imgRows, int imgCols, int disparityAlgorithm);

	/*
	 * calculate disparity map for a pair of stereo images
	 * 
	 * @param uLeftImg
	 * 
	 * @param uRightImg
	 * 
	 * @param disparityMap
	 */
	public static native byte[] calculateDisparityMap(byte[] uLeftImg, byte[] uRightImg, int imgCols,
			int disparityAlgorithm);


	public static native byte[] calculateDisparityMapGPU(byte[] uLeftImg, byte[] uRightImg, int imgCols,
			int disparityAlgorithm);
	/*
	 * display disparity map
	 * 
	 * @param disparity map
	 */
	public static native void displayDisparityMap(byte[] disparity, int imgRows, int imgCols, int disparityNum,
			boolean waitKey);

	/*
	 * reproject 2d coordinates to 3d
	 * 
	 * @param disparity
	 * 
	 * @param xyz
	 * 
	 * @param Q
	 */
	public static native byte[] reprojectTo3D(byte[] disparity, byte[] Q, int imgRows, int imgCols);

	/*
	 * query the depth of a given coordinate (x, y)
	 * 
	 * @param x, y coordinate on the input image
	 */
	public static native float[] queryDepth(byte[] xyz, int x, int y, int imgRows, int imgCols);

	/*
	 * display an image
	 */
	public static native void displayImage(String imgName, byte[] img, int imgCols, boolean waitKey);

	/*
	 * test the function of image partition and merge if merged result equals to the
	 * original image, return successfully..
	 */

	public static native void waitKey(long time);

	public static void main(String args[]){
		ReconstructionAPI api = new ReconstructionAPI();
		String leftImgAddr = "/home/wuyang/ar/cpp/uleft.png";
		String rightImgAddr = "/home/wuyang/ar/cpp/uright.png";
		byte leftImg[] = new byte[640 * 480 * 3];
		byte rightImg[] = new byte[640 * 480 * 3];
		api.readImage(leftImgAddr, leftImg);
		api.readImage(rightImgAddr, rightImg);

		for(int i = 0; i < 100; i++){
			long start = System.nanoTime();
			api.calculateDisparityMapGPU(leftImg, rightImg, 480, 0);
			long end = System.nanoTime();
			System.out.println("running time is " + (end - start) / 1000000);
		}
	}
}
