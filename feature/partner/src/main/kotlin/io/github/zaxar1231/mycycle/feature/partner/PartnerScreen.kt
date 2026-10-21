package io.github.zaxar1231.mycycle.feature.partner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerView
import io.github.zaxar1231.mycycle.core.domain.pet.PetConstants
import io.github.zaxar1231.mycycle.core.domain.pet.PetItems
import io.github.zaxar1231.mycycle.core.domain.pet.PetMood
import io.github.zaxar1231.mycycle.core.domain.pet.PetRules
import io.github.zaxar1231.mycycle.core.domain.pet.SharedPet
import io.github.zaxar1231.mycycle.core.ui.component.BlockCard
import io.github.zaxar1231.mycycle.core.ui.component.emoji
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import io.github.zaxar1231.mycycle.core.ui.pet.PetLook
import io.github.zaxar1231.mycycle.core.ui.pet.PetPicture
import io.github.zaxar1231.mycycle.core.ui.pet.nameRes
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Serializable
data object PartnerRoute

fun NavGraphBuilder.partnerScreen(onBack: () -> Unit) {
    composable<PartnerRoute> { PartnerScreen(onBack) }
}

/** The partner's view of the shared cycle with ideas for support in each phase. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerScreen(onBack: () -> Unit, viewModel: PartnerCycleViewModel = hiltViewModel()) {
    val cycle by viewModel.cycle.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.partner_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.partner_back)) }
                },
                actions = { TextButton(onClick = viewModel::load) { Text(stringResource(R.string.partner_reload)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val c = cycle) {
                PartnerCycleUi.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                PartnerCycleUi.Waiting -> Message(stringResource(R.string.partner_nothing_yet))
                PartnerCycleUi.Ended -> Message(stringResource(R.string.partner_ended))
                is PartnerCycleUi.Failed -> {
                    Message(stringResource(c.error.messageRes()))
                    OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.partner_retry)) }
                }
                is PartnerCycleUi.Shown -> {
                    SharedCycleContent(c.view, c.snapshot.date)
                    c.snapshot.pet?.let { SharedPetCard(it, viewModel) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedCycleContent(view: PartnerView, sharedOn: LocalDate) {
    val dateFormat = DateTimeFormatter.ofPattern(stringResource(R.string.partner_date_format))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        view.cycleDay?.let {
            Text(stringResource(R.string.partner_day, it), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light))
        }
        view.phase?.let { Text(stringResource(it.labelRes()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        periodLine(view)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (view.phase == null) Text(stringResource(R.string.partner_phase_hidden), style = MaterialTheme.typography.bodyMedium)
        if (view.daysOld > 0) {
            Text(
                pluralStringResource(R.plurals.partner_updated_days_ago, view.daysOld, view.daysOld),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val fertileStart = view.fertileStart
    val fertileEnd = view.fertileEnd
    if (fertileStart != null && fertileEnd != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.partner_fertile_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.partner_fertile_range, fertileStart.format(dateFormat), fertileEnd.format(dateFormat)))
                if (view.fertileToday) Text(stringResource(R.string.partner_fertile_today), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (view.moods.isNotEmpty() || view.symptoms.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.partner_logged_on, sharedOn.format(dateFormat)), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                view.moods.sortedBy { it.ordinal }.forEach { mood ->
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.partner_mood_chip, mood.emoji(), stringResource(mood.labelRes()))) })
                }
                view.symptoms.sortedBy { it.ordinal }.forEach { symptom ->
                    AssistChip(onClick = {}, label = { Text(stringResource(symptom.labelRes())) })
                }
            }
        }
    }
    view.phase?.let { Tips(it) }
    Text(stringResource(R.string.partner_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The owner's pet, which this phone can feed once a day and give a gift once a week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedPetCard(pet: SharedPet, viewModel: PartnerCycleViewModel) {
    val care by viewModel.care.collectAsStateWithLifecycle()
    val error by viewModel.careError.collectAsStateWithLifecycle()
    val today = viewModel.today()
    val fedToday = care?.lastFed == today
    val canGift = PetRules.canGift(care?.lastGift, today)
    var choosing by rememberSaveable { mutableStateOf(false) }
    val worn = pet.worn.mapNotNull(PetItems::byId).associateBy { it.slot }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.partner_pet_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            val name = stringResource(pet.type.nameRes())
            // The owner's clock is not shared: at night on this phone the pet sleeps.
            val hour = LocalTime.now().hour
            val mood = if (hour >= PetConstants.SLEEP_HOUR || hour < PetConstants.WAKE_HOUR) PetMood.SLEEPY else pet.mood
            PetPicture(PetLook(pet.type, mood, worn), stringResource(R.string.partner_pet_description, name, pet.level), Modifier.size(PET_SIZE))
            Text(stringResource(R.string.partner_pet_level, name, pet.level), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::feed, enabled = !fedToday) {
                    Text(stringResource(if (fedToday) R.string.partner_pet_fed else R.string.partner_pet_feed))
                }
                OutlinedButton(onClick = { choosing = !choosing }, enabled = canGift) { Text(stringResource(R.string.partner_pet_gift)) }
            }
            if (!canGift) Text(stringResource(R.string.partner_pet_gift_later, PetConstants.PARTNER_GIFT_DAYS), style = MaterialTheme.typography.bodySmall)
            val gifts = PetItems.ALL.filter { it.id !in pet.ownedItems && it.minLevel <= pet.level }
            if (choosing && canGift) {
                if (gifts.isEmpty()) {
                    Text(stringResource(R.string.partner_pet_no_gifts), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.partner_pet_choose), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gifts.forEach { item ->
                            AssistChip(
                                onClick = {
                                    choosing = false
                                    viewModel.gift(item)
                                },
                                label = { Text(stringResource(item.nameRes())) },
                            )
                        }
                    }
                }
            }
            error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.partner_pet_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tips(phase: CyclePhase) {
    val tips = stringArrayResource(
        when (phase) {
            CyclePhase.MENSTRUAL -> R.array.partner_tips_menstrual
            CyclePhase.FOLLICULAR -> R.array.partner_tips_follicular
            CyclePhase.OVULATORY -> R.array.partner_tips_ovulatory
            CyclePhase.LUTEAL -> R.array.partner_tips_luteal
        },
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.partner_tips_title), style = MaterialTheme.typography.titleSmall)
            tips.forEach { Text(stringResource(R.string.partner_tip_bullet, it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** When the period is expected, or how late it is; null when the forecast is not shared. */
@Composable
private fun periodLine(view: PartnerView): String? {
    val days = view.daysUntilPeriod ?: return null
    return when {
        view.daysLate > 0 -> pluralStringResource(R.plurals.partner_period_late, view.daysLate, view.daysLate)
        days == 0 -> stringResource(R.string.partner_period_today)
        else -> pluralStringResource(R.plurals.partner_period_in, days, days)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(PaddingValues(vertical = 24.dp)))
}

/** A home card for the partner: today's day and phase of the shared cycle. Nothing for anyone else. */
@Composable
fun PartnerHomeCard(onOpen: () -> Unit, viewModel: PartnerCycleViewModel = hiltViewModel()) {
    val following by viewModel.following.collectAsStateWithLifecycle()
    if (!following) return
    val cycle by viewModel.cycle.collectAsStateWithLifecycle()
    // Fresh data each time the home screen comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }
    BlockCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.partner_home_title), style = MaterialTheme.typography.titleMedium)
            when (val c = cycle) {
                is PartnerCycleUi.Shown -> {
                    val view = c.view
                    val phase = view.phase?.let { stringResource(it.labelRes()) }
                    val day = view.cycleDay?.let { stringResource(R.string.partner_day, it) }
                    listOfNotNull(day, phase).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(stringResource(R.string.partner_separator)), style = MaterialTheme.typography.bodyLarge)
                    }
                    periodLine(view)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                PartnerCycleUi.Loading -> CircularProgressIndicator()
                PartnerCycleUi.Waiting -> Text(stringResource(R.string.partner_nothing_yet), style = MaterialTheme.typography.bodyMedium)
                PartnerCycleUi.Ended -> Text(stringResource(R.string.partner_ended), style = MaterialTheme.typography.bodyMedium)
                is PartnerCycleUi.Failed -> Text(stringResource(c.error.messageRes()), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.partner_open), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Calls [onInvite] when an invitation link opened the app. */
@Composable
fun PartnerInviteNavigator(onInvite: () -> Unit, viewModel: PartnerInviteViewModel = hiltViewModel()) {
    val code by viewModel.code.collectAsStateWithLifecycle()
    LaunchedEffect(code) {
        val incoming = code ?: return@LaunchedEffect
        if (incoming != viewModel.handled) {
            viewModel.handled = incoming
            onInvite()
        }
    }
}

private val PET_SIZE = 180.dp
