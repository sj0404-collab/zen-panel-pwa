package com.zenpanel.app

import android.os.Bundle
import android.app.AlertDialog
import android.widget.Button
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenpanel.app.databinding.ActivityServerListBinding
import com.zenpanel.app.databinding.DialogAddServerBinding
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

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

        val pickButton = Button(this).apply {
            text = getString(R.string.apps_pick_button)
            isAllCaps = false
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            setOnClickListener { showAppPicker(dialogBinding.inputConfig) }
        }
        dialogBinding.root.addView(pickButton)
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


    private fun showAppPicker(configField: android.widget.EditText) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || true }
            .map { ApplicationInfoWrapper(it, pm) }
            .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) { toast(getString(R.string.apps_pick_none)); return }

        val picked = BooleanArray(apps.size)
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.apps_pick_title)
            .setMultiChoiceItems(labels, picked) { _, which, isChecked -> picked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selected = picked.indices.filter { picked[it] }.map { apps[it].packageName }
                if (selected.isEmpty()) { toast(getString(R.string.apps_pick_none)); return@setPositiveButton }
                val base = configField.text?.toString()?.trim().orEmpty()
                val cleaned = base.lines().filterNot { it.trim().startsWith("IncludedApplication", true) }.joinToString("\n")
                val lines = selected.map { "IncludedApplication = $it" }
                configField.setText((cleaned + "\n" + lines.joinToString("\n")).trim())
                toast(getString(R.string.apps_pick_done, selected.size))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private data class ApplicationInfoWrapper(
        val info: ApplicationInfo,
        private val pm: PackageManager
    ) {
        val packageName: String get() = info.packageName
        val label: String get() = pm.getApplicationLabel(info).toString()
    }
}
