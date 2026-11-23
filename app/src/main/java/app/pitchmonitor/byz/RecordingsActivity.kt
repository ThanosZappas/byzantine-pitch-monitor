package app.pitchmonitor.byz

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Date

/** The saved recordings: tap to open one, long-press to rename, delete or send it. */
class RecordingsActivity : AppCompatActivity() {
    private lateinit var store: RecordingStore
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        store = RecordingStore(this)
        val frame = android.widget.FrameLayout(this)
        list = ListView(this)
        empty = TextView(this).apply {
            text = "No recordings yet"
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        frame.addView(list)
        frame.addView(empty)
        setContentView(frame)
        list.setOnItemClickListener { _, _, pos, _ ->
            setResult(RESULT_OK, Intent().putExtra(EXTRA_FILE, files[pos].absolutePath))
            finish()
        }
        list.setOnItemLongClickListener { _, _, pos, _ -> options(files[pos]); true }
        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun refresh() {
        files = store.list()
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        list.adapter = object : ArrayAdapter<File>(this, android.R.layout.simple_list_item_2, android.R.id.text1, files) {
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

    private fun options(file: File) {
        AlertDialog.Builder(this)
            .setTitle(file.nameWithoutExtension)
            .setItems(arrayOf("Load", "Send", "Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> { setResult(RESULT_OK, Intent().putExtra(EXTRA_FILE, file.absolutePath)); finish() }
                    1 -> MainActivity.shareFile(this, file)
                    2 -> rename(file)
                    3 -> AlertDialog.Builder(this)
                        .setMessage("Delete ${file.nameWithoutExtension}?")
                        .setPositiveButton("Delete") { _, _ -> file.delete(); refresh() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun rename(file: File) {
        val input = EditText(this).apply { setText(file.nameWithoutExtension); selectAll() }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (store.rename(file, input.text.toString()) == null) {
                    Toast.makeText(this, "That name is taken", Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_FILE = "file"
    }
}
