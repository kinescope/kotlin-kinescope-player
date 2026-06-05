package io.kinescope.demo.customplayer

import android.view.View
import android.widget.AdapterView

internal fun simpleItemSelectedListener(onChanged: () -> Unit): AdapterView.OnItemSelectedListener =
    object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            onChanged()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
