package com.example.driverfatiguedetection

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    // ---- UI ----
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var btnPickContact: Button
    private lateinit var txtContact: TextView
    private lateinit var btnAwake: Button
    private var countdownText: TextView? = null // optional if present in layout

    // Dataset evaluation button
    private lateinit var btnRunDatasetEval: Button

    // ---- Detection thresholds (tuned to reduce false alarms) ----
    private val EAR_THRESHOLD = 0.15
    private val EAR_HOLD_MS = 1200L
    private val MAR_THRESHOLD = 0.70
    private val MAR_HOLD_MS = 1500L

    // EAR/MAR look normal for this long, stop alarm & cancel SMS
    private val RECOVER_HOLD_MS = 800L
    private var recoveredCandidateStart = 0L

    private var eyeBelowSince = 0L
    private var mouthAboveSince = 0L

    //  Auto-SMS timing & countdown ----
    private val COUNTDOWN_TOTAL_MS = 8_000L
    private val SMS_COOLDOWN_MS = 60_000L
    @Volatile private var isDrowsy = false
    private var drowsyEpisodeStart = 0L
    private var lastSmsAt = 0L
    private var countdownJob: Job? = null
    private var autoSendJobActive = false

    // ---- Alarm ----
    private var alarmPlayer: MediaPlayer? = null

    // ---- Helpers ----
    private var landmarker: FaceLandmarkerHelper? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // ---- Permissions ----
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startIfReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.statusText)
        btnPickContact = findViewById(R.id.btnPickContact)
        txtContact = findViewById(R.id.txtContact)
        btnAwake = findViewById(R.id.btnAwake)

        //  find the eval button (id exists in XML)
        btnRunDatasetEval = findViewById(R.id.btnRunDatasetEval)

        countdownText = findViewById<TextView?>(
            resources.getIdentifier("countdownText", "id", packageName)
        )

        try {
            landmarker = FaceLandmarkerHelper(this)
        } catch (e: Exception) {
            updateStatus("Model load error: ${e.message}")
        }

        btnPickContact.setOnClickListener {
            val dialog = android.app.AlertDialog.Builder(this)
            val input = android.widget.EditText(this).apply {
                hint = "Enter emergency phone number"
                inputType = android.text.InputType.TYPE_CLASS_PHONE
            }
            dialog.setTitle("Emergency contact")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    Prefs.setEmergencyNumber(this, input.text.toString())
                    txtContact.text = "Emergency: ${input.text}"
                }.setNegativeButton("Cancel", null).show()
        }

        btnAwake.setOnClickListener { onUserAwake() }

        Prefs.getEmergencyNumber(this)?.let { txtContact.text = "Emergency: $it" }

        //Hook DatasetEvaluator to one button
        btnRunDatasetEval.setOnClickListener {
            runDatasetEvaluationFirst5()
        }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPerms.launch(needed.toTypedArray()) else startIfReady()
    }

    private fun startIfReady() {
        if (landmarker == null) {
            updateStatus("Model not available. Cannot start camera.")
            return
        }
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { proxy ->
                        val bmp = ImageUtils.imageProxyToBitmap(proxy)
                        val landmarks: List<NormalizedLandmark>? = landmarker?.detect(bmp)
                        runOnUiThread { onLandmarks(landmarks) }
                        proxy.close()
                    }
                }

            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    // ------------ Landmark processing with recovery debounce ------------
    private fun onLandmarks(landmarks: List<NormalizedLandmark>?) {
        if (landmarks.isNullOrEmpty()) {
            updateStatus("No face")
            eyeBelowSince = 0L
            mouthAboveSince = 0L
            recoveredCandidateStart = 0L
            return
        }

        val pts = landmarks.map { DrowsinessMetrics.P(it.x(), it.y()) }
        val (ear, mar) = DrowsinessMetrics.compute(pts)
        val now = SystemClock.elapsedRealtime()

        if (ear < EAR_THRESHOLD) {
            if (eyeBelowSince == 0L) eyeBelowSince = now
        } else eyeBelowSince = 0L

        if (mar > MAR_THRESHOLD) {
            if (mouthAboveSince == 0L) mouthAboveSince = now
        } else mouthAboveSince = 0L

        val eyeClosedLong = eyeBelowSince != 0L && (now - eyeBelowSince) >= EAR_HOLD_MS
        val yawnLong      = mouthAboveSince != 0L && (now - mouthAboveSince) >= MAR_HOLD_MS

        if (eyeClosedLong || yawnLong) {
            recoveredCandidateStart = 0L
            onDrowsyDetected()
            updateStatus("Drowsy  EAR=%.2f  MAR=%.2f".format(ear, mar))
        } else {
            if (recoveredCandidateStart == 0L) recoveredCandidateStart = now
            if (now - recoveredCandidateStart >= RECOVER_HOLD_MS) {
                onRecovered() // auto-stop alarm & cancel countdown
            }
            updateStatus("Awake  EAR=%.2f  MAR=%.2f".format(ear, mar))
        }
    }

    // NEW: Run dataset evaluation (first 50 images per folder)
    private fun runDatasetEvaluationFirst5() {
        if (landmarker == null) {
            Toast.makeText(this, "Landmarker not ready", Toast.LENGTH_SHORT).show()
            return
        }

        updateStatus("Running dataset eval… (As per folder)")

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val evaluator = DatasetEvaluator(this@MainActivity)

                val (eyeCsv, yawnCsv) = evaluator.runAll(

                    // ----- Eye predictor: Open vs Closed -----
                    predictEyeLabelAndEar = { bitmap ->
                        val lm = landmarker?.detect(bitmap)
                        if (lm.isNullOrEmpty()) {
                            Triple("no_face", null, false)
                        } else {
                            val pts = lm.map { DrowsinessMetrics.P(it.x(), it.y()) }
                            val (ear, _) = DrowsinessMetrics.compute(pts)
                            val pred = if (ear < EAR_THRESHOLD) "Closed" else "Open"
                            Triple(pred, ear, true)
                        }
                    },

                    // ----- Yawn predictor: yawn vs no_yawn -----
                    predictYawnLabelAndMar = { bitmap ->
                        val lm = landmarker?.detect(bitmap)
                        if (lm.isNullOrEmpty()) {
                            Triple("no_face", null, false)
                        } else {
                            val pts = lm.map { DrowsinessMetrics.P(it.x(), it.y()) }
                            val (_, mar) = DrowsinessMetrics.compute(pts)
                            val pred = if (mar > MAR_THRESHOLD) "yawn" else "no_yawn"
                            Triple(pred, mar, true)
                        }
                    }
                )

                // Back to UI thread for Toast/status
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Saved CSV:\n${eyeCsv.absolutePath}\n${yawnCsv.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                    updateStatus("Evaluation done. CSV saved in app files.")
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    updateStatus("Eval error: ${e.message}")
                    Toast.makeText(this@MainActivity, "Eval error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---- Alarm helpers ----
    private fun startAlarm() {
        if (alarmPlayer == null) {
            alarmPlayer = MediaPlayer.create(this, R.raw.alarm_sound).apply { isLooping = true }
        }
        if (alarmPlayer?.isPlaying != true) alarmPlayer?.start()
    }

    private fun stopAlarm() {
        alarmPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        alarmPlayer = null
    }

    // ---- countdown ----
    private fun onDrowsyDetected() {
        if (!isDrowsy) {
            isDrowsy = true
            drowsyEpisodeStart = SystemClock.elapsedRealtime()
            btnAwake.visibility = View.VISIBLE
            startAlarm()
            startCountdownAndAutoSms()
        }
    }

    private fun onRecovered() {
        if (isDrowsy || alarmPlayer != null || btnAwake.visibility == View.VISIBLE) {
            isDrowsy = false
            drowsyEpisodeStart = 0L
            btnAwake.visibility = View.GONE
            stopAlarm()
            clearCountdownUI()
            countdownJob?.cancel()
            countdownJob = null
        }
    }

    private fun onUserAwake() {
        isDrowsy = false
        drowsyEpisodeStart = 0L
        btnAwake.visibility = View.GONE
        stopAlarm()
        clearCountdownUI()
        countdownJob?.cancel()
        countdownJob = null
        updateStatus("User confirmed awake")
    }

    private fun startCountdownAndAutoSms() {
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            val startStamp = drowsyEpisodeStart
            val endAt = SystemClock.elapsedRealtime() + COUNTDOWN_TOTAL_MS
            autoSendJobActive = true
            try {
                while (isDrowsy && drowsyEpisodeStart == startStamp) {
                    val remaining = endAt - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break
                    updateCountdownUI(remaining)
                    delay(200)
                }
                clearCountdownUI()

                if (isDrowsy && drowsyEpisodeStart == startStamp) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSmsAt >= SMS_COOLDOWN_MS) {
                        sendEmergencySmsWithLocation()
                        lastSmsAt = now
                        updateStatus("Auto-SMS sent to emergency contact.")
                    } else {
                        updateStatus("SMS skipped (cooldown).")
                    }
                }
            } finally {
                autoSendJobActive = false
            }
        }
    }

    private fun updateCountdownUI(remainingMs: Long) {
        val secs = kotlin.math.ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
        countdownText?.visibility = View.VISIBLE
        countdownText?.text = "Sending alert in $secs s…"
        if (countdownText == null) {
            statusText.text = "Status: Drowsy • sending in $secs s…"
        }
    }

    private fun clearCountdownUI() {
        countdownText?.visibility = View.GONE
        countdownText?.text = ""
    }

    private fun sendEmergencySmsWithLocation() {
        val number = Prefs.getEmergencyNumber(this)
        if (number.isNullOrBlank()) {
            updateStatus("No emergency contact set.")
            return
        }
        val fused = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            sendSms(number, "Fatigue alert! This person is sleeping at this location (no GPS).")
            return
        }
        fused.lastLocation
            .addOnSuccessListener { loc ->
                val link = if (loc != null)
                    "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                else "(location unavailable)"
                sendSms(number, "Fatigue alert! This person is sleeping at this location: $link")
            }
            .addOnFailureListener {
                sendSms(number, "Fatigue alert! This person is sleeping at this location (location lookup failed).")
            }
    }

    private fun sendSms(number: String, msg: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, msg, null, null)
            updateStatus("SMS sent to $number")
        } catch (e: Exception) {
            updateStatus("SMS error: ${e.message}")
            e.printStackTrace()
        }
    }

    // ---- UI helper ----
    private fun updateStatus(msg: String) { statusText.text = "Status: $msg" }

    override fun onDestroy() {
        stopAlarm()
        countdownJob?.cancel()
        landmarker?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
