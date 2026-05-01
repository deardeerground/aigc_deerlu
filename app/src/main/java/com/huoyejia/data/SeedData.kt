package com.huoyejia.data

import com.huoyejia.domain.NoteProcessor

class SeedData(
    private val noteRepository: NoteRepository,
    private val processor: NoteProcessor
) {
    suspend fun ensureSeeded() {
        if (noteRepository.countNotes() > 0) return
        processor.captureAndProcess(
            rawText = "凡尔赛体系削弱德国但没有解决欧洲安全结构问题，经济危机又强化了极端政治，这是二战爆发的重要原因。",
            imagePath = null,
            sourceType = "bilibili",
            sourceTitle = "二战爆发深层原因",
            url = "https://www.bilibili.com/video/BV-demo"
        )
        processor.captureAndProcess(
            rawText = "",
            imagePath = "/mock/screenshot/europe-map.png",
            sourceType = "image",
            sourceTitle = "课堂地图截图：欧洲势力范围",
            url = null
        )
        processor.captureAndProcess(
            rawText = "费曼学习法的关键是用自己的话输出，暴露理解漏洞，再回到材料中修正。",
            imagePath = null,
            sourceType = "wechat",
            sourceTitle = "学习方法：费曼输出法",
            url = "https://mp.weixin.qq.com/mock"
        )
    }
}
