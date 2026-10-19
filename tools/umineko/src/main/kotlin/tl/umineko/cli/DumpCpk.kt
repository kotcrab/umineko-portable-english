package tl.umineko.cli

import kio.util.child
import tl.file.CpkFile
import tl.umineko.uminekoExtract
import tl.umineko.uminekoOutput
import java.io.File

fun main() {
  val iso1Dir = uminekoExtract.child("ISO 1")
  val iso2Dir = uminekoExtract.child("ISO 2")
  val cpk1Dir = uminekoOutput.child("CPK 1")
  val cpk2Dir = uminekoOutput.child("CPK 2")
  val tmpFile = uminekoOutput.child("tmp.cpk")

  extractCpk(iso1Dir.child("DATA.CPK"), tmpFile, cpk1Dir.child("DATA"))
  extractCpk(iso1Dir.child("STREAM.CPK"), tmpFile, cpk1Dir.child("STREAM"))
  extractCpk(iso2Dir.child("DATA.CPK"), tmpFile, cpk2Dir.child("DATA"))
  extractCpk(iso2Dir.child("STREAM.CPK"), tmpFile, cpk2Dir.child("STREAM"))
  tmpFile.delete()

  println("Done")
}

private fun extractCpk(inFile: File, tmpFile: File, outDir: File) {
  println("Extracting ${inFile.name}...")
  inFile.copyTo(tmpFile, overwrite = true)
  CpkFile(tmpFile, allowInPlaceDecrypt = true)
    .extractTo(outDir)
}
