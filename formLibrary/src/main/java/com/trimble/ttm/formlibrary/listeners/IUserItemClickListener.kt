package com.trimble.ttm.formlibrary.listeners

import com.trimble.ttm.formlibrary.model.User

interface IUserItemClickListener {
    fun onUserItemClicked(user: User)
}