package io.github.zaxar1231.mycycle.feature.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.pet.PetConstants
import io.github.zaxar1231.mycycle.core.domain.pet.PetItem
import io.github.zaxar1231.mycycle.core.domain.pet.PetItems
import io.github.zaxar1231.mycycle.core.domain.pet.PetMood
import io.github.zaxar1231.mycycle.core.domain.pet.PetRules
import io.github.zaxar1231.mycycle.core.domain.pet.PetShop
import io.github.zaxar1231.mycycle.core.domain.pet.PurchaseResult
import io.github.zaxar1231.mycycle.core.ui.component.BlockCard
import kotlinx.serialization.Serializable

@Serializable
data object PetRoute

fun NavGraphBuilder.petScreen() {
    composable<PetRoute> { PetScreen() }
}

/** The pet tab: the pet in its current mood, its level and coins, and the shop and wardrobe. */
@Composable
fun PetScreen(modifier: Modifier = Modifier, viewModel: PetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val purchase by viewModel.purchase.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    purchase?.let { result ->
        val message = stringResource(result.messageRes())
        LaunchedEffect(result) {
            snackbar.showSnackbar(message)
            viewModel.purchaseShown()
        }
    }
    Box(modifier.fillMaxSize()) {
        val pet = state.pet
        val look = viewModel.look(state)
        when {
            !state.loaded -> Unit
            pet == null || look == null -> Text(
                stringResource(R.string.pet_none),
                Modifier.align(Alignment.Center).padding(32.dp),
                textAlign = TextAlign.Center,
            )
            else -> PetContent(pet, state, look, viewModel)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PetContent(pet: Pet, state: PetUiState, look: PetLook, viewModel: PetViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(pet.type.nameRes()), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light))
        Text(stringResource(state.mood.textRes()), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        val glow = Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))
        Box(
            Modifier
                .size(PET_SIZE)
                .clip(CircleShape)
                .background(glow)
                .clickable(onClickLabel = stringResource(R.string.pet_stroke), onClick = viewModel::stroke),
            contentAlignment = Alignment.Center,
        ) {
            PetPicture(look, stringResource(R.string.pet_description, stringResource(pet.type.nameRes()), stringResource(state.mood.textRes())), Modifier.size(PET_SIZE))
        }
        Text(stringResource(R.string.pet_stroke_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Stats(pet, state.streak)
        HowToEarn()
        Wardrobe(pet, state.tryOn, viewModel)
    }
}

@Composable
private fun Stats(pet: Pet, streak: Int) {
    val (current, needed) = PetRules.progress(pet.xp)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.pet_level, pet.level), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pet_coins, pet.coins), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { current.toFloat() / needed }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.pet_xp, current, needed), style = MaterialTheme.typography.bodySmall)
            if (streak > 0) Text(pluralStringResource(R.plurals.pet_streak, streak, streak), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HowToEarn() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pet_earn_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.pet_earn_log, PetConstants.LOG_COINS), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pet_earn_breathing, PetConstants.BREATHING_COINS), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pet_earn_streak, PetConstants.STREAK_BONUS_DAYS, PetConstants.STREAK_BONUS_COINS), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Wardrobe(pet: Pet, tryOn: PetItem?, viewModel: PetViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    TabRow(selectedTabIndex = tab) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.pet_shop)) })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.pet_wardrobe)) })
    }
    val items = if (tab == 0) PetItems.ALL.filter { it.id !in pet.ownedItems } else PetItems.ALL.filter { it.id in pet.ownedItems }
    if (items.isEmpty()) {
        Text(
            stringResource(if (tab == 0) R.string.pet_shop_empty else R.string.pet_wardrobe_empty),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        return
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = tryOn == item || (tab == 1 && item.id in pet.equippedItems),
                onClick = { viewModel.tryOn(if (tryOn == item) null else item) },
                label = { Text(stringResource(item.nameRes())) },
            )
        }
    }
    val selected = tryOn?.takeIf { it in items } ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(selected.nameRes()), style = MaterialTheme.typography.titleMedium)
            if (tab == 0) {
                Text(stringResource(R.string.pet_price, selected.price, selected.minLevel), style = MaterialTheme.typography.bodyMedium)
                val check = PetShop.check(pet, selected)
                if (check != PurchaseResult.BOUGHT) {
                    Text(stringResource(check.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.buy(selected) }, enabled = check == PurchaseResult.BOUGHT) { Text(stringResource(R.string.pet_buy)) }
                    OutlinedButton(onClick = { viewModel.tryOn(null) }) { Text(stringResource(R.string.pet_take_back)) }
                }
            } else {
                val wearing = selected.id in pet.equippedItems
                Button(onClick = { viewModel.wear(selected, !wearing) }) {
                    Text(stringResource(if (wearing) R.string.pet_take_off else R.string.pet_put_on))
                }
            }
        }
    }
}

/** A home card: the pet in its mood, with a word on how it feels. Opens the pet tab. */
@Composable
fun PetHomeCard(onOpen: () -> Unit, viewModel: PetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pet = state.pet ?: return
    val look = viewModel.look(state) ?: return
    BlockCard(Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.pet_open), onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PetPicture(look, stringResource(R.string.pet_description, stringResource(pet.type.nameRes()), stringResource(state.mood.textRes())), Modifier.size(HOME_PET_SIZE))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(pet.type.nameRes()), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(state.mood.textRes()), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.pet_level_coins, pet.level, pet.coins),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun PetType.nameRes() = when (this) {
    PetType.CAT -> R.string.pet_name_cat
    PetType.FOX -> R.string.pet_name_fox
    PetType.BUNNY -> R.string.pet_name_bunny
    PetType.PANDA -> R.string.pet_name_panda
    PetType.PENGUIN -> R.string.pet_name_penguin
}

private fun PetMood.textRes() = when (this) {
    PetMood.SLEEPY -> R.string.pet_mood_sleepy
    PetMood.HAPPY -> R.string.pet_mood_happy
    PetMood.PLAYFUL -> R.string.pet_mood_playful
    PetMood.CALM -> R.string.pet_mood_calm
    PetMood.SAD -> R.string.pet_mood_sad
    PetMood.COZY -> R.string.pet_mood_cozy
}

private fun PurchaseResult.messageRes() = when (this) {
    PurchaseResult.BOUGHT -> R.string.pet_bought
    PurchaseResult.NOT_ENOUGH_COINS -> R.string.pet_need_coins
    PurchaseResult.LEVEL_TOO_LOW -> R.string.pet_need_level
    PurchaseResult.ALREADY_OWNED -> R.string.pet_owned
}

private fun PetItem.nameRes() = when (id) {
    "tuft" -> R.string.pet_item_tuft
    "bangs" -> R.string.pet_item_bangs
    "bow" -> R.string.pet_item_bow
    "flower_crown" -> R.string.pet_item_flower_crown
    "beret" -> R.string.pet_item_beret
    "party_hat" -> R.string.pet_item_party_hat
    "crown" -> R.string.pet_item_crown
    "glasses" -> R.string.pet_item_glasses
    "heart_glasses" -> R.string.pet_item_heart_glasses
    "scarf" -> R.string.pet_item_scarf
    "bowtie" -> R.string.pet_item_bowtie
    "bell" -> R.string.pet_item_bell
    "sweater" -> R.string.pet_item_sweater
    "cape" -> R.string.pet_item_cape
    else -> R.string.pet_item_unknown
}

private val PET_SIZE = 260.dp
private val HOME_PET_SIZE = 96.dp
