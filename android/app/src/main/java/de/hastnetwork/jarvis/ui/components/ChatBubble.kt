package de.hastnetwork.jarvis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.hastnetwork.jarvis.data.model.Message

/** ChatGPT-style message bubble: user right-aligned, assistant left-aligned. */
@Composable
fun ChatBubble(message: Message, modifier: Modifier = Modifier) {
    val isUser = message.isUser
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = bubbleShape(isUser),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.attachments.isNotEmpty()) {
                    Column {
                        message.attachments.forEach { attachment ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text(text = attachment.filename, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Box(Modifier.padding(top = 4.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun bubbleShape(isUser: Boolean) = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = if (isUser) 18.dp else 4.dp,
    bottomEnd = if (isUser) 4.dp else 18.dp,
)
