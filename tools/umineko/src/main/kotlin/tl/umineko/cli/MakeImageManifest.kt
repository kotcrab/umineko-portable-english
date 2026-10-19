package tl.umineko.cli

import kio.util.child
import kio.util.relativizePath
import kio.util.walkDir
import kio.util.writeJson
import tl.umineko.uminekoExtract
import tl.umineko.uminekoOutput
import tl.umineko.util.ImageManifest
import java.io.File
import javax.imageio.ImageIO

fun main() {
  makeImageManifest(uminekoExtract.child("IMG 1"), uminekoOutput.child("images-1.json"))
  makeImageManifest(uminekoExtract.child("IMG 2"), uminekoOutput.child("images-2.json"))
  println("Done")
}

private fun makeImageManifest(inDir: File, outFile: File) {
  println("Processing ${inDir.name}...")
  val entries = mutableMapOf<String, ImageManifest.ImageDetails>()
  walkDir(inDir) { file ->
    val relPath = file.relativizePath(inDir)
    val image = ImageIO.read(file)
    entries[relPath] = ImageManifest.ImageDetails(image.width, image.height)
  }
  outFile.writeJson(ImageManifest(entries))
}
