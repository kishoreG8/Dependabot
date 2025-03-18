package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.model.DispatcherFormValuesPath
import com.trimble.ttm.commons.model.UIFormResponse

/**This data class us used to send values from to MessageFormViewModel to MessageFormUseCase.
The purpose is only to passing data between two classes**/
data class DriverMessageFormData(val formId: String,
                                 val isFreeForm: Boolean,
                                 val uiFormResponse: UIFormResponse,
                                 val formResponseType: String,
                                 val asn: String,
                                 val dispatcherFormValuesPath: DispatcherFormValuesPath,
                                 val dispatcherFormValuesFormFieldMap : HashMap<String, ArrayList<String>>,
                                 val savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String,Boolean>> = hashMapOf(),)
