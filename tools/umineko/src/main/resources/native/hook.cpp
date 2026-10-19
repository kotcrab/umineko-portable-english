#include "hook.h"

#ifdef PORTABLE_1

const char* GameControlCodes = reinterpret_cast<const char*>(0x088ec990);
const char* GameControlCodeRubyStart = reinterpret_cast<const char*>(0x088ec9bc);
const char* GameControlCodeRubyEnd = reinterpret_cast<const char*>(0x088ec9c0);
const char** GameScriptEpisode = reinterpret_cast<const char**>(0x09f44690);
const char** GameScriptChapter = reinterpret_cast<const char**>(0x09f44694);
LineCtl* GameLineCtl = reinterpret_cast<LineCtl*>(0x09f43b20);

u8** GameCurrentFontData = reinterpret_cast<u8**>(0x08976d40);

u32* GameFontCurrentX = reinterpret_cast<u32*>(0x08976d48);
u32* GameFontCurrentId = reinterpret_cast<u32*>(0x08944478);

const GameLineDoNewLineFn GameLineDoNewLine = (GameLineDoNewLineFn)0x08819b14;
const GameLineSetScriptGlyphFn GameLineSetScriptGlyph = (GameLineSetScriptGlyphFn)0x08819bd8;
const GameLineSetRubyGlyphFn GameLineSetRubyGlyph = (GameLineSetRubyGlyphFn)0x08819198;
const GameLineSetGlyphFn GameLineSetGlyph = (GameLineSetGlyphFn)0x0881909c;

char* GameSaveText = reinterpret_cast<char*>(0x0894447c);

const GameFontClearFn GameFontClear = (GameFontClearFn)0x088076fc;
const GameFontSetColorFn GameFontSetColor = (GameFontSetColorFn)0x0880778c;
const GameFontSetPositionFn GameFontSetPosition = (GameFontSetPositionFn)0x08807754;
const GameFontSetGlyphPropsFn GameFontSetGlyphProps = (GameFontSetGlyphPropsFn)0x08807770;
const GameFontDrawFn GameFontDraw = (GameFontDrawFn)0x08807364;

#if SCRIPT_FRAGMENT_MAX_SIZE != 0
const GameAllocateFn GameAllocate = (GameAllocateFn)0x08808514;

const SceIoOpenFn sceIoOpen = (SceIoOpenFn)0x088EBAD4;
const SceIoReadFn sceIoRead = (SceIoReadFn)0x088EBA8C;
const SceIoCloseFn sceIoClose = (SceIoCloseFn)0x088EBAA4;
#endif

const SceRtcGetCurrentTickFn sceRtcGetCurrentTick = (SceRtcGetCurrentTickFn)0x088EBAFC;

#elif PORTABLE_2

const char* GameControlCodes = reinterpret_cast<const char*>(0x088f3a38);
const char* GameControlCodeRubyStart = reinterpret_cast<const char*>(0x088f3a60);
const char* GameControlCodeRubyEnd = reinterpret_cast<const char*>(0x088f3a64);
const char** GameScriptEpisode = reinterpret_cast<const char**>(0x09f53198);
const char** GameScriptChapter = reinterpret_cast<const char**>(0x09f5319c);
LineCtl* GameLineCtl = reinterpret_cast<LineCtl*>(0x09f52610);

u8** GameCurrentFontData = reinterpret_cast<u8**>(0x08985440);

u32* GameFontCurrentX = reinterpret_cast<u32*>(0x08985448);
u32* GameFontCurrentId = reinterpret_cast<u32*>(0x08950d48);

const GameLineDoNewLineFn GameLineDoNewLine = (GameLineDoNewLineFn)0x0881b590;
const GameLineSetScriptGlyphFn GameLineSetScriptGlyph = (GameLineSetScriptGlyphFn)0x0881b654;
const GameLineSetRubyGlyphFn GameLineSetRubyGlyph = (GameLineSetRubyGlyphFn)0x0881aa3c;
const GameLineSetGlyphFn GameLineSetGlyph = (GameLineSetGlyphFn)0x0881a940;

char* GameSaveText = reinterpret_cast<char*>(0x08950d4c);

const GameFontClearFn GameFontClear = (GameFontClearFn)0x08807794;
const GameFontSetColorFn GameFontSetColor = (GameFontSetColorFn)0x08807824;
const GameFontSetPositionFn GameFontSetPosition = (GameFontSetPositionFn)0x088077ec;
const GameFontSetGlyphPropsFn GameFontSetGlyphProps = (GameFontSetGlyphPropsFn)0x08807808;
const GameFontDrawFn GameFontDraw = (GameFontDrawFn)0x088073fc;

#if SCRIPT_FRAGMENT_MAX_SIZE != 0
const GameAllocateFn GameAllocate = (GameAllocateFn)0x088085ac;

const SceIoOpenFn sceIoOpen = (SceIoOpenFn)0x088f2b78;
const SceIoReadFn sceIoRead = (SceIoReadFn)0x088f2b30;
const SceIoCloseFn sceIoClose = (SceIoCloseFn)0x088f2b48;
#endif

const SceRtcGetCurrentTickFn sceRtcGetCurrentTick = (SceRtcGetCurrentTickFn)0x088F2BA0;

#else

#error "Mode not defined"

#endif
