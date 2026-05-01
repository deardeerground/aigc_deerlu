package com.huoyejia.domain

import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.UserStatsEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StatsCalculator {
    fun calculate(notes: List<NoteEntity>): UserStatsEntity {
        val total = notes.size
        val read = notes.count { it.readStatus }
        val reviewed = notes.count { it.reviewedCount > 0 }
        val duplicateRate = if (total == 0) 0f else notes.count { it.duplicateScore >= 0.72f }.toFloat() / total
        val unprocessedRatio = if (total == 0) 0f else notes.count { it.processedStatus != "PROCESSED" }.toFloat() / total
        val result = calcHoardingIndex(total, read, reviewed, duplicateRate, unprocessedRatio)
        return UserStatsEntity(
            statDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()),
            totalCollected = total,
            totalRead = read,
            totalReviewed = reviewed,
            duplicateRate = duplicateRate,
            unprocessedRatio = unprocessedRatio,
            hoardingIndex = result.index,
            indexReason = result.reason
        )
    }

    private fun calcHoardingIndex(
        totalCollected: Int,
        totalRead: Int,
        totalReviewed: Int,
        duplicateRate: Float,
        unprocessedRatio: Float
    ): HoardingIndexResult {
        if (totalCollected <= 0) return HoardingIndexResult(0, "暂无囤积，保持输入与理解平衡。")
        val unreadRatio = (totalCollected - totalRead).coerceAtLeast(0).toFloat() / totalCollected
        val unreviewedRatio = (totalCollected - totalReviewed).coerceAtLeast(0).toFloat() / totalCollected
        // MVP demo should surface intervention early instead of waiting for hundreds of notes.
        val collectPressure = (totalCollected / 5f).coerceAtMost(1f)
        val score = 100f * (
            0.20f * collectPressure +
                0.25f * unreadRatio +
                0.25f * unreviewedRatio +
                0.15f * duplicateRate.coerceIn(0f, 1f) +
                0.15f * unprocessedRatio.coerceIn(0f, 1f)
            )
        val index = score.toInt().coerceIn(0, 100)
        val reason = when {
            index >= 80 -> "高囤积预警：收藏远超消化，建议先处理重复内容并完成今日回流卡。"
            index >= 60 -> "中度囤积：输入活跃但复习不足，优先处理高重要度未复习内容。"
            index >= 40 -> "轻度囤积：整体可控，建议坚持收藏后24小时内回流。"
            else -> "健康状态：收藏正在转化为可复习知识。"
        }
        return HoardingIndexResult(index, reason)
    }
}
