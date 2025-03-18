package com.trimble.ttm.formlibrary.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.formlibrary.BR
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.CustomListItemMessageResponseListBinding
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.utils.NORMAL_ALPHA
import com.trimble.ttm.formlibrary.utils.REDUCED_ALPHA
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.isGreaterThan

class MessageResponseListAdapter(
    private val onMessageClicked: (MessageFormResponse) -> Unit,
    private val onMultipleMessageSelectedForDeletion: (Int) -> Unit
) : RecyclerView.Adapter<MessageResponseListAdapter.MessageResponseSetViewHolder>() {

    var messageSet = mutableSetOf<MessageFormResponse>()

    var multiSelect = false
    //keep track of all selected items in the recyclerview
    var selectedItems = mutableSetOf<MessageFormResponse>()

    private lateinit var itemRowBinding: CustomListItemMessageResponseListBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageResponseSetViewHolder {
        val itemRowBinding =
            DataBindingUtil.inflate<CustomListItemMessageResponseListBinding>(
                LayoutInflater.from(parent.context),
                R.layout.custom_list_item_message_response_list,
                parent,
                false
            )
        return MessageResponseSetViewHolder(itemRowBinding)
    }

    override fun getItemCount(): Int = messageSet.size

    override fun onBindViewHolder(holder: MessageResponseSetViewHolder, position: Int) {
        val currentItem = messageSet.elementAt(position)
        holder.bind(currentItem)

        if (selectedItems.contains(currentItem)){
            itemRowBinding.cboxMessage.isChecked = true
            itemRowBinding.constraintLayoutItemHolderMessageList.alpha = REDUCED_ALPHA
        } else {
            itemRowBinding.cboxMessage.isChecked = false
            itemRowBinding.constraintLayoutItemHolderMessageList.alpha = NORMAL_ALPHA
        }
        itemRowBinding.constraintLayoutItemHolderMessageList.setOnLongClickListener {
            enableMultiSelect()
            if (multiSelect.not()){
                multiSelect = true
                selectOrDeSelectItem(currentItem)
            }
            true
        }
        itemRowBinding.constraintLayoutItemHolderMessageList.setOnClickListener {
            enableMultiSelect()
            if (multiSelect) selectOrDeSelectItem(currentItem) else onMessageClicked(currentItem)
        }
        itemRowBinding.cboxMessage.setOnClickListener {
            if (multiSelect.not()){
                multiSelect = true
            }
            selectOrDeSelectItem(currentItem)
        }
    }

    fun selectAllItems() {
        multiSelect = true
        selectedItems.clear()
        if (messageSet.size.isGreaterThan(ZERO)) {
            //The list is shallow copied
            selectedItems = messageSet.toMutableSet()
        }
        onMultipleMessageSelectedForDeletion(selectedItems.size)
    }

    fun clearAllSelectedItems() {
        multiSelect = false
        selectedItems.clear()
        onMultipleMessageSelectedForDeletion(selectedItems.size)
    }

    private fun selectOrDeSelectItem(messageFormResponse: MessageFormResponse) {
        if (selectedItems.contains(messageFormResponse)) {
            selectedItems.remove(messageFormResponse)
            itemRowBinding.cboxMessage.isChecked = false
            itemRowBinding.constraintLayoutItemHolderMessageList.alpha = NORMAL_ALPHA


        } else {
            //The message is shallow copied
            selectedItems.add(messageFormResponse.copy())
            itemRowBinding.cboxMessage.isChecked = true
            itemRowBinding.constraintLayoutItemHolderMessageList.alpha = REDUCED_ALPHA
        }
        notifyDataSetChanged()
        onMultipleMessageSelectedForDeletion(selectedItems.size)
    }

    private fun enableMultiSelect() {
        if (selectedItems.size == ZERO) multiSelect = false
    }

    companion object {
        @JvmStatic
        @BindingAdapter("setCustomTextStyle")
        fun TextView.setTextCustomStyle(isRead: Boolean){
            if (isRead) {
                this.typeface = Typeface.DEFAULT
                this.setTextColor(context.getColor(R.color.silver))
            } else {
                this.typeface = Typeface.DEFAULT_BOLD
                this.setTextColor(context.getColor(R.color.white))
            }
        }
    }

    inner class MessageResponseSetViewHolder(private val itemRowBinding: CustomListItemMessageResponseListBinding) :
        RecyclerView.ViewHolder(itemRowBinding.root) {
        fun bind(messageFormResponse: MessageFormResponse) {
            itemRowBinding.setVariable(BR.messageFormReponse, messageFormResponse)
            itemRowBinding.executePendingBindings()
            this@MessageResponseListAdapter.itemRowBinding = itemRowBinding
        }
    }
}