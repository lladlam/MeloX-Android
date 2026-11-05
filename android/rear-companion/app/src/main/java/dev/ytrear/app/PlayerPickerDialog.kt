package dev.ytrear.app

import android.app.Activity
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** Searchable launcher-app picker shown whenever the main activity is opened. */
object PlayerPickerDialog {

    fun show(
        activity: Activity,
        onSelected: (PlayerSelection.AppEntry) -> Unit,
        onCancelled: () -> Unit
    ) {
        val apps = PlayerSelection.launchableApps(activity)
        val current = PlayerSelection.selectedPackage(activity)
        val padding = activity.dp(20)

        val description = TextView(activity).apply {
            text = activity.getString(R.string.player_picker_detail)
            setPadding(0, 0, 0, activity.dp(12))
        }
        val search = EditText(activity).apply {
            hint = activity.getString(R.string.search_apps)
            isSingleLine = true
        }
        val list = ListView(activity).apply {
            dividerHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(420)
            )
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(description)
            addView(search)
            addView(list)
        }

        val adapter = AppAdapter(activity, apps, current)
        list.adapter = adapter

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.choose_player_title)
            .setView(content)
            .setNegativeButton(
                if (current == null) R.string.not_now else R.string.keep_current_player
            ) { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .create()

        list.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            dialog.dismiss()
            onSelected(selected)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.show()
    }

    private class AppAdapter(
        private val activity: Activity,
        private val all: List<PlayerSelection.AppEntry>,
        private val currentPackage: String?
    ) : BaseAdapter() {

        private val visible = all.toMutableList()

        fun filter(query: String) {
            val needle = query.trim()
            visible.clear()
            visible += if (needle.isEmpty()) {
                all
            } else {
                all.filter {
                    it.label.contains(needle, ignoreCase = true) ||
                        it.packageName.contains(needle, ignoreCase = true)
                }
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = visible.size
        override fun getItem(position: Int): PlayerSelection.AppEntry = visible[position]
        override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(activity)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            val app = getItem(position)
            view.findViewById<TextView>(android.R.id.text1).apply {
                text = if (app.packageName == currentPackage) "✓ ${app.label}" else app.label
                setTypeface(null, Typeface.BOLD)
            }
            view.findViewById<TextView>(android.R.id.text2).text = app.packageName
            return view
        }
    }

    private fun Activity.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
