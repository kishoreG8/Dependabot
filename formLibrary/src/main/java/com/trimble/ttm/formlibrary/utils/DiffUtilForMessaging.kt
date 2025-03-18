package com.trimble.ttm.formlibrary.utils

import androidx.annotation.Nullable
import androidx.recyclerview.widget.DiffUtil
import com.trimble.ttm.commons.logger.INBOX_LIST
import com.trimble.ttm.commons.logger.INBOX_LIST_DIFF_UTIL
import com.trimble.ttm.commons.logger.Log

open class DiffUtilForMessaging(
    private val oldList: Set<*>,
    private val newList: Set<*>
): DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        Log.d(INBOX_LIST_DIFF_UTIL,"oldListSize${oldList.size}")
        return oldList.size
    }

    override fun getNewListSize(): Int {
        Log.d(INBOX_LIST_DIFF_UTIL,"newListSize${newList.size}")
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList.elementAt(oldItemPosition) == newList.elementAt(newItemPosition)
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList.elementAt(oldItemPosition) == newList.elementAt(newItemPosition)
    }

    @Nullable
    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        return super.getChangePayload(oldItemPosition, newItemPosition)
    }
}