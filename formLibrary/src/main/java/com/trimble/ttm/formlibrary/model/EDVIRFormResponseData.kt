package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.model.FormResponse

data class EDVIRFormResponseRepoData(val customerId: String,
                                     val dsn: String,
                                     val curTimeUtcFormattedStr: String,
                                     val formResponse: FormResponse,
                                     val driverName: String,
                                     val formId: Int,
                                     val formClass: Int,
                                     val currentTimeInMillisInUTC: Long,
                                     val inspectionType: String,
                                     val isSyncToQueue: Boolean)

data class EDVIRFormResponseUsecasesData(val customerId: String,
                                         val obcId: String,
                                         val curTimeUtcFormattedStr: String,
                                         val formResponse: FormResponse,
                                         val driverName: String,
                                         val formId: Int,
                                         val formClass: Int,
                                         val currentTimeInMillisInUTC: Long,
                                         val inspectionType: String,
                                         val isSyncToQueue: Boolean)
