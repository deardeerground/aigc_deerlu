package com.huoyejia.domain

/**
 * 收藏夹/回流箱数据模型
 * 用于新的收藏夹管理界面
 */

data class CollectionFolder(
    val folderId: String,
    val title: String,
    val cardCount: Int
)

data class CollectionCard(
    val cardId: String,
    val title: String,
    val reviewCount: Int
)
