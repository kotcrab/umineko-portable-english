#include "hook.h"
#include "platform.h"
#include "subs.h"
#include "subs_generated.h"

#define FAST_CONTROL_CODE_CHECK

#ifndef SCRIPT_ZERO_WIDTH_SPACE
#error "Script zero width space char must be defined"
#endif

#ifdef PORTABLE_1
#define LINE_INSTRUCTION_PUT_GLYPH 8
#elif PORTABLE_2
#define LINE_INSTRUCTION_PUT_GLYPH 9
#else
#error "Mode must be defined"
#endif

extern "C" {
typedef struct {
  u16 ch;
  u8 xAdvance;
  u8 _structPadding;
} XAdvanceEx;

typedef struct {
  u16 ch1;
  u16 ch2;
  i8 kerning;
  u8 _structPadding1;
  u8 _structPadding2;
  u8 _structPadding3;
} KerningEx;

u64 common_previousTick = 0;
u32 common_previousChar = 0;
u32 common_inBacklog = 0;

u32 font_currentChar = 0;
u8* font_xAdvance = NULL;
i8* font_kerning = NULL;
u32* font_xAdvanceExCount = NULL;
u32* font_kerningExCount = NULL;
XAdvanceEx* font_xAdvanceEx = NULL;
KerningEx* font_kerningEx = NULL;

i32 font_extraWidthChange = 0;
i32 font_extraXAdvanceChange = 0;

u32 subs_nextBgmId = 0;
u32 subs_nextBgmWait = 1;
SubtitleCtl subs_ctl;

#if SCRIPT_FRAGMENT_MAX_SIZE != 0
u8* fragment_bytes = NULL;
u32 fragment_currentId = -1;
char fragment_currentPath[] = "disc0:/PSP_GAME/USRDIR/FRAG/0.BIN";
static const u32 fragment_currentPathIdIndex = 28;

const u32 fragment_sizes[] = {SCRIPT_FRAGMENT_SIZES};

void fragment_activate(u32 fragmentId) {
  if (fragment_currentId == fragmentId) {
    return;
  }
  if (fragmentId > SCRIPT_FRAGMENT_LAST_INDEX) {
    return;
  }
  fragment_currentId = fragmentId;
  fragment_currentPath[fragment_currentPathIdIndex] = '0' + fragment_currentId;
  i32 fd = sceIoOpen(fragment_currentPath, 1, 511);
  sceIoRead(fd, fragment_bytes, fragment_sizes[fragment_currentId]);
  sceIoClose(fd);
}

u8* fragment_remapText(u8* text) {
  if (text[0] == SCRIPT_FRAGMENT_TEXT_MARKER) {
    u32 fragmentId = text[1] >> 5;
    u32 fragmentOffset = ((text[1] & 0x1F) << 16) | (text[2] << 8) | text[3];
    fragment_activate(fragmentId);
    return fragment_bytes + fragmentOffset;
  }
  return text;
}
#endif

void initPatch() {
#if SCRIPT_FRAGMENT_MAX_SIZE != 0
  fragment_bytes = (u8*)GameAllocate(SCRIPT_FRAGMENT_MAX_SIZE, 0);
  fragment_activate(0);
#endif
  sceRtcGetCurrentTick(&common_previousTick);
}

static __attribute__((always_inline)) float roundFloat(float x) {
  return (float)(int)(x + (x < 0.0f ? -0.5f : 0.5f));
}

static __attribute__((always_inline)) u32 startsWith(const char* text, const char* prefix) {
  while (*prefix != '\0') {
    if (*text == '\0') {
      return 0;
    }

    if (*text != *prefix) {
      return 0;
    }

    text++;
    prefix++;
  }

  return 1;
}

static __attribute__((always_inline)) u32 sjis_charSize(const unsigned char c) {
  // if (c & 0x80) {
  // return 2;
  // }
  // return 1;
  return 1 + (c >> 7); // same as above

  // above is faster and matches game code behavior, we know for sure other range is unused
  // if ((c >= 0x81 && c <= 0x9F) ||
  // (c >= 0xE0 && c <= 0xFC)) {
  // return 2;
  // }
  // return 1;
}

static __attribute__((always_inline)) u16 sjis_firstChar(const char* text, u32 size) {
  const unsigned char* t = reinterpret_cast<const unsigned char*>(text);
  return size == 1 ? static_cast<u16>(t[0]) : static_cast<u16>((static_cast<u16>(t[0]) << 8) | t[1]);
}

const char* sjis_contains(const char* text, const char* containsText) {
  while (*text != '\0') {
    if (startsWith(text, containsText)) {
      return text;
    }

    text += sjis_charSize(static_cast<unsigned char>(*text));
  }
  return NULL;
}

#ifdef FAST_CONTROL_CODE_CHECK
bool text_isControlCode(u16 ch) {
  return ch >= 0x83BF && ch <= 0x83D6; // must match replacement range in ControlCodes.kt
}
#endif

const char* text_findNextControlCode(const char* text) {
  while (*text != '\0') {
    const u32 size = sjis_charSize(static_cast<unsigned char>(*text));
#ifdef FAST_CONTROL_CODE_CHECK
    if (text_isControlCode(sjis_firstChar(text, size))) {
      break;
    }
#else
    const char currentChar[3] = {text[0], size == 2 ? text[1] : '\0', '\0'};
    if (sjis_contains(GameControlCodes, currentChar)) {
      break;
    }
#endif
    text += size;
  }
  return text;
}

u32 text_needsNewLineCalc(const char* text) {
  if (*text == '\0') {
    return 0;
  }
  const u32 size = sjis_charSize(static_cast<unsigned char>(*text));
#ifdef FAST_CONTROL_CODE_CHECK
  if (text_isControlCode(sjis_firstChar(text, size))) {
    return 0;
  }
#else
  const char currentChar[3] = {text[0], size == 2 ? text[1] : '\0', '\0'};
  if (sjis_contains(GameControlCodes, currentChar)) {
    return 0;
  }
#endif
  return 1;
}

u32 glyph_fallbackXAdvance(u16 ch) {
  const u32 exCount = *font_xAdvanceExCount;
  for (u32 i = 0; i < exCount; i++) {
    const XAdvanceEx& e = font_xAdvanceEx[i];
    if (e.ch == ch) {
      return e.xAdvance;
    }
  }
  return 18;
}

static __attribute__((always_inline)) u32 glyph_xAdvance(u16 ch) {
  if (__builtin_expect(ch < 128, 1)) {
    u8 xAdvance = font_xAdvance[ch];
    return xAdvance == 0 ? 0 : xAdvance + font_extraXAdvanceChange;
  }
  u32 xAdvance = glyph_fallbackXAdvance(ch);
  return xAdvance == 0 ? 0 : xAdvance + font_extraXAdvanceChange;
}

i32 glyph_fallbackKerning(u16 ch1, u16 ch2) {
  const u32 exCount = *font_kerningExCount;
  for (u32 i = 0; i < exCount; i++) {
    const KerningEx& e = font_kerningEx[i];
    if (e.ch1 == ch1 && e.ch2 == ch2) {
      return e.kerning;
    }
  }
  return 0;
}

static __attribute__((always_inline)) i32 glyph_kerning(u16 ch1, u16 ch2) {
  if (__builtin_expect(ch1 < 128, 1) && __builtin_expect(ch2 < 128, 1)) {
    return font_kerning[ch1 * 128 + ch2];
  }
  return glyph_fallbackKerning(ch1, ch2);
}

const char* text_findLineBreakPoint(const char* textStart, const char* textEnd, u32 currentX, u32 maxX, u32 initialX) {
  if (textStart == NULL || textEnd == NULL || textStart >= textEnd) {
    return textEnd;
  }
  const char* text = textStart;
  const char* lastBreakPoint = NULL;
  const bool startedAtLineBeginning = currentX == initialX;
  u16 previousCh = 0;
  while (text < textEnd && *text != '\0') {
    const unsigned char c = static_cast<unsigned char>(*text);
    const u32 size = sjis_charSize(c);
    const u16 ch = sjis_firstChar(text, size);
    const u32 glyphWidth = glyph_xAdvance(ch) + glyph_kerning(previousCh, ch);
    previousCh = ch;

    if (currentX + glyphWidth > maxX) {
      if (lastBreakPoint != NULL) {
        return lastBreakPoint + 1; // return after break point
      }
      if (startedAtLineBeginning) {
        // started from line beginning and no valid breakpoint, must split at current position to avoid infinite loop
        // if even the first glyph didn't fit then force progress
        return text == textStart ? text + size : text;
      }
      return textStart; // otherwise retry run on next line
    }

    if (size == 1) {
      if (c == ' ' || c == SCRIPT_ZERO_WIDTH_SPACE) {
        lastBreakPoint = text;
      }
    }

    text += size;
    currentX += glyphWidth;
  }
  return text;
}

u32 text_measureWidthUntilEnd(const char* start) {
  const char* text = start;
  u32 width = 0;
  u16 previousCh = 0;
  while (*text != 0) {
    const u32 size = sjis_charSize(static_cast<unsigned char>(*text));
    const u16 ch = sjis_firstChar(text, size);
    const u32 glyphWidth = glyph_xAdvance(ch) + glyph_kerning(previousCh, ch);
    previousCh = ch;
    width += glyphWidth;
    text += size;
  }
  return width;
}

u32 text_measureWidth(const char* start, const char* end) {
  const char* text = start;
  u32 width = 0;
  u16 previousCh = 0;
  while (text < end) {
    const u32 size = sjis_charSize(static_cast<unsigned char>(*text));
    const u16 ch = sjis_firstChar(text, size);
    const u32 glyphWidth = glyph_xAdvance(ch) + glyph_kerning(previousCh, ch);
    previousCh = ch;
    width += glyphWidth;
    text += size;
  }
  return width;
}

const char* text_handleRuby(char* callerStack, char* scriptCtl, const char* textEnd) {
  // textEnd points after rb code
  // Format: rb TEXT ABOVE rs TEXT UNDER re

  bool rubyVisible = true;
  if (textEnd[0] == SCRIPT_LOG_HINT_MARKER) { // if log hint draw ruby only in backlog
    rubyVisible = common_inBacklog == 1;
    textEnd++;
  }

  const char* textRubyStart = sjis_contains(textEnd, GameControlCodeRubyStart);
  const char* textRubyEnd = sjis_contains(textRubyStart + 2, GameControlCodeRubyEnd);

  u32 rubyWidth = text_measureWidth(textEnd, textRubyStart) / 2;
  u32 textWidth = text_measureWidth(textRubyStart + 2, textRubyEnd);

  u32 lineTakenWidth = textWidth;
  if (rubyVisible && textWidth < rubyWidth) {
    lineTakenWidth = rubyWidth;
  }
  u32 rubyOffsetX = (lineTakenWidth - rubyWidth) / 2;
  u32 textOffsetX = (lineTakenWidth - textWidth) / 2;

  LineUpdate* lineUpdate = reinterpret_cast<LineUpdate*>(callerStack);
  u32* lineMaxPx = reinterpret_cast<u32*>(callerStack + 0x8);
  u32* currentIdx = reinterpret_cast<u32*>(callerStack + 0x10);
#ifdef PORTABLE_1
  u32 centerFlag = *reinterpret_cast<u32*>(callerStack + 0x34);
#elif PORTABLE_2
  u32 centerFlag = *reinterpret_cast<u32*>(callerStack + 0x38);
#endif

  u32* displayIndex = reinterpret_cast<u32*>(scriptCtl + 0x1c);
  u32* textColor = reinterpret_cast<u32*>(scriptCtl + 0x24);

  if (*lineMaxPx <= lineUpdate->x + lineTakenWidth) {
    GameLineDoNewLine(scriptCtl, currentIdx, lineMaxPx, lineUpdate, centerFlag);
  }

  // Draw ruby text
  if (rubyVisible) {
    LineUpdate layout;
    float rubyCurrentX = rubyOffsetX;
    layout.y = lineUpdate->y;
    layout.glyphWidth = 9;
    layout.lineHeight = 9;
    const char* text = textEnd;
    u16 previousCh = 0;
    while (text < textRubyStart) {
      const u32 size = sjis_charSize(static_cast<unsigned char>(*text));
      const u16 ch = sjis_firstChar(text, size);
      rubyCurrentX += glyph_kerning(previousCh, ch) / 2.0f;
      layout.x = lineUpdate->x + static_cast<u16>(roundFloat(rubyCurrentX));
      GameLineSetRubyGlyph(GameLineCtl, ch, *displayIndex, &layout, *textColor);
      rubyCurrentX += glyph_xAdvance(ch) / 2.0f;
      previousCh = ch;
      text += size;
    }
  }

  // Draw normal text
  lineUpdate->x = lineUpdate->x + textOffsetX;
  if (textRubyStart + 2 < textRubyEnd) {
    const char* text = textRubyStart + 2;
    while (text < textRubyEnd) {
      const u32 size = sjis_charSize(static_cast<unsigned char>(*text));
      const u16 ch = sjis_firstChar(text, size);
      if (rubyVisible) {
        GameLineSetScriptGlyph(scriptCtl, ch, *displayIndex, lineUpdate, *textColor);
      } else {
        GameLineSetScriptGlyph(scriptCtl, ch, ++*displayIndex, lineUpdate, *textColor);
      }
      text += size;
    }
  }

  // Finalize
  lineUpdate->x = lineUpdate->x + textOffsetX; // Add the right padding if any
  if (rubyVisible) {
    (*displayIndex)++;
  }
  return textRubyEnd + 2;
}

void line_setScriptGlyph(char* scriptCtl, u16 ch, u32 displayIndex, LineUpdate* lineUpdate, u32 color) {
  u32 layoutFlags = *reinterpret_cast<u32*>(scriptCtl + 0x28);
  u16 glyphWidth = lineUpdate->glyphWidth;
  if ((layoutFlags & 1) != 0) {
    lineUpdate->glyphWidth = (u16)glyphWidth / 2;
  }
  lineUpdate->x += glyph_kerning(common_previousChar, ch);
  GameLineSetGlyph(GameLineCtl, ch, displayIndex, lineUpdate, color);
  lineUpdate->glyphWidth = glyphWidth;
  lineUpdate->x += glyph_xAdvance(ch);
  common_previousChar = ch;
}

void line_setSimpleText(void* unused, char* text, u16 x, u16 y, u32 color) {
  UNUSED(unused);
  LineUpdate layout;
  layout.x = x;
  layout.y = y;
  layout.glyphWidth = 18;
  layout.lineHeight = 18;
  u16 previousCh = 0;
  while (*text != '\0') {
    const u32 size = sjis_charSize(static_cast<unsigned char>(*text));
    const u16 ch = sjis_firstChar(text, size);
    layout.x += glyph_kerning(previousCh, ch);
    GameLineSetGlyph(GameLineCtl, ch, 0, &layout, color);
    layout.x += glyph_xAdvance(ch);
    previousCh = ch;
    text += size;
  }
}

void font_setStandardExtraProperties() {
  if (*GameFontCurrentId == 2) {
    font_extraWidthChange = -1;
    font_extraXAdvanceChange = -1;
  } else {
    font_extraWidthChange = 0;
    font_extraXAdvanceChange = 0;
  }
}

void font_setCompactExtraPropertiesForSubs(u32 mode) {
  if (mode == 0) {
    font_setStandardExtraProperties();
  } else if (mode == 1) {
    if (*GameFontCurrentId == 2) {
      font_extraWidthChange = -2;
      font_extraXAdvanceChange = -2;
    } else {
      font_extraWidthChange = 0;
      font_extraXAdvanceChange = 0;
    }
  } else if (mode == 2) {
    if (*GameFontCurrentId == 2) {
      font_extraWidthChange = -4;
      font_extraXAdvanceChange = -3;
    } else if (*GameFontCurrentId == 1) {
      font_extraWidthChange = -2;
      font_extraXAdvanceChange = -2;
    } else if (*GameFontCurrentId == 0) {
      font_extraWidthChange = -2;
      font_extraXAdvanceChange = -1;
    }
  }
}

void font_setCompactExtraProperties(u32 inTips) { // inTips check only implemented for P2
#ifdef PORTABLE_2
  if (*GameFontCurrentId == 2) {
    font_extraWidthChange = -4;
    font_extraXAdvanceChange = -3;
  } else if (*GameFontCurrentId == 1) {
    if (inTips) {
      font_extraWidthChange = -2;
      font_extraXAdvanceChange = -2;
    } else {
      font_extraWidthChange = -1;
      font_extraXAdvanceChange = -1;
    }
  } else if (*GameFontCurrentId == 0) {
    font_extraWidthChange = -2;
    font_extraXAdvanceChange = -1;
  } else {
    font_extraWidthChange = 0;
    font_extraXAdvanceChange = 0;
  }
#else
  if (*GameFontCurrentId == 2) {
    font_extraWidthChange = -2;
    font_extraXAdvanceChange = -2;
  } else {
    font_extraWidthChange = 0;
    font_extraXAdvanceChange = 0;
  }
#endif
}

void font_fontReinit() {
  font_xAdvance = *GameCurrentFontData + 0x1DBE0;
  i8* font_yOffset = reinterpret_cast<i8*>(font_xAdvance + 128); // Y offset not implemented
  font_kerning = font_yOffset + 128;
  font_xAdvanceExCount = reinterpret_cast<u32*>(font_kerning + 128 * 128);
  font_kerningExCount = reinterpret_cast<u32*>(font_xAdvanceExCount + 1);
  font_xAdvanceEx = reinterpret_cast<XAdvanceEx*>(font_kerningExCount + 1);
  font_kerningEx = reinterpret_cast<KerningEx*>(font_xAdvanceEx + *font_xAdvanceExCount);
  font_setStandardExtraProperties();
}

void font_applyKerning() {
  *GameFontCurrentX += glyph_kerning(common_previousChar, font_currentChar);
}

void font_applyXAdvance() {
  *GameFontCurrentX += glyph_xAdvance(font_currentChar);
  common_previousChar = font_currentChar;
}

void save_prepareSaveText(u16 dialogMode, const char* speaker) {
  const char* episode = *GameScriptEpisode;
  const char* chapter = *GameScriptChapter;
  char* saveText = GameSaveText;
  const char* saveTextTruncatePoint = GameSaveText + 500;

  // Episode
  while (*episode != '\0' && *episode != '.') {
    *saveText++ = *episode++;
  }

  // Chapter
  *saveText++ = ' ';
  *saveText++ = '\"';
  while (*chapter != '\0') {
    *saveText++ = *chapter++;
  }
  *saveText++ = '\"';
  *saveText++ = '\n';

  // Speaker
  u32 lineIdx = 0;
  if (dialogMode == 3) {
    while (*speaker != '\0') {
      *saveText++ = *speaker++;
      lineIdx++;
    }
    if (lineIdx > 0) {
      *saveText++ = '\n';
    }
  }

  // Text
  u32 currentIdx = GameLineCtl->currentIdx;
  for (u32 i = lineIdx; i < currentIdx; i++) {
    if (saveText > saveTextTruncatePoint) {
      *saveText++ = '.';
      *saveText++ = '.';
      *saveText++ = '.';
      break;
    }
    LineCtlEntry* entry = GameLineCtl->entries + i;
    if (entry->instruction == 7) {
      *saveText++ = '\n';
    } else if (entry->instruction == LINE_INSTRUCTION_PUT_GLYPH) {
      if (entry->ch < 128) {
        if (entry->ch != SCRIPT_ZERO_WIDTH_SPACE) {
          *saveText++ = entry->ch;
        }
      } else {
        *saveText++ = entry->ch >> 8;
        *saveText++ = entry->ch;
      }
    }
  }

  *saveText++ = 0;
}

u32 cursor_getGlyphWidth(LineCtlEntry* entry) {
  return glyph_xAdvance(entry->ch) + 1;
}

void subs_clearAll() {
  subs_ctl.clear();
}

void subs_schedule(u32 bgmId) {
  for (u32 i = 0; i < subsCount; i++) {
    if (subs[i].bgmId == bgmId) {
      subs_ctl.setSubtitle(subs + i);
      return;
    }
  }
}

void subs_render() {
  if (subs_nextBgmId != 0 && subs_nextBgmWait == 0) {
    subs_schedule(subs_nextBgmId);
    subs_nextBgmId = 0;
    subs_nextBgmWait = 1;
  }
  u64 currentTick;
  sceRtcGetCurrentTick(&currentTick);
  u64 deltaTicks = currentTick - common_previousTick;
  common_previousTick = currentTick;

  subs_ctl.update(deltaTicks);
}
}
