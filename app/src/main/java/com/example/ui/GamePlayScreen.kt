package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamePlayScreen(
    selectedPlayer: FighterSelection,
    selectedCpu: FighterSelection,
    selectedStage: StageSelection,
    isTrainingMode: Boolean,
    onBackToMenu: () -> Unit,
    recordsManager: BattleRecordsManager
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    // 1. Core Physics Engine Instance
    val engineState = remember {
        mutableStateOf(
            BrawlerEngine(selectedPlayer, selectedCpu, selectedStage)
        )
    }

    // Capture Training sandbag state if applicable
    LaunchedEffect(isTrainingMode) {
        if (isTrainingMode) {
            engineState.value.player2.stockCount = 99
            engineState.value.player2.isCpu = true
        }
    }

    val engine = engineState.value

    // Focus management for keyboard inputs
    val focusRequester = remember { FocusRequester() }
    val keysHeld = remember { mutableStateMapOf<Key, Boolean>() }
    LaunchedEffect(Unit) {
        // Staggered attempts to request focus of keyboard on screen launch
        delay(100)
        focusRequester.requestFocus()
        delay(300)
        focusRequester.requestFocus()
        delay(600)
        focusRequester.requestFocus()
    }

    // 2. Controller Button States
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var jumpPressed by remember { mutableStateOf(false) }
    var attackPressed by remember { mutableStateOf(false) }
    var specialPressed by remember { mutableStateOf(false) }
    var shieldPressed by remember { mutableStateOf(false) }

    // Game loop ticks
    var tickCount by remember { mutableStateOf(0) }
    val isPaused = remember { mutableStateOf(false) }

    // Reset loop
    LaunchedEffect(engine, isPaused.value) {
        while (!engine.isMatchOver && !isPaused.value) {
            engine.updateTicks(
                p1LeftPressed = leftPressed,
                p1RightPressed = rightPressed,
                p1DownPressed = downPressed,
                p1JumpPressed = jumpPressed,
                p1AttackPressed = attackPressed,
                p1SpecialPressed = specialPressed,
                p1ShieldPressed = shieldPressed
            )

            // Reset tap/instant triggers (jump, attack, special) to prevent repeating
            if (jumpPressed) jumpPressed = false
            if (attackPressed) attackPressed = false
            if (specialPressed) specialPressed = false

            tickCount++
            delay(16) // ~60fps ticks
        }

        // On game over - record stats
        if (engine.isMatchOver) {
            val winnerName = if (engine.player1.stockCount > 0 && engine.player2.stockCount <= 0) "PLAYER 1" else "CPU"
            if (!isTrainingMode) {
                recordsManager.addBattleRecord(
                    playerFighter = engine.player1.selection.characterName,
                    cpuFighter = engine.player2.selection.characterName,
                    stage = engine.selectedStage.displayName,
                    duration = 120 - engine.matchTimeSeconds,
                    winner = winnerName,
                    kos = engine.player1.totalKOs,
                    falls = engine.player1.totalFalls,
                    damageDealt = engine.player1.totalDamageDealt
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19)) // Cool slate dark outer
            .onKeyEvent { keyEvent ->
                val key = keyEvent.key
                val isKeyDown = keyEvent.type == KeyEventType.KeyDown
                val isKeyUp = keyEvent.type == KeyEventType.KeyUp
                
                if (isKeyDown) {
                    val isRepeat = keysHeld.containsKey(key)
                    keysHeld[key] = true
                    
                    when (key) {
                        Key.DirectionLeft, Key.A -> { leftPressed = true; true }
                        Key.DirectionRight, Key.D -> { rightPressed = true; true }
                        Key.DirectionDown, Key.S -> { downPressed = true; true }
                        Key.DirectionUp, Key.W, Key.Spacebar -> {
                            if (!isRepeat) {
                                jumpPressed = true
                            }
                            true
                        }
                        Key.J, Key.I -> { // Support J or I keys for normal attack (A)
                            if (!isRepeat) {
                                attackPressed = true
                            }
                            true
                        }
                        Key.K, Key.O -> { // Support K or O keys for special attack (B)
                            if (!isRepeat) {
                                specialPressed = true
                            }
                            true
                        }
                        Key.L, Key.P -> { // Support L or P keys for shield (S)
                            shieldPressed = true
                            true
                        }
                        else -> false
                    }
                } else if (isKeyUp) {
                    keysHeld.remove(key)
                    when (key) {
                        Key.DirectionLeft, Key.A -> { leftPressed = false; true }
                        Key.DirectionRight, Key.D -> { rightPressed = false; true }
                        Key.DirectionDown, Key.S -> { downPressed = false; true }
                        Key.DirectionUp, Key.W, Key.Spacebar -> {
                            true
                        }
                        Key.J, Key.I -> {
                            true
                        }
                        Key.K, Key.O -> {
                            true
                        }
                        Key.L, Key.P -> {
                            shieldPressed = false
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusRequester.requestFocus() }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // HUD / Top Navigation bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackToMenu,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return",
                        tint = Color.White
                    )
                }

                // Stage Info and Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isTrainingMode) "TRAINING ARENA" else "LIVE COMBAT",
                        color = if (isTrainingMode) Color(0xFFFBC02D) else Color(0xFFEC407A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = selectedStage.displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Match Clock
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isTrainingMode) "FREE PLAY" else "TIME: ${engine.matchTimeSeconds}s",
                        color = if (isTrainingMode) Color.White else if (engine.matchTimeSeconds <= 15) Color.Red else Color.Green,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- MAIN FIGHT SCREEN CANVAS ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .border(2.dp, selectedStage.primaryColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // Dynamic scale down virtual viewport coordinates (800 x 500)
                    val scaleX = canvasWidth / 800f
                    val scaleY = canvasHeight / 500f

                    // 1. Draw beautiful parallax backdrop
                    drawStageBackdrop(selectedStage, canvasWidth, canvasHeight)

                    // 2. Draw Blast Zone Boundaries alerts if players get too close
                    drawBlastZoneWarnings(engine, scaleX, scaleY)

                    // 3. Draw Stage Platforms
                    drawStagePlatforms(selectedStage, scaleX, scaleY)

                    // 4. Draw Projectiles
                    for (proj in engine.projectiles) {
                        drawCircle(
                            color = proj.color,
                            radius = proj.radius * scaleX,
                            center = Offset(proj.x * scaleX, proj.y * scaleY)
                        )
                        // Add electric or fire core
                        drawCircle(
                            color = Color.White,
                            radius = (proj.radius * 0.4f) * scaleX,
                            center = Offset(proj.x * scaleX, proj.y * scaleY)
                        )
                    }

                    // 5. Draw Fighter: Player 1
                    drawFighter(engine.player1, scaleX, scaleY, tickCount)

                    // 6. Draw Fighter: CPU Player 2
                    drawFighter(engine.player2, scaleX, scaleY, tickCount)

                    // On-scene sandbag stats if in Training Mode
                    if (isTrainingMode) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "TRAINING STAND: COMPOSITE DUMMY",
                            style = TextStyle(color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset((engine.player2.x - 70f) * scaleX, (engine.player2.y - 45f) * scaleY)
                        )
                    }

                    // 7. Draw Visual FX & Impact splash
                    for (fx in engine.effects) {
                        val fxX = fx.x * scaleX
                        val fxY = fx.y * scaleY
                        val sizeRaw = 30f * fx.sizeScale * scaleX

                        when (fx.type) {
                            EffectType.DAMAGE_POP -> {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = fx.text,
                                    style = TextStyle(
                                        color = fx.color,
                                        fontSize = (15f * (1f + (fx.totalTicks - fx.ticksRemaining) * 0.01f)).sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    topLeft = Offset(fxX - 25f, fxY)
                                )
                            }
                            EffectType.SMASH_RING -> {
                                val radius = sizeRaw * (1.2f - (fx.ticksRemaining / fx.totalTicks.toFloat()))
                                drawCircle(
                                    color = fx.color.copy(alpha = fx.ticksRemaining / fx.totalTicks.toFloat()),
                                    radius = radius,
                                    center = Offset(fxX, fxY),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                            EffectType.BLAST_RING -> {
                                val radius = (sizeRaw * 3f) * (1f - (fx.ticksRemaining / fx.totalTicks.toFloat()))
                                drawCircle(
                                    color = Color.Red.copy(alpha = fx.ticksRemaining / fx.totalTicks.toFloat()),
                                    radius = radius,
                                    center = Offset(fxX, fxY),
                                    style = Stroke(width = 5.dp.toPx())
                                )
                                // Star popping
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = fx.text,
                                    style = TextStyle(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold),
                                    topLeft = Offset(fxX - 15f, fxY - 10f)
                                )
                            }
                            EffectType.SHIELD_RING -> {
                                val radius = sizeRaw * 1.5f * (1f - (fx.ticksRemaining / fx.totalTicks.toFloat()))
                                drawCircle(
                                    color = Color.Yellow.copy(alpha = fx.ticksRemaining / fx.totalTicks.toFloat()),
                                    radius = radius,
                                    center = Offset(fxX, fxY),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            EffectType.SPARK_BURST -> {
                                val sparkSize = 4f * (fx.ticksRemaining / fx.totalTicks.toFloat()) * scaleX
                                drawCircle(
                                    color = fx.color,
                                    radius = sparkSize,
                                    center = Offset(fxX, fxY)
                                )
                            }
                            EffectType.PROJECTILE_BLOOM -> {
                                val radius = sizeRaw * (1.1f - (fx.ticksRemaining / fx.totalTicks.toFloat()))
                                drawCircle(
                                    color = Color.White.copy(alpha = fx.ticksRemaining / fx.totalTicks.toFloat()),
                                    radius = radius,
                                    center = Offset(fxX, fxY)
                                )
                            }
                        }
                    }
                }

                // Keyboard Controls Legend overlay at the bottom center of the fight screen canvas
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⌨️ Keyboard Controls: [A/D] or [◀/▶] Move | [W/Space] Jump | [S] Crouch | [J] Attack | [K] Special | [L] Shield",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // --- BOTTOM PLAYER PANEL & METERS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // PLAYER 1 STAT METER
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, engine.player1.selection.primaryColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "P1: ${engine.player1.selection.characterName}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        // STOCKS ICONS
                        Row {
                            (1..3).forEach { stock ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 1.5.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (engine.player1.stockCount >= stock) engine.player1.selection.primaryColor else Color.White.copy(
                                                alpha = 0.15f
                                            )
                                        )
                                )
                            }
                        }
                    }

                    // GIANT PERCENTAGE
                    Text(
                        text = "${engine.player1.damagePercentage}%",
                        color = getPercentageColor(engine.player1.damagePercentage),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )

                    // Shield status energy bar
                    LinearProgressIndicator(
                        progress = { engine.player1.shieldHealth / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Color.Yellow,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }

                // PLAYER 2 STAT METER
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, engine.player2.selection.primaryColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // STOCKS ICONS (infinite for Sandbag dummy)
                        Row {
                            (1..3).forEach { stock ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 1.5.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isTrainingMode) Color.Magenta else if (engine.player2.stockCount >= stock) engine.player2.selection.primaryColor else Color.White.copy(
                                                alpha = 0.15f
                                            )
                                        )
                                )
                            }
                        }
                        Text(
                            text = if (isTrainingMode) "SANDBAG" else "CPU: ${engine.player2.selection.characterName}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // GIANT PERCENTAGE
                    Text(
                        text = "${engine.player2.damagePercentage}%",
                        color = getPercentageColor(engine.player2.damagePercentage),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )

                    // Shield status energy bar
                    LinearProgressIndicator(
                        progress = { engine.player2.shieldHealth / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Color.Yellow,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }

            // --- VIRTUAL RETRO GAMEPAD CONTROLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT SIDE: MOVEMENT D-PAD (Left, Right, Down)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // LEFT BUTTON
                    GameControllerButton(
                        label = "◀",
                        color = Color(0xFF64B5F6),
                        onPressedStateChanged = { leftPressed = it }
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // JUMP UP TAPPED
                        IconButton(
                            onClick = { jumpPressed = true },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0xFF64B5F6).copy(alpha = 0.15f), CircleShape)
                                .border(1.5.dp, Color(0xFF64B5F6), CircleShape)
                        ) {
                            Text("▲", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        // DOWN BUTTON
                        GameControllerButton(
                            label = "▼",
                            color = Color(0xFF64B5F6),
                            onPressedStateChanged = { downPressed = it }
                        )
                    }

                    // RIGHT BUTTON
                    GameControllerButton(
                        label = "▶",
                        color = Color(0xFF64B5F6),
                        onPressedStateChanged = { rightPressed = it }
                    )
                }

                // CENTER: PAUSE / RESET (Useful for training sandbag)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isTrainingMode) {
                        Button(
                            onClick = {
                                engine.player2.damagePercentage = 0
                                engine.player2.x = selectedStage.spawnX2
                                engine.player2.y = selectedStage.spawnY2
                                engine.player2.vx = 0f
                                engine.player2.vy = 0f
                                engine.projectiles.clear()
                                engine.effects.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Dummy", Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset Dummy", fontSize = 10.sp)
                        }
                    } else {
                        IconButton(
                            onClick = { isPaused.value = !isPaused.value },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                if (isPaused.value) Icons.Default.PlayArrow else Icons.Default.Info,
                                contentDescription = if (isPaused.value) "Play" else "Pause",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = if (isPaused.value) "RESUME" else "ACTIVE",
                            fontSize = 8.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // RIGHT SIDE: ACTION BUTTONS (Jump, Attack, Special, Shield)
                FlowRow(
                    modifier = Modifier.widthIn(max = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ATTACK BUTTON (A)
                    IconButton(
                        onClick = { attackPressed = true },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFE57373).copy(alpha = 0.25f), CircleShape)
                            .border(2.dp, Color(0xFFE57373), CircleShape)
                    ) {
                        Text("A", color = Color(0xFFE57373), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    }

                    // SPECIAL BUTTON (B)
                    IconButton(
                        onClick = { specialPressed = true },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFFFB74D).copy(alpha = 0.25f), CircleShape)
                            .border(2.dp, Color(0xFFFFB74D), CircleShape)
                    ) {
                        Text("B", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    }

                    // SHIELD BUTTON (Shield defense held toggled)
                    GameControllerButton(
                        label = "S",
                        color = Color(0xFF81C784),
                        onPressedStateChanged = { shieldPressed = it }
                    )
                }
            }
        }

        // --- PAUSED DIALOG OVERLAY ---
        if (isPaused.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier
                        .width(280.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "GAME PAUSED",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ready to resume battle?",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { isPaused.value = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resume Fight", color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onBackToMenu,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Quit to Menu")
                        }
                    }
                }
            }
        }

        // --- MATCH RESOLVED DIALOG OVERLAY ---
        if (engine.isMatchOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .width(320.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, engine.selectedStage.primaryColor)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "VICTORY SHIELD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Yellow,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = engine.victoryMessage,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(16.dp))

                        // Match stats card
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            MatchStatLine("Winner Status", if (engine.player1.stockCount > 0) "Player 1 Wins" else "CPU Wins")
                            MatchStatLine("Player KOs", "${engine.player1.totalKOs}")
                            MatchStatLine("Player Damage Dealt", "${engine.player1.totalDamageDealt}%")
                            MatchStatLine("Player Falls", "${engine.player1.totalFalls}")
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                engineState.value = BrawlerEngine(selectedPlayer, selectedCpu, selectedStage)
                                if (isTrainingMode) {
                                    engineState.value.player2.stockCount = 99
                                    engineState.value.player2.isCpu = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = engine.selectedStage.secondaryColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rematch", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onBackToMenu,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Main Menu")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchStatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun GameControllerButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color,
    onPressedStateChanged: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(2.dp, color, CircleShape)
            .pointerInput(onPressedStateChanged) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPressedStateChanged(true)
                    
                    var isInside = true
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            val position = change.position
                            isInside = position.x in 0f..size.width.toFloat() &&
                                       position.y in 0f..size.height.toFloat()
                            val pressed = change.pressed && isInside
                            onPressedStateChanged(pressed)
                        } else {
                            onPressedStateChanged(false)
                        }
                    } while (event.changes.any { it.pressed })
                    
                    onPressedStateChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun getPercentageColor(percentage: Int): Color {
    return when {
        percentage == 0 -> Color.Green
        percentage < 50 -> Color(0xFFD4E157) // Yellow-Green
        percentage < 100 -> Color(0xFFFFB74D) // Amber
        percentage < 150 -> Color(0xFFFF5722) // Orange-red
        else -> Color(0xFFD32F2F) // Deep scarlet
    }
}

// Draw instructions for stages
private fun DrawScope.drawStageBackdrop(stage: StageSelection, width: Float, height: Float) {
    val gradBrush = when (stage) {
        StageSelection.FINAL_FRONTIER -> Brush.radialGradient(
            colors = listOf(Color(0xFF312E81), Color(0xFF03001C)),
            center = Offset(width * 0.5f, height * 0.5f),
            radius = width * 0.8f
        )
        StageSelection.BATTLEFIELD_PEAKS -> Brush.verticalGradient(
            colors = listOf(Color(0xFF1E293B), Color(0xFF111827))
        )
        StageSelection.SKY_SANCTUARY -> Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
        )
    }
    drawRect(brush = gradBrush, size = Size(width, height))

    // Aesthetic grid line patterns
    if (stage == StageSelection.FINAL_FRONTIER) {
        // Draw stardust circles
        for (i in 0..15) {
            val starX = (cos(i * 3.14 / 8) * 150f + width * 0.5f).toFloat()
            val starY = (sin(i * 3.14 / 8) * 90f + height * 0.4f).toFloat()
            drawCircle(Color.White.copy(alpha = 0.15f), radius = 3.dp.toPx(), center = Offset(starX, starY))
        }
    } else if (stage == StageSelection.SKY_SANCTUARY) {
        // Draw cloud vector hints
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = 60.dp.toPx(),
            center = Offset(width * 0.2f, height * 0.25f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = 80.dp.toPx(),
            center = Offset(width * 0.8f, height * 0.4f)
        )
    }
}

private fun DrawScope.drawBlastZoneWarnings(engine: BrawlerEngine, scaleX: Float, scaleY: Float) {
    // Left boundary trigger
    if (engine.player1.x < 80f || engine.player2.x < 80f) {
        drawRect(
            color = Color.Red.copy(alpha = 0.15f),
            topLeft = Offset(0f, 0f),
            size = Size(20.dp.toPx(), size.height)
        )
    }
    // Right boundary trigger
    if (engine.player1.x > 720f || engine.player2.x > 720f) {
        drawRect(
            color = Color.Red.copy(alpha = 0.15f),
            topLeft = Offset(size.width - 20.dp.toPx(), 0f),
            size = Size(20.dp.toPx(), size.height)
        )
    }
}

private fun DrawScope.drawStagePlatforms(stage: StageSelection, scaleX: Float, scaleY: Float) {
    // 1. Draw solid main floor
    val main = stage.mainPlatform
    val mainLeft = main.xMin * scaleX
    val mainRight = main.xMax * scaleX
    val mainWidth = mainRight - mainLeft
    val mainY = main.y * scaleY

    // Draw main solid slab
    drawRoundRect(
        color = stage.primaryColor,
        topLeft = Offset(mainLeft, mainY),
        size = Size(mainWidth, 24.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )

    // Glowing border Accent
    drawRoundRect(
        color = stage.secondaryColor,
        topLeft = Offset(mainLeft, mainY),
        size = Size(mainWidth, 24.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )

    // Inner detail grid strip
    drawLine(
        color = stage.secondaryColor.copy(alpha = 0.35f),
        start = Offset(mainLeft, mainY + 8.dp.toPx()),
        end = Offset(mainRight, mainY + 8.dp.toPx()),
        strokeWidth = 2.dp.toPx()
    )

    // 2. Draw floating shelves
    for (plat in stage.floatingPlatforms) {
        val platLeft = plat.xMin * scaleX
        val platRight = plat.xMax * scaleX
        val platWidth = platRight - platLeft
        val platY = plat.y * scaleY

        drawRoundRect(
            color = stage.primaryColor.copy(alpha = 0.9f),
            topLeft = Offset(platLeft, platY),
            size = Size(platWidth, 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = stage.secondaryColor,
            topLeft = Offset(platLeft, platY),
            size = Size(platWidth, 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

private fun DrawScope.drawFighter(fighter: FighterState, scaleX: Float, scaleY: Float, tick: Int) {
    if (fighter.isDead || fighter.isRespawning) return

    val fx = fighter.x * scaleX
    val fy = fighter.y * scaleY
    val radius = 22f * scaleX

    // Invincible flashing ghost effect
    val alpha = if (fighter.isInvincible && (tick / 4) % 2 == 0) 0.35f else 1.0f

    // --- COOL DETAILED ACTION TRAIL (Motion Blur Effect) ---
    val speedVal = kotlin.math.sqrt(fighter.vx * fighter.vx + fighter.vy * fighter.vy)
    if (speedVal > 1.2f) {
        // Draw transparent kinetic shadows behind the movement path
        for (i in 1..2) {
            val backX = fx - fighter.vx * i * 3.2f * scaleX / 4.8f
            val backY = fy - fighter.vy * i * 3.2f * scaleY / 4.8f
            drawCircle(
                color = fighter.selection.primaryColor.copy(alpha = (0.28f / i) * alpha),
                radius = radius * (1f - i * 0.12f),
                center = Offset(backX, backY)
            )
        }
    }

    // 1. Fighter Body Gradient (Sleek deep volumetric 3D look)
    val bodyGrad = Brush.radialGradient(
        colors = listOf(Color.White, fighter.selection.primaryColor),
        center = Offset(fx - radius * 0.25f, fy - radius * 0.25f),
        radius = radius
    )

    drawCircle(
        brush = bodyGrad,
        radius = radius,
        center = Offset(fx, fy),
        alpha = alpha
    )

    // Outline character
    drawCircle(
        color = Color.White.copy(alpha = 0.45f),
        radius = radius,
        center = Offset(fx, fy),
        style = Stroke(width = 1.8.dp.toPx())
    )

    val facingLeft = fighter.isFacingLeft

    // --- CHARACTER SPECIAL ACCESSORIES & DETAILED FEATURES ---
    when (fighter.selection) {
        FighterSelection.PLUMBER_JOE -> {
            // Cool Plumber Mustache
            val mustX = fx + if (facingLeft) -6.dp.toPx() else 6.dp.toPx()
            val mustY = fy + 3.dp.toPx()
            drawRoundRect(
                color = Color(0xFF3E2723), // Deep brown/black moustache
                topLeft = Offset(mustX - 6.dp.toPx(), mustY),
                size = Size(12.dp.toPx(), 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                alpha = alpha
            )
            // Oversized Plumber Cap Brim
            drawLine(
                color = Color(0xFFD32F2F), // Caps Red
                start = Offset(fx - 13.dp.toPx(), fy - 11.dp.toPx()),
                end = Offset(fx + 13.dp.toPx(), fy - 11.dp.toPx()),
                strokeWidth = 4.5.dp.toPx(),
                alpha = alpha
            )
            // Little White Badge on Plumber's Cap
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(fx, fy - 14.dp.toPx()),
                alpha = alpha
            )
            drawCircle(
                color = Color(0xFFD32F2F),
                radius = 1.5.dp.toPx(),
                center = Offset(fx, fy - 14.dp.toPx()),
                alpha = alpha
            )
        }
        FighterSelection.SHADOW_HUNTER -> {
            // Dark ninja eye mask wrap background
            drawRect(
                color = Color(0xFF111111),
                topLeft = Offset(fx - 13.dp.toPx(), fy - 6.dp.toPx()),
                size = Size(26.dp.toPx(), 5.dp.toPx()),
                alpha = alpha
            )
            // Glowing neon purple slit eyes (Cyberpunk Ninja feel)
            val leftSlitX = fx + (if (facingLeft) -10.dp.toPx() else 3.dp.toPx())
            val rightSlitX = fx + (if (facingLeft) -3.dp.toPx() else 10.dp.toPx())
            drawLine(
                color = Color(0xFFD500F9), // Glowing Neon Violet
                start = Offset(leftSlitX, fy - 4.dp.toPx()),
                end = Offset(leftSlitX + (if (facingLeft) 3.dp.toPx() else -3.dp.toPx()), fy - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                alpha = alpha
            )
            
            // Flowing Ninja Mask Scarf ribbons behind the brawler
            val knotDirection = if (facingLeft) 11.dp.toPx() else -11.dp.toPx()
            val waveY = sin(tick * 0.16f) * 4.dp.toPx()
            val knotX = fx + knotDirection
            val knotY = fy + 3.dp.toPx()
            
            // Draw ribbons
            drawCircle(color = Color(0xFF7B1FA2), radius = 2.5.dp.toPx(), center = Offset(knotX, knotY), alpha = alpha)
            drawLine(
                color = Color(0xFF9C27B0),
                start = Offset(knotX, knotY),
                end = Offset(knotX + knotDirection, knotY + 4.dp.toPx() + waveY),
                strokeWidth = 3.dp.toPx(),
                alpha = alpha
            )
            drawLine(
                color = Color(0xFFE040FB),
                start = Offset(knotX, knotY),
                end = Offset(knotX + knotDirection * 1.3f, knotY + 1.dp.toPx() + waveY * 0.7f),
                strokeWidth = 2.dp.toPx(),
                alpha = alpha
            )
        }
        FighterSelection.ELVEN_ARCHER -> {
            // Green woodland hood draped over head
            drawArc(
                color = Color(0xFF1B5E20),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(fx - radius * 1.05f, fy - radius * 1.05f),
                size = Size(radius * 2.1f, radius * 2.1f),
                alpha = alpha * 0.85f
            )
            // Wooden composite bow on his back
            val bowBackX = if (facingLeft) 9.dp.toPx() else -9.dp.toPx()
            drawArc(
                color = Color(0xFF5D4037), // Polished wood bow
                startAngle = if (facingLeft) 45f else 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(fx + bowBackX - 12.dp.toPx(), fy - 12.dp.toPx()),
                size = Size(24.dp.toPx(), 24.dp.toPx()),
                style = Stroke(width = 3.dp.toPx()),
                alpha = alpha
            )
            // Little woodland Archer Cap yellow feather
            drawLine(
                color = Color(0xFFFFEB3B),
                start = Offset(fx - 1.dp.toPx(), fy - 19.dp.toPx()),
                end = Offset(fx + 6.dp.toPx(), fy - 26.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                alpha = alpha
            )
        }
        FighterSelection.SPARKY -> {
            // Iconic golden yellow zigzag dynamic lightning tail
            val tailLeft = facingLeft
            val tDir = if (tailLeft) 1f else -1f
            val sX = fx + tDir * 12.dp.toPx()
            val sY = fy + 5.dp.toPx()
            val p1x = sX + tDir * 8.dp.toPx()
            val p1y = sY - 5.dp.toPx()
            val p2x = p1x - tDir * 4.dp.toPx()
            val p2y = p1y - 9.dp.toPx()
            val p3x = p2x + tDir * 12.dp.toPx()
            val p3y = p2y - 11.dp.toPx()

            drawLine(Color(0xFFE65100), Offset(sX, sY), Offset(p1x, p1y), 4.5.dp.toPx(), alpha = alpha)
            drawLine(Color(0xFFFBC02D), Offset(p1x, p1y), Offset(p2x, p2y), 4.5.dp.toPx(), alpha = alpha)
            drawLine(Color(0xFFFFF59D), Offset(p2x, p2y), Offset(p3x, p3y), 5.dp.toPx(), alpha = alpha)

            // Rosy cheeks
            drawCircle(
                color = Color(0xFFD32F2F),
                radius = 3.5.dp.toPx(),
                center = Offset(fx + (if (tailLeft) -11f else 11f).dp.toPx(), fy + 2.dp.toPx()),
                alpha = alpha
            )

            // Continuous Electrical Orbit sparks
            val sparkAngle = ((tick * 12) % 628) * 0.01f
            val pathRadius = radius * 1.35f
            val sparkX = fx + pathRadius * cos(sparkAngle).toFloat()
            val sparkY = fy + pathRadius * sin(sparkAngle).toFloat()
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(sparkX, sparkY),
                alpha = alpha
            )
            drawCircle(
                color = Color(0xFFFBC02D),
                radius = 4.dp.toPx(),
                center = Offset(sparkX, sparkY),
                style = Stroke(width = 1.dp.toPx()),
                alpha = alpha * 0.6f
            )
        }
        FighterSelection.IRON_GOLEM -> {
            // Hexagonal shield grid armor pattern
            drawCircle(
                color = Color.Black.copy(alpha = 0.55f),
                radius = radius * 0.55f,
                center = Offset(fx, fy),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawLine(
                color = Color(0xFF26C6DA),
                start = Offset(fx - radius * 0.7f, fy),
                end = Offset(fx + radius * 0.7f, fy),
                strokeWidth = 2.dp.toPx(),
                alpha = alpha
            )
            // Cybernetic Crimson Core Eye glowing heavily
            val eyeX = fx + (if (facingLeft) -7.dp.toPx() else 7.dp.toPx())
            drawCircle(
                color = Color(0xFFFF1744), // High-intensity glowing neon crimson
                radius = 4.dp.toPx(),
                center = Offset(eyeX, fy - 4.dp.toPx()),
                alpha = alpha
            )
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = Offset(eyeX, fy - 4.dp.toPx()),
                alpha = alpha
            )
        }
        FighterSelection.PINK_PUFF -> {
            // Cute tiny stubby red boots/shoes at the bottom
            val shoeColor = Color(0xFFFF1744)
            drawCircle(
                color = shoeColor,
                radius = 4.5.dp.toPx(),
                center = Offset(fx - 10.dp.toPx(), fy + radius * 0.85f),
                alpha = alpha
            )
            drawCircle(
                color = shoeColor,
                radius = 4.5.dp.toPx(),
                center = Offset(fx + 10.dp.toPx(), fy + radius * 0.85f),
                alpha = alpha
            )

            // Tiny stubby hands waving
            val waveOffset = sin(tick * 0.2f) * 3f
            val handX = fx + (if (facingLeft) -11f else 11f).dp.toPx()
            val handY = fy + 3.dp.toPx() + waveOffset
            drawCircle(
                color = fighter.selection.primaryColor,
                radius = 3.5.dp.toPx(),
                center = Offset(handX, handY),
                alpha = alpha
            )

            // Pink cute star blush
            drawCircle(
                color = Color(0xFFF06292).copy(alpha = 0.7f),
                radius = 3.5.dp.toPx(),
                center = Offset(fx + (if (facingLeft) -10f else 10f).dp.toPx(), fy + 2.dp.toPx()),
                alpha = alpha
            )
        }
    }

    // 2. Base Character Eye (Overlay on top of custom skins to keep general expressiveness)
    if (fighter.selection != FighterSelection.SHADOW_HUNTER) {
        val eyeOffset = if (facingLeft) -7.dp.toPx() else 7.dp.toPx()
        val eyeHeightOffset = -3.dp.toPx()
        drawCircle(
            color = Color.White,
            radius = 3.5.dp.toPx(),
            center = Offset(fx + eyeOffset, fy + eyeHeightOffset),
            alpha = alpha
        )
        drawCircle(
            color = Color.Black,
            radius = 1.8.dp.toPx(),
            center = Offset(fx + eyeOffset + (if (facingLeft) -0.8f else 0.8f).dp.toPx(), fy + eyeHeightOffset),
            alpha = alpha
        )
    }

    // 3. Draw Active Shield Overlay (If blocking)
    if (fighter.isShieldActive) {
        val shieldRadius = (radius * 1.5f) * (fighter.shieldHealth / 100f)
        drawCircle(
            color = Color(0x7F22C55E), // Semi transparent green shield
            radius = shieldRadius,
            center = Offset(fx, fy)
        )
        drawCircle(
            color = Color(0xFF4ADE80),
            radius = shieldRadius,
            center = Offset(fx, fy),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // 4. Hit Stun stars / dizzy details
    if (fighter.isHitStun) {
        val starAngle = (tick % 360) * 0.05
        val orbitDist = radius * 1.2f
        val starX = fx + orbitDist * cos(starAngle).toFloat()
        val starY = fy - radius * 0.8f + (radius * 0.2f) * sin(starAngle).toFloat()
        drawCircle(
            color = Color.Yellow,
            radius = 3.dp.toPx(),
            center = Offset(starX, starY)
        )
    }

    // Respawn Invincibility aura Ring
    if (fighter.isInvincible) {
        drawCircle(
            color = Color.Cyan.copy(alpha = 0.5f),
            radius = radius * 1.3f,
            center = Offset(fx, fy),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )
    }
}
