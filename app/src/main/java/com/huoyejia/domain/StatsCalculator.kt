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
        val collectPressure = (kotlin.math.ln(totalCollected + 1f) / kotlin.math.ln(16f)).coerceIn(0f, 1f)
        val delayRatio = estimateProcessingDelayRatio(totalCollected, unprocessedRatio)
        val factors = listOf(
            IndexFactor("采集压力", collectPressure, 0.16f),
            IndexFactor("未读衰减", unreadRatio.coerceIn(0f, 1f), 0.18f),
            IndexFactor("回流缺口", unreviewedRatio.coerceIn(0f, 1f), 0.26f),
            IndexFactor("重复收藏", duplicateRate.coerceIn(0f, 1f), 0.16f),
            IndexFactor("处理延迟", delayRatio, 0.12f),
            IndexFactor("未处理率", unprocessedRatio.coerceIn(0f, 1f), 0.12f)
        )
        val weighted = normalizeDynamicWeights(factors)
        val score = 100f * weighted.sumOf { (it.value * it.dynamicWeight).toDouble() }.toFloat()
        val index = score.toInt().coerceIn(0, 100)
        val topReasons = weighted
            .sortedByDescending { it.value * it.dynamicWeight }
            .take(3)
            .joinToString("、") { "${it.name}${(it.value * 100).toInt()}%" }
        val reason = when {
            index >= 80 -> "高囤积预警：多因子指数=$index，主要压力来自$topReasons。建议暂停新增，先清重复并完成今日回流卡。"
            index >= 60 -> "中度囤积：多因子指数=$index，主要压力来自$topReasons。优先处理高重要度未复习内容。"
            index >= 40 -> "轻度囤积：多因子指数=$index，主要压力来自$topReasons。建议坚持收藏后24小时内回流。"
            else -> "健康状态：多因子指数=$index，收藏正在转化为可复习知识。"
        }
        return HoardingIndexResult(index, reason)
    }

    private fun estimateProcessingDelayRatio(totalCollected: Int, unprocessedRatio: Float): Float {
        val scale = (totalCollected / 8f).coerceIn(0.35f, 1f)
        return (unprocessedRatio.coerceIn(0f, 1f) * scale).coerceIn(0f, 1f)
    }

    private fun normalizeDynamicWeights(factors: List<IndexFactor>): List<WeightedIndexFactor> {
        val adjusted = factors.map { factor ->
            // Entropy-inspired dynamic weight: pressure-heavy factors receive a modest weight lift.
            val lift = 0.72f + 0.56f * factor.value.coerceIn(0f, 1f)
            factor to factor.baseWeight * lift
        }
        val total = adjusted.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        return adjusted.map { (factor, weight) ->
            WeightedIndexFactor(factor.name, factor.value, weight / total)
        }
    }

    private data class IndexFactor(
        val name: String,
        val value: Float,
        val baseWeight: Float
    )

    private data class WeightedIndexFactor(
        val name: String,
        val value: Float,
        val dynamicWeight: Float
    )
}
