package com.trimble.ttm.routemanifest.model

data class BuildNotificationData(var cls: Class<*>?,
                                 val contentTitle: String,
                                 val contentText: String,
                                 val notificationId: Int,
                                 val color: Int?,
                                 val priority: Int,
                                 val autoDismiss: Boolean,
                                 val displayPriority: Int = 6,
                                 val dispatch: Dispatch?,
                                 val isForNewMessage: Boolean = false)
