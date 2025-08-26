package com.ably.example.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ably.example.getRealtimeChannel
import com.ably.example.incrementCoroutine
import com.ably.example.observeCounter
import com.ably.example.observeRootObject
import io.ably.lib.realtime.AblyRealtime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorVotingScreen(realtimeClient: AblyRealtime) {
  val scope = rememberCoroutineScope()
  val channel = getRealtimeChannel(realtimeClient, "objects-live-counter")

  val root = observeRootObject(channel)

  val (redCount, redCounter, resetRed) = observeCounter(channel, root,"red")
  val (greenCount, greenCounter, resetGreen) = observeCounter(channel, root,"green")
  val (blueCount, blueCounter, resetBlue) = observeCounter(channel, root,"blue")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp)
  ) {
    Text(
      text = "Vote for your favorite color",
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 16.dp)
    )

    ColorVoteCard(
      color = Color.Red,
      colorName = "Red",
      count = redCount ?: 0,
      enabled = greenCounter != null,
      onVote = {
        scope.launch {
          redCounter?.incrementCoroutine(1)
        }
      }
    )

    ColorVoteCard(
      color = Color.Green,
      colorName = "Green",
      count = greenCount ?: 0,
      enabled = greenCounter != null,
      onVote = {
        scope.launch {
          greenCounter?.incrementCoroutine(1)
        }
      }
    )

    ColorVoteCard(
      color = Color.Blue,
      colorName = "Blue",
      count = blueCount ?: 0,
      enabled = blueCounter != null,
      onVote = {
        scope.launch {
          blueCounter?.incrementCoroutine(1)
        }
      }
    )

    Button(
      enabled = redCounter != null && greenCounter != null && blueCounter != null,
      onClick = {
        scope.launch {
          resetRed()
          resetBlue()
          resetGreen()
        }
      },
    ) {
      Text(
        text = "Reset all",
        color = Color.White,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

@Composable
fun ColorVoteCard(
  color: Color,
  colorName: String,
  count: Int,
  enabled: Boolean,
  onVote: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(120.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    shape = RoundedCornerShape(12.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .background(color, RoundedCornerShape(8.dp))
        )
        Text(
          text = colorName,
          fontSize = 18.sp,
          fontWeight = FontWeight.Medium
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          text = count.toString(),
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("counter_${colorName.lowercase()}")
        )
        OutlinedButton(
          onClick = onVote,
          enabled = enabled,
          modifier = Modifier.testTag("vote_button_${colorName.lowercase()}")
        ) {
          Text(
            text = "Vote",
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
  }
}
