package com.trimble.ttm.formlibrary.listeners

import com.trimble.ttm.commons.model.FormDef

interface IFormClickListener {
    fun onFormClicked(formDef: FormDef)
}