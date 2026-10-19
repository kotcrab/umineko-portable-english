package tl.umineko.patcher

import tl.file.HieroFile
import java.awt.Color
import java.awt.Graphics2D
import java.io.File
import javax.imageio.ImageIO

class FontMerge(
  private val srcPngFile: File,
  private val hieroFile: HieroFile,
  private val warn: (String) -> Unit,
  private val warnBleed: Boolean = false,
  private val dashGlyphFile: File,
) {
  fun mergeTo(outPngFile: File): Result {
    val font = ImageIO.read(srcPngFile)

    val glyphWidth = 18
    val glyphHeight = 18

    val graphics = font.graphics as Graphics2D
    graphics.background = Color(0x00FFFFFF, true)

    val chars = mapOf(
      '!' to Pair(0, 9),
      '"' to Pair(0, 76),
      '#' to Pair(0, 83),
      '%' to Pair(0, 82),
      '&' to Pair(0, 84),
      '\'' to Pair(0, 75),
      '(' to Pair(0, 41),
      ')' to Pair(0, 42),
      '*' to Pair(0, 85),
      '+' to Pair(0, 59),
      ',' to Pair(0, 3),
      '-' to Pair(0, 60),
      '.' to Pair(0, 4),
      '/' to Pair(0, 30),
      ':' to Pair(0, 6),
      ';' to Pair(0, 7),
      '<' to Pair(0, 66),
      '=' to Pair(0, 64),
      '>' to Pair(0, 67),
      '?' to Pair(0, 8),
      '_' to Pair(0, 17),
      '{' to Pair(0, 47),
      '}' to Pair(0, 48),
      '~' to Pair(0, 32),
      '«' to Pair(1, 66),
      '»' to Pair(1, 67),
      '×' to Pair(0, 62),
      '÷' to Pair(0, 63),

      '•' to Pair(0, 5),
      '‘' to Pair(0, 37),
      '’' to Pair(0, 38),
      '“' to Pair(0, 39),
      '”' to Pair(0, 40),

      '⁰' to Pair(6, 48),
      '³' to Pair(6, 49),
      '⁵' to Pair(6, 50),
      '⁶' to Pair(6, 51),
      '⁸' to Pair(6, 52),
      'â' to Pair(6, 53),
      'ç' to Pair(6, 54),
      'è' to Pair(6, 55),
      'é' to Pair(6, 56),
      'ê' to Pair(6, 57),
      'ò' to Pair(6, 58),
      'ö' to Pair(6, 59),

      '‐' to Pair(10, 0), // ignoring it
      '–' to Pair(0, 29),
      '—' to Pair(0, 27),
      '―' to Pair(10, 1), // overridden with glyph file
    )
      .mapKeys { it.key.code }

    val drawXOffset = mapOf(
      '–' to 2,
    )
      .mapKeys { it.key.code }
    val drawYOffset = mapOf(
      '⁰' to 1,
      '³' to 1,
      '⁵' to 1,
      '⁶' to 1,
      '⁸' to 1,
    )
      .mapKeys { it.key.code }

    val xAdvanceOffset = mapOf(
      '―' to 1,
    )
      .mapKeys { it.key.code }

    hieroFile.chars.forEach {
      val row: Int
      val column: Int
      when {
        it.id in 48..57 -> { // numbers
          row = 2
          column = 15 + it.id - 48
        }
        it.id in 65..90 -> { // uppercase
          row = 2
          column = 32 + it.id - 65
        }
        it.id in 97..122 -> { // lowercase
          row = 2
          column = 64 + it.id - 97
        }
        it.id in setOf(0, 10, 32, 160) -> {
          row = 9
          column = 0
        }
        it.id in chars -> {
          val cords = chars[it.id]!!
          row = cords.first
          column = cords.second
        }
        else -> {
          warn("Don't know where to write font char ID ${it.id} (${it.id.toChar()})")
          row = 9
          column = 0
        }
      }

      val cellX = column * glyphWidth
      val cellY = row * glyphHeight
      val targetX = cellX + it.xoffset + 2 + drawXOffset.getOrDefault(it.id, 0)
      val targetY = cellY + it.yoffset - 5 + drawYOffset.getOrDefault(it.id, 0)
      if (warnBleed && (it.width > 18 || it.height > 18)) {
        warn("Char ID ${it.id} may bleed.")
      }
      graphics.clearRect(cellX, cellY, glyphWidth, glyphHeight)
      graphics.drawImage(hieroFile.glyphs[it.id], targetX, targetY, null)
    }

    // draw 8213 (―)
    graphics.clearRect(28 * glyphWidth, 0, glyphWidth, glyphHeight)
    graphics.drawImage(ImageIO.read(dashGlyphFile), 28 * glyphWidth, 0, null)

    ImageIO.write(font, "PNG", outPngFile)

    val xAdvance = hieroFile.chars
      .filter { it.id != 160 }
      .associate { it.id.toChar() to it.xadvance + xAdvanceOffset.getOrDefault(it.id, 0) }
      .mapKeys { scriptRemappedCharacters.getOrDefault(it.key, it.key) }
    val kerning = hieroFile.kernings
      .associate { Pair(it.first.toChar(), it.second.toChar()) to it.amount }
    return Result(
      xAdvance = xAdvance
        .plus('【' to 15),
      kerning = kerning
        .plus(('?' to '”') to 2)
        .plus(('’' to '?') to 4)
    )
  }

  data class Result(
    val xAdvance: Map<Char, Int>,
    val kerning: Map<Pair<Char, Char>, Int>,
  )
}
