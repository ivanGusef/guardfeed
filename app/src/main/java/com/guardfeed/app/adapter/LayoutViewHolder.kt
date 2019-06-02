package com.guardfeed.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class LayoutViewHolder(
        layout: Int,
        parent: ViewGroup
) : RecyclerView.ViewHolder(LayoutInflater.from((parent.context)).inflate(layout, parent, false))