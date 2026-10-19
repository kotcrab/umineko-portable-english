package tl.umineko.patcher

import kio.KioOutputStream
import kio.util.highBits
import kio.util.lowBits
import kmips.Assembler
import kmips.Label
import kmips.Reg.*
import kmipsx.elf.CompileResult
import kmipsx.elf.ElfPatchSuite
import kmipsx.util.bytes
import kmipsx.util.preserve
import kmipsx.util.region
import kmipsx.util.word
import kmipsx.util.zeroTerminatedString
import tl.util.highBitsForSigned
import java.io.ByteArrayOutputStream

internal fun Assembler.includeExtMainPatches(
  mode: UminekoPatcher.Mode,
  funcs: EbootPatches.Functions,
  vars: EbootPatches.Variables,
  compileResult: CompileResult,
) = context(mode) {
  funcs.tipsFixButtonsUi = region {
    li(a1, 0x64)
    ush(a1, 0xa, a0)
    ush(a1, 0x1e, a0)

    addiu(a0, a0, -0x9c0)

    // btn 1
    li(a1, 0x126)
    ush(a1, 0x8c4, a0)
    ush(a1, 0x8d8, a0)
    li(a1, 0x5b)
    ush(a1, 0x8ca, a0)
    ush(a1, 0x8de, a0)
    li(a1, 0x181)
    ush(a1, 0x8ce, a0)
    ush(a1, 0x8e2, a0)

    // btn 2
    li(a1, 0x17a)
    ush(a1, 0x904, a0)
    ush(a1, 0x918, a0)
    li(a1, 0x1a2)
    ush(a1, 0x90e, a0)
    ush(a1, 0x922, a0)

    // btn 3
    li(a1, 0x1a4)
    ush(a1, 0x944, a0)
    ush(a1, 0x958, a0)
    li(a1, 0x64)
    ush(a1, 0x94a, a0)
    ush(a1, 0x95e, a0)

    jr(ra)
    nop()
  }

  funcs.crossCircleButtonSwap = region {
    val ctx = preserve(listOf(ra, s7))
    val exit = Label()
    move(s7, a0)
    jal(funcs.sceCtrlReadBufferPositive)
    nop()
    li(t0, 0x6000)
    lw(t3, 0x4, s7) // load button bit mask
    and(t3, t3, t0) // only use CIRCLE and CROSS bit fields
    beq(t3, zero, exit) // if no CIRCLE or CROSS buttons are pressed then do nothing
    nop()
    beq(t3, t0, exit) // if both buttons are pressed do nothing
    nop()
    // swap buttons
    lw(t3, 0x4, s7) // load button bit mask
    xor(t3, t3, t0)
    sw(t3, 0x4, s7) // store new bit mask
    label(exit)
    ctx.restoreAndExit()
  }

  funcs.saveChangeLanguage = region {
    val ctx = preserve(listOf(t0))
    li(t0, 0x1)
    sw(t0, 4, a0)
    sw(t0, 8, a0)
    ctx.restore()
    j(s(0x0880bdb4, 0x0880be48))
    nop()
  }

  funcs.configInstallSkipMovingUp = region {
    val exit = Label()
    li(at, 4)
    beql(a2, at, exit)
    addiu(a2, a2, -1)
    label(exit)
    jr(ra)
    sh(a2, 0x16, a1)
  }
  funcs.configInstallSkipMovingDown = region {
    val exit = Label()
    li(at, 4)
    beql(a2, at, exit)
    addiu(a2, a2, 1)
    label(exit)
    jr(ra)
    sh(a2, 0x16, a1)
  }

  funcs.subsRenderDispatch = region {
    val ctx = preserve(listOf(ra))
    jal(compileResult.functions.getValue("subs_render"))
    nop()
    ctx.restoreAndExit()
  }
  funcs.subsLoadCheckHook = region {
    val ctx = preserve(listOf(ra))

    lw(a1, 0x4, a0)
    addiu(a1, a1, s(-0x6b, -0x137))
    la(at, compileResult.variables.getValue("subs_nextBgmId"))
    sw(a1, 0, at)

    jal(s(0x08842d94, 0x08847b10))
    nop()

    la(at, compileResult.variables.getValue("subs_nextBgmWait"))
    sw(v0, 0, at)

    ctx.restoreAndExit()
  }
  funcs.subsClearAllDispatch = region {
    val ctx = preserve(listOf(ra))
    jal(s(0x08842c70, 0x088479cc))
    nop()
    jal(compileResult.functions.getValue("subs_clearAll"))
    nop()
    ctx.restoreAndExit()
  }
}

fun ElfPatchSuite.includeMainPatches(
  mode: UminekoPatcher.Mode,
  funcs: EbootPatches.Functions,
  vars: EbootPatches.Variables,
  compileResult: CompileResult,
  patchSize: Int,
  displayListEnlargeSize: Int,
) = context(mode) {
  patch("Load patch file") {
    val path = s(0x08900f78, 0x0890cc80)
    val loader = s(0x08900fb8, 0x0890ccc0)
    change(s(0x08805d68, 0x08805d8c)) {
      jal(loader)
      nop()
    }
    change(loader) {
      val ctx = preserve(listOf(ra, s0))

      la(a0, path)
      li(a1, 1)
      jal(funcs.sceIoOpen)
      li(a2, 511)

      move(s0, v0)

      la(a0, patchSize)
      jal(funcs.allocate)
      li(a1, 0)

      move(a1, v0)
      la(a2, patchSize)
      jal(funcs.sceIoRead)
      move(a0, s0)

      jal(funcs.sceIoClose)
      move(a0, s0)

      jal(compileResult.functions.getValue("initPatch"))
      nop()

      ctx.restoreAndExit()
    }
    change(path) {
      zeroTerminatedString("disc0:/patch")
    }
  }

  patch("Update ASCII to ShiftJIS map") {
    change(s(0x088073a0, 0x08807438)) { lui(fp, vars.asciiToShiftJisValues.highBitsForSigned()) }
    change(s(0x088073c0, 0x08807458)) { addiu(fp, fp, vars.asciiToShiftJisValues.lowBits()) }
    change(s(0x08903400, 0x0890f100)) {
      with(KioOutputStream(ByteArrayOutputStream())) {
        writeBytes(
          (wideChars.keys.joinToString(separator = "") + " ") // must end with space
            .toByteArray(Charsets.US_ASCII)
        )
        align(4)
        getAsByteArrayOutputStream().toByteArray()
      }
        .let { bytes(it) }
    }
  }

  patch("Display list - enlarge and move other symbols") {
    // 08976dc0 - original display list start (P1), 089854c0 (P2)

    val newDisplayListSize = 0x20000 + displayListEnlargeSize
    if (newDisplayListSize.lowBits() != 0) {
      error("Display list size low bits must be 0")
    }

    val matricesAddr = s(0x08996dc0, 0x089a54c0) + displayListEnlargeSize
    val edramAddr = s(0x08997340, 0x089a5a40) + displayListEnlargeSize
    val frameBufferAddr = s(0x08997344, 0x089a5a44) + displayListEnlargeSize
    val arenaStartAddr = s(0x08997380, 0x089a5a80) + displayListEnlargeSize

    // patch calls to GU start
    change(s(0x08807c40, 0x08807cd8)) { lui(a2, newDisplayListSize.highBits()) }
    change(s(0x08807e80, 0x08807f18)) { lui(a2, newDisplayListSize.highBits()) }
    change(s(0x08807fec, 0x08808084)) { lui(a2, newDisplayListSize.highBits()) }
    change(s(0x08808054, 0x088080ec)) { lui(a2, newDisplayListSize.highBits()) }

    // patch matrices
    change(s(0x08807d7c, 0x08807e14)) { lui(a0, matricesAddr.highBits()) }

    // patch EDRAM
    change(s(0x08807bf8, 0x08807c90)) { lui(a0, edramAddr.highBits()) }
    change(s(0x08807e18, 0x08807eb0)) { lui(a0, edramAddr.highBits()) }
    change(s(0x08807f84, 0x0880801c)) { lui(a0, edramAddr.highBits()) }

    // patch frame buffer
    change(s(0x08807bec, 0x08807c84)) { lui(a0, frameBufferAddr.highBits()) }
    change(s(0x08807e24, 0x08807ebc)) { lui(a0, frameBufferAddr.highBits()) }
    change(s(0x08807f90, 0x08808028)) { lui(s0, frameBufferAddr.highBits()) }

    // patch arena start
    change(s(0x0880854c, 0x088085e4)) { lui(t4, arenaStartAddr.highBits()) }
    change(s(0x08808624, 0x088086bc)) { lui(t4, arenaStartAddr.highBits()) }
  }

  patch("Backlog - increase chars limit") {
    change(s(0x08812380, 0x08811e24)) {
      li(a0, 0x800)
    }
    change(s(0x088123ac, 0x08811e50)) {
      li(a1, 0x800)
    }
  }
  patch("Story - increase chars limit") {
    change(s(0x0884ab34, 0x08851030)) {
      li(a1, 0x400)
    }
  }

  patch("Tips - fix max width") {
    // same as characters
    change(s(0x0883c5fc, 0x0883fa6c)) { li(a3, 0x120) }
    change(s(0x0883c620, 0x0883fa90)) { li(a3, 0x120) }
    change(s(0x0883c6c4, 0x0883fb34)) { li(a3, 0x120) }
  }
  patch("Tips - fix buttons UI") {
    change(s(0x0883b868, 0x0883ecd4)) { li(a1, 0x1a4) }
    change(s(0x0883b968, 0x0883edd4)) { j(funcs.tipsFixButtonsUi) }
  }

  patch("Music - fix buttons UI") {
    // btn 1
    change(s(0x08821c44, 0x08822594)) { li(a2, 0x60) } // W
    change(s(0x08821c68, 0x088225b8)) { li(t0, 0x60) } // W
    change(s(0x08821c90, 0x088225e0)) { li(a1, 0x100) } // X

    // btn 2
    change(s(0x08821cc8, 0x08822618)) { li(a2, 0x36) } // W
    change(s(0x08821cec, 0x0882263c)) { li(t0, 0x36) } // W
    change(s(0x08821d14, 0x08822664)) { li(a1, 0x1a9) } // X

    // btn 3
    change(s(0x08821d4c, 0x0882269c)) { li(a2, 0x2A) } // W
    change(s(0x08821d70, 0x088226c0)) { li(t0, 0x2A) } // W
    change(s(0x08821d68, 0x088226b8)) { li(a2, 0x36) } // reg X
    change(s(0x08821d98, 0x088226e8)) { li(a1, 0x17d) } // X

    // btn 4
    change(s(0x08821e1c, 0x0882276c)) { li(a1, 0x156) } // X
  }

  patch("Picture - fix buttons UI") {
    // btn 1
    change(s(0x08826470, 0x08826e00)) { li(a2, 0x60) } // W
    change(s(0x08826494, 0x08826e24)) { li(t0, 0x60) } // W
    change(s(0x088264bc, 0x08826e4c)) { li(a1, 0xb6) } // X

    // btn 2
    change(s(0x08826544, 0x08826ed4)) { li(a1, 0x10e) } // X

    // btn 3
    change(s(0x08826580, 0x08826f10)) { li(a2, 0x60) } // W
    change(s(0x088265a4, 0x08826f34)) { li(t0, 0x60) } // W
    change(s(0x088265cc, 0x08826f5c)) { li(a1, 0x1a9) } // X

    // btn 4
    change(s(0x08826608, 0x08826f98)) { li(a2, 0x60) } // W
    change(s(0x0882662c, 0x08826fbc)) { li(t0, 0x60) } // W
    change(s(0x08826654, 0x08826fe4)) { li(a1, 0x17a) } // X
  }

  patch("Config - fix buttons UI") {
    // btn 1
    change(s(0x0882da34, 0x0882f418)) { li(a2, 0x60) } // W
    change(s(0x0882da58, 0x0882f43c)) { li(t0, 0x60) } // W
    change(s(0x0882da80, 0x0882f464)) { li(a1, 0x85) } // X

    // btn 2
    change(s(0x0882db08, 0x0882f4ec)) { li(a1, 0xdf) } // X

    // btn 3
    change(s(0x0882db90, 0x0882f574)) { li(a1, 0x1a9) } // X

    // btn 4
    change(s(0x0882dbcc, 0x0882f5b0)) { li(a2, 0x60) } // W
    change(s(0x0882dbf0, 0x0882f5d4)) { li(t0, 0x60) } // W
    change(s(0x0882dc18, 0x0882f5fc)) { li(a1, 0x14c) } // X
  }

  patch("Title - adjust scroll") {
    change(s(0x88ede60, 0x088f5488)) {
      word(0x6a)
    }
    change(s(0x88ede7c, 0x088F54A4)) {
      word(0x6a)
      word(0x6a)
    }
    change(s(0x88edeb8, 0x088f54e0)) {
      word(s(0xdf, 0xe2))
      word(s(0xf9, 0xef))
      word(0x87)
    }
  }

  patch("Misc - X/O buttons swap") {
    change(s(0x088088e0, 0x08808978)) {
      jal(funcs.crossCircleButtonSwap)
    }
    change(s(0x08810520, 0x0880ff98)) {
      jal(funcs.crossCircleButtonSwap)
    }
  }

  patch("Misc - change impose language") {
    change(s(0x0880a654, 0x0880a6ec)) { li(a0, 1) }
    change(s(0x0880a660, 0x0880a6f8)) { li(a1, 1) }
  }
  patch("Misc - change save language") {
    change(s(0x0880bda8, 0x0880be3c)) {
      j(funcs.saveChangeLanguage)
    }
  }

  patch("Misc - increase default message and auto play speed") {
    change(s(0x08837020, 0x0883a4a8)) {
      li(a1, 8)
    }
  }
  patch("Misc - change default font") {
    change(s(0x08837034, 0x0883a4bc)) {
      nop()
    }
  }

  patch("Save - prepare save text") {
    change(s(0x0884b498, 0x08851994)) {
      lhu(a0, 0x76c, s3)
      lw(a1, 0x24, s2)
      addiu(a1, a1, 6)
      jal(compileResult.functions.getValue("save_prepareSaveText"))
      nop()
      j(s(0x0884bd78, 0x08852274))
      nop()
    }
  }

  patch("Install - hide in config") {
    change(s(0x0882ef8c, 0x088312f0)) {
      jal(funcs.configInstallSkipMovingUp)
      addiu(a2, s2, -1)
    }
    change(s(0x0882eff4, 0x08831358)) {
      jal(funcs.configInstallSkipMovingDown)
      addiu(a2, s2, 1)
    }
    change(s(0x0882b420, 0x0882c380)) {
      nop() // don't draw install switch
    }
  }
  patch("Install - change DNS path") {
    change(s(0x088ED95A, 0x088F35CA)) { zeroTerminatedString("DISABLED") }
    change(s(0x088EC54A, 0x088F4F3A)) { zeroTerminatedString("DISABLED") }
  }
  patch("Install - override prompt") {
    change(s(0x08831b2c, 0x08834948)) {
      j(s(0x08831bd4, 0x088349f0)) // jump to prompt as soon as possible
      nop()
    }
    change(s(0x08831be4, 0x08834a00)) { li(a1, 0) }
  }

  patch("Subs - render dispatch") {
    change(s(0x0880a84c, 0x0880A8E4)) {
      j(funcs.subsRenderDispatch)
    }
  }
  patch("Subs - BGM load check hook") {
    change(s(0x08810cf4, 0x0881076c)) {
      jal(funcs.subsLoadCheckHook)
    }
  }
  patch("Subs - clear on skip") {
    change(s(0x08842c5c, 0x088479b8)) {
      jal(funcs.subsClearAllDispatch)
    }
  }
}
