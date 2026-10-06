package com.moa.babylanguage

import android.app.ActivityManager
import android.content.Context
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.exp

/** Provisional AST candidate runtime. No decision threshold or infant meaning is assigned here. */
internal object AstAudioAnalyzer {
    private val inferenceLock = Mutex()
    private const val MAX_RUN_MS = 120_000L
    private const val MIN_AVAILABLE_BYTES = 640L * 1024 * 1024
    private val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "ast-native-cancellation").apply { isDaemon = true }
    }

    suspend fun analyze(context: Context, rawPcm: FloatArray): AstAudioAnalysis = withContext(Dispatchers.Default) {
        inferenceLock.withLock {
            coroutineContext.ensureActive()
            if (rawPcm.size !in 16_000..320_000 || rawPcm.any { !it.isFinite() || it < -1f || it > 1f })
                return@withLock AstAudioAnalysis(emptyList(), "AST requires 1..20 seconds of finite normalized 16kHz mono PCM")
            val job = coroutineContext[Job]!!
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_RUN_MS)
            fun checkActive() {
                job.ensureActive()
                if (System.nanoTime() >= deadline) throw AstDeadlineException()
            }
            try {
                val memory = ActivityManager.MemoryInfo()
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@withLock AstAudioAnalysis(emptyList(), "AST memory status unavailable")
                manager.getMemoryInfo(memory)
                // Host peak was ~563MB; this conservative guard needs ARM measurement.
                if (memory.lowMemory || memory.availMem < MIN_AVAILABLE_BYTES)
                    return@withLock AstAudioAnalysis(emptyList(), "AST needs more available device memory")
                val model = AstModelStore.resolve(context.applicationContext, ::checkActive)
                checkActive()
                // ORT's process environment is a singleton; its close() is documented as a no-op.
                val environment = OrtEnvironment.getEnvironment()
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2)
                    options.setInterOpNumThreads(1)
                    options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                    options.setMemoryPatternOptimization(false)
                    options.setCPUArenaAllocator(false)
                    environment.createSession(model.absolutePath, options).use { session ->
                        checkActive()
                        validateGraph(session)
                        val windows = mutableListOf<AstAudioWindow>()
                        for ((start, end) in windowRanges(rawPcm.size)) {
                            checkActive()
                            val slice = rawPcm.copyOfRange(start, end)
                            var windowLevel: Double? = null
                            val features = try {
                                if (BuildConfig.AST_INFANT_PRESENCE) {
                                    val cleaned = AudioCleanup.filter(slice)
                                    try { windowLevel = AudioCleanup.strongestSecondRms(cleaned) }
                                    finally { cleaned.fill(0f) }
                                }
                                checkActive()
                                AstFeatures.extract(slice, ::checkActive)
                            } finally { slice.fill(0f) }
                            val (logits, presence) = try {
                                val buffer = ByteBuffer.allocateDirect(features.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                                buffer.put(features).rewind()
                                try { OnnxTensor.createTensor(environment, buffer, longArrayOf(1, 1024, 128)).use { input ->
                                    NativeRunGuard(job, deadline).use { guard ->
                                        val outputs = if (BuildConfig.AST_INFANT_PRESENCE) setOf("logits", "infant_presence") else setOf("logits")
                                        session.run(mapOf("input_values" to input), outputs, guard.options).use { result ->
                                            checkActive()
                                            val output = result.get("logits").orElseThrow { IllegalStateException("AST output missing") } as? OnnxTensor
                                                ?: error("AST output is not a tensor")
                                            val values = output.floatBuffer
                                            check(values.remaining() == 527) { "AST output length mismatch" }
                                            val presenceScore = if (BuildConfig.AST_INFANT_PRESENCE) {
                                                val tensor = result.get("infant_presence").orElseThrow {
                                                    IllegalStateException("AST infant output missing") } as? OnnxTensor
                                                    ?: error("AST infant output is not a tensor")
                                                val scores = tensor.floatBuffer
                                                check(scores.remaining() == 1) { "AST infant output length mismatch" }
                                                scores.get().also { check(it.isFinite() && it in 0f..1f) { "AST infant output invalid" } }
                                            } else null
                                            FloatArray(527).also { values.get(it) } to presenceScore
                                        }
                                    }
                                } } finally {
                                    buffer.clear()
                                    while (buffer.hasRemaining()) buffer.put(0f)
                                }
                            } finally { features.fill(0f) }
                            checkActive()
                            val scores = try { scoresFromLogits(logits) } finally { logits.fill(0f) }
                            windows += AstAudioWindow(start * 1000L / 16000, end * 1000L / 16000, scores, presence, windowLevel)
                        }
                        checkActive()
                        AstAudioAnalysis(windows)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                job.ensureActive()
                AstAudioAnalysis(emptyList(), "AST device memory exhausted")
            } catch (_: LinkageError) {
                job.ensureActive()
                AstAudioAnalysis(emptyList(), "AST native runtime unavailable on this device")
            } catch (_: Exception) {
                job.ensureActive()
                AstAudioAnalysis(emptyList(), if (System.nanoTime() >= deadline) "AST analysis exceeded its time limit" else "AST model or analysis unavailable")
            }
        }
    }

    internal fun windowRanges(sampleCount: Int): List<Pair<Int, Int>> {
        require(sampleCount in 16_000..320_000)
        if (sampleCount <= 160_000) return listOf(0 to sampleCount)
        val starts = (0..sampleCount - 160_000 step 80_000).toMutableList()
        if (starts.last() != sampleCount - 160_000) starts += sampleCount - 160_000
        return starts.map { it to it + 160_000 }
    }

    internal fun scoresFromLogits(logits: FloatArray): Map<String, Float> {
        require(logits.size == 527 && logits.all { it.isFinite() }) { "AST logits invalid" }
        return AstLabels.values.mapIndexed { index, label ->
            val x = logits[index].toDouble()
            val score = if (x >= 0) 1.0 / (1.0 + exp(-x)) else exp(x) / (1.0 + exp(x))
            label to score.toFloat()
        }.toMap()
    }

    private fun validateGraph(session: OrtSession) {
        val expectedOutputs = if (BuildConfig.AST_INFANT_PRESENCE) setOf("logits", "infant_presence",
            "/source/classifier/layernorm/LayerNormalization_output_0") else setOf("logits")
        check(session.inputNames == setOf("input_values") && session.outputNames == expectedOutputs)
        val input = session.inputInfo.getValue("input_values").info as? TensorInfo ?: error("AST input invalid")
        val output = session.outputInfo.getValue("logits").info as? TensorInfo ?: error("AST output invalid")
        check(input.type == OnnxJavaType.FLOAT && input.shape.contentEquals(longArrayOf(1, 1024, 128)))
        check(output.type == OnnxJavaType.FLOAT && output.shape.contentEquals(longArrayOf(1, 527)))
        if (BuildConfig.AST_INFANT_PRESENCE) {
            val presence = session.outputInfo.getValue("infant_presence").info as? TensorInfo ?: error("AST infant output invalid")
            val embedding = session.outputInfo.getValue("/source/classifier/layernorm/LayerNormalization_output_0").info as? TensorInfo
                ?: error("AST representation output invalid")
            check(presence.type == OnnxJavaType.FLOAT && presence.shape.contentEquals(longArrayOf(1, 1)))
            check(embedding.type == OnnxJavaType.FLOAT && embedding.shape.contentEquals(longArrayOf(1, 768)))
        }
    }

    /** The monitor only sets terminate. The inference owner closes handles after run returns.
     * synchronized prevents the timer from racing native RunOptions.close(). */
    internal class NativeRunGuard(job: Job, deadline: Long) : AutoCloseable {
        val options = OrtSession.RunOptions()
        private var closed = false
        // Check the original absolute deadline without replaying missed polls after
        // Android resumes a cached process. The owner still closes native handles.
        private val monitor = watchdog.scheduleWithFixedDelay({
            synchronized(this) {
                if (!closed && (!job.isActive || System.nanoTime() >= deadline)) {
                    try { options.setTerminate(true) } catch (_: Exception) { /* owner reports run failure */ }
                }
            }
        }, 0, 20, TimeUnit.MILLISECONDS)

        override fun close() = synchronized(this) {
            if (!closed) {
                closed = true
                monitor.cancel(false)
                options.close()
            }
        }
    }

    private class AstDeadlineException : Exception()
}

// Labels pinned to config SHA256 a93d525511d77e8ecc933d09674b85099815bbbb417c228a4edd655e252fb9ff.
private object AstLabels {
    val values = arrayOf(
        "Speech",
        "Male speech, man speaking",
        "Female speech, woman speaking",
        "Child speech, kid speaking",
        "Conversation",
        "Narration, monologue",
        "Babbling",
        "Speech synthesizer",
        "Shout",
        "Bellow",
        "Whoop",
        "Yell",
        "Battle cry",
        "Children shouting",
        "Screaming",
        "Whispering",
        "Laughter",
        "Baby laughter",
        "Giggle",
        "Snicker",
        "Belly laugh",
        "Chuckle, chortle",
        "Crying, sobbing",
        "Baby cry, infant cry",
        "Whimper",
        "Wail, moan",
        "Sigh",
        "Singing",
        "Choir",
        "Yodeling",
        "Chant",
        "Mantra",
        "Male singing",
        "Female singing",
        "Child singing",
        "Synthetic singing",
        "Rapping",
        "Humming",
        "Groan",
        "Grunt",
        "Whistling",
        "Breathing",
        "Wheeze",
        "Snoring",
        "Gasp",
        "Pant",
        "Snort",
        "Cough",
        "Throat clearing",
        "Sneeze",
        "Sniff",
        "Run",
        "Shuffle",
        "Walk, footsteps",
        "Chewing, mastication",
        "Biting",
        "Gargling",
        "Stomach rumble",
        "Burping, eructation",
        "Hiccup",
        "Fart",
        "Hands",
        "Finger snapping",
        "Clapping",
        "Heart sounds, heartbeat",
        "Heart murmur",
        "Cheering",
        "Applause",
        "Chatter",
        "Crowd",
        "Hubbub, speech noise, speech babble",
        "Children playing",
        "Animal",
        "Domestic animals, pets",
        "Dog",
        "Bark",
        "Yip",
        "Howl",
        "Bow-wow",
        "Growling",
        "Whimper (dog)",
        "Cat",
        "Purr",
        "Meow",
        "Hiss",
        "Caterwaul",
        "Livestock, farm animals, working animals",
        "Horse",
        "Clip-clop",
        "Neigh, whinny",
        "Cattle, bovinae",
        "Moo",
        "Cowbell",
        "Pig",
        "Oink",
        "Goat",
        "Bleat",
        "Sheep",
        "Fowl",
        "Chicken, rooster",
        "Cluck",
        "Crowing, cock-a-doodle-doo",
        "Turkey",
        "Gobble",
        "Duck",
        "Quack",
        "Goose",
        "Honk",
        "Wild animals",
        "Roaring cats (lions, tigers)",
        "Roar",
        "Bird",
        "Bird vocalization, bird call, bird song",
        "Chirp, tweet",
        "Squawk",
        "Pigeon, dove",
        "Coo",
        "Crow",
        "Caw",
        "Owl",
        "Hoot",
        "Bird flight, flapping wings",
        "Canidae, dogs, wolves",
        "Rodents, rats, mice",
        "Mouse",
        "Patter",
        "Insect",
        "Cricket",
        "Mosquito",
        "Fly, housefly",
        "Buzz",
        "Bee, wasp, etc.",
        "Frog",
        "Croak",
        "Snake",
        "Rattle",
        "Whale vocalization",
        "Music",
        "Musical instrument",
        "Plucked string instrument",
        "Guitar",
        "Electric guitar",
        "Bass guitar",
        "Acoustic guitar",
        "Steel guitar, slide guitar",
        "Tapping (guitar technique)",
        "Strum",
        "Banjo",
        "Sitar",
        "Mandolin",
        "Zither",
        "Ukulele",
        "Keyboard (musical)",
        "Piano",
        "Electric piano",
        "Organ",
        "Electronic organ",
        "Hammond organ",
        "Synthesizer",
        "Sampler",
        "Harpsichord",
        "Percussion",
        "Drum kit",
        "Drum machine",
        "Drum",
        "Snare drum",
        "Rimshot",
        "Drum roll",
        "Bass drum",
        "Timpani",
        "Tabla",
        "Cymbal",
        "Hi-hat",
        "Wood block",
        "Tambourine",
        "Rattle (instrument)",
        "Maraca",
        "Gong",
        "Tubular bells",
        "Mallet percussion",
        "Marimba, xylophone",
        "Glockenspiel",
        "Vibraphone",
        "Steelpan",
        "Orchestra",
        "Brass instrument",
        "French horn",
        "Trumpet",
        "Trombone",
        "Bowed string instrument",
        "String section",
        "Violin, fiddle",
        "Pizzicato",
        "Cello",
        "Double bass",
        "Wind instrument, woodwind instrument",
        "Flute",
        "Saxophone",
        "Clarinet",
        "Harp",
        "Bell",
        "Church bell",
        "Jingle bell",
        "Bicycle bell",
        "Tuning fork",
        "Chime",
        "Wind chime",
        "Change ringing (campanology)",
        "Harmonica",
        "Accordion",
        "Bagpipes",
        "Didgeridoo",
        "Shofar",
        "Theremin",
        "Singing bowl",
        "Scratching (performance technique)",
        "Pop music",
        "Hip hop music",
        "Beatboxing",
        "Rock music",
        "Heavy metal",
        "Punk rock",
        "Grunge",
        "Progressive rock",
        "Rock and roll",
        "Psychedelic rock",
        "Rhythm and blues",
        "Soul music",
        "Reggae",
        "Country",
        "Swing music",
        "Bluegrass",
        "Funk",
        "Folk music",
        "Middle Eastern music",
        "Jazz",
        "Disco",
        "Classical music",
        "Opera",
        "Electronic music",
        "House music",
        "Techno",
        "Dubstep",
        "Drum and bass",
        "Electronica",
        "Electronic dance music",
        "Ambient music",
        "Trance music",
        "Music of Latin America",
        "Salsa music",
        "Flamenco",
        "Blues",
        "Music for children",
        "New-age music",
        "Vocal music",
        "A capella",
        "Music of Africa",
        "Afrobeat",
        "Christian music",
        "Gospel music",
        "Music of Asia",
        "Carnatic music",
        "Music of Bollywood",
        "Ska",
        "Traditional music",
        "Independent music",
        "Song",
        "Background music",
        "Theme music",
        "Jingle (music)",
        "Soundtrack music",
        "Lullaby",
        "Video game music",
        "Christmas music",
        "Dance music",
        "Wedding music",
        "Happy music",
        "Funny music",
        "Sad music",
        "Tender music",
        "Exciting music",
        "Angry music",
        "Scary music",
        "Wind",
        "Rustling leaves",
        "Wind noise (microphone)",
        "Thunderstorm",
        "Thunder",
        "Water",
        "Rain",
        "Raindrop",
        "Rain on surface",
        "Stream",
        "Waterfall",
        "Ocean",
        "Waves, surf",
        "Steam",
        "Gurgling",
        "Fire",
        "Crackle",
        "Vehicle",
        "Boat, Water vehicle",
        "Sailboat, sailing ship",
        "Rowboat, canoe, kayak",
        "Motorboat, speedboat",
        "Ship",
        "Motor vehicle (road)",
        "Car",
        "Vehicle horn, car horn, honking",
        "Toot",
        "Car alarm",
        "Power windows, electric windows",
        "Skidding",
        "Tire squeal",
        "Car passing by",
        "Race car, auto racing",
        "Truck",
        "Air brake",
        "Air horn, truck horn",
        "Reversing beeps",
        "Ice cream truck, ice cream van",
        "Bus",
        "Emergency vehicle",
        "Police car (siren)",
        "Ambulance (siren)",
        "Fire engine, fire truck (siren)",
        "Motorcycle",
        "Traffic noise, roadway noise",
        "Rail transport",
        "Train",
        "Train whistle",
        "Train horn",
        "Railroad car, train wagon",
        "Train wheels squealing",
        "Subway, metro, underground",
        "Aircraft",
        "Aircraft engine",
        "Jet engine",
        "Propeller, airscrew",
        "Helicopter",
        "Fixed-wing aircraft, airplane",
        "Bicycle",
        "Skateboard",
        "Engine",
        "Light engine (high frequency)",
        "Dental drill, dentist's drill",
        "Lawn mower",
        "Chainsaw",
        "Medium engine (mid frequency)",
        "Heavy engine (low frequency)",
        "Engine knocking",
        "Engine starting",
        "Idling",
        "Accelerating, revving, vroom",
        "Door",
        "Doorbell",
        "Ding-dong",
        "Sliding door",
        "Slam",
        "Knock",
        "Tap",
        "Squeak",
        "Cupboard open or close",
        "Drawer open or close",
        "Dishes, pots, and pans",
        "Cutlery, silverware",
        "Chopping (food)",
        "Frying (food)",
        "Microwave oven",
        "Blender",
        "Water tap, faucet",
        "Sink (filling or washing)",
        "Bathtub (filling or washing)",
        "Hair dryer",
        "Toilet flush",
        "Toothbrush",
        "Electric toothbrush",
        "Vacuum cleaner",
        "Zipper (clothing)",
        "Keys jangling",
        "Coin (dropping)",
        "Scissors",
        "Electric shaver, electric razor",
        "Shuffling cards",
        "Typing",
        "Typewriter",
        "Computer keyboard",
        "Writing",
        "Alarm",
        "Telephone",
        "Telephone bell ringing",
        "Ringtone",
        "Telephone dialing, DTMF",
        "Dial tone",
        "Busy signal",
        "Alarm clock",
        "Siren",
        "Civil defense siren",
        "Buzzer",
        "Smoke detector, smoke alarm",
        "Fire alarm",
        "Foghorn",
        "Whistle",
        "Steam whistle",
        "Mechanisms",
        "Ratchet, pawl",
        "Clock",
        "Tick",
        "Tick-tock",
        "Gears",
        "Pulleys",
        "Sewing machine",
        "Mechanical fan",
        "Air conditioning",
        "Cash register",
        "Printer",
        "Camera",
        "Single-lens reflex camera",
        "Tools",
        "Hammer",
        "Jackhammer",
        "Sawing",
        "Filing (rasp)",
        "Sanding",
        "Power tool",
        "Drill",
        "Explosion",
        "Gunshot, gunfire",
        "Machine gun",
        "Fusillade",
        "Artillery fire",
        "Cap gun",
        "Fireworks",
        "Firecracker",
        "Burst, pop",
        "Eruption",
        "Boom",
        "Wood",
        "Chop",
        "Splinter",
        "Crack",
        "Glass",
        "Chink, clink",
        "Shatter",
        "Liquid",
        "Splash, splatter",
        "Slosh",
        "Squish",
        "Drip",
        "Pour",
        "Trickle, dribble",
        "Gush",
        "Fill (with liquid)",
        "Spray",
        "Pump (liquid)",
        "Stir",
        "Boiling",
        "Sonar",
        "Arrow",
        "Whoosh, swoosh, swish",
        "Thump, thud",
        "Thunk",
        "Electronic tuner",
        "Effects unit",
        "Chorus effect",
        "Basketball bounce",
        "Bang",
        "Slap, smack",
        "Whack, thwack",
        "Smash, crash",
        "Breaking",
        "Bouncing",
        "Whip",
        "Flap",
        "Scratch",
        "Scrape",
        "Rub",
        "Roll",
        "Crushing",
        "Crumpling, crinkling",
        "Tearing",
        "Beep, bleep",
        "Ping",
        "Ding",
        "Clang",
        "Squeal",
        "Creak",
        "Rustle",
        "Whir",
        "Clatter",
        "Sizzle",
        "Clicking",
        "Clickety-clack",
        "Rumble",
        "Plop",
        "Jingle, tinkle",
        "Hum",
        "Zing",
        "Boing",
        "Crunch",
        "Silence",
        "Sine wave",
        "Harmonic",
        "Chirp tone",
        "Sound effect",
        "Pulse",
        "Inside, small room",
        "Inside, large room or hall",
        "Inside, public space",
        "Outside, urban or manmade",
        "Outside, rural or natural",
        "Reverberation",
        "Echo",
        "Noise",
        "Environmental noise",
        "Static",
        "Mains hum",
        "Distortion",
        "Sidetone",
        "Cacophony",
        "White noise",
        "Pink noise",
        "Throbbing",
        "Vibration",
        "Television",
        "Radio",
        "Field recording",
    )
}
