package com.trimble.ttm.commons.model

data class DTFConditions(val branchTargetId: Int,
                         val selectedViewId: Int,
                         val loopEndId: Int,
                         val actualLoopCount : Int,
                         val currentLoopCount : Int)