package com.zenpanel.app

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenpanel.app.databinding.ActivityServerListBinding
import com.zenpanel.app.databinding.DialogAddServerBinding

class ServerListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerListBinding
    private lateinit var app: App
    private lateinit var adapter: ServerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as App

        binding.recyclerServers.layoutManager = LinearLayoutManager(this)
        render()

        binding.buttonAddServer.setOnClickListener { showAddDialog() }
    }

    private fun render() {
        val selectedId = app.servers.selectedId
        adapter = ServerAdapter(
            servers = app.servers.all(),
            selectedId = selectedId,
            onSelect = { server ->
                app.servers.selectedId = server.id
                render()
                toast(getString(R.string.server_selected, server.displayName()))
            },
            onDelete = { server ->
                app.servers.remove(server.id)
                render()
            }
        )
        binding.recyclerServers.adapter = adapter
        binding.textEmpty.visibility =
            if (app.servers.all().isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddServerBinding.inflate(LayoutInflater.from(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.server_add_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.server_add_save) { _, _ ->
                val config = dialogBinding.inputConfig.text?.toString()?.trim().orEmpty()
                if (config.isBlank()) {
                    toast(getString(R.string.server_config_required))
                    return@setPositiveButton
                }
                val countryCode = dialogBinding.inputCountryCode.text?.toString()?.trim().orEmpty()
                val server = VpnServer(
                    name = dialogBinding.inputName.text?.toString()?.trim().orEmpty()
                        .ifBlank { getString(R.string.server_default_name) },
                    country = dialogBinding.inputCountry.text?.toString()?.trim().orEmpty(),
                    countryCode = countryCode,
                    config = config
                )
                app.servers.add(server)
                render()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
