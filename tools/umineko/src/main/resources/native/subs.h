#ifndef SUBS_H_
#define SUBS_H_

#include "platform.h"

struct SubtitlePart {
  const u8* const translatedText;
  const u8* const romajiText;
  const u16 displayTime;
  const u8 fontMode;
};

struct Subtitle {
  const u32 bgmId;
  const u8 partsCount;
  const SubtitlePart* const parts;
};

struct SubtitleCtl {
  const Subtitle* subtitle;
  u16 currentPart;
  i32 currentPartRemainingTime;

  void clear();
  void setSubtitle(const Subtitle* sub);
  void update(u32 deltaTicks);
};

#endif
