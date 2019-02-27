/*
 * timer.cpp
 *
 *  Created on: Jan 3, 2018
 *      Author: wuyang
 */

#include "Timer.h"

Timer::Timer() {
 timeUse = 0.f;

}

Timer::~Timer() {
  // TODO Auto-generated destructor stub
}

void Timer::start() {
  gettimeofday(&tpstart, NULL);
}

void Timer::end() {
  gettimeofday(&tpend, NULL);
  countElapseTime();
}

inline void Timer::countElapseTime(){
  timeUse = 1000 * (tpend.tv_sec - tpstart.tv_sec) + (tpend.tv_usec - tpstart.tv_usec) / 1000;
}

long Timer::getTimeUse(){
  return timeUse;
}
