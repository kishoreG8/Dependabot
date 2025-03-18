package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize18Sp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize22Sp

@Composable
fun ErrorWithRetry(errorMessage: String,onRetryClicked:()->Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(id = R.dimen.padding_size_15),
            alignment = Alignment.CenterVertically
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CustomText(
            textViewBuilder = TextViewBuilder(
                text = errorMessage,
                alignment = TextAlign.Center,
                modifier = Modifier.padding(0.dp),
                textStyle = TextStyle(
                    color = colorResource(id = R.color.white),
                    fontSize = fontSize22Sp,
                    fontWeight = FontWeight.Bold
                )
            )
        )
        CustomButton(
            buttonViewBuilder = ButtonViewBuilder(
                onclick = { onRetryClicked() },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                color = colorResource(id = R.color.outlineBoxErrorColor)

            ),
            textViewBuilder = TextViewBuilder(
                stringResource(R.string.retry_button_text),
                alignment = TextAlign.Center,
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.CenterHorizontally)
                    .align(Alignment.CenterHorizontally),
                textStyle = TextStyle(
                    color = colorResource(id = R.color.colorPrimaryDark),
                    fontSize = fontSize18Sp,
                    fontWeight = FontWeight.Bold
                )
            )
        )
    }
}