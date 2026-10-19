package tl.umineko.cli

import kio.util.child
import tl.pspSdkHome
import tl.umineko.patcher.UminekoPatcher
import tl.umineko.uminekoBase
import tl.umineko.uminekoExtract
import tl.umineko.uminekoPatch
import tl.util.startPpssppWithIso

fun main(args: Array<String>) {
  val mode = UminekoPatcher.Mode.valueOf(args[0].uppercase())
  val (isoExtractDir, stockIso) = when (mode) {
    UminekoPatcher.Mode.PORTABLE_1 -> uminekoExtract.child("ISO 1") to uminekoBase.child("ISO/Umineko Portable 1.iso")
    UminekoPatcher.Mode.PORTABLE_2 -> uminekoExtract.child("ISO 2") to uminekoBase.child("ISO/Umineko Portable 2.iso")
  }
  val isoBuildDir = UminekoPatcher(
    mode = mode,
    projectDir = uminekoPatch,
    isoExtractDir = isoExtractDir,
    stockIso = stockIso,
    pspSdkDir = pspSdkHome,
    publicBuild = false,
    cleanBuild = false,
  ).buildAll()
  startPpssppWithIso(isoBuildDir)
}
