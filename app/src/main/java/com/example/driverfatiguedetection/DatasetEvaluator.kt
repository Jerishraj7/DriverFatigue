package com.example.driverfatiguedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

data class EvalRow(
    val fileName: String,
    val trueLabel: String,
    val predicted: String,
    val score: Double?,          // EAR or MAR
    val faceDetected: Boolean
)

class DatasetEvaluator(
    private val context: Context
) {

    // CHANGE THIS IF NEEDED
    // ALWAYS correct on any Android version
    private val basePath = File(context.getExternalFilesDir(null), "train").absolutePath


    //  IMAGES FROM EACH FOLDER
    private val limitPerFolder = 50

    fun runAll(
        predictEyeLabelAndEar: (Bitmap) -> Triple<String, Double?, Boolean>,
        predictYawnLabelAndMar: (Bitmap) -> Triple<String, Double?, Boolean>
    ): Pair<File, File> {

        val eyeRows = mutableListOf<EvalRow>()
        val yawnRows = mutableListOf<EvalRow>()

        // ---------- EYE EVALUATION ----------
        eyeRows += evalFolder(
            folderName = "Open",
            trueLabel = "Open",
            predictor = predictEyeLabelAndEar
        )

        eyeRows += evalFolder(
            folderName = "Closed",
            trueLabel = "Closed",
            predictor = predictEyeLabelAndEar
        )

        // ---------- YAWN EVALUATION ----------
        yawnRows += evalFolder(
            folderName = "yawn",
            trueLabel = "yawn",
            predictor = predictYawnLabelAndMar
        )

        yawnRows += evalFolder(
            folderName = "no_yawn",
            trueLabel = "no_yawn",
            predictor = predictYawnLabelAndMar
        )

        val eyeCsv = writeCsv("eye_results.csv", eyeRows)
        val yawnCsv = writeCsv("yawn_results.csv", yawnRows)

        return Pair(eyeCsv, yawnCsv)
    }

    private fun evalFolder(
        folderName: String,
        trueLabel: String,
        predictor: (Bitmap) -> Triple<String, Double?, Boolean>
    ): List<EvalRow> {

        val dir = File("$basePath/$folderName")
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val rows = mutableListOf<EvalRow>()

        val files = (dir.listFiles { f ->
            f.isFile && (
                    f.name.endsWith(".png", true) ||
                            f.name.endsWith(".jpg", true) ||
                            f.name.endsWith(".jpeg", true)
                    )
        }?.sortedBy { it.name } ?: emptyList())
            .take(limitPerFolder)   // ✅ LIMIT HERE

        for (file in files) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue

            val (predicted, score, faceDetected) = predictor(bitmap)

            rows.add(
                EvalRow(
                    fileName = "${folderName}/${file.name}",
                    trueLabel = trueLabel,
                    predicted = predicted,
                    score = score,
                    faceDetected = faceDetected
                )
            )
        }

        return rows
    }

    private fun writeCsv(fileName: String, rows: List<EvalRow>): File {
        val outFile = File(context.getExternalFilesDir(null), fileName)

        outFile.bufferedWriter().use { writer ->
            writer.write("file,true_label,predicted,score,faceDetected,correct\n")
            for (r in rows) {
                val correct = r.trueLabel.equals(r.predicted, ignoreCase = true)
                writer.write(
                    "${r.fileName}," +
                            "${r.trueLabel}," +
                            "${r.predicted}," +
                            "${r.score ?: ""}," +
                            "${r.faceDetected}," +
                            "$correct\n"
                )
            }
        }
        return outFile
    }
}
