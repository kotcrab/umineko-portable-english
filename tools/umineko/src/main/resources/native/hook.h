#ifndef HOOK_H_
#define HOOK_H_

#include "platform.h"

typedef struct {
  u16 x;
  u16 y;
  u16 glyphWidth;
  u16 lineHeight;
} LineUpdate;

typedef struct {
  u16 instruction;
  u16 instructionData;
  u16 ch;
  u16 yOffset; // maybe
  u32 color;
  u16 x;
  u16 y;
  u16 glyphWidth;
  u16 lineHeight;
} LineCtlEntry;

typedef struct {
  LineCtlEntry* entries;
  u32 maxIdx;
  u32 currentIdx;
  u32 unk;
} LineCtl;

extern const char* GameControlCodes;
extern const char* GameControlCodeRubyStart;
extern const char* GameControlCodeRubyEnd;
extern const char** GameScriptEpisode;
extern const char** GameScriptChapter;
extern LineCtl* GameLineCtl;

extern u8** GameCurrentFontData;

extern u32* GameFontCurrentX;
extern u32* GameFontCurrentId;

typedef void (*GameLineDoNewLineFn)(void* scriptCtl, u32* outCurrentIdx, u32* outLineMaxPx, void* lineUpdate,
                                    u32 centerFlag);

extern const GameLineDoNewLineFn GameLineDoNewLine;

typedef void (*GameLineSetScriptGlyphFn)(void* scriptCtl, u16 ch, u32 displayIndex, LineUpdate* lineUpdate, u32 color);

extern const GameLineSetScriptGlyphFn GameLineSetScriptGlyph;

typedef void (*GameLineSetRubyGlyphFn)(LineCtl* lineCtl, u16 ch, u32 displayIndex, LineUpdate* lineUpdate, u32 color);

extern const GameLineSetRubyGlyphFn GameLineSetRubyGlyph;

typedef void (*GameLineSetGlyphFn)(LineCtl* lineCtl, u16 ch, u32 displayIndex, LineUpdate* lineUpdate, u32 color);

extern const GameLineSetGlyphFn GameLineSetGlyph;

extern char* GameSaveText;

typedef void* (*GameFontClearFn)(u32 tag);
extern const GameFontClearFn GameFontClear;

typedef void* (*GameFontSetColorFn)(u32 color);
extern const GameFontSetColorFn GameFontSetColor;

typedef void* (*GameFontSetPositionFn)(i32 x, i32 y);
extern const GameFontSetPositionFn GameFontSetPosition;

typedef void* (*GameFontSetGlyphPropsFn)(u32 width, u32 lineHeight, u32 stretch);
extern const GameFontSetGlyphPropsFn GameFontSetGlyphProps;

typedef void* (*GameFontDrawFn)(const u8* text, u32 tag);
extern const GameFontDrawFn GameFontDraw;

#if SCRIPT_FRAGMENT_MAX_SIZE != 0
typedef void* (*GameAllocateFn)(u32 size, u32 top);

extern const GameAllocateFn GameAllocate;

typedef int (*SceIoOpenFn)(const char* file, i32 flags, i32 mode);

extern const SceIoOpenFn sceIoOpen;

typedef int (*SceIoReadFn)(i32 fd, void* data, u32 size);

extern const SceIoReadFn sceIoRead;

typedef int (*SceIoCloseFn)(i32 fd);

extern const SceIoCloseFn sceIoClose;
#endif

typedef void (*SceRtcGetCurrentTickFn)(u64* tick);

extern const SceRtcGetCurrentTickFn sceRtcGetCurrentTick;

#endif
