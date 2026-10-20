package io.github.zaxar1231.mycycle.feature.pet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.ui.component.PlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object PetRoute

fun NavGraphBuilder.petScreen() {
    composable<PetRoute> { PetScreen() }
}

@Composable
fun PetScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.pet_title),
        text = stringResource(R.string.pet_placeholder),
        modifier = modifier,
    )
}
