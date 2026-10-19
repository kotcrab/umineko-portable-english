package tl.umineko.cli

import kio.util.child
import kio.util.relativizePath
import kio.util.walkDir
import tl.umineko.file.PftFile
import tl.umineko.uminekoExtract
import tl.umineko.uminekoOutput
import java.io.File

fun main() {
  val inDir1 = uminekoExtract.child("CPK 1/DATA")
  val outDir1 = uminekoOutput.child("IMG 1").also { it.mkdir() }
  val inDir2 = uminekoExtract.child("CPK 2/DATA")
  val outDir2 = uminekoOutput.child("IMG 2").also { it.mkdir() }

  dumpImages(inDir1, outDir1)
  dumpImages(inDir2, outDir2)

  println("Done")
}

private fun dumpImages(inDir: File, outDir: File) {
  walkDir(inDir) { inFile ->
    if (inFile.extension == "pft") {
      val relPath = inFile.relativizePath(inDir)
      println("Processing $relPath...")
      val outFile = outDir.child("$relPath.png")
        .also { it.parentFile.mkdirs() }
      PftFile(inFile).writeToPng(outFile)
    }
  }
}
