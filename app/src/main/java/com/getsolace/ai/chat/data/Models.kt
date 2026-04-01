package com.getsolace.ai.chat.data

import java.util.UUID

// ─── AI Generated Image ───────────────────────────────────────────────────────

data class AIGeneratedImage(
    val id: String          = UUID.randomUUID().toString(),
    val prompt: String      = "",
    val styleTitle: String  = "",
    val imageUrl: String    = "",   // remote URL (from API)
    val localPath: String   = "",   // saved to local storage
    val aspectRatio: String = "1:1",
    val createdAt: Long     = System.currentTimeMillis()
)

// ─── AI Image Style ───────────────────────────────────────────────────────────

data class AIImageStyle(
    val id: String,
    val title: String,
    val description: String,
    val previewResId: Int = 0,       // drawable resource id (placeholder)
    val promptSuffix: String = ""    // appended to user prompt
)

val AIImageStyles = listOf(
    AIImageStyle("davinci",     "DaVinci",     "古典油画风格",      promptSuffix = ", da vinci painting style, oil on canvas"),
    AIImageStyle("3d",          "3D渲染",      "三维立体效果",      promptSuffix = ", 3D render, octane render, hyperrealistic"),
    AIImageStyle("turbo",       "Turbo",       "快速高质量生成",     promptSuffix = ", turbo style, high quality, detailed"),
    AIImageStyle("imagineart",  "ImagineArt",  "艺术想象创作",      promptSuffix = ", imagine art style, artistic, creative"),
    AIImageStyle("seedream",    "SeeDream",    "梦幻意境风格",      promptSuffix = ", dreamy aesthetic, surreal, ethereal atmosphere")
)

// ─── Aspect Ratio ─────────────────────────────────────────────────────────────

data class AspectRatioOption(
    val id: String,
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    fun apiWidth(): Int = when (id) {
        "1:1"   -> 512
        "4:3"   -> 768
        "3:2"   -> 768
        "16:9"  -> 896
        "8:6"   -> 768
        else    -> 512
    }
    fun apiHeight(): Int = when (id) {
        "1:1"   -> 512
        "4:3"   -> 576
        "3:2"   -> 512
        "16:9"  -> 504
        "8:6"   -> 576
        else    -> 512
    }
}

val AspectRatioOptions = listOf(
    AspectRatioOption("1:1",  "1:1",  1, 1),
    AspectRatioOption("4:3",  "4:3",  4, 3),
    AspectRatioOption("3:2",  "3:2",  3, 2),
    AspectRatioOption("16:9", "16:9", 16, 9),
    AspectRatioOption("8:6",  "8:6",  8, 6)
)

// ─── Encrypted Vault Image ────────────────────────────────────────────────────

data class EncryptedImage(
    val id: String              = UUID.randomUUID().toString(),
    val fileName: String        = "",
    val thumbnailBase64: String = "",   // base64 of thumbnail jpeg
    val createdAt: Long         = System.currentTimeMillis()
)

// ─── Create AI Step ───────────────────────────────────────────────────────────

enum class CreateAIStep {
    CONFIG, PROCESSING, RESULT
}
