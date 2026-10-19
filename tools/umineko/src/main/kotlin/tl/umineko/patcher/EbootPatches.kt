package tl.umineko.patcher

import kio.KioOutputStream
import kio.util.WINDOWS_932
import kio.util.child
import kmips.Assembler
import kmips.Reg
import kmips.Reg.*
import kmips.assembleAsByteArray
import kmipsx.elf.patchSuite
import kmipsx.elf.pspCodeCompiler
import kmipsx.elf.standardPspGccFlags
import kmipsx.util.align16
import kmipsx.util.bytes
import kmipsx.util.compiledElf
import kmipsx.util.region
import kmipsx.util.zeroTerminatedString
import tl.util.copyFromJar
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class EbootPatches(
  private val mode: UminekoPatcher.Mode,
  pspSdkDir: File,
  nativeImDir: File,
  fragmentDir: File,
  private val extPatchBase: Int,
  private val newEbootTexts: List<String>,
  private val publicBuild: Boolean,
  private val debugNoInline: Boolean = false,
) {
  private val funcs = Functions(mode)
  private val vars = Variables(mode)

  private val compileResult by lazy {
    println("Copying native code...")
    val fileNames = listOf(
      "hook.cpp",
      "hook.h",
      "patch.cpp",
      "platform.h",
      "subs.cpp",
      "subs.h",
      "subs_generated.h",
    )
    val providedFiles = listOf(
      "subs_generated.cpp"
    )
      .map { nativeImDir.child(it) }
    nativeImDir.mkdir()
    val files = fileNames.map { fileName ->
      val targetFile = nativeImDir.child(fileName)
      copyFromJar(this, "/native/$fileName", targetFile)
      targetFile
    }
    val compileFiles = files.filter { it.extension == "cpp" } + providedFiles

    println("Compiling native code...")
    if (publicBuild && debugNoInline) {
      error("Can't disable inline code for public build")
    }
    val fragmentSizes = (0..ScriptPatcher.MAX_FRAGMENTS).map { index ->
      fragmentDir.child("$index.BIN").takeIf { it.exists() }
        ?.length() ?: 0
    }
    pspCodeCompiler(
      pspSdkDir,
      gccFlags = standardPspGccFlags.asSequence()
        .plus("-Werror=overflow")
        .plus("-O2")
        .plus("-DPORTABLE_${mode.number}")
        .plus("-DSCRIPT_ZERO_WIDTH_SPACE=${scriptZeroWidthSpace.code}")
        .plus("-DSCRIPT_LOG_HINT_MARKER=${scriptLogHintMarker.code}")
        .plus("-DSCRIPT_FRAGMENT_TEXT_MARKER=${scriptFragmentTextMarker.code}")
        .plus("-DSCRIPT_FRAGMENT_LAST_INDEX=${fragmentSizes.lastIndex}")
        .plus("-DSCRIPT_FRAGMENT_MAX_SIZE=${fragmentSizes.max()}")
        .plus("-DSCRIPT_FRAGMENT_SIZES=${fragmentSizes.joinToString(",")}")
        .plus("-DSUBS_FONT_TAG=${mode.subsFontTag}")
        .let { if (debugNoInline) it.plus("-fno-inline") else it }
        .toList()
    )
      .compile(
        patchBaseAddress = extPatchBase + 0x10,
        outDir = nativeImDir.child("out"),
        srcFiles = compileFiles,
      )
  }

  fun assembleExternalPatch() = assembleAsByteArray(extPatchBase) {
    zeroTerminatedString("~Umineko patch~")
    compiledElf(compileResult)
    zeroTerminatedString(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString())

    vars.asciiToShiftJisValues = region {
      with(KioOutputStream(ByteArrayOutputStream())) {
        val jisBytes = wideChars.values
          .flatMap {
            val bytes = it.toString().toByteArray(Charsets.WINDOWS_932)
            listOf(bytes[1], bytes[0])
          }
          .toByteArray()
        writeBytes(jisBytes)
        align(4)
        getAsByteArrayOutputStream().toByteArray()
      }
        .let { bytes(it) }
    }

    vars.newEbootTexts = newEbootTexts.mapIndexed { index, text ->
      val textConfig = when {
        index in 30..61 -> null // used directly
        index >= 63 -> { // P2 tips
          when (mode) {
            UminekoPatcher.Mode.PORTABLE_1 -> null
            UminekoPatcher.Mode.PORTABLE_2 -> applyScriptReplacements(text) to Charsets.WINDOWS_932
          }
        }
        else -> text to Charsets.WINDOWS_932
      }
      when (textConfig) {
        null -> 0xCDCDCDCD.toInt()
        else -> {
          val (transformedText, encoding) = textConfig
          zeroTerminatedString(transformedText, encoding)
        }
      }
    }

    includeExtMainPatches(mode, funcs, vars, compileResult)
    includeExtFontPatches(mode, funcs, vars, compileResult)

    align16()
  }

  fun assembleEbootPatches(
    patchSize: Int,
    displayListEnlargeSize: Int,
  ) = patchSuite(baseAddress = 0x8804000, useRelativeChangeAddress = false) {
    includeMainPatches(mode, funcs, vars, compileResult, patchSize, displayListEnlargeSize)
    includeFontPatches(mode, funcs, vars, compileResult)
    includeTextPatches(mode, funcs, vars, newEbootTexts)
  }

  class Functions(mode: UminekoPatcher.Mode) {
    val sceIoOpen: Int
    val sceIoRead: Int
    val sceIoClose: Int
    val sceCtrlReadBufferPositive: Int
    val strcpy: Int
    val allocate: Int

    var controlCodeProgressText = 0
    var controlCodeSkipSpaceAfterAutoLineBreak = 0
    var controlCodeRemapFragmentText = 0

    var fontStoreAsciiChar = 0
    var fontStoreWideChar = 0
    var fontApplyKerningDispatch = 0
    var fontApplyXAdvanceDispatch = 0
    var fontReinitDispatch = 0
    var fontGlyphsDrawCompact = 0

    var commonResetPreviousChar = 0

    var scriptCalculateActualNameWidth = 0

    var backlogChapterListSetCompactFont = 0
    var backlogChapterListUnsetCompactFont = 0
    var backlogOnEnter = 0
    var backlogOnExit = 0

    var tipsFixButtonsUi = 0
    var crossCircleButtonSwap = 0
    var saveChangeLanguage = 0
    var cursorUseGlyphXAdvance = 0
    var configInstallSkipMovingUp = 0
    var configInstallSkipMovingDown = 0

    var subsRenderDispatch = 0
    var subsLoadCheckHook = 0
    var subsClearAllDispatch = 0

    init {
      when (mode) {
        UminekoPatcher.Mode.PORTABLE_1 -> {
          sceIoOpen = 0x088EBAD4
          sceIoRead = 0x088EBA8C
          sceIoClose = 0x088EBAA4
          sceCtrlReadBufferPositive = 0x088ebc0c
          strcpy = 0x08857010
          allocate = 0x08808514
        }
        UminekoPatcher.Mode.PORTABLE_2 -> {
          sceIoOpen = 0x088f2b78
          sceIoRead = 0x088f2b30
          sceIoClose = 0x088f2b48
          sceCtrlReadBufferPositive = 0x088F2CB0
          strcpy = 0x0885df74
          allocate = 0x088085ac
        }
      }
    }
  }

  class Variables(mode: UminekoPatcher.Mode) {
    val fontCurrentX: Int

    var asciiToShiftJisValues = 0
    var newEbootTexts: List<Int> = emptyList()

    init {
      when (mode) {
        UminekoPatcher.Mode.PORTABLE_1 -> {
          fontCurrentX = 0x08976d48
        }
        UminekoPatcher.Mode.PORTABLE_2 -> {
          fontCurrentX = 0x08985448
        }
      }
    }
  }
}

internal fun Assembler.ush(rt: Reg, offset: Int, base: Reg) {
  sb(rt, offset, base)
  srl(at, rt, 8)
  sb(at, offset + 1, base)
}

context(mode: UminekoPatcher.Mode)
internal fun <T> s(p1: T, p2: T) = mode.select(p1, p2)
