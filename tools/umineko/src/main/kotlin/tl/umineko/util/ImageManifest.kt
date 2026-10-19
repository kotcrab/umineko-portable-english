package tl.umineko.util

data class ImageManifest(
  val images: Map<String, ImageDetails>,
) {
  data class ImageDetails(
    val width: Int,
    val height: Int,
  )
}
