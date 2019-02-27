/*
 * timer.h
 *
 *  Created on: Jan 3, 2018
 *      Author: wuyang
 */

#ifndef SRC_TIMER_TIMER_H_
#define SRC_TIMER_TIMER_H_

#include <sys/time.h>
#include <iostream>
#include <stdio.h>
class Timer {
 public:
  Timer();
  virtual ~Timer();
  void start();
  void end();
  void countElapseTime();
  long getTimeUse();
 private:
  struct timeval tpstart, tpend;
  long timeUse;
};

#endif /* SRC_TIMER_TIMER_H_ */
