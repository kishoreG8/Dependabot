package com.trimble.ttm.formlibrary.model

data class HamburgerMenuItem(val menuItemStringRes: Int)
data class HamburgerMenu(val group: HamburgerMenuItem, val child: List<HamburgerMenuItem>)