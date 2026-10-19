#include "subs.h"
#include "hook.h"

extern "C" {
void font_setCompactExtraPropertiesForSubs(u32 mode);
void font_setStandardExtraProperties();
u32 text_measureWidthUntilEnd(const char* start);
}

void SubtitleCtl::clear() {
  subtitle = NULL;
  currentPart = 0;
  currentPartRemainingTime = 0;
  GameFontClear(SUBS_FONT_TAG);
}

void SubtitleCtl::setSubtitle(const Subtitle* sub) {
  subtitle = sub;
  currentPart = 0;
  currentPartRemainingTime = subtitle->parts[currentPart].displayTime * 1000;
}

void SubtitleCtl::update(u32 deltaTicks) {
  if (subtitle == NULL) {
    return;
  }
  currentPartRemainingTime -= deltaTicks;
  if (currentPartRemainingTime <= 0) {
    currentPart++;
    if (currentPart == subtitle->partsCount) {
      clear();
      return;
    }
    currentPartRemainingTime = subtitle->parts[currentPart].displayTime * 1000;
    font_setCompactExtraPropertiesForSubs(subtitle->parts[currentPart].fontMode);
    GameFontClear(SUBS_FONT_TAG);
    GameFontSetColor(0xffffffff);
    GameFontSetGlyphProps(0x12, 0x12, 0);
    GameFontSetPosition(3, 2);
    GameFontDraw(subtitle->parts[currentPart].romajiText, SUBS_FONT_TAG);
    u32 translatedWidth = text_measureWidthUntilEnd((const char*)subtitle->parts[currentPart].translatedText);
    GameFontSetPosition(480 - translatedWidth - 3, 250);
    GameFontDraw(subtitle->parts[currentPart].translatedText, SUBS_FONT_TAG);
    font_setStandardExtraProperties();
  }
}
