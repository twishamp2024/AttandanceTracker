package com.twisham.attendance.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twisham.attendance.domain.model.ClassSession
import com.twisham.attendance.ui.components.CompactSessionRow
import com.twisham.attendance.ui.components.SessionEditorSheet
import com.twisham.attendance.ui.theme.LocalStatusPalette
import com.twisham.attendance.util.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month view.
 *
 * Each day carries a small coloured dot rather than a filled cell: a full-bleed
 * colour block makes the date numbers hard to read and turns a normal month into an
 * alarming wall of red. The dot conveys the same information while the grid stays
 * legible, and every cell carries a spoken description for screen readers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenSubject: (Long) -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ClassSession?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                actions = {
                    IconButton(onClick = { viewModel.showMonth(YearMonth.from(state.today)) }) {
                        Text("Today", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            MonthHeader(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
            Spacer(Modifier.height(8.dp))
            WeekdayHeader()
            Spacer(Modifier.height(4.dp))
            MonthGrid(
                month = state.month,
                today = state.today,
                days = state.days,
                onSelect = viewModel::select,
            )
            Spacer(Modifier.height(16.dp))
            Legend()
            Spacer(Modifier.height(24.dp))
        }
    }

    state.selectedDate?.let { date ->
        DaySheet(
            date = date,
            today = state.today,
            sessions = state.selectedSessions,
            subjectsById = state.subjectsById,
            onDismiss = { viewModel.select(null) },
            onOpenSession = { editing = it },
        )
    }

    editing?.let { session ->
        SessionEditorSheet(
            session = session,
            subject = state.subjectsById[session.subjectId],
            today = state.today,
            onDismiss = { editing = null },
            onStatusChange = { viewModel.setStatus(session, it) },
            onNoteChange = { viewModel.setNote(session, it) },
            onDelete = { viewModel.delete(session) },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "Previous month",
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = Formatters.monthYear(month.atDay(1)),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Next month",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    // Read through LocalConfiguration so a locale change recomposes the header
    // rather than leaving stale day names on screen.
    val locale: Locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    days: Map<LocalDate, DaySummary>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    // Monday-first grid; DayOfWeek.value is 1..7 with Monday = 1.
    val leading = first.dayOfWeek.value - 1
    val totalCells = ((leading + month.lengthOfMonth() + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until totalCells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val index = row * 7 + column
                    val dayOfMonth = index - leading + 1
                    if (dayOfMonth in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            summary = days[date],
                            isToday = date == today,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStatusPalette.current
    val moodColor: Color? = when (summary?.mood) {
        DayMood.ALL_PRESENT -> palette.safe
        DayMood.MIXED -> palette.caution
        DayMood.MOSTLY_ABSENT -> palette.critical
        DayMood.UNMARKED -> MaterialTheme.colorScheme.outline
        DayMood.CANCELLED_ONLY -> palette.neutral
        else -> null
    }

    val spoken = buildString {
        append(Formatters.fullDate(date))
        if (isToday) append(", today")
        append(", ${summary?.spoken ?: "no classes"}")
    }

    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .padding(2.dp)
            .clickable(enabled = summary != null && summary.total > 0, onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .then(
                        if (isToday) {
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(2.dp))
            MoodDot(mood = summary?.mood, color = moodColor)
        }
    }
}

/** The dots mean nothing without this, so it is always on screen, not in a menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val palette = LocalStatusPalette.current
    Column {
        Text("What the dots mean", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendItem(palette.safe, "Present for all")
            LegendItem(palette.caution, "Mixed")
            LegendItem(palette.critical, "Mostly absent")
            LegendItem(MaterialTheme.colorScheme.outline, "Not marked yet")
            LegendItem(palette.neutral, "All cancelled", hollow = true)
        }
    }
}

/**
 * A day's marker.
 *
 * "All cancelled" is drawn hollow rather than filled. It and "not marked yet" are
 * both muted greys, and two greys a shade apart is not a distinction anyone can
 * rely on — so they differ in shape as well as colour.
 */
@Composable
private fun MoodDot(mood: DayMood?, color: Color?, size: androidx.compose.ui.unit.Dp = 7.dp) {
    when {
        color == null -> Box(Modifier.size(size))
        mood == DayMood.CANCELLED_ONLY -> Box(
            Modifier
                .size(size)
                .border(1.5.dp, color, CircleShape),
        )
        else -> Box(Modifier.size(size).background(color, CircleShape))
    }
}

@Composable
private fun LegendItem(color: Color, label: String, hollow: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hollow) {
            Box(Modifier.size(8.dp).border(1.5.dp, color, CircleShape))
        } else {
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    date: LocalDate,
    today: LocalDate,
    sessions: List<ClassSession>,
    subjectsById: Map<Long, com.twisham.attendance.domain.model.Subject>,
    onDismiss: () -> Unit,
    onOpenSession: (ClassSession) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(Formatters.fullDate(date), style = MaterialTheme.typography.titleLarge)
            Text(
                text = Formatters.relativeDate(date, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (sessions.isEmpty()) {
                Text("No classes on this day.", style = MaterialTheme.typography.bodyMedium)
            } else {
                sessions.forEach { session ->
                    CompactSessionRow(
                        session = session,
                        subject = subjectsById[session.subjectId],
                        onClick = { onOpenSession(session) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap a class to change it — you can correct any past record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
