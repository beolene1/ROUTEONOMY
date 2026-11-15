package com.routeonomy.app.domain

/** Speech output contract. Each [speak] carries a fresh utterance id; completion is reported back with that id. */
interface SpeechOutput {
    fun speak(utteranceId: Int, text: String)
    fun stop()
}

/**
 * Briefing playback of one drive (F3/F4/F5/F7): queue, current sentence, play history, finished segments
 * and the single A→B region switch. Android-free; the speech engine is reached only through [SpeechOutput].
 *
 * Completion is accepted only for the utterance id spoken last, while that sentence is playing, so a late
 * or duplicate completion of a stopped sentence cannot finish the next one.
 */
class BriefingSession @JvmOverloads constructor(
    private val segments: List<BriefingSegment>,
    private val regions: List<Region>,
    private val destination: GeoPoint,
    private var location: GeoPoint,
    private var etaMinutes: Int,
    private val speech: SpeechOutput,
    private val clock: () -> Long,
    private val planner: BriefingPlanner = BriefingPlanner(),
) {
    enum class State { IDLE, PLAYING, PAUSED, WAITING_FOR_REGION, FINISHED, NOTHING_LEFT, ERROR }

    private val regionsWithContent = segments.mapNotNullTo(HashSet()) { it.regionId }
    private val playedIds = HashSet<String>()
    private val completedIds = LinkedHashSet<String>()
    /** Segments in the order they started playing; "이전 주제" steps back through it. */
    private val playHistory = ArrayList<String>()
    private val regionTransition = RegionTransition()
    private var plan = BriefingPlan(emptyList())
    private var prediction: RegionPrediction? = null
    private var regionSectionId: String? = null
    private var pendingAnnouncement: String? = null
    private var preferredFirstId: String? = null
    private var currentSegmentId: String? = null
    private var chunks: List<String> = emptyList()
    private var chunkIndex = 0
    private var utteranceId = 0
    private var chunkStartedAt = 0L
    private var studyMillis = 0L
    private var started = false
    private var active = false
    private var paused = false
    private var waitingForRegion = false
    private var finished = false
    private var nothingLeft = false
    private var failed = false
    private var speechReady = false
    private var autoplayHeld = false
    private var completionListener: ((BriefingSegment) -> Unit)? = null
    private var segmentGapListener: ((Int) -> Unit)? = null
    // Short pause (with a chime, played by the screen) after a segment heard to the end, before the next one.
    private var inGap = false
    private var gapToken = 0

    init {
        updatePrediction()
        replan()
    }

    // ---- read-only state for the screen ----

    fun state(): State = when {
        active -> State.PLAYING
        paused -> State.PAUSED
        waitingForRegion -> State.WAITING_FOR_REGION
        failed -> State.ERROR
        finished -> State.FINISHED
        nothingLeft && plan.segments.isEmpty() -> State.NOTHING_LEFT
        else -> State.IDLE
    }
    fun isPlaying(): Boolean = active
    fun isWaitingForRegion(): Boolean = waitingForRegion
    fun hasStarted(): Boolean = started
    fun currentSegment(): BriefingSegment? = plan.segments.firstOrNull()
    fun completedSegmentIds(): Set<String> = completedIds
    fun studyMillis(): Long = studyMillis
    fun prediction(): RegionPrediction? = prediction
    fun regionSectionId(): String? = regionSectionId
    fun enteredRegionId(): String? = regionTransition.enteredRegionId()
    fun hasPendingRegionSwitch(): Boolean = regionTransition.hasPendingSwitch()

    // ---- inputs ----

    fun setSpeechReady(ready: Boolean) { speechReady = ready }

    /** Called once per segment, the first time it is heard to the end (e.g. to give points). */
    fun setCompletionListener(listener: ((BriefingSegment) -> Unit)?) { completionListener = listener }

    /**
     * Called with a token when a segment was heard to the end and another follows. Nothing plays until
     * [finishGap] with that token (the screen plays a chime and waits a few seconds). Without a listener
     * the next segment starts right away.
     */
    fun setSegmentGapListener(listener: ((Int) -> Unit)?) { segmentGapListener = listener }

    /** True between two segments, while the chime pause runs. */
    fun isInGap(): Boolean = inGap

    /** Ends the pause started with [token]; ignored when playback was paused or moved on meanwhile. */
    fun finishGap(token: Int) {
        if (!inGap || token != gapToken) return
        endGap()
        speakCurrent()
    }

    private fun endGap() {
        inGap = false
        active = false
    }

    /** While held (e.g. the home screen is shown) a region entry or new estimate does not start audio by itself. */
    fun setAutoplayHeld(held: Boolean) { autoplayHeld = held }

    /** Stores a position / remaining-time update; call [refreshRoute] or [onRegionEntered] afterwards. */
    fun updatePosition(point: GeoPoint, minutes: Int) {
        location = point
        etaMinutes = minutes
    }

    /** Recomputes the prediction and queue after a position or remaining-time change. */
    fun refreshRoute() {
        updatePrediction()
        replan()
        if (waitingForRegion && !active && !paused && (prediction == null || plan.segments.isNotEmpty())) {
            // Passed the expected region, or the new estimate leaves room for more A segments: resume.
            waitingForRegion = false
            if (mayAutoplay()) speakCurrent()
        }
    }

    /**
     * Accepted region entry. Returns true when the switch was applied now (nothing was playing);
     * while a segment plays or is paused the switch waits until that segment ends.
     */
    fun onRegionEntered(regionId: String): Boolean {
        if (!regionTransition.onRegionEntered(regionId)) return false
        if (active || paused) return false
        regionTransition.consumePendingSwitch()
        startRegionSection(regionId)
        replan()
        if (mayAutoplay()) speakCurrent()
        return true
    }

    // ---- user commands ----

    /** Play / pause button. Ignored until speech is ready. */
    fun togglePlayback() {
        if (!speechReady) return
        when {
            paused -> resume()
            active -> pause()
            else -> speakCurrent()
        }
    }

    fun pause() {
        if (inGap) { endGap(); return } // play starts the next segment from its beginning
        if (!active) return
        paused = true
        active = false
        speech.stop()
    }

    /** Start button on the dashboard: resumes a paused sentence, else starts with the banner topic. */
    fun startFromDashboard() {
        if (!speechReady) return
        if (inGap) { finishGap(gapToken); return }
        if (paused) { resume(); return }
        // The banner topic plays first, whatever the remaining time; only before the first start.
        val picked = dashboardTopic()
        if (!started && picked != null) { preferredFirstId = picked.id; replan() }
        speakCurrent()
    }

    /** "다음 주제": skips the topic being played; before anything started it does nothing. */
    fun nextTopic() {
        if (inGap) { finishGap(gapToken); return } // skip the pause, not the next topic
        if (currentSegmentId == null) return
        advance(completed = false)
    }

    /**
     * "이전 주제": replays the topic started before the current one from its start; the interrupted
     * topic goes back to the queue. With no earlier topic, restarts the current one.
     */
    fun previousTopic() {
        if (!speechReady || playHistory.isEmpty()) return
        if (inGap) endGap()
        val onCurrent = playHistory.last() == currentSegmentId
        if (onCurrent && playHistory.size == 1) {
            active = false
            speech.stop()
            chunkIndex = 0
            paused = false
            speakChunk()
            return
        }
        if (onCurrent) playHistory.removeAt(playHistory.lastIndex) // interrupted: it will be planned again
        val previous = playHistory.removeAt(playHistory.lastIndex)
        speech.stop()
        active = false
        paused = false
        currentSegmentId?.let(playedIds::remove)
        currentSegmentId = null
        playedIds.remove(previous)
        waitingForRegion = false
        preferredFirstId = previous // replan puts it first even outside the time budget
        replan()
        speakCurrent()
    }

    // ---- dashboard topic banner ----

    /** Every MAIN topic not heard yet, in content order; independent of location and remaining time. */
    fun dashboardTopics(): List<BriefingSegment> = segments.filter {
        it.type == BriefingSegment.Type.MAIN && (it.id !in playedIds || it.id == currentSegmentId)
    }

    /** After the first start the plan head; before it the banner pick or the first page. */
    fun dashboardTopic(): BriefingSegment? {
        if (started) return plan.segments.firstOrNull()
        val topics = dashboardTopics()
        return topics.firstOrNull { it.id == preferredFirstId } ?: topics.firstOrNull()
    }

    fun canChangeDashboardTopic(): Boolean = !started && dashboardTopics().size > 1

    /** Topic picked in the Change topic popup; it plays first on start. */
    fun chooseDashboardTopic(id: String) {
        if (!canChangeDashboardTopic() || dashboardTopics().none { it.id == id }) return
        preferredFirstId = id
        replan()
    }

    /** Banner swipe / Change topic: picks the next (+1) or previous (-1) page; it plays first on start. */
    fun shiftDashboardTopic(delta: Int) {
        if (!canChangeDashboardTopic()) return
        val topics = dashboardTopics()
        val index = topics.indexOf(dashboardTopic()).coerceAtLeast(0)
        preferredFirstId = topics[Math.floorMod(index + delta, topics.size)].id
        replan()
    }

    // ---- speech callbacks ----

    fun onUtteranceDone(id: Int) {
        if (id != utteranceId || !active || paused || currentSegmentId == null) return
        studyMillis += clock() - chunkStartedAt
        if (chunkIndex + 1 < chunks.size) { chunkIndex++; speakChunk() }
        else advance(completed = true)
    }

    /** The sentence failed; play restarts the current segment from its first sentence. */
    fun onUtteranceError(id: Int) {
        if (id != utteranceId || !active) return
        active = false
        failed = true
    }

    // ---- internals ----

    private fun mayAutoplay() = started && !autoplayHeld && speechReady && plan.segments.isNotEmpty()

    private fun resume() {
        paused = false
        speakChunk()
    }

    private fun advance(completed: Boolean) {
        val previous = plan.segments.firstOrNull() ?: return
        speech.stop()
        if (completed && completedIds.add(previous.id)) completionListener?.invoke(previous)
        playedIds += previous.id
        active = false
        paused = false
        currentSegmentId = null
        val switchTo = regionTransition.consumePendingSwitch()
        if (switchTo != null) startRegionSection(switchTo) else updatePrediction()
        replan()
        if (plan.segments.isNotEmpty()) {
            val gap = segmentGapListener
            if (completed && gap != null) {
                inGap = true
                active = true // still "playing" for the screen and for region entry, which waits for the pause
                gap(++gapToken)
            } else speakCurrent()
            return
        }
        if (regionSectionId == null && prediction != null) waitingForRegion = true
        else finished = true
    }

    private fun speakCurrent() {
        if (!speechReady) return
        val requestedFirst = preferredFirstId // e.g. "이전 주제": must survive a pending region switch
        preferredFirstId = null
        val switchTo = regionTransition.consumePendingSwitch()
        if (switchTo != null) {
            preferredFirstId = requestedFirst
            // Playback stopped (e.g. speech error) while a switch was pending: drop the interrupted A segment.
            currentSegmentId?.let(playedIds::add)
            currentSegmentId = null
            startRegionSection(switchTo)
            replan()
            preferredFirstId = null
        }
        val segment = plan.segments.firstOrNull()
        if (segment == null) {
            if (regionSectionId == null && prediction != null) {
                waitingForRegion = true
                started = true
            } else nothingLeft = true
            return
        }
        waitingForRegion = false
        currentSegmentId = segment.id
        if (playHistory.lastOrNull() != segment.id) playHistory += segment.id
        chunks = splitSentences(segment.script).let { sentences ->
            pendingAnnouncement?.let { listOf(it) + sentences } ?: sentences
        }
        pendingAnnouncement = null
        chunkIndex = 0
        speakChunk()
    }

    private fun speakChunk() {
        if (currentSegmentId == null || chunks.isEmpty() || !speechReady) return
        paused = false
        active = true
        started = true
        finished = false
        nothingLeft = false
        failed = false
        chunkStartedAt = clock()
        speech.speak(++utteranceId, chunks[chunkIndex])
    }

    private fun updatePrediction() {
        prediction = if (regionTransition.enteredRegionId() != null) null
        else RegionPredictor.predict(location, destination, etaMinutes, regions, regionsWithContent)
    }

    private fun startRegionSection(regionId: String) {
        regionSectionId = regionId
        prediction = null
        waitingForRegion = false
        val name = regions.firstOrNull { it.id == regionId }?.name ?: regionId
        pendingAnnouncement = "지금 ${name}에 들어섰습니다. 이 지역과 연결된 이야기를 들려드릴게요."
    }

    /** Replans unplayed segments for the current section, keeping a playing or paused segment at the head. */
    private fun replan() {
        val head = plan.segments.firstOrNull()?.takeIf { it.id == currentSegmentId }
        val exclude = if (head == null) playedIds else playedIds + head.id
        val section = regionSectionId
        val predicted = prediction
        val next = when {
            section != null -> planner.planRegion(etaMinutes, section, segments, exclude)
            predicted != null -> planner.planBeforeRegion(predicted.estimatedMinutesToRegion, segments, exclude)
            else -> planner.plan(etaMinutes, segments, exclude)
        }
        val queue = ArrayList<BriefingSegment>()
        head?.let(queue::add)
        queue += next.segments
        val preferred = preferredFirstId
        if (head == null && preferred != null && preferred !in playedIds) {
            // Keep the picked topic first until playback starts, even when the time budget left it out.
            val index = queue.indexOfFirst { it.id == preferred }
            if (index >= 0) queue.add(0, queue.removeAt(index))
            else segments.firstOrNull { it.id == preferred }?.let { queue.add(0, it) }
        }
        plan = BriefingPlan(queue)
    }

    companion object {
        /** Sentence split used for speech and the Now Playing hook line. */
        @JvmStatic
        fun splitSentences(script: String?): List<String> {
            if (script == null) return emptyList()
            val parts = script.split(Regex("(?<=[.!?。！？])\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
            return parts.ifEmpty { listOfNotNull(script.trim().takeIf { it.isNotEmpty() }) }
        }
    }
}
