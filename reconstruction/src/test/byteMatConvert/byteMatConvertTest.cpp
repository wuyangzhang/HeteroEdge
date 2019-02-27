#include "byteMatConvertTest.hpp"
#include "../../timer/Timer.h"
#include <sys/time.h>

void testByteMatConvert() {
  Mat testMat = imread(
      "/home/wuyang/git/reconstruction/Reconstruction/rectL.png");
  assert(!testMat.empty());
  printf("Read image size is %d x %d\n", testMat.rows, testMat.cols);

  const int size = testMat.rows * testMat.cols * 3;
  uchar* byteTest = new uchar[size];

  Timer timer;
  timer.start();

  MatToByte(byteTest, testMat);
  timer.end();
  printf("mat convert to byte cost %ld ms\n", timer.getTimeUse());

  Mat convertMat;
  timer.start();
  ByteToMat(byteTest, convertMat, testMat.cols, testMat.rows);
  timer.end();
  printf("byte convert to mat cost %ld ms\n", timer.getTimeUse());

  if (convertMat.rows == testMat.rows && convertMat.cols == testMat.cols) {
    printf("convert successfully!\n");
  } else {
    printf("convert fails! convert mat size (%d, %d)\n", convertMat.rows,
           convertMat.cols);
  }

  imshow("result", convertMat);
  waitKey();
}
