package com.v2ray.ang.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityServerListBinding
import com.v2ray.ang.databinding.ItemServerResultBinding
import com.v2ray.ang.databinding.ItemServerSectionHeaderBinding
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.handler.AutoConnectManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.CountryUtils
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils

class ServerListActivity : BaseActivity() {
    private lateinit var swipeDetector: android.view.GestureDetector
    private lateinit var layoutManager: LinearLayoutManager

    private val binding by lazy { ActivityServerListBinding.inflate(layoutInflater) }
    private val adapter = ResultAdapter(
        onClick = { guid ->
            MmkvManager.setSelectServer(guid)
            if (com.v2ray.ang.core.CoreServiceManager.isRunning()) {
                com.v2ray.ang.core.CoreServiceManager.stopVService(this)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    com.v2ray.ang.core.CoreServiceManager.startVService(this)
                }, 500)
            } else {
                com.v2ray.ang.core.CoreServiceManager.startVService(this)
            }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_GUID, guid))
            finish()
        },
        onPingClick = { subId -> testSubscriptionPing(subId) }
    )

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS, AppConfig.MSG_MEASURE_CONFIG_FINISH -> reload()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.server_list_title))

        layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.clipToPadding = false
        binding.recyclerView.setPadding(binding.recyclerView.paddingLeft, (56 * resources.displayMetrics.density).toInt(), binding.recyclerView.paddingRight, binding.recyclerView.paddingBottom)
        com.v2ray.ang.util.BottomNavHelper.setup(this, binding.bottomNav.root, R.id.nav_servers)
        swipeDetector = com.v2ray.ang.util.BottomNavHelper.createSwipeDetector(this, R.id.nav_servers)
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.root.selectedItemId = R.id.nav_servers
        ContextCompat.registerReceiver(
            this,
            mReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
        reload()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(mReceiver) }
    }

    private fun buildRows(subId: String, selected: String?): List<ResultRow> {
        val guids = MmkvManager.decodeServerList(subId)
        return guids.mapNotNull { guid ->
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val (flag, name) = CountryUtils.countryFromRemarks(profile.remarks)
            val displayName = name ?: profile.remarks.ifBlank { null }
            ResultRow(
                guid = guid,
                flag = flag ?: CountryUtils.UNKNOWN_FLAG,
                countryName = displayName,
                delayMillis = delay,
                isSelected = guid == selected
            )
        }.sortedBy { if (it.delayMillis <= 0L) Long.MAX_VALUE else it.delayMillis }
    }

    private fun testSubscriptionPing(subId: String) {
        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isEmpty()) return
        MessageUtil.sendMsg2TestService(
            this,
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        MmkvManager.clearAllTestDelayResults(guids)
        adapter.notifyDataSetChanged()
        MessageUtil.sendMsg2TestService(
            this,
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, subscriptionId = subId)
        )
    }

    private fun setupSectionTabs(listItems: List<ListItem>) {
        val headers = listItems.withIndex()
            .filter { it.value is ListItem.Header }
            .map { it.index to (it.value as ListItem.Header) }

        binding.sectionTabsContainer.removeAllViews()

        if (headers.size < 2) {
            binding.sectionTabsScroll.isVisible = false
            binding.recyclerView.setPadding(binding.recyclerView.paddingLeft, (56 * resources.displayMetrics.density).toInt(), binding.recyclerView.paddingRight, binding.recyclerView.paddingBottom)
            return
        }

        binding.sectionTabsScroll.isVisible = true
            binding.recyclerView.setPadding(binding.recyclerView.paddingLeft, (100 * resources.displayMetrics.density).toInt(), binding.recyclerView.paddingRight, binding.recyclerView.paddingBottom)
        val density = resources.displayMetrics.density
        val chipMarginEnd = (8 * density).toInt()
        val paddingH = (14 * density).toInt()
        val paddingV = (8 * density).toInt()

        headers.forEach { (position, header) ->
            val chip = TextView(this).apply {
                text = header.title
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.home_text_primary))
                setPadding(paddingH, paddingV, paddingH, paddingV)
                setBackgroundResource(R.drawable.bg_server_item_glass)
                setOnClickListener {
                    layoutManager.scrollToPositionWithOffset(position, 0)
                }
            }
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = chipMarginEnd }
            binding.sectionTabsContainer.addView(chip, params)
        }
    }

    private fun reload() {
        if (!AutoConnectManager.isPanelConfigured()) {
            binding.emptyState.text = getString(R.string.server_list_empty)
            binding.emptyState.isVisible = true
            binding.recyclerView.isVisible = false
            binding.sectionTabsScroll.isVisible = false
            binding.recyclerView.setPadding(binding.recyclerView.paddingLeft, (56 * resources.displayMetrics.density).toInt(), binding.recyclerView.paddingRight, binding.recyclerView.paddingBottom)
            return
        }

        val selected = MmkvManager.getSelectServer()
        val listItems = mutableListOf<ListItem>()

        val panelSubId = AutoConnectManager.ensureSubscription(fetchFresh = false)
        val panelRows = buildRows(panelSubId, selected)
        if (panelRows.isNotEmpty()) {
            listItems.add(ListItem.Header(getString(R.string.server_list_default_section), panelSubId))
            listItems.addAll(panelRows.map { ListItem.Server(it) })
        }

        val userSubs = MmkvManager.decodeSubscriptions().filter {
            it.guid != panelSubId && !it.subscription.isHiddenSystem &&
                it.subscription.enabled && it.subscription.url.isNotBlank()
        }

        userSubs.forEach { sub ->
            val rows = buildRows(sub.guid, selected)
            if (rows.isNotEmpty()) {
                val title = sub.subscription.remarks.ifBlank { sub.guid }
                listItems.add(ListItem.Header(title, sub.guid))
                listItems.addAll(rows.map { ListItem.Server(it) })
            }
        }

        adapter.submitList(listItems)
        setupSectionTabs(listItems)
        binding.emptyState.isVisible = listItems.isEmpty()
        binding.recyclerView.isVisible = listItems.isNotEmpty()
    }

    companion object {
        const val EXTRA_SELECTED_GUID = "selectedGuid"
    }
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}

private sealed class ListItem {
    data class Header(val title: String, val subId: String) : ListItem()
    data class Server(val row: ResultRow) : ListItem()
}

private data class ResultRow(
    val guid: String,
    val flag: String,
    val countryName: String?,
    val delayMillis: Long,
    val isSelected: Boolean
)

private class ResultAdapter(
    private val onClick: (String) -> Unit,
    private val onPingClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ListItem>()

    fun submitList(newItems: List<ListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.Header -> VIEW_TYPE_HEADER
        is ListItem.Server -> VIEW_TYPE_SERVER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val headerBinding = ItemServerSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderViewHolder(headerBinding)
        } else {
            val itemBinding = ItemServerResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ServerViewHolder(itemBinding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item, onPingClick)
            is ListItem.Server -> (holder as ServerViewHolder).bind(item.row, onClick)
        }
    }

    override fun getItemCount() = items.size

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SERVER = 1
    }

    class HeaderViewHolder(private val binding: ItemServerSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ListItem.Header, onPingClick: (String) -> Unit) {
            binding.tvSectionTitle.text = item.title
            binding.ivSectionPing.setOnClickListener { onPingClick(item.subId) }
        }
    }

    class ServerViewHolder(private val itemBinding: ItemServerResultBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(row: ResultRow, onClick: (String) -> Unit) {
            val context = itemBinding.root.context
            itemBinding.tvFlag.text = row.flag
            itemBinding.tvCountryName.text = row.countryName ?: context.getString(R.string.home_unknown_location)
            itemBinding.tvPing.text = if (row.delayMillis > 0L) context.getString(R.string.home_ping_ms, row.delayMillis) else "---"
            itemBinding.tvPing.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (row.delayMillis in 1 until 300) R.color.colorPing else R.color.home_warning
                )
            )
            itemBinding.ivSelected.isVisible = row.isSelected
            itemBinding.root.setOnClickListener { onClick(row.guid) }
        }
    }
}
