package io.kinescope.sdk.player

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(UnstableApi::class)
class KinescopePlayerHostTest {

    @Test
    fun startsWithLocalPlayerActive() {
        val local = mock<Player>()
        val host = KinescopePlayerHost(local)

        assertSame(local, host.activePlayer)
        assertFalse(host.isCasting)
    }

    @Test
    fun switchToCastPlayer_updatesActivePlayerAndNotifies() {
        val local = mock<Player>()
        val cast = mock<Player>()
        val host = KinescopePlayerHost(local)
        var notified: Player? = null
        host.onActivePlayerChanged = { notified = it }

        host.switchTo(cast)

        assertSame(cast, host.activePlayer)
        assertTrue(host.isCasting)
        assertSame(cast, notified)
    }

    @Test
    fun switchToSamePlayer_doesNotNotify() {
        val local = mock<Player>()
        val host = KinescopePlayerHost(local)
        var notifications = 0
        host.onActivePlayerChanged = { notifications++ }

        host.switchTo(local)

        assertEquals(0, notifications)
    }

    @Test
    fun switchToLocal_restoresLocalPlayer() {
        val local = mock<Player>()
        val cast = mock<Player>()
        val host = KinescopePlayerHost(local)
        host.switchTo(cast)

        host.switchToLocal()

        assertSame(local, host.activePlayer)
        assertFalse(host.isCasting)
    }
}
