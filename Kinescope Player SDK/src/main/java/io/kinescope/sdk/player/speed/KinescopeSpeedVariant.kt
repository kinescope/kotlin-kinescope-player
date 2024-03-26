package io.kinescope.sdk.player.speed

data class KinescopeSpeedVariant(
    val name: String,
    val speed: Float,
) {
    companion object {
        const val SPEED_VARIANT_0_25 = .25f
        const val SPEED_VARIANT_0_5 = .5f
        const val SPEED_VARIANT_0_75 = .75f
        const val SPEED_VARIANT_NORMAL = 1f
        const val SPEED_VARIANT_1_25 = 1.25f
        const val SPEED_VARIANT_1_5 = 1.5f
        const val SPEED_VARIANT_1_75 = 1.75f
        const val SPEED_VARIANT_2 = 2f
    }
}