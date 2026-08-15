package com.v2ray.ang.handler

import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.LogUtil

object AutoConnectManager {

    fun ensureSubscription(fetchFresh: Boolean = true): String {
        val subUrl = "https://raw.githubusercontent.com/hamedp-71/N_sub_cheker/refs/heads/patch-1/final.txt"
        LogUtil.i(AppConfig.TAG, "AutoConnectManager: Using subscription URL: $subUrl")

        // Find or create subscription
        val subscriptions = MmkvManager.decodeSubscriptions()
        val existing = subscriptions.find { it.subscription.url == subUrl }

        val guid = if (existing != null) {
            var needsUpdate = false
            if (!existing.subscription.enabled) {
                existing.subscription.enabled = true
                needsUpdate = true
            }
            if (!existing.subscription.isHiddenSystem) {
                existing.subscription.isHiddenSystem = true
                needsUpdate = true
            }
            if (needsUpdate) {
                MmkvManager.encodeSubscription(existing.guid, existing.subscription)
            }
            existing.guid
        } else {
            val subItem = SubscriptionItem().apply {
                remarks = "V2RayNG Panel"
                url = subUrl
                enabled = true
                autoUpdate = true
                updateInterval = 60
                isHiddenSystem = true
            }
            MmkvManager.encodeSubscription("", subItem)
            val updatedSubs = MmkvManager.decodeSubscriptions()
            updatedSubs.lastOrNull()?.guid ?: return ""
        }

        if (fetchFresh) {
        // Always fetch fresh configs from panel
        try {
            val subItem = MmkvManager.decodeSubscription(guid) ?: return guid
            val result = AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, subItem))
            LogUtil.i(AppConfig.TAG, "AutoConnectManager: Fetch result - configCount=${result.configCount}, success=${result.successCount}, failure=${result.failureCount}, skip=${result.skipCount}")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AutoConnectManager: Failed to fetch from panel", e)
        }

        }
        return guid
    }

    fun refreshBatch(subId: String): List<String> {
        if (subId.isBlank()) return emptyList()

        val subItem = MmkvManager.decodeSubscription(subId) ?: return emptyList()
        if (!subItem.enabled) return emptyList()

        val servers = MmkvManager.decodeServerList(subId)
        LogUtil.i(AppConfig.TAG, "AutoConnectManager: refreshBatch for $subId found ${servers.size} servers")
        return servers
    }

    fun isPanelConfigured(): Boolean {
        // Always configured because we have default panel values
        return true
    }
}
