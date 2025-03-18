package com.trimble.ttm.formlibrary.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.library.baseAdapters.BR
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRASH_LIST_UI_ON_BIND
import com.trimble.ttm.commons.utils.newConcurrentHashSet
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.CustomListItemMessageListBinding
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.utils.NORMAL_ALPHA
import com.trimble.ttm.formlibrary.utils.REDUCED_ALPHA
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.isGreaterThan
import com.trimble.ttm.formlibrary.viewmodel.TrashViewModel

class TrashListAdapter(
    private val trashViewModel: TrashViewModel,
    private val onMessageClicked: (Message) -> Unit,
    private val onMultipleMessageSelectedForDeletion: (Int) -> Unit,
    private val unCheckSelectAllOnItemClick: () -> Unit
) : RecyclerView.Adapter<TrashListAdapter.MessageListViewHolder>(){

    private var messageSet = newConcurrentHashSet<Message>()
    var multiSelect = false
    private lateinit var itemRowBinding: CustomListItemMessageListBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageListViewHolder {
        val itemRowBinding =
            DataBindingUtil.inflate<CustomListItemMessageListBinding>(
                LayoutInflater.from(parent.context),
                R.layout.custom_list_item_message_list,
                parent,
                false
            )
        return MessageListViewHolder(itemRowBinding)
    }

    override fun getItemCount(): Int = messageSet.size

    override fun onBindViewHolder(holder: MessageListViewHolder, position: Int) {
        if (position in messageSet.indices) {
            val currentItem = messageSet.elementAt(position)
            holder.bind(currentItem)

            if (trashViewModel.getSelectedItems().contains(currentItem)) {
                itemRowBinding.cboxMessage.isChecked = true
                itemRowBinding.constraintLayoutItemHolderMessageList.alpha = REDUCED_ALPHA
            } else {
                itemRowBinding.cboxMessage.isChecked = false
                itemRowBinding.constraintLayoutItemHolderMessageList.alpha = NORMAL_ALPHA
            }

            itemRowBinding.constraintLayoutItemHolderMessageList.setOnLongClickListener {
                enableMultiSelect()
                if (multiSelect.not()) {
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
                if (multiSelect.not()) {
                    multiSelect = true
                }
                selectOrDeSelectItem(currentItem)
            }
        } else {
            Log.e(
                TRASH_LIST_UI_ON_BIND,
                "IndexOutOfBound in TrashList",
                throwable = null,
                "messages" to messageSet.map { it.asn },
                "pos" to position
            )
        }
    }

    fun selectAllItems() {
        multiSelect = true
        trashViewModel.setSelectedItems(newConcurrentHashSet())
        if (messageSet.size.isGreaterThan(ZERO)) {
            //The list is shallow copied
            trashViewModel.setSelectedItems(newConcurrentHashSet(messageSet))
        }
        onMultipleMessageSelectedForDeletion(trashViewModel.getSelectedItems().size)
    }

    fun clearAllSelectedItems() {
        multiSelect = false
        trashViewModel.setSelectedItems(
            newConcurrentHashSet()
        )
        onMultipleMessageSelectedForDeletion(trashViewModel.getSelectedItems().size)
    }

    private fun selectOrDeSelectItem(message: Message) {
        val selectedItems = trashViewModel.getSelectedItems()
        if (selectedItems.contains(message)) {
            selectedItems.remove(message)
            trashViewModel.setSelectedItems(selectedItems)
            itemRowBinding.cboxMessage.isChecked = false
            itemRowBinding.constraintLayoutItemHolderMessageList.alpha = NORMAL_ALPHA
        } else {
            //The message is shallow copied
            selectedItems.add(message.copy())
            trashViewModel.setSelectedItems(selectedItems)
            itemRowBinding.cboxMessage.isChecked = true
            itemRowBinding.constraintLayoutItemHolderMessageList.alpha = REDUCED_ALPHA
        }
        notifyDataSetChanged()
        onMultipleMessageSelectedForDeletion(selectedItems.size)
        unCheckSelectAllOnItemClick()
    }

    private fun enableMultiSelect() {
        if (trashViewModel.getSelectedItems().size == ZERO) multiSelect = false
    }

    fun updateMessageSet(newMessageSet: MutableSet<Message>) {
        messageSet = Utils.updateMessageSet(newMessageSet, messageSet, this)
    }

    inner class MessageListViewHolder(private val itemRowBinding: CustomListItemMessageListBinding) :
        RecyclerView.ViewHolder(itemRowBinding.root) {
        fun bind(message: Message) {
            itemRowBinding.setVariable(BR.message, message)
            itemRowBinding.executePendingBindings()
            this@TrashListAdapter.itemRowBinding = itemRowBinding
        }
    }
}