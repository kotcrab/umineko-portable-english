package tl.umineko.patcher

import kio.util.WINDOWS_932
import kio.util.child
import kio.util.toWHex
import tl.util.SrtSubtitlesConverter
import java.io.File

class SubsGenerator(
  private val nativeImDir: File,
) {
  fun generate(subtitles: List<Input>) {
    val subs = subtitles.map {
      val fps = 1000
      val convertedTranslated = SrtSubtitlesConverter(it.translatedSrtFile, fps).convert()
      val convertedRomaji = SrtSubtitlesConverter(it.romajiSrtFile, fps).convert()
      val parts = convertedTranslated.lines.zip(convertedTranslated.timings)
        .mapIndexed { index, (text, frames) ->
          val (translatedText, fontMode) = when {
            text.startsWith("!") -> text.substringAfter(" ") to text.substringBefore(" ").drop(1).toInt()
            else -> text to 0
          }
          SubPart(translatedText, convertedRomaji.lines[index], frames, fontMode)
        }
      Sub(it.bgmId, parts)
    }
    generateCode(subs)
  }

  private fun generateCode(subs: List<Sub>) {
    val subsGen = subs.mapIndexed { index, sub ->
      "{${sub.bgmId}, ${sub.parts.size}, sub${index}_parts}"
    }

    nativeImDir.child("subs_generated.cpp").writeText(
      """
// DO NOT MODIFY. THIS FILE IS AUTO-GENERATED.

#include "subs_generated.h"

${generatePartsData(subs).joinToString(separator = "\n")}

${generateParts(subs).joinToString(separator = "\n")}

const u32 subsCount = ${subsGen.size};
const Subtitle subs[] = {
  ${subsGen.joinToString(separator = ",\n  ")}
};

""".trimStart(),
    )
  }

  private fun generatePartsData(subs: List<Sub>): List<String> {
    return subs.flatMapIndexed { subIdx, sub ->
      val translated = sub.parts.mapIndexed { partIdx, part ->
        generateTextData(subIdx, partIdx, part.translatedText)
      }
      val romaji = sub.parts.mapIndexed { partIdx, part ->
        generateTextData(subIdx, partIdx, part.romajiText, "_r")
      }
      translated + romaji
    }
  }

  private fun generateTextData(subIdx: Int, partIdx: Int, partText: String, suffix: String = ""): String {
    val bytes = partText
      .toByteArray(Charsets.WINDOWS_932)
      .toMutableList()
      .apply {
        add(0)
      }
      .joinToString(separator = ", ", prefix = "{", postfix = "}") {
        "0x${it.toWHex()}"
      }
    return """// ${partText.replace("\n", "<br>")}
const u8 partdata_${subIdx}_$partIdx$suffix[] = $bytes;
    """.trimIndent()
  }

  private fun generateParts(subs: List<Sub>): List<String> {
    return subs.mapIndexed { subIndex, sub ->
      """
const SubtitlePart sub${subIndex}_parts[] = {
  ${generatePartsList(subIndex, sub)}
};""".trimStart()
    }
  }

  private fun generatePartsList(subIdx: Int, sub: Sub) = sub.parts
    .mapIndexed { partIdx, part -> partIdx to part }
    .joinToString(separator = ",\n  ") { (partIdx, part) ->
      "{partdata_${subIdx}_$partIdx, partdata_${subIdx}_${partIdx}_r, ${part.frames}, ${part.fontMode}}"
    }

  private class Sub(
    val bgmId: Int,
    val parts: List<SubPart>,
  )

  private class SubPart(
    val translatedText: String,
    val romajiText: String,
    val frames: Int,
    val fontMode: Int,
  )

  data class Input(
    val bgmId: Int,
    val translatedSrtFile: File,
    val romajiSrtFile: File,
  )
}
