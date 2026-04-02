package com.getsolace.ai.chat

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class SolaceApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                // 注册视频帧解码器，让 coil 能从视频 URI 提取缩略图帧
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
