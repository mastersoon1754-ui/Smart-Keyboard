package com.azertyai.keyboard.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azertyai.keyboard.data.KeyHeight
import com.azertyai.keyboard.data.ThemeMode
import com.azertyai.keyboard.logic.Provider
import com.azertyai.keyboard.system.openAccessibilitySettings
import com.azertyai.keyboard.system.openKeyboardSettings
import com.azertyai.keyboard.system.showKeyboardPicker

private val Gold = Color(0xFFC6A15B)
private val GoldDeep = Color(0xFF8C6A32)
private val Night = Color(0xFF100F0D)
private val NightCard = Color(0xFF1C1B18)
private val Cream = Color(0xFFF6F3EE)
private val Ink = Color(0xFF1C1B19)
private val Paper = Color(0xFFF3EFE8)
private val Muted = Color(0xFF8A847A)

@Composable
fun AzertyApp(model: SetupViewModel) {
    val dark = when (model.settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Gold,
            onPrimary = Color(0xFF1A1408),
            background = Night,
            surface = NightCard,
            onBackground = Cream,
            onSurface = Cream,
            surfaceVariant = Color(0xFF2A2824),
            onSurfaceVariant = Color(0xFFB7B1A6),
            outline = Color(0xFF3C3934),
        )
    } else {
        lightColorScheme(
            primary = GoldDeep,
            onPrimary = Color(0xFFFFF8EC),
            background = Paper,
            surface = Color.White,
            onBackground = Ink,
            onSurface = Ink,
            surfaceVariant = Color(0xFFE7E1D8),
            onSurfaceVariant = Color(0xFF5E594F),
            outline = Color(0xFFD5CFC6),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BackHandler(enabled = model.route != Route.Home) { model.open(Route.Home) }
            when (model.route) {
                Route.Home -> HomeScreen(model)
                Route.Keys -> KeysScreen(model)
                Route.Privacy -> PrivacyScreen(model)
                Route.Preferences -> PreferencesScreen(model)
            }
        }
    }
}

@Composable
private fun HomeScreen(model: SetupViewModel) {
    val context = LocalContext.current
    ScreenColumn {
        Text("AZERTY", fontFamily = FontFamily.Serif, fontSize = 44.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        BoxRule()
        Spacer(Modifier.height(14.dp))
        Text(
            "Un clavier français, avec un assistant discret. La saisie reste sur l’appareil. Le réseau ne sert que lorsque vous lancez une action.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(22.dp))
        StepCard("1", "Activer", "Autorisez AZERTY dans les claviers du système.", model.settings.keyboardEnabled) {
            openKeyboardSettings(context)
        }
        Spacer(Modifier.height(10.dp))
        StepCard("2", "Choisir", "Sélectionnez-le quand le clavier est ouvert.", model.settings.keyboardSelected) {
            showKeyboardPicker(context)
        }
        Spacer(Modifier.height(10.dp))
        StepCard(
            "3",
            "Clé API",
            "Facultatif. Gemini ou Mistral, chiffrée sur ce téléphone.",
            model.settings.hasGemini || model.settings.hasMistral,
        ) { model.open(Route.Keys) }
        Spacer(Modifier.height(22.dp))
        NavRow("Clés API") { model.open(Route.Keys) }
        NavRow("Confidentialité") { model.open(Route.Privacy) }
        NavRow("Préférences") { model.open(Route.Preferences) }
        Spacer(Modifier.height(28.dp))
        Text("Version 1.0", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun KeysScreen(model: SetupViewModel) {
    val context = LocalContext.current
    val gemini = model.settings.provider == Provider.GEMINI
    ScreenColumn {
        BackTitle("Clés API") { model.open(Route.Home) }
        Text(
            "La clé reste dans le coffre du téléphone. Elle n’est lisible que par AZERTY, au moment d’une action.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Gemini", gemini) { model.selectProvider(Provider.GEMINI) }
            ChoiceChip("Mistral", !gemini) { model.selectProvider(Provider.MISTRAL) }
        }
        Spacer(Modifier.height(18.dp))
        if (gemini) {
            KeyBlock(
                stored = model.settings.hasGemini,
                draft = model.geminiDraft,
                revealed = model.revealGemini,
                onDraft = { model.geminiDraft = it },
                onReveal = model::toggleGeminiReveal,
                onSave = model::saveGemini,
                onClear = model::clearGemini,
                modelValue = model.geminiModelDraft,
                onModel = { model.geminiModelDraft = it },
                help = "Créer une clé Gemini",
                helpUrl = "https://aistudio.google.com/apikey",
            )
        } else {
            KeyBlock(
                stored = model.settings.hasMistral,
                draft = model.mistralDraft,
                revealed = model.revealMistral,
                onDraft = { model.mistralDraft = it },
                onReveal = model::toggleMistralReveal,
                onSave = model::saveMistral,
                onClear = model::clearMistral,
                modelValue = model.mistralModelDraft,
                onModel = { model.mistralModelDraft = it },
                help = "Créer une clé Mistral",
                helpUrl = "https://console.mistral.ai/",
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            model.saveModels()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (gemini) "https://aistudio.google.com/apikey" else "https://console.mistral.ai/")))
        }) { Text(if (gemini) "Ouvrir Google AI Studio" else "Ouvrir la console Mistral") }
        Button(
            onClick = model::testConnection,
            enabled = !model.testing,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1A1408)),
        ) { Text(if (model.testing) "Essai…" else "Essayer la connexion") }
        model.testMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun KeyBlock(
    stored: Boolean,
    draft: String,
    revealed: Boolean,
    onDraft: (String) -> Unit,
    onReveal: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modelValue: String,
    onModel: (String) -> Unit,
    help: String,
    helpUrl: String,
) {
    Text(if (stored) "Une clé est enregistrée." else "Aucune clé enregistrée.", color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = draft,
        onValueChange = onDraft,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nouvelle clé") },
        singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Row {
        TextButton(onClick = onReveal) { Text(if (revealed) "Masquer" else "Afficher") }
        TextButton(onClick = onSave, enabled = draft.isNotBlank()) { Text("Enregistrer") }
        TextButton(onClick = onClear, enabled = stored) { Text("Effacer") }
    }
    OutlinedTextField(
        value = modelValue,
        onValueChange = onModel,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Modèle") },
        singleLine = true,
        supportingText = { Text(help) },
    )
    Spacer(Modifier.height(4.dp))
    Text(helpUrl, color = Muted, fontSize = 12.sp)
}

@Composable
private fun PrivacyScreen(model: SetupViewModel) {
    val context = LocalContext.current
    ScreenColumn {
        BackTitle("Confidentialité") { model.open(Route.Home) }
        Text(
            "AZERTY n’envoie rien tout seul. Une question, une réécriture ou une traduction partent seulement quand vous les demandez. Une réponse n’est jamais écrite dans le champ sans Insérer ou Remplacer.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(16.dp))
        SettingRow(
            "Analyse de contexte",
            "Autorise la lecture, sur votre demande, des 5, 10 ou 30 derniers messages affichés. Désactivé par défaut.",
            model.settings.contextEnabled,
            model::setContext,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (model.settings.accessibilityOn) {
                "Le service d’accessibilité est actif. Il ne conserve pas de journal : il lit l’écran au moment où vous choisissez un nombre de messages, puis vous montrez l’aperçu avant l’envoi."
            } else {
                "Pour lire une conversation déjà affichée, activez le service « Contexte AZERTY ». Sans lui, vous pouvez coller un extrait."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { openAccessibilitySettings(context) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
        ) { Text(if (model.settings.accessibilityOn) "Réglages d’accessibilité" else "Activer le service") }
        Spacer(Modifier.height(18.dp))
        InfoCard("Champs masqués", "Mots de passe et champs privés : l’assistant peut répondre à une question, jamais lire le contenu.")
        Spacer(Modifier.height(10.dp))
        InfoCard("Gemini", "Les appels utilisent store=false, pour ne pas garder l’échange côté Google. La politique du fournisseur s’applique quand même.")
        Spacer(Modifier.height(10.dp))
        InfoCard("Mémoire", "Aucun historique de conversation n’est écrit sur le téléphone. L’aperçu disparaît quand vous fermez le panneau.")
    }
}

@Composable
private fun PreferencesScreen(model: SetupViewModel) {
    ScreenColumn {
        BackTitle("Préférences") { model.open(Route.Home) }
        Text("Apparence", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Système", model.settings.theme == ThemeMode.SYSTEM) { model.setTheme(ThemeMode.SYSTEM) }
            ChoiceChip("Clair", model.settings.theme == ThemeMode.LIGHT) { model.setTheme(ThemeMode.LIGHT) }
            ChoiceChip("Sombre", model.settings.theme == ThemeMode.DARK) { model.setTheme(ThemeMode.DARK) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Hauteur", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Basse", model.settings.height == KeyHeight.COMPACT) { model.setHeight(KeyHeight.COMPACT) }
            ChoiceChip("Normale", model.settings.height == KeyHeight.NORMAL) { model.setHeight(KeyHeight.NORMAL) }
            ChoiceChip("Haute", model.settings.height == KeyHeight.TALL) { model.setHeight(KeyHeight.TALL) }
        }
        Spacer(Modifier.height(8.dp))
        SettingRow("Retour haptique", "Une impulsion légère à chaque touche.", model.settings.haptics, model::setHaptics)
        SettingRow("Son des touches", "Désactivé par défaut.", model.settings.sound, model::setSound)
        SettingRow("Aperçu de la touche", "La lettre s’affiche au-dessus du doigt.", model.settings.popups, model::setPopups)
        SettingRow("Espace double", "Deux espaces insèrent un point.", model.settings.doubleSpace, model::setDoubleSpace)
        SettingRow("Majuscules automatiques", "Après un point, et en début de champ.", model.settings.autoCap, model::setAutoCap)
        SettingRow("Suggestions", "Dictionnaire français local. Rien n’est appris de votre saisie.", model.settings.suggestions, model::setSuggestions)
    }
}

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        content = { content() },
    )
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text("Retour") }
    Text(title, fontFamily = FontFamily.Serif, fontSize = 34.sp, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun BoxRule() {
    Spacer(
        Modifier
            .width(36.dp)
            .height(2.dp)
            .background(Gold),
    )
}

@Composable
private fun StepCard(index: String, title: String, body: String, done: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(index, color = GoldDeep, fontFamily = FontFamily.Serif, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
            }
            Text(if (done) "OK" else "→", color = if (done) GoldDeep else Muted)
        }
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp)
        Text("→", color = Muted)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Gold else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (selected) Color(0xFF1A1408) else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SettingRow(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = GoldDeep, checkedThumbColor = Cream),
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
    }
}
