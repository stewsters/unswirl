package com.stewsters

import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

fun main() {
    println("Starting")

    // have an image at input.png

    // swirl image, this may be done for you
    val dir = File(".")
    "convert -swirl 720 input.png swirl.png".runCommand(dir)

    // Load image we are working on into memory
    val swirledImage = ImageIO.read(File("swirl.png"))

    // This is the output image, we will build it up
    val output = BufferedImage(swirledImage.width, swirledImage.height, TYPE_INT_RGB)

    // Make a directory, we will create 1 pixel images in there, swirl them forward to find the mapping
    val pixelDir = File("pixel")
    pixelDir.mkdirs()

    val out = output.graphics

    // This could be parallelized
    for (y in 0 until swirledImage.height) {
        for (x in 0 until swirledImage.width) {

            // we make a 1 pixel image
            val pixelImage = BufferedImage(swirledImage.width, swirledImage.height, TYPE_INT_ARGB)
            with(pixelImage.graphics) {
                color = Color.WHITE
                drawRect(x, y, 1, 1)
            }
            ImageIO.write(pixelImage, "png", File("pixel/pixel_${x}_${y}.png"))

            // we apply the operation using the swirling software
            "convert -swirl 720 pixel/pixel_${x}_${y}.png pixel/pixelSwirlMask_${x}_${y}.png".runCommand(dir)

            // now pixelSwirlMask is a mask, where it is white is where we should look on the swirled image
            val mask = ImageIO.read(File("pixel/pixelSwirlMask_${x}_${y}.png"))

            // clean up some garbage, ideally you could do this without hitting disk, it would be a lot faster
            File("pixel/pixelSwirlMask_${x}_${y}.png").delete()
            File("pixel/pixel_${x}_${y}.png").delete()

            // use the mask on the image, get the color from the swirled one
            val swirlCopy = deepCopy(swirledImage)
            applyAlphaMask(swirlCopy, mask)
            val avg = averageColorExcludingAlpha(swirlCopy)

            // set that color in the final image
            out.color = avg
            out.drawRect(x, y, 1, 1)

            println("processing $x,$y")

        }
        ImageIO.write(output, "png", File("unswirled.png"))
    }
}

fun String.runCommand(workingDir: File) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}

/*
 * average color of image where not alpha masked
 */
fun averageColorExcludingAlpha(
    bi: BufferedImage
): Color {
    var sumr = 0.0
    var sumg = 0.0
    var sumb = 0.0
    var sumAlpha = 0.0
    for (x in 0 until bi.width) {
        for (y in 0 until bi.height) {
            val pixel = Color(bi.getRGB(x, y), true)

            val alpha = pixel.alpha

            val alphaPercentage = alpha / 255.0
            sumAlpha += alphaPercentage

            sumr += pixel.red.toDouble() / 255.0 * alphaPercentage
            sumg += pixel.green.toDouble() / 255.0 * alphaPercentage
            sumb += pixel.blue.toDouble() / 255.0 * alphaPercentage
        }
    }
    return Color((sumr / sumAlpha).toFloat(), (sumg / sumAlpha).toFloat(), (sumb / sumAlpha).toFloat())
}

// Applies the alpha mask from the second image to the first
fun applyAlphaMask(image: BufferedImage, mask: BufferedImage ) {
    val width = image.width
    val height = image.height

    val imagePixels = image.getRGB(0, 0, width, height, null, 0, width)
    val maskPixels = mask.getRGB(0, 0, width, height, null, 0, width)

    for (i in imagePixels.indices) {
        val color = imagePixels[i] and (0x00ffffff) // Mask preexisting alpha
        val alpha = maskPixels[i] and (0xff000000.toInt()) //
        imagePixels[i] = color or alpha
    }

    image.setRGB(0, 0, width, height, imagePixels, 0, width)
}

// make a copy of an image
fun deepCopy(bi: BufferedImage): BufferedImage {
    val cm = bi.colorModel
    val isAlphaPremultiplied = cm.isAlphaPremultiplied
    val raster = bi.copyData(null)
    return BufferedImage(cm, raster, isAlphaPremultiplied, null)
}