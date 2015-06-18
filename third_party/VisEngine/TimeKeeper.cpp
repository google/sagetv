#ifndef WIN32
#include <sys/time.h>
#else
#endif /** !WIN32 */
#include <stdlib.h>
#include "Common.hpp"
#include "TimeKeeper.hpp"
#include "RandomNumberGenerators.hpp"

TimeKeeper::TimeKeeper(mathtype presetDuration, mathtype smoothDuration, mathtype easterEgg)
  {    
    _smoothDuration = smoothDuration;
    _presetDuration = presetDuration;
    _easterEgg = easterEgg;

#ifndef WIN32
	gettimeofday ( &this->startTime, NULL );
#else
	startTime = GetTickCount();
#endif /** !WIN32 */

	UpdateTimers();
  }

  void TimeKeeper::UpdateTimers()
  {
#ifndef WIN32
	_currentTime = getTicks ( &startTime ) * mathval(0.001f);
#else
	_currentTime = getTicks ( startTime ) * mathval(0.001f);
#endif /** !WIN32 */

	_presetFrameA++;
	_presetFrameB++;

  }

  void TimeKeeper::StartPreset()
  {
    _isSmoothing = false;
    _presetTimeA = _currentTime;
    _presetFrameA = 1;
    _presetDurationA = sampledPresetDuration();
  }
  void TimeKeeper::StartSmoothing()
  {
    _isSmoothing = true;
    _presetTimeB = _currentTime;
    _presetFrameB = 1;
    _presetDurationB = sampledPresetDuration();
  }
  void TimeKeeper::EndSmoothing()
  {
    _isSmoothing = false;
    _presetTimeA = _presetTimeB;
    _presetFrameA = _presetFrameB;
    _presetDurationA = _presetDurationB;
  }
 
  bool TimeKeeper::CanHardCut()
  {
    return ((_currentTime - _presetTimeA) > mathval(HARD_CUT_DELAY));
  }

  mathtype TimeKeeper::SmoothRatio()
  {
    return mathdiv((_currentTime - _presetTimeB), _smoothDuration);
  }
  bool TimeKeeper::IsSmoothing()
  {
    return _isSmoothing;
  }

  mathtype TimeKeeper::GetRunningTime()
  {
    return _currentTime;
  } 

  mathtype TimeKeeper::PresetProgressA()
  {
    if (_isSmoothing) return mathval(1.0f);
    else return mathdiv((_currentTime - _presetTimeA),_presetDurationA);
  }
  mathtype TimeKeeper::PresetProgressB()
  {
    return mathdiv((_currentTime - _presetTimeB), _presetDurationB);
  }

int TimeKeeper::PresetFrameB()
  {
    return _presetFrameB;
  }

int TimeKeeper::PresetFrameA()
  {
    return _presetFrameA;
  }

mathtype TimeKeeper::sampledPresetDuration() {
#ifdef WIN32
	return  _presetDuration;
#else
		return mathval(projectM_fmax(1, projectM_fmin(60, RandomNumberGenerators::gaussian
			(_presetDuration, _easterEgg))));
#endif
}
