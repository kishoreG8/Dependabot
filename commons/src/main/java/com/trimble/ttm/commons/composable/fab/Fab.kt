package com.trimble.ttm.commons.composable.fab

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults.elevation
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize20Sp
import com.trimble.ttm.commons.model.FabItem
import com.trimble.ttm.commons.utils.FAB_ICON

@Composable
fun MultiFloatingActionButton(
    items: List<FabItem>,
    toState: FabState,
    stateChanged: (fabState: FabState) -> Unit,
    onFabItemClicked: (item: FabItem) -> Unit
) {
    val transition: Transition<FabState> = updateTransition(targetState = toState, label = "fab transition")
    val alpha: Float by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 10)
        }, label = "fab alpha"
    ) { state ->
        if (state == FabState.EXPANDED) 1f else 0f
    }

    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(dimensionResource(id = R.dimen.dp_30))) {
        items.forEach { item ->
            FabItem(item, alpha, onFabItemClicked)
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.dp_15)))
        }
        FloatingActionButton(
            modifier = Modifier
                .size(dimensionResource(id = R.dimen.dp_60)),
            onClick = {
                stateChanged(
                    if (transition.currentState == FabState.EXPANDED) {
                        FabState.COLLAPSED
                    } else {
                        FabState.EXPANDED
                    }
                )
            },
            elevation = elevation(dimensionResource(id = R.dimen.dp_15)),
            backgroundColor = colorResource(id = R.color.fabMenuColor),
            contentColor = colorResource(
                id = R.color.textColor
            ),
        ) {
            Image(
                painter =
                    painterResource(id = if (toState== FabState.EXPANDED) {
                        R.drawable.ic_arrow_down_white
                    } else {
                        R.drawable.ic_add_white
                    }),
                contentDescription = FAB_ICON,
                modifier = Modifier
                    .width(dimensionResource(id = R.dimen.dp_32))
                    .height(dimensionResource(id = R.dimen.dp_32))
            )
        }
    }
}

@Composable
private fun FabItem(
    item: FabItem,
    alpha: Float,
    onFabItemClicked: (item: FabItem) -> Unit
) {
    Row {
            Text(
                item.label,
                fontSize = fontSize20Sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(id = R.color.fabMenuColor),
                modifier = Modifier
                    .alpha(animateFloatAsState(alpha).value)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = false,
                            radius = dimensionResource(id = R.dimen.dp_20),
                            color = MaterialTheme.colors.onSecondary
                        ),
                        onClick = { onFabItemClicked(item) },
                    )
            )
            Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.dp_10)))
    }
}

