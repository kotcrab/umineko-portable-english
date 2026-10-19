package tl.umineko.cli

import kio.util.child
import tl.umineko.file.FontBinFile
import tl.umineko.uminekoExtract
import tl.umineko.uminekoOutput

fun main() {
  val inDir = uminekoExtract.child("CPK 1/DATA/font")
  val outDir = uminekoOutput.child("fonts").also { it.mkdir() }

  inDir.listFiles()!!.forEach { file ->
    println("Processing ${file.name}...")
    FontBinFile(file)
      .writeToPng(outDir.child("${file.nameWithoutExtension}.png"))
  }

  println("Done")
}
