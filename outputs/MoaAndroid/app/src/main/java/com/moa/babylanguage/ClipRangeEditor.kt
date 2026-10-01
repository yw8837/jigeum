package com.moa.babylanguage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** All edit paths (drag, TalkBack, buttons) use these clamps; maximum is the current plan. */
internal object ClipRangeEdits {
    fun fit(range: ClipRange, durationMs: Long, maxSelectionMs: Long): ClipRange {
        require(durationMs >= 1_000 && maxSelectionMs >= 1_000)
        val length = range.durationMs.coerceIn(1_000, minOf(durationMs, maxSelectionMs))
        val start = range.startMs.coerceIn(0, durationMs - length)
        return ClipRange(start, start + length)
    }

    fun start(range: ClipRange, proposedMs: Long, durationMs: Long, maxSelectionMs: Long): ClipRange {
        val current = fit(range, durationMs, maxSelectionMs)
        return ClipRange(proposedMs.coerceIn(maxOf(0, current.endMs - maxSelectionMs), current.endMs - 1_000), current.endMs)
    }

    fun end(range: ClipRange, proposedMs: Long, durationMs: Long, maxSelectionMs: Long): ClipRange {
        val current = fit(range, durationMs, maxSelectionMs)
        return ClipRange(current.startMs, proposedMs.coerceIn(current.startMs + 1_000, minOf(durationMs, current.startMs + maxSelectionMs)))
    }

    fun move(range: ClipRange, deltaMs: Long, durationMs: Long, maxSelectionMs: Long): ClipRange {
        val current = fit(range, durationMs, maxSelectionMs)
        val delta = deltaMs.coerceIn(-current.startMs, durationMs - current.endMs)
        return ClipRange(current.startMs + delta, current.endMs + delta)
    }

    fun time(ms: Long) = "%d:%02d.%d".format(java.util.Locale.ROOT, ms / 60_000, ms / 1_000 % 60, ms / 100 % 10)
    fun seconds(ms: Long) = if (ms % 1_000 == 0L) "${ms / 1_000}" else "${ms / 1_000}.${ms / 100 % 10}"
}

@Composable
internal fun ClipRangeEditor(
    clip: LocalClip,
    range: ClipRange,
    maxSelectionMs: Long,
    enabled: Boolean,
    onRangeChange: (ClipRange) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val current = ClipRangeEdits.fit(range, clip.durationMs, maxSelectionMs)
    val currentRange by rememberUpdatedState(current)
    val onChange by rememberUpdatedState(onRangeChange)
    var preview by remember(clip.uri) { mutableStateOf<TimelinePreview?>(null) }
    var zoom by rememberSaveable(clip.uri.toString()) { mutableIntStateOf(1) }
    var viewStart by rememberSaveable(clip.uri.toString()) { mutableLongStateOf(0) }
    var fine by rememberSaveable { mutableStateOf(false) }
    val viewDuration = (clip.durationMs / zoom).coerceAtLeast(minOf(clip.durationMs, 5_000))
    val visibleStart = viewStart.coerceIn(0, clip.durationMs - viewDuration)

    // Analysis gets decoder priority. A cancelled/incomplete preview restarts when editing returns.
    LaunchedEffect(clip.uri, enabled) {
        if (enabled && preview == null) preview = TimelinePreviewLoader.load(context, clip)
    }
    fun emit(next: ClipRange) { if (enabled && next != currentRange) onChange(next) }
    fun shift(delta: Long) = emit(ClipRangeEdits.move(currentRange, delta, clip.durationMs, maxSelectionMs))
    fun chooseZoom(value: Int) {
        val newDuration = (clip.durationMs / value).coerceAtLeast(minOf(clip.durationMs, 5_000))
        viewStart = ((current.startMs + current.endMs - newDuration) / 2).coerceIn(0, clip.durationMs - newDuration)
        zoom = value
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${ClipRangeEdits.time(current.startMs)} – ${ClipRangeEdits.time(current.endMs)} · ${ClipRangeEdits.seconds(current.durationMs)}초",
            color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("selected-range"))
        Text("양끝을 끌면 길이 조절, 가운데를 끌면 구간 이동", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)

        TimelineTrack(clip, current, maxSelectionMs, enabled, visibleStart, viewDuration, preview, ::emit)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ClipRangeEdits.time(visibleStart), color = Muted, fontSize = 12.sp)
            Text(ClipRangeEdits.time(visibleStart + viewDuration), color = Muted, fontSize = 12.sp)
        }
        if (preview == null) Text("소리 파형 준비 중… 구간은 먼저 고를 수 있어요.", color = Muted, fontSize = 12.sp)
        else preview?.notice?.let { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }

        // A full-width 48dp drag surface remains usable even when a 1s selection is very narrow.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .background(PaleSage).testTag("clip-range-move")
            .semantics {
                contentDescription = "선택 구간 이동"
                stateDescription = "${ClipRangeEdits.time(current.startMs)}부터 ${ClipRangeEdits.seconds(current.durationMs)}초"
                if (!enabled) disabled()
                customActions = listOf(
                    CustomAccessibilityAction("0.5초 앞으로 이동") { if (enabled) { shift(-500); true } else false },
                    CustomAccessibilityAction("0.5초 뒤로 이동") { if (enabled) { shift(500); true } else false }
                )
            }.pointerInput(enabled, visibleStart, viewDuration, maxSelectionMs) {
                if (!enabled) return@pointerInput
                var initial = currentRange
                var moved = 0f
                detectHorizontalDragGestures(onDragStart = { initial = currentRange; moved = 0f }) { change, amount ->
                    change.consume(); moved += amount
                    val delta = (moved / size.width.coerceAtLeast(1) * viewDuration / 100f).roundToLong() * 100
                    onChange(ClipRangeEdits.move(initial, delta, clip.durationMs, maxSelectionMs))
                }
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.ChevronLeft, null, Modifier.size(20.dp), tint = Ink)
            Icon(Icons.Outlined.DragHandle, null, Modifier.padding(horizontal = 8.dp).size(20.dp), tint = Ink)
            Text("구간 통째로 옮기기", Modifier.padding(vertical = 12.dp), color = if (enabled) Ink else Muted, fontSize = 14.sp)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = Ink)
        }
        if (clip.durationMs > 5_000) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 4).forEach { factor ->
                    TextButton(onClick = { chooseZoom(factor) }, enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("clip-zoom-$factor"),
                        colors = ButtonDefaults.textButtonColors(contentColor = Ink,
                            containerColor = if (zoom == factor) PaleSage else Color.Transparent)) {
                        Text(if (factor == 1) "전체" else "${factor}배 확대", fontSize = 13.sp)
                    }
                }
            }
        }
        if (zoom > 1 && viewDuration < clip.durationMs) {
            Text("보는 위치 · ${ClipRangeEdits.time(visibleStart)}–${ClipRangeEdits.time(visibleStart + viewDuration)}", color = Muted, fontSize = 12.sp)
            Slider(value = visibleStart.toFloat(), onValueChange = { viewStart = it.roundToLong() },
                valueRange = 0f..(clip.durationMs - viewDuration).toFloat(), enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("clip-timeline-position").semantics { contentDescription = "확대 타임라인의 보는 위치" },
                colors = SliderDefaults.colors(thumbColor = Ink, activeTrackColor = Ink, inactiveTrackColor = Line))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("최대 ${maxSelectionMs / 1_000}초 선택", Modifier.weight(1f), color = Muted, fontSize = 13.sp)
            TextButton(onClick = { fine = !fine }, modifier = Modifier.heightIn(min = 48.dp).testTag("clip-fine-toggle")) {
                Text(if (fine) "미세 조절 접기" else "0.5초씩 맞추기", color = Ink, fontSize = 13.sp)
            }
        }
        if (fine) {
            FineRangeRow("시작", ClipRangeEdits.time(current.startMs), "start", enabled,
                current.startMs > maxOf(0, current.endMs - maxSelectionMs), current.durationMs > 1_000,
                { emit(ClipRangeEdits.start(currentRange, currentRange.startMs - 500, clip.durationMs, maxSelectionMs)) },
                { emit(ClipRangeEdits.start(currentRange, currentRange.startMs + 500, clip.durationMs, maxSelectionMs)) })
            FineRangeRow("끝", ClipRangeEdits.time(current.endMs), "end", enabled,
                current.durationMs > 1_000, current.endMs < minOf(clip.durationMs, current.startMs + maxSelectionMs),
                { emit(ClipRangeEdits.end(currentRange, currentRange.endMs - 500, clip.durationMs, maxSelectionMs)) },
                { emit(ClipRangeEdits.end(currentRange, currentRange.endMs + 500, clip.durationMs, maxSelectionMs)) })
            FineRangeRow("구간 이동", "길이는 그대로", "move", enabled, current.startMs > 0, current.endMs < clip.durationMs,
                { shift(-500) }, { shift(500) })
        }
    }
}

@Composable
private fun TimelineTrack(clip: LocalClip, range: ClipRange, maxMs: Long, enabled: Boolean,
                          viewStart: Long, viewDuration: Long, preview: TimelinePreview?, onChange: (ClipRange) -> Unit) {
    val current by rememberUpdatedState(range)
    val emit by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val handleHit = with(density) { 24.dp.toPx() }
    val frames = remember(preview) { preview?.thumbnails?.map { it.asImageBitmap() }.orEmpty() }
    val envelope = preview?.waveform
    val envelopeMax = remember(envelope) { (envelope?.maxOrNull() ?: 0f).coerceAtLeast(.001f) }

    BoxWithConstraints(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(12.dp))
        .background(PaleSage).testTag("clip-range")) {
        val trackWidth = with(density) { maxWidth.toPx() }
        fun x(ms: Long) = ((ms - viewStart).toFloat() / viewDuration * trackWidth)
        Canvas(Modifier.fillMaxSize().pointerInput(enabled, viewStart, viewDuration, maxMs) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val initial = current
                var moved = 0f
                val left = (initial.startMs - viewStart).toFloat() / viewDuration * size.width
                val right = (initial.endMs - viewStart).toFloat() / viewDuration * size.width
                val toLeft = abs(down.position.x - left); val toRight = abs(down.position.x - right)
                // Decide at pointer-down, before touch slop can cross into another handle.
                val target = when {
                    toLeft <= handleHit && toLeft <= toRight -> -1
                    toRight <= handleHit -> 1
                    else -> 0
                }
                fun update(amount: Float) {
                    moved += amount
                    val delta = (moved / size.width.coerceAtLeast(1) * viewDuration / 100f).roundToLong() * 100
                    emit(when (target) {
                        -1 -> ClipRangeEdits.start(initial, initial.startMs + delta, clip.durationMs, maxMs)
                        1 -> ClipRangeEdits.end(initial, initial.endMs + delta, clip.durationMs, maxMs)
                        else -> ClipRangeEdits.move(initial, delta, clip.durationMs, maxMs)
                    })
                }
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume(); update(over)
                }
                if (drag != null) horizontalDrag(drag.id) { change ->
                    update(change.position.x - change.previousPosition.x); change.consume()
                }
            }
        }) {
            val thumbnailHeight = if (clip.hasVideo) 62.dp.toPx() else 0f
            if (frames.isNotEmpty()) {
                clipRect {
                    val tileWidth = clip.durationMs.toFloat() / frames.size / viewDuration * size.width
                    frames.forEachIndexed { index, frame ->
                        val left = (clip.durationMs.toFloat() * index / frames.size - viewStart) / viewDuration * size.width
                        if (left < size.width && left + tileWidth > 0) {
                            // Center-crop each real frame to its corresponding time cell.
                            val aspect = tileWidth / thumbnailHeight
                            val sourceWidth = minOf(frame.width, (frame.height * aspect).roundToInt()).coerceAtLeast(1)
                            val sourceHeight = minOf(frame.height, (frame.width / aspect).roundToInt()).coerceAtLeast(1)
                            drawImage(frame, srcOffset = IntOffset((frame.width - sourceWidth) / 2, (frame.height - sourceHeight) / 2),
                                srcSize = IntSize(sourceWidth, sourceHeight), dstOffset = IntOffset(left.roundToInt(), 0),
                                dstSize = IntSize(tileWidth.roundToInt().coerceAtLeast(1), thumbnailHeight.roundToInt()))
                        }
                    }
                }
            }
            val waveCenter = thumbnailHeight + (size.height - thumbnailHeight) / 2
            drawLine(Line, Offset(0f, waveCenter), Offset(size.width, waveCenter), 1.dp.toPx())
            if (envelope != null) {
                val first = (viewStart / 100).toInt().coerceAtLeast(0)
                val last = minOf(envelope.lastIndex, ((viewStart + viewDuration) / 100).toInt())
                val step = maxOf(1, ((last - first) / (size.width / 3.dp.toPx()).coerceAtLeast(1f)).toInt())
                for (index in first..last step step) {
                    val position = ((index * 100L - viewStart).toFloat() / viewDuration) * size.width
                    var amplitude = 0f
                    for (bucket in index until minOf(index + step, envelope.size)) amplitude = maxOf(amplitude, envelope[bucket])
                    val height = (amplitude / envelopeMax).coerceIn(0f, 1f) * (size.height - thumbnailHeight - 12.dp.toPx()) / 2
                    if (height > .2f) drawLine(Ink.copy(alpha = .72f), Offset(position, waveCenter - height),
                        Offset(position, waveCenter + height), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            val left = x(range.startMs); val right = x(range.endMs)
            drawRect(Ink.copy(alpha = .26f), size = Size(left.coerceIn(0f, size.width), size.height))
            val dimRight = right.coerceIn(0f, size.width)
            drawRect(Ink.copy(alpha = .26f), topLeft = Offset(dimRight, 0f), size = Size(size.width - dimRight, size.height))
            clipRect {
                drawRoundRect(Ink, topLeft = Offset(left, 2.dp.toPx()), size = Size(right - left, size.height - 4.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
                listOf(left, right).forEach { edge ->
                    drawRoundRect(Ink, Offset(edge - 7.dp.toPx(), 0f), Size(14.dp.toPx(), size.height), CornerRadius(5.dp.toPx()))
                    drawLine(Color.White, Offset(edge, size.height / 2 - 10.dp.toPx()), Offset(edge, size.height / 2 + 10.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        // Separate adjustable accessibility nodes: TalkBack does not need a precision drag.
        listOf(true, false).forEach { isStart ->
            val value = if (isStart) range.startMs else range.endMs
            val lower = if (isStart) maxOf(0, range.endMs - maxMs) else range.startMs + 1_000
            val upper = if (isStart) range.endMs - 1_000 else minOf(clip.durationMs, range.startMs + maxMs)
            Box(Modifier.offset { IntOffset((x(value) - handleHit).roundToInt().coerceIn(0, (trackWidth - handleHit * 2).coerceAtLeast(0f).roundToInt()), 0) }
                .width(48.dp).fillMaxHeight().testTag(if (isStart) "clip-range-start" else "clip-range-end")
                .semantics {
                    contentDescription = if (isStart) "선택 시작" else "선택 끝"
                    stateDescription = ClipRangeEdits.time(value)
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), lower.toFloat()..upper.toFloat())
                    if (!enabled) disabled()
                    setProgress { proposed ->
                        if (!enabled || !proposed.isFinite()) false
                        else { emit(if (isStart) ClipRangeEdits.start(current, proposed.roundToLong(), clip.durationMs, maxMs)
                            else ClipRangeEdits.end(current, proposed.roundToLong(), clip.durationMs, maxMs)); true }
                    }
                })
        }
    }
}

@Composable
private fun FineRangeRow(label: String, value: String, tag: String, enabled: Boolean,
                         canDecrease: Boolean, canIncrease: Boolean, decrease: () -> Unit, increase: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(value, color = Muted, fontSize = 13.sp)
        }
        IconButton(onClick = decrease, enabled = enabled && canDecrease,
            modifier = Modifier.size(48.dp).testTag("clip-$tag-minus")) {
            Icon(Icons.Outlined.Remove, "$label 0.5초 앞으로", tint = if (enabled && canDecrease) Ink else Muted)
        }
        IconButton(onClick = increase, enabled = enabled && canIncrease,
            modifier = Modifier.size(48.dp).testTag("clip-$tag-plus")) {
            Icon(Icons.Outlined.Add, "$label 0.5초 뒤로", tint = if (enabled && canIncrease) Ink else Muted)
        }
    }
}
