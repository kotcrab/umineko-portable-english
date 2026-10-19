package tl.umineko.patcher

import kio.LERandomAccessFile
import kio.util.align
import kio.util.child
import kio.util.execute
import kio.util.nullStreamHandler
import kio.util.readJson
import kio.util.seek
import kio.util.stdoutStreamHandler
import kio.util.toRelativeNixPath
import kmipsx.elf.patchElf
import tl.file.CpkFile
import tl.file.HieroFile
import tl.umineko.file.Jis2UcsFile
import tl.util.FileCollector
import tl.util.WarningCollector
import tl.util.createXdeltaPatch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UminekoPatcher(
  private val mode: Mode,
  private val projectDir: File,
  private val isoExtractDir: File,
  private val stockIso: File,
  private val pspSdkDir: File,
  private val publicBuild: Boolean,
  private val cleanBuild: Boolean,
) {
  private val buildDir = projectDir.child("build-${mode.number}")
  private val imDir = projectDir.child("im-${mode.number}")
  private val srcDir = projectDir.child("src")
  private val toolsDir = projectDir.child("tools")

  private val isoBuildDir = buildDir.child("iso")
  private val isoBuildUsrDir = isoBuildDir.child("PSP_GAME/USRDIR")
  private val isoBuildFragmentDir = isoBuildUsrDir.child("FRAG")
  private val isoOut = buildDir.child("umineko-portable-${mode.number}-patched.iso")

  private val imDataCpkFilesDir = imDir.child("data-cpk-files")
  private val imStreamCpkFilesDir = imDir.child("stream-cpk-files")
  private val imNativeDir = imDir.child("native")

  private val customIsoFilesDir = srcDir.child("custom-iso-files-${mode.number}")
  private val customDataCpkFilesDir = srcDir.child("custom-data-cpk-files-${mode.number}")
  private val customStreamCpkFilesDir = srcDir.child("custom-stream-cpk-files-${mode.number}")
  private val isoBaseDir = srcDir.child("iso-base-${mode.number}")
  private val translationDir = srcDir.child("translation")

  private val patcherDir = buildDir.child("patcher")

  private val mkisofsTool = toolsDir.child("mkisofs.exe")
  private val sevenZipTool = toolsDir.child("7za.exe")
  private val xdeltaTool = toolsDir.child("xdelta.exe")

  private val warningCollector = WarningCollector()

  fun buildAll(): File {
    println("--- Umineko Portable Internal Patcher ---")
    println("Working directory: ${projectDir.absolutePath}")
    if (!projectDir.exists()) {
      error("Working directory does not exist")
    }
    createDirs()

    copyIsoFiles()
    copyIsoUserFiles()
    copyCustomIsoFiles()

    generateSubs()

    patchFonts()
    patchPft()
    patchScript()
    patchEboot()
    patchCpks()
    patchEncoding()

    buildIso()
    createPatches()
    createPatchZip()
    postBuildChecks()
    return isoBuildDir
  }

  private fun createDirs() {
    imDir.mkdir()
    imDataCpkFilesDir.mkdir()
    imStreamCpkFilesDir.mkdir()
    if (mode.useScriptFragments) {
      isoBuildFragmentDir.mkdir()
    }
  }

  private fun copyIsoFiles() {
    println("--- Copy ISO files ---")
    if (cleanBuild) {
      println("Cleaning ISO build directory...")
      isoBuildDir.deleteRecursively()
    }
    if (isoBuildDir.exists()) {
      println("ISO build directory already exists, skipping copy")
      return
    }
    isoBaseDir.copyRecursively(isoBuildDir, overwrite = true)
  }

  private fun copyIsoUserFiles() {
    println("--- Copy ISO user files ---")
    isoExtractDir.copyRecursively(isoBuildUsrDir, overwrite = true)
  }

  private fun copyCustomIsoFiles() {
    println("--- Copy custom ISO files ---")
    customIsoFilesDir.copyRecursively(isoBuildDir, overwrite = true)
  }

  private fun generateSubs() {
    println("--- Generate subtitles ---")
    val subsDir = translationDir.child("subs")
    val subs = when (mode) {
      Mode.PORTABLE_1 -> listOf(
        SubsGenerator.Input(0x3B, subsDir.child("EP2_58_eng.srt"), subsDir.child("EP2_58_rom.srt")),
      )
      Mode.PORTABLE_2 -> listOf(
        SubsGenerator.Input(0x4F, subsDir.child("EP3_83_eng.srt"), subsDir.child("EP3_83_rom.srt")),
        SubsGenerator.Input(0x68, subsDir.child("EP4_109_eng.srt"), subsDir.child("EP4_109_rom.srt")),
      )
    }
    SubsGenerator(imNativeDir)
      .generate(subs)
  }

  private fun patchFonts() {
    println("--- Patch fonts ---")
    val srcFontDir = srcDir.child("font")
    val maruxMerged = FontMerge(
      srcPngFile = srcFontDir.child("marux-src.png"),
      hieroFile = HieroFile(srcFontDir.child("hiero/noto.fnt"), srcFontDir.child("hiero/noto.png")),
      dashGlyphFile = srcFontDir.child("hiero/dash.png"),
      warn = warningCollector::warn
    )
      .mergeTo(srcFontDir.child("marux.png"))
    FontConverter(
      srcFontDir = srcFontDir,
      imFontDir = imDataCpkFilesDir.child("font"),
      imFontVerifyDir = imDir.child("font-verify"),
      hashFile = imDir.child("font-hash.json")
    )
      .patch(
        listOf(
          FontConverter.InputFont("popx", FontTables.popxXAdvance, FontTables.popxKerning),
          FontConverter.InputFont("marux", maruxMerged.xAdvance, maruxMerged.kerning),
          FontConverter.InputFont("minx", FontTables.minxXAdvance, FontTables.minxKerning),
        )
      )
  }

  private fun patchPft() {
    println("--- Patch PFT ---")
    PftConverter(
      manifest = srcDir.child("images-${mode.number}.json").readJson(),
      srcDir = srcDir.child("png2pft-${mode.number}"),
      outDir = imDataCpkFilesDir,
      workDir = imDir.child("pft-work"),
      verifyDir = imDir.child("pft-verify"),
      hashFile = imDir.child("pft-hash.json"),
    )
      .patchAll()
  }

  private fun patchScript() {
    println("--- Patch script ---")
    ScriptPatcher(
      mode = mode,
      translationDir = translationDir,
      publicBuild = publicBuild,
      warn = warningCollector::warn
    )
      .patchScript(
        scrFile = isoBuildUsrDir.child("SCR.BIN"),
        fragmentDir = isoBuildFragmentDir,
      )
  }

  private fun patchEboot() {
    println("--- Patch EBOOT ---")

    println("Final assembly of patched EBOOT...")
    val displayListEnlargeSize = 0x20000
    val patchStartVirtual = mode.patchStartVirtual + displayListEnlargeSize
    val ebootPatches = EbootPatches(
      mode = mode,
      pspSdkDir = pspSdkDir,
      nativeImDir = imNativeDir,
      fragmentDir = isoBuildFragmentDir,
      extPatchBase = patchStartVirtual,
      newEbootTexts = translationDir.child("eboot.txt")
        .readLines()
        .map {
          it
            .replace("\\n", "\n")
            .replace("Portable 1", "Portable ${mode.number}")
        },
      publicBuild = publicBuild,
    )
    val patchOut = isoBuildDir.child("patch")
    with(LERandomAccessFile(patchOut)) {
      setLength(0)
      write(ebootPatches.assembleExternalPatch())
      align(0x10)
      close()
    }
    val ebootSrc = srcDir.child(mode.ebootName)
    val ebootOut = isoBuildDir.child("PSP_GAME/SYSDIR/EBOOT.BIN")
    patchElf(
      ebootSrc,
      ebootOut,
      ebootPatches.assembleEbootPatches(
        patchSize = patchOut.length().toInt(),
        displayListEnlargeSize = displayListEnlargeSize,
      ),
      baseProgramHeaderIndex = 0,
      relocationSectionHeaderIndexes = listOf(2, 37, 39),
    )
    with(LERandomAccessFile(ebootOut)) {
      srcDir.child("eboot-${mode.number}").listFiles()
        ?.forEach { patchFile ->
          val vAddr = patchFile.name.split("-")[0].toInt(16)
          val pAddr = vAddr - 0x8804000 + 0xC0
          seek(pAddr)
          write(patchFile.readBytes())
        }
      close()
    }
    ebootOut.copyTo(ebootOut.resolveSibling("BOOT.BIN"), overwrite = true)
  }

  private fun patchCpks() {
    println("--- Patch CPKs in-place ---")
    println("Patching DATA.CPK...")
    patchCpkInPlace(isoBuildUsrDir.child("DATA.CPK"), collectDataCpkReplacementFiles())
    println("Patching STREAM.CPK...")
    patchCpkInPlace(isoBuildUsrDir.child("STREAM.CPK"), collectStreamCpkReplacementFiles())
  }

  private fun collectDataCpkReplacementFiles(): Map<String, File> {
    val collector = FileCollector(projectDir, "DATA CPK", warningCollector::warn)
    collector.includeDir(customDataCpkFilesDir)
    collector.includeDir(imDataCpkFilesDir)
    return collector.files
  }

  private fun collectStreamCpkReplacementFiles(): Map<String, File> {
    val collector = FileCollector(projectDir, "STREAM CPK", warningCollector::warn)
    collector.includeDir(customStreamCpkFilesDir)
    collector.includeDir(imStreamCpkFilesDir)
    return collector.files
  }

  private fun patchCpkInPlace(
    cpkFile: File,
    cpkReplacements: Map<String, File>,
  ) {
    CpkFile(
      cpkFile,
      allowInPlaceDecrypt = true
    )
      .patchInPlace(
        patchedFiles = cpkReplacements,
        insertNewFilesAt = cpkFile.length(),
        allowBlockReplace = publicBuild,
      )
  }

  private fun patchEncoding() {
    println("--- Patch encoding file ---")
    val file = isoBuildUsrDir.child("JIS2UCS.BIN")
    val newChars = scriptRemappedCharacters
      .map { (newChar, oldChar) ->
        oldChar.code to newChar.code
      }
      .toMap()
      .plus(
        scriptSaveRemappedCharacters
          .map { it.key.code to it.value.code }
      )
    val chars = Jis2UcsFile(file)
      .chars
      .map { char ->
        newChars.getOrDefault(char, char)
      }
    Jis2UcsWriter(chars)
      .writeTo(file)
  }

  private fun buildIso() {
    println("--- Write ISO ---")
    if (!publicBuild) {
      println("Not making public build, deleting existing and skipping building ISO")
      isoOut.delete()
      return
    }
    execute(
      mkisofsTool,
      workingDirectory = projectDir,
      streamHandler = stdoutStreamHandler(),
      args = listOf(
        "-iso-level", "4", "-xa",
        "-sysid", "\"PSP GAME\"", "-A", "\"PSP GAME\"", "-V", "UMINEKO", "-publisher", "UMINEKO",
        "-o", isoOut.toRelativeNixPath(projectDir), isoBuildDir.toRelativeNixPath(projectDir),
      ),
    )
  }

  private fun createPatches() {
    if (!publicBuild) {
      return
    }
    println("--- Create xdelta patch ---")
    val dataDir = patcherDir.child("data")
      .also { it.mkdirs() }
    xdeltaTool.copyTo(dataDir.child("xdelta.exe"), overwrite = true)
    createXdeltaPatch(
      xdeltaTool,
      baseDir = projectDir.parentFile,
      oldFile = stockIso,
      newFile = isoOut,
      patchOut = dataDir.child("patch${mode.number}.xdelta"),
    )
    srcDir.child("bat/patch.bat")
      .copyTo(patcherDir.child("Drag and drop Umineko Portable (PSP) ISO on me.bat"), overwrite = true)
  }

  private fun createPatchZip() {
    println("--- Update zip archive ---")
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val zipName = "Umineko Portable (PSP) Patcher $timestamp.zip"
    buildDir.child(zipName).delete()
    println("Deleted '$zipName'")
    if (publicBuild) {
      execute(
        sevenZipTool,
        args = listOf("a", zipName, "./patcher/*"),
        workingDirectory = buildDir,
        streamHandler = nullStreamHandler(),
      )
      println("Created '$zipName'")
    }
  }

  private fun postBuildChecks() {
    println("--- Post build checks ---")
    println("Build finished")
    warningCollector.printSummary()
  }

  enum class Mode(
    val number: String,
    val ebootName: String,
    val patchStartVirtual: Int,
    val useScriptFragments: Boolean,
    val subsFontTag: Int = 3,
  ) {
    PORTABLE_1(
      number = "1",
      ebootName = "ULJM05968.BIN",
      patchStartVirtual = 0x0899FDC0,
      useScriptFragments = false,
    ),
    PORTABLE_2(
      number = "2",
      ebootName = "ULJM05969.BIN",
      patchStartVirtual = 0x089AE080,
      useScriptFragments = true,
    ),
    ;

    fun <T> select(p1: T, p2: T): T {
      return when (this) {
        PORTABLE_1 -> p1
        PORTABLE_2 -> p2
      }
    }
  }
}
