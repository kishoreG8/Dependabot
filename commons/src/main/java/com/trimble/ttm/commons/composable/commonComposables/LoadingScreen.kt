package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize20Sp

@Preview(device = Devices.PIXEL_4)
@Composable
fun LoadingPreview() {
    LoadingScreen("Hello", true)
}

@Composable
fun LoadingScreen(progressText: String, show: Boolean) {
    if (show || progressText.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (show) {
                CircularProgressIndicator(
                    color = colorResource(id = R.color.circularProgressBarColor)
                )
            }

            CustomText(
                TextViewBuilder(
                    text = progressText,
                    alignment = TextAlign.Center,
                    modifier = Modifier.padding(start = 20.dp),
                    textStyle = TextStyle(
                        color = colorResource(id = R.color.textColor),
                        fontSize = fontSize20Sp
                    )
                )
            )
        }
    }
}

@Composable
fun ShowErrorText(errorText: String) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomText(
            TextViewBuilder(
                text = errorText,
                alignment = TextAlign.Center,
                modifier = Modifier.padding(start = 20.dp),
                textStyle = TextStyle(
                    color = colorResource(id = R.color.textColor),
                    fontSize = fontSize20Sp
                )

            )
        )
    }
}