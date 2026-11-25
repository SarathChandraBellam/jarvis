package com.astra.hud.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.OrganizedItem
import com.astra.hud.ui.theme.HudColors
import com.astra.hud.ui.theme.AstraType

/** Bottom sheet with everything ASTRA knows about one item, plus calm actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
  item: OrganizedItem,
  onStatus: (String, ItemStatus) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheet,
    containerColor = HudColors.Surface,
    modifier = Modifier.testTag("itemSheet"),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(item.category.icon(), item.category.accent(), size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(item.category.label, style = MaterialTheme.typography.labelLarge, color = item.category.accent())
          Text(HudFormat.dateTime(item.timestamp), style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
        }
        IconButton(onClick = {
          val text = buildString {
            append(item.title)
            if (item.summary.isNotBlank()) append("\n").append(item.summary)
            item.amount?.let { append("\n").append(HudFormat.money(it, item.currency)) }
            item.dueDate?.let { append("\nDue ").append(it) }
          }
          val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
          runCatching { context.startActivity(Intent.createChooser(send, null)) }
        }) {
          Icon(Icons.Outlined.Share, "Share", tint = HudColors.TextDim)
        }
      }
      Spacer(Modifier.height(16.dp))
      Text(item.title, style = AstraType.TitleSerif, color = HudColors.Text)
      if (item.summary.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(item.summary, style = MaterialTheme.typography.bodyLarge, color = HudColors.TextDim)
      }
      Spacer(Modifier.height(16.dp))
      HorizontalDivider(color = HudColors.Outline)
      Spacer(Modifier.height(8.dp))
      item.amount?.let {
        DetailRow("Amount", (if (item.isCredit) "+" else "−") + HudFormat.money(it, item.currency),
          if (item.isCredit) HudColors.Mint else HudColors.Text)
      }
      item.merchant?.let { DetailRow("Merchant", it) }
      item.dueDate?.let { DetailRow("Due", it, HudColors.Amber) }
      DetailRow("Priority", item.priority.displayLabel(), item.priority.color())
      DetailRow("Source", item.source.label)
      item.sender?.let { DetailRow("From", it) }
      item.accountTail?.let { DetailRow("Account", "··$it") }
      item.reference?.let { DetailRow("Reference", it) }
      DetailRow(
        "Status",
        when (item.status) {
          ItemStatus.ACTIVE -> "Open"
          ItemStatus.DONE -> "Completed"
          ItemStatus.DISMISSED -> "Dismissed"
        },
      )
      Spacer(Modifier.height(20.dp))
      if (item.status == ItemStatus.ACTIVE) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(onClick = { onStatus(item.id, ItemStatus.DISMISSED) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Close, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Dismiss")
          }
          Button(
            onClick = { onStatus(item.id, ItemStatus.DONE) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
          ) {
            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Mark done")
          }
        }
      } else {
        OutlinedButton(onClick = { onStatus(item.id, ItemStatus.ACTIVE) }, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Move back to inbox")
        }
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = HudColors.Text) {
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextMuted, modifier = Modifier.width(96.dp))
    Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
  }
}
