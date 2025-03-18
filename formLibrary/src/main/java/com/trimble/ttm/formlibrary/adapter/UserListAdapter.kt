package com.trimble.ttm.formlibrary.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.formlibrary.BR
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.CustomUserListItemBinding
import com.trimble.ttm.formlibrary.listeners.IUserItemClickListener
import com.trimble.ttm.formlibrary.model.User

class UserListAdapter : RecyclerView.Adapter<UserListAdapter.UserViewHolder>(),
    IUserItemClickListener {

    var userSet = mutableSetOf<User>()

    //To maintain the users list based on selected order
    private var checkedUsers = mutableSetOf<User>()

    private lateinit var userListItemBinding: CustomUserListItemBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val userListItemBinding = DataBindingUtil.inflate<CustomUserListItemBinding>(
            LayoutInflater.from(parent.context),
            R.layout.custom_user_list_item,
            parent,
            false
        )
        userListItemBinding.userItemClickListener = this@UserListAdapter
        return UserViewHolder(userListItemBinding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userSet.elementAt(position))
        userListItemBinding.selectBox.isChecked = userSet.elementAt(position).isSelected
        userListItemBinding.selectBox.setOnClickListener {
            notifySelection(position)
        }
        holder.itemView.setOnClickListener {
            notifySelection(position)
        }
    }

    private fun notifySelection(position: Int) {
        val isSelected = !userSet.elementAt(position).isSelected
        userSet.elementAt(position).isSelected = isSelected
        if (userSet.elementAt(position).isSelected) checkedUsers.add(userSet.elementAt(position))
        else if (!userSet.elementAt(position).isSelected && checkedUsers.contains(
                userSet.elementAt(
                    position
                )
            )
        ) checkedUsers.remove(userSet.elementAt(position))
        Log.logUiInteractionInInfoLevel("ContactListScreen", "User selected: ${userSet.elementAt(position).username}. Selected users: ${checkedUsers.map { it.username }}")
        notifyItemChanged(position)
    }

    override fun getItemCount(): Int = userSet.size

    inner class UserViewHolder(private val userListItemBinding: CustomUserListItemBinding) :
        RecyclerView.ViewHolder(userListItemBinding.root) {
        fun bind(user: User) {
            userListItemBinding.setVariable(BR.user, user)
            userListItemBinding.executePendingBindings()
            this@UserListAdapter.userListItemBinding = userListItemBinding
        }
    }

    override fun onUserItemClicked(user: User) {
        //Ignore
    }

    fun getUsersList() =
        Triple(userSet.filter { it.isSelected }, checkedUsers, userSet.filter { !it.isSelected })
}