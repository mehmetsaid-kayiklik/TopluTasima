package com.example.toplutasima.ui.components.transit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.domain.transit.validation.ValidationIssue
import com.example.toplutasima.domain.transit.validation.ValidationIssueCode
import com.example.toplutasima.domain.transit.validation.ValidationSeverity
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.viewmodel.rmvlog.TransitValidationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitValidationSheet(
    state: TransitValidationUiState,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onIssueSelected: (ValidationIssue) -> Unit,
    onContinueWithWarnings: () -> Unit
) {
    val result = state.result ?: return
    if (!state.showSheet) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title(lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = description(lang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            result.issues.forEach { issue ->
                ValidationIssueRow(
                    issue = issue,
                    lang = lang,
                    onClick = { onIssueSelected(issue) }
                )
            }

            HorizontalDivider()

            if (result.criticalIssues.isNotEmpty()) {
                Text(
                    text = criticalBlockedLabel(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(cancelLabel(lang))
                }
                if (result.requiresUserConfirmation) {
                    Button(onClick = onContinueWithWarnings, modifier = Modifier.weight(1f)) {
                        Text(continueLabel(lang))
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationIssueRow(
    issue: ValidationIssue,
    lang: AppLanguage,
    onClick: () -> Unit
) {
    val critical = issue.severity == ValidationSeverity.CRITICAL
    val contentColor = if (critical) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val containerColor = if (critical) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (critical) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                contentDescription = if (critical) criticalLabel(lang) else warningLabel(lang),
                tint = contentColor
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = issueLabel(issue.code, lang),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val context = buildList {
                    issue.target.segmentIndex?.let { add(segmentLabel(lang, it + 1)) }
                    if (issue.detail.isNotBlank()) add(issue.detail)
                    add(goToFieldLabel(lang))
                }.joinToString(" · ")
                Text(
                    text = context,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun issueLabel(code: ValidationIssueCode, lang: AppLanguage): String = when (code) {
    ValidationIssueCode.SAME_STOP -> tr(lang, "Biniş ve iniş durağı aynı.", "Ein- und Ausstiegshaltestelle sind identisch.", "Boarding and alighting stops are the same.")
    ValidationIssueCode.INVALID_PLANNED_TIME -> tr(lang, "Planlanan saat geçersiz.", "Die planmäßige Uhrzeit ist ungültig.", "The planned time is invalid.")
    ValidationIssueCode.INVALID_ACTUAL_TIME -> tr(lang, "Gerçek saat geçersiz.", "Die tatsächliche Uhrzeit ist ungültig.", "The actual time is invalid.")
    ValidationIssueCode.INCOMPLETE_PLANNED_TIMES -> tr(lang, "Planlanan saat çiftinden biri eksik.", "Eine planmäßige Uhrzeit fehlt.", "One planned time is missing.")
    ValidationIssueCode.INCOMPLETE_ACTUAL_TIMES -> tr(lang, "Gerçek saat çiftinden biri eksik.", "Eine tatsächliche Uhrzeit fehlt.", "One actual time is missing.")
    ValidationIssueCode.MISSING_ACTUAL_TIMES -> tr(lang, "Gerçek biniş ve iniş saatleri eksik.", "Tatsächliche Ein- und Ausstiegszeiten fehlen.", "Actual boarding and alighting times are missing.")
    ValidationIssueCode.PLANNED_TIME_ORDER -> tr(lang, "Planlanan saat sırası geçersiz.", "Die Reihenfolge der planmäßigen Zeiten ist ungültig.", "The planned time order is invalid.")
    ValidationIssueCode.ACTUAL_TIME_ORDER -> tr(lang, "Gerçek saat sırası geçersiz.", "Die Reihenfolge der tatsächlichen Zeiten ist ungültig.", "The actual time order is invalid.")
    ValidationIssueCode.NEGATIVE_DURATION -> tr(lang, "Negatif yolculuk süresi algılandı.", "Eine negative Fahrtdauer wurde erkannt.", "A negative trip duration was detected.")
    ValidationIssueCode.UNUSUAL_DURATION -> tr(lang, "Yolculuk süresi olağan dışı görünüyor.", "Die Fahrtdauer wirkt ungewöhnlich.", "The trip duration looks unusual.")
    ValidationIssueCode.STORED_DURATION_MISMATCH -> tr(lang, "Saatlerle kayıtlı süre uyuşmuyor.", "Gespeicherte Dauer und Uhrzeiten stimmen nicht überein.", "Stored duration does not match the times.")
    ValidationIssueCode.INVALID_DISTANCE -> tr(lang, "Mesafe değeri geçersiz.", "Der Distanzwert ist ungültig.", "The distance value is invalid.")
    ValidationIssueCode.INCONSISTENT_DISTANCE -> tr(lang, "RMV ve ORS mesafeleri belirgin biçimde farklı.", "RMV- und ORS-Distanz unterscheiden sich deutlich.", "RMV and ORS distances differ substantially.")
    ValidationIssueCode.PLANNED_SEGMENT_OVERLAP -> tr(lang, "Planlanan segment saatleri çakışıyor.", "Planmäßige Segmentzeiten überschneiden sich.", "Planned segment times overlap.")
    ValidationIssueCode.ACTUAL_SEGMENT_OVERLAP -> tr(lang, "Gerçek segment saatleri çakışıyor.", "Tatsächliche Segmentzeiten überschneiden sich.", "Actual segment times overlap.")
}

private fun title(lang: AppLanguage) = tr(lang, "Kaydetmeden önce kontrol", "Prüfung vor dem Speichern", "Check before saving")
private fun description(lang: AppLanguage) = tr(lang, "Bulunan maddeleri gözden geçirin. Kritik sorunlar düzeltilmeden kayıt yapılamaz.", "Bitte prüfen Sie die gefundenen Punkte. Kritische Probleme müssen zuerst behoben werden.", "Review the detected issues. Critical problems must be fixed before saving.")
private fun criticalBlockedLabel(lang: AppLanguage) = tr(lang, "Kritik sorunlar nedeniyle kayıt engellendi.", "Speichern ist wegen kritischer Probleme blockiert.", "Saving is blocked by critical issues.")
private fun continueLabel(lang: AppLanguage) = tr(lang, "Uyarıları kabul et ve devam et", "Warnungen bestätigen", "Accept warnings and continue")
private fun cancelLabel(lang: AppLanguage) = tr(lang, "Düzeltmeye dön", "Zur Korrektur", "Return to fix")
private fun criticalLabel(lang: AppLanguage) = tr(lang, "Kritik sorun", "Kritisches Problem", "Critical issue")
private fun warningLabel(lang: AppLanguage) = tr(lang, "Uyarı", "Warnung", "Warning")
private fun goToFieldLabel(lang: AppLanguage) = tr(lang, "Alana git", "Zum Feld", "Go to field")
private fun segmentLabel(lang: AppLanguage, index: Int) = tr(lang, "Segment $index", "Segment $index", "Segment $index")

private fun tr(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.DE -> de
    AppLanguage.EN -> en
}
