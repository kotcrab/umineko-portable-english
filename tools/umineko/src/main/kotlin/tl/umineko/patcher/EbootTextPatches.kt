package tl.umineko.patcher

import kio.util.highBits
import kio.util.lowBits
import kmips.Reg.*
import kmipsx.elf.ElfPatchSuite
import kmipsx.util.word
import kmipsx.util.zeroTerminatedString

fun ElfPatchSuite.includeTextPatches(
  mode: UminekoPatcher.Mode,
  funcs: EbootPatches.Functions,
  vars: EbootPatches.Variables,
  newEbootTexts: List<String>,
) = context(mode) {
  patch("Pause menu - chapter text") {
    change(s(0x088ed9e0, 0x088f4fc0)) { zeroTerminatedString("Chapter:\n") }
    change(s(0x08832ddc, 0x08835c14)) { // skip ZH/ZZ handling and just copy text
      // s3 - current length
      lw(a0, s(0x41f8, 0xa58), s4)
      addiu(a0, a0, 0x1d)
      addu(a0, a0, s3)
      jal(funcs.strcpy) // copy chapter name
      move(a1, a3)

      j(s(0x0883320c, 0x08836044))
      nop()
    }
    when (mode) {
      UminekoPatcher.Mode.PORTABLE_1 -> {
        change(0x08833218) { // skip text measure and change X pos, same as playtime
          li(a0, 0x9) // X pos
          j(0x08833248)
          nop()
        }
      }
      UminekoPatcher.Mode.PORTABLE_2 -> {
        change(0x08836050) { // skip text measure and jump to font color change
          j(0x08836088)
          li(a0, -1)
        }
        change(0x08836090) {
          li(a0, 0x9) // X pos
        }
      }
    }
    change(s(0x0883324c, 0x08836098)) {
      li(a1, 0xb8) // Y pos
    }
    change(s(0x08833268, 0x088360b4)) {
      jal(funcs.fontGlyphsDrawCompact)
    }
  }

  patch("Pause menu - playtime text") {
    change(s(0x088edacc, 0x088f50ac)) { zeroTerminatedString("Total playtime:\n%d h %d min") } // barely fits
    change(s(0x088edae8, 0x088f50c8)) { zeroTerminatedString("Total playtime:\n%d min") }
    when (mode) {
      UminekoPatcher.Mode.PORTABLE_1 -> {
        change(0x08831f98) { // skip text measure and change X pos, same as chapter
          li(a0, 0x9)
          j(0x08831fd0)
          nop()
        }
      }
      UminekoPatcher.Mode.PORTABLE_2 -> {
        change(0x08834db8) { // skip text measure and jump to font color change
          j(0x08834df8)
          li(a0, -1)
        }
        change(0x08834e00) {
          li(a0, 0x9) // X pos
        }
      }
    }
    change(s(0x08831fd4, 0x08834e08)) { li(a1, 0xe0) } // Y pos
  }

  patch("Movie play - skip text") {
    change(s(0x088ef990, 0x088f6fc0)) { zeroTerminatedString("Press START to skip") }
  }

  patch("System texts") {
    val p2Base = 0x088f5598
    change(s(0x088edf70, p2Base)) { zeroTerminatedString(newEbootTexts[30], Charsets.UTF_8) }
    change(s(0x088edf98, p2Base + 0x28)) { zeroTerminatedString(newEbootTexts[31], Charsets.UTF_8) }
    change(s(0x088ee0bc, p2Base + 0x14C)) { zeroTerminatedString(newEbootTexts[32], Charsets.UTF_8) }
    change(s(0x088ee0f0, p2Base + 0x180)) { zeroTerminatedString(newEbootTexts[33], Charsets.UTF_8) }
    change(s(0x088ee108, p2Base + 0x198)) { zeroTerminatedString(newEbootTexts[34], Charsets.UTF_8) }
    change(s(0x088ee158, p2Base + 0x1E8)) { zeroTerminatedString(newEbootTexts[35], Charsets.UTF_8) }
    change(s(0x088ee1d8, p2Base + 0x268)) { zeroTerminatedString(newEbootTexts[36], Charsets.UTF_8) }
    change(s(0x088ee2a4, p2Base + 0x334)) { zeroTerminatedString(newEbootTexts[37], Charsets.UTF_8) }
    change(s(0x088ee31c, p2Base + 0x3AC)) { zeroTerminatedString(newEbootTexts[38], Charsets.UTF_8) }
    change(s(0x088ee354, p2Base + 0x3E4)) { zeroTerminatedString(newEbootTexts[39], Charsets.UTF_8) }
    change(s(0x088ee3c8, p2Base + 0x458)) { zeroTerminatedString(newEbootTexts[40], Charsets.UTF_8) }
    change(s(0x088ee424, p2Base + 0x4B4)) { zeroTerminatedString(newEbootTexts[41], Charsets.UTF_8) }
    change(s(0x088ee47c, p2Base + 0x50C)) { zeroTerminatedString(newEbootTexts[42], Charsets.UTF_8) }
    change(s(0x088ee4bc, p2Base + 0x54C)) { zeroTerminatedString(newEbootTexts[43], Charsets.UTF_8) }
    change(s(0x088ee524, p2Base + 0x5B4)) { zeroTerminatedString(newEbootTexts[44], Charsets.UTF_8) }
    change(s(0x088ee58c, p2Base + 0x61C)) { zeroTerminatedString(newEbootTexts[45], Charsets.UTF_8) }
    change(s(0x088ee5e8, p2Base + 0x678)) { zeroTerminatedString(newEbootTexts[46], Charsets.UTF_8) }
    change(s(0x088ee5fc, p2Base + 0x68C)) { zeroTerminatedString(newEbootTexts[47], Charsets.UTF_8) }
    change(s(0x088ee614, p2Base + 0x6A4)) { zeroTerminatedString(newEbootTexts[48], Charsets.UTF_8) }
    change(s(0x088ee6dc, p2Base + 0x76C)) { zeroTerminatedString(newEbootTexts[49], Charsets.UTF_8) }
    change(s(0x088ee750, p2Base + 0x7E0)) { zeroTerminatedString(newEbootTexts[50], Charsets.UTF_8) }
    change(s(0x088ee7a4, p2Base + 0x834)) { zeroTerminatedString(newEbootTexts[51], Charsets.UTF_8) }
    change(s(0x088ee81c, p2Base + 0x8AC)) { zeroTerminatedString(newEbootTexts[52], Charsets.UTF_8) }
    change(s(0x088ee84c, p2Base + 0x8DC)) { zeroTerminatedString(newEbootTexts[53], Charsets.UTF_8) }
    change(s(0x088ee8b4, p2Base + 0x944)) { zeroTerminatedString(newEbootTexts[54], Charsets.UTF_8) }
    change(s(0x088ee910, p2Base + 0x9A0)) { zeroTerminatedString(newEbootTexts[55], Charsets.UTF_8) }
    change(s(0x088ee964, p2Base + 0x9F4)) { zeroTerminatedString(newEbootTexts[56], Charsets.UTF_8) }
    // skip 0x088ee9e4 (P1) install data
    change(s(0x088eea00, p2Base + 0xA90)) { zeroTerminatedString(newEbootTexts[57], Charsets.UTF_8) }
    // skip all install data related
    change(s(0x088eeeb4, 0x088f64e0)) { zeroTerminatedString(newEbootTexts[58], Charsets.UTF_8) }
    // skip 0x088eeef4 (P1)
    change(s(0x088eef58, 0x088f6584)) { zeroTerminatedString(newEbootTexts[59], Charsets.UTF_8) }

    // install data disabled help texts
    change(s(0x088eea4c, 0x088f6074)) { zeroTerminatedString(newEbootTexts[60], Charsets.UTF_8) }
    change(s(0x088eeafc, 0x088f6124)) { zeroTerminatedString(newEbootTexts[61], Charsets.UTF_8) }
  }

  patch("Save - total playtime") {
    change(s(0x08837338, 0x0883a7c0)) { lui(a1, vars.newEbootTexts[62].highBits()) }
    change(s(0x08837388, 0x0883a810)) { ori(a1, a1, vars.newEbootTexts[62].lowBits()) }
  }

  patch("Tips - titles") {
    change(s(0x08839f80, 0x0883d3ec)) { // Use last X pos for "NEW" label
      la(v0, vars.fontCurrentX)
      lw(a0, 0, v0)
      addiu(a0, a0, 0x3)
      nop()
    }
    change(s(0x08839fac, 0x0883d418)) { // Y pos adjust
      addiu(a2, s0, 6)
    }
    when (mode) {
      UminekoPatcher.Mode.PORTABLE_1 -> {
        change(0x08944270) { // EP 1
          word(vars.newEbootTexts[0])
          word(vars.newEbootTexts[2])
          word(vars.newEbootTexts[3])
          word(vars.newEbootTexts[4])
          word(vars.newEbootTexts[8])
          word(vars.newEbootTexts[6])
          word(vars.newEbootTexts[7])
        }
        change(0x0894428c) { // EP 2
          word(vars.newEbootTexts[0])
          word(vars.newEbootTexts[2])
          word(vars.newEbootTexts[3])
          word(vars.newEbootTexts[4])
          word(vars.newEbootTexts[5])
          word(vars.newEbootTexts[8])
          word(vars.newEbootTexts[1])
        }
      }
      UminekoPatcher.Mode.PORTABLE_2 -> {
        change(0x08950ad4) { // EP 3
          word(vars.newEbootTexts[0])
          word(vars.newEbootTexts[2])
          word(vars.newEbootTexts[8])
          word(vars.newEbootTexts[63])
          word(vars.newEbootTexts[64])
          word(vars.newEbootTexts[65])
        }
        change(0x08950aec) { // EP 4
          word(vars.newEbootTexts[66])
          word(vars.newEbootTexts[67])
          word(vars.newEbootTexts[68])
          word(vars.newEbootTexts[69])
          word(vars.newEbootTexts[70])
          word(vars.newEbootTexts[71])
          word(vars.newEbootTexts[72])
        }
      }
    }
  }

  patch("Chapter select - texts") {
    change(s(0x088ec7b0, 0x088f3820)) { zeroTerminatedString(newEbootTexts[9]) }
    change(s(0x088ec7ec, 0x088f385c)) { zeroTerminatedString(newEbootTexts[12]) }
    change(s(0x088ec7f4, 0x088f3864)) { zeroTerminatedString(newEbootTexts[13]) }
    change(s(0x08814288, 0x08813d18)) { la(a1, vars.newEbootTexts[10]) }
    change(s(0x088141ac, 0x08813c04)) { la(v0, vars.newEbootTexts[10]) }
    change(s(0x088142cc, 0x08813d68)) { la(a1, vars.newEbootTexts[11]) }
    change(s(0x088141b4, 0x08813c0c)) { lui(t3, vars.newEbootTexts[11].highBits()) }
    change(s(0x088141bc, 0x08813c14)) { ori(t3, t3, vars.newEbootTexts[11].lowBits()) }
  }

  patch("Config - texts") {
    change(s(0x08944158, 0x089509bc)) {
      word(vars.newEbootTexts[14])
      word(vars.newEbootTexts[15])
      word(vars.newEbootTexts[16])
      word(vars.newEbootTexts[17])
      word(vars.newEbootTexts[18])
      word(vars.newEbootTexts[19])
    }
    change(s(0x08944170, 0x089509d4)) {
      word(vars.newEbootTexts[20])
      word(vars.newEbootTexts[21])
      word(vars.newEbootTexts[22])
      word(vars.newEbootTexts[23])
      word(vars.newEbootTexts[19])
    }
    change(s(0x08944184, 0x089509e8)) {
      word(vars.newEbootTexts[24])
      word(vars.newEbootTexts[25])
      word(vars.newEbootTexts[26])
      word(vars.newEbootTexts[27])
      word(vars.newEbootTexts[28])
      word(vars.newEbootTexts[19])
    }
    change(s(0x0894419c, 0x08950a00)) {
      word(vars.newEbootTexts[29])
      word(vars.newEbootTexts[19])
    }
  }
}
