package com.huoyejia.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Environment
import com.huoyejia.ai.BlueLMAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val SLIDE_W = 9144000
private const val SLIDE_H = 5143500

class PptExportService(
    private val context: Context,
    private val blueLM: BlueLMAdapter
) {
    suspend fun export(pack: ExplainPack): File {
        val slides = pack.toExportSlides()
        val foregroundImages = slides.map { slide ->
            blueLM.generateSlideImage(slide.imagePrompt) ?: TransparentIconFactory.createPng(slide.icon)
        }
        val backgroundImages = slides.mapIndexed { index, slide ->
            if (slide.backgroundAsset != null) readAssetBytes(slide.backgroundAsset) else readTemplateBackground(index)
        }

        return withContext(Dispatchers.IO) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val file = File(dir, "${pack.title.safeFileName()}_$timestamp.pptx")
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                zip.text("[Content_Types].xml", contentTypes(slides.size))
                zip.text("_rels/.rels", rootRels())
                zip.text("docProps/core.xml", coreProps(pack.title))
                zip.text("docProps/app.xml", appProps(slides.size))
                zip.text("ppt/presentation.xml", presentation(slides.size))
                zip.text("ppt/_rels/presentation.xml.rels", presentationRels(slides.size))
                zip.text("ppt/slideMasters/slideMaster1.xml", slideMaster())
                zip.text("ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels())
                zip.text("ppt/slideLayouts/slideLayout1.xml", slideLayout())
                zip.text("ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels())
                zip.text("ppt/theme/theme1.xml", theme())
                slides.forEachIndexed { index, slide ->
                    val slideNumber = index + 1
                    zip.bytes("ppt/media/fg$slideNumber.png", foregroundImages[index])
                    backgroundImages[index]?.let { zip.bytes("ppt/media/bg$slideNumber.jpeg", it) }
                    zip.text("ppt/slides/slide$slideNumber.xml", slideXml(slide, slideNumber, backgroundImages[index] != null))
                    zip.text("ppt/slides/_rels/slide$slideNumber.xml.rels", slideRels(slideNumber, backgroundImages[index] != null))
                }
            }
            file
        }
    }

    private fun readTemplateBackground(index: Int): ByteArray? {
        val asset = "ppt_template/image${(index % 5) + 1}.jpeg"
        return readAssetBytes(asset)
    }

    private fun readAssetBytes(path: String): ByteArray? {
        return runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()
    }

    private fun ExplainPack.toExportSlides(): List<ExportSlide> {
        val cover = ExportSlide(
            layout = LayoutStyle.Cover,
            title = title,
            bullets = listOf(hook, conciseExplanation).filter { it.isNotBlank() },
            imagePrompt = "Transparent education key visual for lesson cover: $title.",
            icon = "book",
            animationHint = "fade"
        )
        val outlineSlides = pptOutline.mapIndexed { index, slide ->
            slide.toExportSlide(index, title)
        }
        val scenes = animationScenes.take(3).mapIndexed { index, scene ->
            ExportSlide(
                layout = LayoutStyle.Storyboard,
                title = "动画 ${index + 1}: ${scene.title}",
                bullets = listOf("画面: ${scene.visual}", "旁白: ${scene.narration}"),
                imagePrompt = "Transparent storyboard illustration, ${scene.visual}, Chinese educational animation.",
                icon = if (index == 0) "spark" else "timeline",
                animationHint = "push"
            )
        }
        val close = ExportSlide(
            layout = LayoutStyle.Closing,
            title = "最后记住这一句",
            bullets = listOf(takeaway),
            imagePrompt = "Transparent summary icon for learning takeaway: $takeaway.",
            icon = "target",
            animationHint = "wipe"
        )
        return listOf(cover) + outlineSlides + scenes + close
    }

    private fun ExplainSlide.toExportSlide(index: Int, packTitle: String): ExportSlide {
        val layout = when (index % 4) {
            0 -> LayoutStyle.Cards
            1 -> LayoutStyle.Timeline
            2 -> LayoutStyle.Compare
            else -> LayoutStyle.Split
        }
        val prompt = imagePrompt.ifBlank {
            "Transparent educational infographic for '$title' in lesson '$packTitle'."
        }
        return ExportSlide(layout, title, bullets, prompt, icon, animationHint)
    }
}

private enum class LayoutStyle { Cover, Split, Cards, Timeline, Compare, Storyboard, Closing }

private data class ExportSlide(
    val layout: LayoutStyle,
    val title: String,
    val bullets: List<String>,
    val imagePrompt: String,
    val icon: String,
    val animationHint: String,
    val backgroundAsset: String? = null
)

private object TransparentIconFactory {
    fun createPng(icon: String): ByteArray {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFC0584D.toInt()
        canvas.drawRoundRect(RectF(170f, 170f, 854f, 854f), 52f, 52f, paint)
        paint.color = 0xFFEEF2F5.toInt()
        when (icon.lowercase()) {
            "network" -> drawNetwork(canvas, paint)
            "target" -> drawTarget(canvas, paint)
            "timeline" -> drawTimeline(canvas, paint)
            "book" -> drawBook(canvas, paint)
            else -> drawSpark(canvas, paint)
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun drawSpark(canvas: Canvas, paint: Paint) {
        val path = Path().apply {
            moveTo(512f, 250f)
            lineTo(585f, 445f)
            lineTo(780f, 512f)
            lineTo(585f, 579f)
            lineTo(512f, 774f)
            lineTo(439f, 579f)
            lineTo(244f, 512f)
            lineTo(439f, 445f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawNetwork(canvas: Canvas, paint: Paint) {
        paint.strokeWidth = 34f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(345f, 360f, 678f, 360f, paint)
        canvas.drawLine(345f, 360f, 512f, 672f, paint)
        canvas.drawLine(678f, 360f, 512f, 672f, paint)
        paint.style = Paint.Style.FILL
        listOf(345f to 360f, 678f to 360f, 512f to 672f).forEach { (x, y) -> canvas.drawCircle(x, y, 74f, paint) }
    }

    private fun drawTarget(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 46f
        canvas.drawCircle(512f, 512f, 230f, paint)
        canvas.drawCircle(512f, 512f, 118f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(512f, 512f, 44f, paint)
    }

    private fun drawTimeline(canvas: Canvas, paint: Paint) {
        paint.strokeWidth = 42f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(330f, 330f, 330f, 700f, paint)
        paint.style = Paint.Style.FILL
        listOf(330f to 330f, 330f to 512f, 330f to 700f).forEach { (x, y) ->
            canvas.drawCircle(x, y, 48f, paint)
            canvas.drawRoundRect(RectF(420f, y - 34f, 710f, y + 34f), 22f, 22f, paint)
        }
    }

    private fun drawBook(canvas: Canvas, paint: Paint) {
        canvas.drawRoundRect(RectF(288f, 300f, 506f, 714f), 26f, 26f, paint)
        canvas.drawRoundRect(RectF(518f, 300f, 736f, 714f), 26f, 26f, paint)
        paint.color = 0xFFC0584D.toInt()
        canvas.drawRect(496f, 314f, 528f, 698f, paint)
    }
}

private fun ZipOutputStream.text(path: String, content: String) = bytes(path, content.toByteArray(Charsets.UTF_8))

private fun ZipOutputStream.bytes(path: String, content: ByteArray) {
    putNextEntry(ZipEntry(path))
    write(content)
    closeEntry()
}

private fun String.safeFileName(): String = replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(40).ifBlank { "huoyejia_ppt" }

private fun String.xml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

private fun contentTypes(slideCount: Int): String {
    val slideOverrides = (1..slideCount).joinToString("") {
        """<Override PartName="/ppt/slides/slide$it.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>"""
    }
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Default Extension="jpeg" ContentType="image/jpeg"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
<Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
$slideOverrides
</Types>"""
}

private fun rootRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

private fun coreProps(title: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:title>${title.xml()}</dc:title><dc:creator>Huoyejia</dc:creator>
<dcterms:created xsi:type="dcterms:W3CDTF">${LocalDateTime.now()}</dcterms:created>
</cp:coreProperties>"""

private fun appProps(slideCount: Int): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
<Application>Huoyejia</Application><PresentationFormat>Screen 16:9</PresentationFormat><Slides>$slideCount</Slides>
</Properties>"""

private fun presentation(slideCount: Int): String {
    val ids = (1..slideCount).joinToString("") { """<p:sldId id="${650 + it}" r:id="rId${it + 1}"/>""" }
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
<p:sldIdLst>$ids</p:sldIdLst>
<p:sldSz cx="$SLIDE_W" cy="$SLIDE_H" type="screen16x9"/>
<p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>"""
}

private fun presentationRels(slideCount: Int): String {
    val slides = (1..slideCount).joinToString("") {
        """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$it.xml"/>"""
    }
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
$slides
</Relationships>"""
}

private fun slideRels(number: Int, hasBackground: Boolean): String {
    val bg = if (hasBackground) """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/bg$number.jpeg"/>""" else ""
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/fg$number.png"/>
$bg
</Relationships>"""
}

private fun slideXml(slide: ExportSlide, number: Int, hasBackground: Boolean): String {
    val content = when (slide.layout) {
        LayoutStyle.Cover -> coverLayout(slide)
        LayoutStyle.Cards -> cardsLayout(slide)
        LayoutStyle.Timeline -> timelineLayout(slide)
        LayoutStyle.Compare -> compareLayout(slide)
        LayoutStyle.Storyboard -> storyboardLayout(slide)
        LayoutStyle.Closing -> closingLayout(slide)
        LayoutStyle.Split -> splitLayout(slide)
    }
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
<p:cSld><p:spTree>
<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
${if (hasBackground) backgroundPicture() else solidBackground("EEF2F5")}
$content
</p:spTree></p:cSld>
<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
${transitionXml(slide.animationHint)}
</p:sld>"""
}

private fun coverLayout(slide: ExportSlide): String {
    val subtitle = slide.bullets.joinToString("\n")
    return overlay(0.58f) +
        labelShape(11, "AI LESSON", 620000, 600000, 1600000, 330000, "FFC000", "000000", 1350, true) +
        textShape(12, "title", slide.title, 620000, 1180000, 5400000, 1350000, 3900, true, "FFFFFF") +
        textShape(13, "subtitle", subtitle, 650000, 2820000, 5300000, 1050000, 1550, false, "EEF2F5") +
        pictureShape(14, 6350000, 1020000, 2100000, 2100000, "rId2")
}

private fun splitLayout(slide: ExportSlide): String {
    val body = slide.bullets.take(4).joinToString("") { bulletParagraph(it, 1580) }
    return accentBand(520000, 540000, 118000, 3920000, "C0584D") +
        textShape(21, "title", slide.title, 780000, 560000, 4400000, 780000, 2750, true, "000000") +
        textBoxShape(22, "bullets", body, 780000, 1560000, 4380000, 2780000, "FFFFFF", "E7E6E6") +
        pictureShape(23, 5750000, 1040000, 2500000, 2500000, "rId2") +
        labelShape(24, "结构化理解", 6100000, 3820000, 1600000, 360000, "C0584D", "FFFFFF", 1250, true)
}

private fun cardsLayout(slide: ExportSlide): String {
    val items = slide.bullets.ifEmpty { listOf("定义", "联系", "应用") }.take(3)
    return textShape(31, "title", slide.title, 620000, 470000, 6200000, 640000, 2600, true, "000000") +
        items.mapIndexed { index, item ->
            val x = 640000 + index * 2770000
            cardShape(32 + index, x, 1500000, 2380000, 2350000, "FFFFFF", "D8D8D8") +
                labelShape(42 + index, "0${index + 1}", x + 210000, 1720000, 650000, 360000, "C0584D", "FFFFFF", 1300, true) +
                textShape(52 + index, "card$index", item, x + 230000, 2260000, 1900000, 1120000, 1500, false, "000000")
        }.joinToString("") +
        pictureShape(62, 7350000, 410000, 1100000, 1100000, "rId2")
}

private fun timelineLayout(slide: ExportSlide): String {
    val items = slide.bullets.ifEmpty { listOf("起点", "变化", "结论") }.take(4)
    return textShape(71, "title", slide.title, 620000, 430000, 6500000, 620000, 2600, true, "000000") +
        lineShape(72, 1120000, 2660000, 6900000, 0, "C0584D", 33000) +
        items.mapIndexed { index, item ->
            val x = 980000 + index * 2050000
            circleShape(80 + index, x, 2480000, 360000, "C0584D") +
                textShape(90 + index, "t$index", item, x - 220000, 3060000, 1150000, 820000, 1220, false, "000000")
        }.joinToString("") +
        pictureShape(101, 7300000, 900000, 1150000, 1150000, "rId2")
}

private fun compareLayout(slide: ExportSlide): String {
    val left = slide.bullets.filterIndexed { index, _ -> index % 2 == 0 }.ifEmpty { slide.bullets.take(1) }
    val right = slide.bullets.filterIndexed { index, _ -> index % 2 == 1 }.ifEmpty { slide.bullets.drop(1).take(1) }
    return textShape(111, "title", slide.title, 620000, 430000, 6500000, 620000, 2500, true, "000000") +
        cardShape(112, 720000, 1400000, 3600000, 2750000, "FFFFFF", "C0584D") +
        cardShape(113, 4840000, 1400000, 3600000, 2750000, "EEF2F5", "66648B") +
        labelShape(114, "先理解", 960000, 1660000, 1260000, 340000, "C0584D", "FFFFFF", 1200, true) +
        labelShape(115, "再迁移", 5080000, 1660000, 1260000, 340000, "66648B", "FFFFFF", 1200, true) +
        textBoxShape(116, "left", left.joinToString("") { bulletParagraph(it, 1300) }, 920000, 2150000, 3000000, 1320000, "FFFFFF", "FFFFFF") +
        textBoxShape(117, "right", right.joinToString("") { bulletParagraph(it, 1300) }, 5040000, 2150000, 3000000, 1320000, "EEF2F5", "EEF2F5") +
        pictureShape(118, 4050000, 2180000, 1100000, 1100000, "rId2")
}

private fun storyboardLayout(slide: ExportSlide): String {
    val items = slide.bullets.take(2)
    return textShape(131, "title", slide.title, 620000, 420000, 6900000, 620000, 2380, true, "000000") +
        listOf("镜头推进", "重点显现", "结论收束").mapIndexed { index, label ->
            val x = 680000 + index * 2780000
            cardShape(140 + index, x, 1360000, 2360000, 2650000, if (index == 1) "EEF2F5" else "FFFFFF", "D8D8D8") +
                labelShape(150 + index, label, x + 210000, 1600000, 1250000, 330000, if (index == 1) "66648B" else "C0584D", "FFFFFF", 1120, true)
        }.joinToString("") +
        textShape(161, "visual", items.getOrNull(0).orEmpty(), 900000, 2150000, 3000000, 920000, 1180, false, "000000") +
        pictureShape(162, 4210000, 1980000, 900000, 900000, "rId2") +
        textShape(163, "narration", items.getOrNull(1).orEmpty(), 5920000, 2150000, 2400000, 980000, 1180, false, "000000")
}

private fun closingLayout(slide: ExportSlide): String {
    return solidBackground("000000") +
        pictureShape(171, 620000, 1440000, 1800000, 1800000, "rId2") +
        labelShape(172, "TAKEAWAY", 3060000, 1180000, 1600000, 360000, "FFC000", "000000", 1250, true) +
        textShape(173, "title", slide.title, 3060000, 1700000, 4600000, 580000, 2450, true, "FFFFFF") +
        textShape(174, "body", slide.bullets.joinToString("\n"), 3060000, 2460000, 4800000, 1120000, 1650, false, "EEF2F5")
}

private fun backgroundPicture(): String = """<p:pic><p:nvPicPr><p:cNvPr id="2" name="template-background"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
<p:blipFill><a:blip r:embed="rId3"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
<p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$SLIDE_W" cy="$SLIDE_H"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:ln><a:noFill/></a:ln></p:spPr></p:pic>"""

private fun solidBackground(color: String): String = """<p:sp><p:nvSpPr><p:cNvPr id="2" name="background"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$SLIDE_W" cy="$SLIDE_H"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="$color"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody></p:sp>"""

private fun overlay(alpha: Float): String = """<p:sp><p:nvSpPr><p:cNvPr id="3" name="overlay"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$SLIDE_W" cy="$SLIDE_H"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="000000"><a:alpha val="${(alpha * 100000).toInt()}"/></a:srgbClr></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody></p:sp>"""

private fun accentBand(x: Int, y: Int, cx: Int, cy: Int, color: String): String = shape(190, "accent", x, y, cx, cy, color, color, "rect")

private fun cardShape(id: Int, x: Int, y: Int, cx: Int, cy: Int, fill: String, stroke: String): String = shape(id, "card$id", x, y, cx, cy, fill, stroke, "roundRect")

private fun circleShape(id: Int, x: Int, y: Int, size: Int, fill: String): String = shape(id, "node$id", x, y, size, size, fill, fill, "ellipse")

private fun shape(id: Int, name: String, x: Int, y: Int, cx: Int, cy: Int, fill: String, stroke: String, prst: String): String = """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="$prst"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="$fill"/></a:solidFill><a:ln w="12700"><a:solidFill><a:srgbClr val="$stroke"/></a:solidFill></a:ln></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody></p:sp>"""

private fun lineShape(id: Int, x: Int, y: Int, cx: Int, cy: Int, color: String, width: Int): String = """<p:cxnSp><p:nvCxnSpPr><p:cNvPr id="$id" name="line$id"/><p:cNvCxnSpPr/><p:nvPr/></p:nvCxnSpPr>
<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="line"><a:avLst/></a:prstGeom><a:ln w="$width"><a:solidFill><a:srgbClr val="$color"/></a:solidFill></a:ln></p:spPr></p:cxnSp>"""

private fun labelShape(id: Int, text: String, x: Int, y: Int, cx: Int, cy: Int, fill: String, color: String, size: Int, bold: Boolean): String {
    val boldAttr = if (bold) """ b="1"""" else ""
    return """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="label$id"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="$fill"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr>
<p:txBody><a:bodyPr anchor="ctr"/><a:lstStyle/><a:p><a:pPr algn="ctr"/><a:r><a:rPr lang="zh-CN" sz="$size"$boldAttr><a:solidFill><a:srgbClr val="$color"/></a:solidFill><a:latin typeface="MiSans Bold"/><a:ea typeface="MiSans Bold"/></a:rPr><a:t>${text.xml()}</a:t></a:r></a:p></p:txBody></p:sp>"""
}

private fun textShape(id: Int, name: String, text: String, x: Int, y: Int, cx: Int, cy: Int, size: Int, bold: Boolean, color: String): String {
    val boldAttr = if (bold) """ b="1"""" else ""
    return """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:ln><a:noFill/></a:ln></p:spPr>
<p:txBody><a:bodyPr wrap="square"/><a:lstStyle/><a:p><a:r><a:rPr lang="zh-CN" sz="$size"$boldAttr><a:solidFill><a:srgbClr val="$color"/></a:solidFill><a:latin typeface="MiSans Bold"/><a:ea typeface="MiSans Bold"/></a:rPr><a:t>${text.xml()}</a:t></a:r></a:p></p:txBody></p:sp>"""
}

private fun textBoxShape(id: Int, name: String, paragraphs: String, x: Int, y: Int, cx: Int, cy: Int, fill: String, stroke: String): String = """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="$fill"/></a:solidFill><a:ln w="12700"><a:solidFill><a:srgbClr val="$stroke"/></a:solidFill></a:ln></p:spPr>
<p:txBody><a:bodyPr wrap="square" lIns="210000" tIns="190000" rIns="210000" bIns="190000"/><a:lstStyle/>$paragraphs</p:txBody></p:sp>"""

private fun bulletParagraph(text: String, size: Int): String = """<a:p><a:pPr marL="210000" indent="-150000"><a:buChar char="•"/></a:pPr><a:r><a:rPr lang="zh-CN" sz="$size"><a:solidFill><a:srgbClr val="000000"/></a:solidFill><a:latin typeface="MiSans Light"/><a:ea typeface="MiSans Light"/></a:rPr><a:t>${text.xml()}</a:t></a:r></a:p>"""

private fun pictureShape(id: Int, x: Int, y: Int, cx: Int, cy: Int, relId: String): String = """<p:pic><p:nvPicPr><p:cNvPr id="$id" name="image$id"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
<p:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:ln><a:noFill/></a:ln></p:spPr></p:pic>"""

private fun transitionXml(hint: String): String = when (hint.lowercase()) {
    "push" -> """<p:transition spd="med"><p:push dir="l"/></p:transition>"""
    "wipe" -> """<p:transition spd="med"><p:wipe dir="r"/></p:transition>"""
    else -> """<p:transition spd="med"><p:fade/></p:transition>"""
}

private fun slideMaster(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld>
<p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
<p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles>
</p:sldMaster>"""

private fun slideMasterRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""

private fun slideLayout(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
<p:cSld name="Template Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld>
<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""

private fun slideLayoutRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""

private fun theme(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="PPTmoban Inspired">
<a:themeElements><a:clrScheme name="PPTmoban"><a:dk1><a:srgbClr val="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="EEF2F5"/></a:dk2><a:lt2><a:srgbClr val="E7E6E6"/></a:lt2><a:accent1><a:srgbClr val="C0584D"/></a:accent1><a:accent2><a:srgbClr val="66648B"/></a:accent2><a:accent3><a:srgbClr val="D8D8D8"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4><a:accent5><a:srgbClr val="4472C4"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6><a:hlink><a:srgbClr val="000000"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme>
<a:fontScheme name="MiSans"><a:majorFont><a:latin typeface="MiSans Bold"/><a:ea typeface="MiSans Heavy"/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="MiSans Light"/><a:ea typeface="MiSans Light"/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="PPTmoban"><a:fillStyleLst/><a:lnStyleLst/><a:effectStyleLst/><a:bgFillStyleLst/></a:fmtScheme></a:themeElements>
</a:theme>"""
