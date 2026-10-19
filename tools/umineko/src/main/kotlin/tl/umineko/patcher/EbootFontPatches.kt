package tl.umineko.patcher

import kio.KioOutputStream
import kio.util.WINDOWS_932
import kio.util.lowBits
import kmips.Assembler
import kmips.Label
import kmips.Reg.*
import kmipsx.elf.CompileResult
import kmipsx.elf.ElfPatchSuite
import kmipsx.util.bytes
import kmipsx.util.callerSavedRegisters
import kmipsx.util.preserve
import kmipsx.util.region
import tl.util.highBitsForSigned
import java.io.ByteArrayOutputStream

internal fun Assembler.includeExtFontPatches(
  mode: UminekoPatcher.Mode,
  funcs: EbootPatches.Functions,
  vars: EbootPatches.Variables,
  compileResult: CompileResult,
) = context(mode) {
  funcs.controlCodeProgressText = region {
    val ascii = Label()
    val ctx = preserve(listOf(t1))

    lb(t1, 0, s1)
    andi(t1, t1, 0x80) // not really true shift jis check but game does the same...
    beq(t1, zero, ascii)
    addiu(s1, s1, 1)

    // not ascii, add one more
    addiu(s1, s1, 1)

    label(ascii)
    ctx.restore()

    // keep orig code behavior
    val bneExit = Label()
    sltu(a0, s1, s2)
    bne(a0, zero, bneExit)
    nop()

    // normal exit
    j(s(0x08819f40, 0x0881b9ac))
    nop()

    label(bneExit)
    j(s(0x08819e84, 0x0881b8f0))
    nop()
  }

  funcs.controlCodeSkipSpaceAfterAutoLineBreak = region {
    val exit = Label()
    val ctx = preserve(listOf(ra))
    jal(s(0x08819b14, 0x0881b590))
    nop()

    lbu(a0, 0, s4)
    li(a1, 0x20) // ASCII space
    bne(a0, a1, exit)
    nop()

    addiu(s4, s4, 1) // skip space

    label(exit)
    ctx.restoreAndExit()
  }

  if (mode.useScriptFragments) {
    funcs.controlCodeRemapFragmentText = region {
      val ctx = preserve(listOf(ra, a0, a2))
      jal(compileResult.functions.getValue("fragment_remapText"))
      move(a0, a1)
      move(a1, v0)

      ctx.restore()
      lh(a3, 0x0, a0)
      j(s(0x08819c74, 0x0881b6f0))
      lh(t0, 0x2, a0)
    }
  }

  funcs.fontStoreAsciiChar = region {
    val ctx = preserve(listOf(s0))
    la(s0, compileResult.variables.getValue("font_currentChar"))
    sw(t2, 0, s0)
    ctx.restore()

    j(s(0x08807510, 0x088075a8))
    move(t2, t6) // was sra(t2, t6, 1) to divide width by 2
  }

  funcs.fontStoreWideChar = region {
    val ctx = preserve(listOf(s0))
    la(s0, compileResult.variables.getValue("font_currentChar"))
    sw(t5, 0, s0)
    ctx.restore()

    j(s(0x088074cc, 0x08807564))
    move(t2, t6)
  }

  funcs.fontApplyKerningDispatch = region {
    val ctx = preserve(listOf(ra))
    jal(s(0x08806ff4, 0x0880708c))
    nop()
    jal(compileResult.functions.getValue("font_applyKerning"))
    nop()
    ctx.restoreAndExit()
  }

  funcs.fontApplyXAdvanceDispatch = region {
    val ctx = preserve(callerSavedRegisters)
    jal(compileResult.functions.getValue("font_applyXAdvance"))
    nop()
    ctx.restore()
    j(s(0x08807660, 0x088076f8))
    nop()
  }

  funcs.fontReinitDispatch = region {
    lw(s0, 0, sp)
    j(compileResult.functions.getValue("font_fontReinit"))
    nop()
    // ra already setup
  }

  funcs.commonResetPreviousChar = region {
    val ctx = preserve(listOf(s0))
    la(s0, compileResult.variables.getValue("common_previousChar"))
    sw(zero, 0, s0)
    ctx.restoreAndExit()
  }

  funcs.scriptCalculateActualNameWidth = region {
    val ctx = preserve(listOf(ra))
    lw(a0, 0x34 + ctx.byteSize(), sp)
    lw(a0, 0x24, a0)
    jal(compileResult.functions.getValue("text_measureWidthUntilEnd"))
    addiu(a0, a0, 0x6)
    addiu(v0, v0, -12)
    sw(v0, 0x68c, s3)
    ctx.restoreAndExit()
  }

  funcs.backlogChapterListSetCompactFont = region {
    val ctx = preserve(listOf(ra))
    jal(s(0x088076ac, 0x08807744))
    nop()
    jal(compileResult.functions.getValue("font_setCompactExtraProperties"))
    li(a0, 0)
    ctx.restoreAndExit()
  }
  funcs.backlogChapterListUnsetCompactFont = region {
    val ctx = preserve(listOf(ra))
    jal(compileResult.functions.getValue("font_setStandardExtraProperties"))
    nop()
    ctx.restoreAndExit()
  }
  funcs.backlogOnEnter = region {
    val ctx = preserve(listOf(ra))
    la(at, compileResult.variables.getValue("common_inBacklog"))
    li(ra, 1)
    sw(ra, 0, at)
    jal(s(0x08812728, 0x088121cc))
    nop()
    ctx.restoreAndExit()
  }
  funcs.backlogOnExit = region {
    la(at, compileResult.variables.getValue("common_inBacklog"))
    sw(zero, 0, at)
    j(s(0x08815678, 0x08815280))
    li(v0, 0x1)
  }

  funcs.fontGlyphsDrawCompact = region {
    val ctx = preserve(listOf(ra))
    val argsCtx = preserve(listOf(a0, a1))
    if (mode == UminekoPatcher.Mode.PORTABLE_2) {
      la(a0, 0x0883D3AC) // check for tips draw return address
      xor(a0, ra, a0)
    }
    jal(compileResult.functions.getValue("font_setCompactExtraProperties"))
    if (mode == UminekoPatcher.Mode.PORTABLE_2) {
      sltiu(a0, a0, 1)
    } else {
      li(a0, 0)
    }
    argsCtx.restore()
    jal(s(0x08807364, 0x088073fc))
    nop()
    jal(compileResult.functions.getValue("font_setStandardExtraProperties"))
    nop()
    ctx.restoreAndExit()
  }

  funcs.cursorUseGlyphXAdvance = region {
    val ctx = preserve(callerSavedRegisters)
    jal(compileResult.functions.getValue("cursor_getGlyphWidth"))
    move(a0, t0)
    addu(s4, s4, v0)
    ctx.restoreAndExit()
  }
}

fun ElfPatchSuite.includeFontPatches(
  mode: UminekoPatcher.Mode,
  funcs: EbootPatches.Functions,
  vars: EbootPatches.Variables,
  compileResult: CompileResult,
) = context(mode) {
  patch("Control codes - replace") {
    fun Assembler.controlCodes(text: String) {
      with(KioOutputStream(ByteArrayOutputStream())) {
        writeNullTerminatedString(text, Charsets.WINDOWS_932)
        align(4)
        getAsByteArrayOutputStream().toByteArray()
      }
        .let { bytes(it) }
    }
    change(s(0x088ec990, 0x088f3a38)) {
      controlCodes(ControlCode.entries.joinToString(separator = "") { it.replacement })
    }
    change(s(0x088ec9bc, 0x088f3a60)) {
      controlCodes(ControlCode.RS.replacement)
    }
    change(s(0x088ec9c0, 0x088f3a64)) {
      controlCodes(ControlCode.RE.replacement)
    }
    change(s(0x088ec7fc, 0x088f386c)) {
      controlCodes(ControlCode.EV.replacement)
    }
  }
  patch("Control code - find next control code") {
    change(s(0x08819d5c, 0x0881b7c8)) {
      jal(compileResult.functions.getValue("text_findNextControlCode"))
      move(a0, s2)
      move(s2, v0)
      lh(t3, 0, sp)
      repeat(4) {
        nop()
      }
    }
  }
  patch("Control code - find line break") {
    change(s(0x08819dac, 0x0881b818)) {
      move(a0, s4) // text fragment start
      move(a1, s2) // text fragment end
      move(a2, t3) // current X px
      move(a3, t2) // line max px
      jal(compileResult.functions.getValue("text_findLineBreakPoint"))
      lh(t0, 0, s3) // line initial X px

      move(s2, v0) // new text fragment end
      j(s(0x08819e78, 0x0881b8e4))
      move(s1, s4) // copy text fragment start to s1
    }
  }
  patch("Control code - skip space after auto line break") {
    change(s(0x08819f64, 0x0881b9d0)) {
      jal(funcs.controlCodeSkipSpaceAfterAutoLineBreak)
    }
  }
  patch("Control code - disable hiding ASCII") {
    change(s(0x08819e94, 0x0881b900)) {
      nop()
      nop()
    }
  }
  patch("Control code - progress text correctly") {
    change(s(0x08819f20, 0x0881b98c)) { nop() }
    change(s(0x08819f30, 0x0881b99c)) { nop() }
    change(s(0x08819f34, 0x0881b9a0)) {
      j(funcs.controlCodeProgressText)
      nop()
    }
  }
  patch("Control code - check needs new line") {
    change(s(0x08819f44, 0x0881b9b0)) {
      val label = Label().also { it.address = s(0x881a394, 0x0881be64) }
      jal(compileResult.functions.getValue("text_needsNewLineCalc"))
      move(a0, s2)
      nop()
      beq(v0, zero, label)
    }
  }

  patch("Control code - ruby - custom handler") {
    change(s(0x08819fe4, 0x0881ba50)) {
      move(a0, sp) // caller stack
      move(a1, s3) // script ctl
      jal(compileResult.functions.getValue("text_handleRuby"))
      move(a2, s4) // text end

      j(s(0x0881a18c, 0x0881bc5c)) // skip rest
      move(s4, v0) // set new text end
    }
  }

  patch("Control code - remap fragment text", enabled = mode.useScriptFragments) {
    change(s(0x08819c6c, 0x0881b6e8)) {
      j(funcs.controlCodeRemapFragmentText)
      nop()
    }
  }

  patch("Font - store current chars and disable ASCII width change") {
    change(s(0x08807508, 0x088075a0)) {
      j(funcs.fontStoreAsciiChar)
    }
    change(s(0x088074c4, 0x0880755c)) {
      j(funcs.fontStoreWideChar)
    }
  }
  patch("Font - apply kerning") {
    change(s(0x088075b4, 0x0880764c)) {
      jal(funcs.fontApplyKerningDispatch)
    }
  }
  patch("Font - apply X advance") {
    change(s(0x0880765c, 0x088076f4)) {
      j(funcs.fontApplyXAdvanceDispatch) // not safe to use jal here
      nop()
      nop()
    }
    change(s(0x08807644, 0x088076dc)) {
      lui(ra, s(0x897, 0x898)) // manual fixup for instruction that breaks after relocation on 0x0880765c is removed
    }
  }

  patch("Font - on font reinit") {
    change(s(0x0884AA74, 0x08850f70)) {
      jal(funcs.fontReinitDispatch)
    }
  }

  patch("Font - use custom extra width change") {
    val addr = compileResult.variables.getValue("font_extraWidthChange")
    change(s(0x088073dc, 0x08807474)) { lui(s6, addr.highBitsForSigned()) }
    change(s(0x088075f8, 0x08807690)) { lw(a3, addr.lowBits(), s6) }
  }

  patch("Font - reset previous char") {
    // script line set
    change(s(0x0881a450, 0x0881bf20)) { j(funcs.commonResetPreviousChar) }

    // font draw
    change(s(0x088076a4, 0x0880773c)) { j(funcs.commonResetPreviousChar) }
  }

  patch("Text Contains - ShiftJIS aware") {
    change(s(0x0881d7d0, 0x0881f3e8)) {
      j(compileResult.functions.getValue("sjis_contains"))
      nop()
    }
  }

  patch("Line - set script glyph with proper X advance") {
    change(s(0x08819bd8, 0x0881b654)) {
      j(compileResult.functions.getValue("line_setScriptGlyph"))
      nop()
    }
  }

  patch("Line - set simple text with ASCII and proper X advance") {
    change(s(0x08819a60, 0x0881b4dc)) {
      j(compileResult.functions.getValue("line_setSimpleText"))
      nop()
    }
  }

  patch("Script - calculate actual name width") {
    change(s(0x0884b27c, 0x08851778)) {
      jal(funcs.scriptCalculateActualNameWidth)
      nop()
    }
  }

  patch("Backlog - Chapter List - set compact font") {
    change(s(0x088144ec, 0x08813fc8)) {
      jal(funcs.backlogChapterListSetCompactFont)
    }
  }
  patch("Backlog - Chapter List - unset compact font") {
    change(s(0x08814614, 0x088140f8)) {
      j(funcs.backlogChapterListUnsetCompactFont)
    }
  }
  patch("Backlog - on enter") {
    change(s(0x08814f3c, 0x08814a20)) {
      jal(funcs.backlogOnEnter)
    }
  }
  patch("Backlog - on exit") {
    change(s(0x08815670, 0x08815278)) {
      j(funcs.backlogOnExit)
    }
  }

  patch("Chapter Appear - draw compact") {
    change(s(0x08818b18, 0x08819b10)) {
      jal(funcs.fontGlyphsDrawCompact)
    }
  }
  patch("Tips Titles - draw compact") {
    change(s(0x08839f3c, 0x0883d3a4)) {
      jal(funcs.fontGlyphsDrawCompact)
    }
  }

  patch("Cursor - use glyph X advance") {
    change(s(0x0884c488, 0x08852990)) {
      jal(funcs.cursorUseGlyphXAdvance)
      nop()
    }
  }
}
