package app.pitchmonitor.byz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import java.util.Date

/**
 * The saved recordings. Tap one to open it; long-press to start selecting, then send or
 * delete several at once (rename when one is selected).
 */
class RecordingsActivity : ThemedActivity() {
    private lateinit var store: RecordingStore
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.recordings_list)
        store = RecordingStore(this)
        val frame = FrameLayout(this)
        list = ListView(this)
        empty = TextView(this).apply {
            setText(R.string.no_recordings)
            textSize = 16f
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        frame.addView(list)
        frame.addView(empty)
        setContentView(frame)
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
        list.setMultiChoiceModeListener(selection)
        list.setOnItemClickListener { _, _, pos, _ -> open(files[pos]) }
        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun open(file: File) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_FILE, file.absolutePath))
        finish()
    }

    private fun refresh() {
        files = store.list()
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        list.adapter = object : ArrayAdapter<File>(this, android.R.layout.simple_list_item_activated_2, android.R.id.text1, files) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val f = files[position]
                v.findViewById<TextView>(android.R.id.text1).text = f.nameWithoutExtension
                val seconds = (f.length() - 44).coerceAtLeast(0) / 2 / SAMPLE_RATE
                v.findViewById<TextView>(android.R.id.text2).text =
                    DateFormat.getMediumDateFormat(context).format(Date(f.lastModified())) + " " +
                        DateFormat.getTimeFormat(context).format(Date(f.lastModified())) +
                        String.format("  ·  %d:%02d", seconds / 60, seconds % 60)
                return v
            }
        }
    }

    private fun selected(): List<File> {
        val checked = list.checkedItemPositions ?: return emptyList()
        return files.indices.filter { checked.get(it) }.map { files[it] }
    }

    private val selection = object : AbsListView.MultiChoiceModeListener {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(0, SEND, 0, R.string.send).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(0, DELETE, 1, R.string.delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(0, RENAME, 2, R.string.rename).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.add(0, ALL, 3, R.string.select_all).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(RENAME)?.isVisible = list.checkedItemCount == 1
            return true
        }

        override fun onItemCheckedStateChanged(mode: ActionMode, position: Int, id: Long, checked: Boolean) {
            mode.title = getString(R.string.n_selected, list.checkedItemCount)
            mode.invalidate()
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val chosen = selected()
            when (item.itemId) {
                SEND -> { share(chosen); mode.finish() }
                DELETE -> confirmDelete(chosen) { mode.finish() }
                RENAME -> { chosen.firstOrNull()?.let { rename(it) }; mode.finish() }
                ALL -> for (i in files.indices) list.setItemChecked(i, true)
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    private fun share(chosen: List<File>) {
        if (chosen.isEmpty()) return
        if (chosen.size == 1) { MainActivity.shareFile(this, chosen[0]); return }
        val uris = ArrayList<Uri>(chosen.map { FileProvider.getUriForFile(this, "$packageName.files", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("audio/wav")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(R.string.send)))
    }

    private fun confirmDelete(chosen: List<File>, done: () -> Unit) {
        if (chosen.isEmpty()) return
        val msg = if (chosen.size == 1) getString(R.string.delete_one, chosen[0].nameWithoutExtension)
        else getString(R.string.delete_many, chosen.size)
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                chosen.forEach { it.delete() }
                done()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun rename(file: File) {
        val input = EditText(this).apply { setText(file.nameWithoutExtension); selectAll() }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (store.rename(file, input.text.toString()) == null) {
                    Toast.makeText(this, R.string.name_taken, Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_FILE = "file"
        private const val SEND = 1
        private const val DELETE = 2
        private const val RENAME = 3
        private const val ALL = 4
    }
}
