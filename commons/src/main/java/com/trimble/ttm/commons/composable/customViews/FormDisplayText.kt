package com.trimble.ttm.commons.composable.customViews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.WorkFlowTheme
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateBlue
import com.trimble.ttm.commons.composable.uiutils.styles.neutralGrey
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.EMPTY_STRING

@Composable
fun CustomDisplayText(formField: FormField) {
    Column(
        modifier = Modifier
            .padding(
                start = dimensionResource(id = R.dimen.padding_5dp),
                end = dimensionResource(id = R.dimen.padding_5dp),
                bottom = dimensionResource(id = R.dimen.padding_size_15)
            )
            .background(color = darkSlateBlue)
    ) {
        Divider(color = neutralGrey, thickness = dimensionResource(id = R.dimen.padding_1dp), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.padding_10dp)))
        CustomText(
            textViewBuilder = TextViewBuilder(
                text = formField.displayText ?: EMPTY_STRING,
                modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_size_15)),
                textStyle = getTextStyle()
            )
        )
        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.padding_10dp)))
        Divider(color = neutralGrey, thickness = dimensionResource(id = R.dimen.padding_1dp), modifier = Modifier.fillMaxWidth())
    }
}

@Preview(device = Devices.PIXEL_4)
@Composable
fun DisplayTextPreview() {
    WorkFlowTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            CustomDisplayText(formField = FormField(displayText = "Thank you for doing the best"))
        }

    }
}