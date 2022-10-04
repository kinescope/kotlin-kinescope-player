package io.kinescope.sdk.utils

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ProgressBar

fun View.animateRotation() {
    val rotate = RotateAnimation(
        0f, 360f,
        Animation.RELATIVE_TO_SELF, 0.5f,
        Animation.RELATIVE_TO_SELF, 0.5f
    )
    rotate.duration = 800
    rotate.repeatCount = Animation.INFINITE
    rotate.interpolator = LinearInterpolator()
    this.startAnimation(rotate)
}