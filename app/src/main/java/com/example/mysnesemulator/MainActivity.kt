package com.example.mysnesemulator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mysnesemulator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val romList = mutableListOf<DocumentFile>()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scanFolder(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerRoms.layoutManager = LinearLayoutManager(this)
        binding.recyclerRoms.adapter = RomAdapter(romList) { uri, name ->
            launchEmulator(uri, name)
        }

        binding.btnSelectFolder.setOnClickListener {
            folderPicker.launch(null)
        }
    }

    private fun scanFolder(treeUri: Uri) {
        val pickedDir = DocumentFile.fromTreeUri(this, treeUri)
        romList.clear()
        
        pickedDir?.listFiles()?.forEach { file ->
            val name = file.name?.lowercase() ?: ""
            if (name.endsWith(".smc") || name.endsWith(".sfc") || name.endsWith(".zip")) {
                romList.add(file)
            }
        }
        binding.recyclerRoms.adapter?.notifyDataSetChanged()
    }

    private fun launchEmulator(uri: Uri, name: String) {
        // Inicia a Activity do Jogo
        val intent = Intent(this, EmulatorActivity::class.java)
        intent.data = uri
        intent.putExtra("ROM_NAME", name)
        intent.putExtra("CRT_MODE", binding.switchCrt.isChecked)
        startActivity(intent)
    }

    class RomAdapter(
        private val list: List<DocumentFile>,
        private val onClick: (Uri, String) -> Unit
    ) : RecyclerView.Adapter<RomAdapter.Holder>() {

        class Holder(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rom, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = list[position]
            val txt = holder.view.findViewById<TextView>(R.id.txtRomName)
            txt.text = file.name?.replace(".smc", "")?.replace(".sfc", "")
            holder.view.setOnClickListener { 
                onClick(file.uri, file.name ?: "game")
            }
        }

        override fun getItemCount() = list.size
    }
}
