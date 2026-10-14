package com.zenpanel.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zenpanel.app.databinding.ItemServerBinding

class ServerAdapter(
    private var servers: List<VpnServer>,
    private var selectedId: String?,
    private val onSelect: (VpnServer) -> Unit,
    private val onDelete: (VpnServer) -> Unit
) : RecyclerView.Adapter<ServerAdapter.Holder>() {

    class Holder(val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = servers.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val server = servers[position]
        val ctx = holder.itemView.context
        holder.binding.textTitle.text = server.displayName()
        holder.binding.textSubtitle.text = server.name
        val isSelected = server.id == selectedId
        holder.binding.root.strokeColor = ContextCompat.getColor(
            ctx,
            if (isSelected) R.color.primary else R.color.border
        )
        holder.binding.root.strokeWidth = if (isSelected) 3 else 1
        holder.binding.root.setOnClickListener { onSelect(server) }
        holder.binding.buttonDelete.setOnClickListener { onDelete(server) }
    }
}
