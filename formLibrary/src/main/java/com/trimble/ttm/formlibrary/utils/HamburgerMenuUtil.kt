package com.trimble.ttm.formlibrary.utils

import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.HamburgerMenu
import com.trimble.ttm.formlibrary.model.HamburgerMenuItem

enum class Screen {
    DISPATCH_LIST,
    DISPATCH_DETAIL
}

fun getHamburgerMenu(): List<HamburgerMenu> =
    mutableListOf<HamburgerMenu>().apply {
        add(
            HamburgerMenu(
                HamburgerMenuItem(R.string.menu_messaging),
                ArrayList()
            )
        )
        add(
            HamburgerMenu(
                HamburgerMenuItem(R.string.menu_form_library),
                ArrayList()
            )
        )
    }