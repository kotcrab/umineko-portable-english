package tl.umineko.patcher

import com.google.common.hash.Hashing
import kio.util.child
import kio.util.execute
import kio.util.readJsonOrElse
import kio.util.relativizePath
import kio.util.walkDir
import kio.util.writeJson
import tl.umineko.file.PftFile
import tl.umineko.util.ImageManifest
import java.io.File

class PftConverter(
  private val manifest: ImageManifest,
  private val srcDir: File,
  private val outDir: File,
  private val workDir: File,
  private val verifyDir: File,
  private val hashFile: File,
) {
  private val hashes: MutableMap<String, ByteArray> = hashFile.readJsonOrElse { mutableMapOf() }

  fun patchAll() {
    workDir.mkdir()
    verifyDir.mkdir()
    walkDir(srcDir) { pngFile ->
      if (!pngFile.extension.equals("png", ignoreCase = true) || pngFile.name.startsWith(".")) {
        return@walkDir
      }
      val relPath = pngFile.relativizePath(srcDir)
      val oldHash = hashes.getOrDefault(relPath, ByteArray(0))
      val newHash = Hashing.murmur3_128().hashBytes(pngFile.readBytes()).asBytes()
      if (oldHash.contentEquals(newHash)) {
        println("Image $relPath is up to date")
        return@walkDir
      }

      println("Converting image $relPath...")

      val resizedPngFile = workDir.child(relPath.replace("/", "$"))
      val outFile = outDir.child(relPath.removeSuffix(".png"))
      outFile.parentFile.mkdirs()

      val details = manifest.images[relPath]
        ?: error("Missing image in manifest: $relPath")

      execute(
        "magick",
        workingDirectory = workDir,
        args = listOf(
          pngFile,
          "-filter", "Lanczos",
          "-resize", "${details.width}x${details.height}!",
          "+dither",
          "-colors", PftWriter.MAX_COLORS.toString(),
          "-type", "TrueColorAlpha",
          "PNG32:${resizedPngFile.name}",
        ),
      )

      PftWriter(resizedPngFile)
        .writeTo(outFile)
      PftFile(outFile)
        .writeToPng(verifyDir.child(relPath.replace("/", "$")))

      hashes[relPath] = newHash
    }

    hashFile.writeJson(hashes)
  }
}
