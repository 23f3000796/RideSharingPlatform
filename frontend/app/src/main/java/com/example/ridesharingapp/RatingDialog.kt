package com.example.ridesharingapp

import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ridesharingapp.databinding.DialogRateBinding
import kotlinx.coroutines.launch

/** 1–5 star rating + comment, posted to /reviews. */
object RatingDialog {

    private val labels = mapOf(1 to "Poor", 2 to "Below average", 3 to "Okay", 4 to "Good", 5 to "Excellent")

    fun show(
        activity: AppCompatActivity,
        rideId: String,
        targetId: String,
        targetName: String,
        asDriver: Boolean,
        onDone: () -> Unit,
    ) {
        val b = DialogRateBinding.inflate(LayoutInflater.from(activity))
        b.tvRatePrompt.text = if (asDriver) {
            "How was ${UiKit.firstName(targetName)} as a co-passenger?"
        } else {
            "How was your ride with ${UiKit.firstName(targetName)}?"
        }
        b.ratingBar.setOnRatingBarChangeListener { bar, value, _ ->
            if (value < 1f) bar.rating = 1f
            b.tvRatingLabel.text = labels[bar.rating.toInt().coerceIn(1, 5)]
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (asDriver) "Rate passenger" else "Rate driver")
            .setView(b.root)
            .setPositiveButton("Submit", null)
            .setNegativeButton("Later", null)
            .create()

        dialog.setOnShowListener {
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submit.setOnClickListener {
                submit.isEnabled = false
                activity.lifecycleScope.launch {
                    val result = RideSharingRepository.submitReview(
                        rideId = rideId,
                        targetId = targetId,
                        rating = b.ratingBar.rating.toInt().coerceIn(1, 5),
                        comment = b.etComment.text?.toString(),
                    )
                    result.fold(
                        onSuccess = { msg ->
                            Toast.makeText(activity, "$msg (+5 points)", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            onDone()
                        },
                        onFailure = { e ->
                            submit.isEnabled = true
                            Toast.makeText(activity, e.message ?: "Could not submit rating", Toast.LENGTH_LONG).show()
                        },
                    )
                }
            }
        }
        dialog.show()
    }
}
