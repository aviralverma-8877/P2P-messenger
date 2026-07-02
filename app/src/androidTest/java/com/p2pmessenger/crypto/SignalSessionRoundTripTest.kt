package com.p2pmessenger.crypto

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.p2pmessenger.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This has to be an *instrumented* test, not a plain JVM unit test: `libsignal-android` ships
 * native (.so) code built for Android ABIs, and [KeystoreCryptoHelper] needs a real
 * AndroidKeyStore -- neither is available on the desktop JVM `app/src/test` runs on. Run this
 * with an emulator or physical device attached (`./gradlew connectedAndroidTest`).
 *
 * If this fails to *compile*, the most likely cause is a mismatch between the class/method
 * names guessed in [RoomSignalProtocolStore] / [SignalSessionManager] and the actual API of
 * whatever `libsignal-android` version Gradle resolves -- see the header comment on
 * [RoomSignalProtocolStore] for how to fix that.
 */
@RunWith(AndroidJUnit4::class)
class SignalSessionRoundTripTest {

    private fun newStore(dbName: String): RoomSignalProtocolStore {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomSignalProtocolStore(
            ownIdentityDao = db.ownIdentityDao(),
            remoteIdentityDao = db.remoteIdentityDao(),
            preKeyDao = db.preKeyDao(),
            signedPreKeyDao = db.signedPreKeyDao(),
            kyberPreKeyDao = db.kyberPreKeyDao(),
            sessionDao = db.sessionDao(),
            crypto = KeystoreCryptoHelper(),
        )
    }

    @Test
    fun aliceAndBobExchangeAnEndToEndEncryptedMessageBothWays() = runBlocking {
        val aliceManager = SignalSessionManager(newStore("alice"))
        val bobManager = SignalSessionManager(newStore("bob"))

        val aliceName = aliceManager.ownSignalName()
        val bobName = bobManager.ownSignalName()

        // Bob publishes a prekey bundle (the offline equivalent of Signal's server-hosted one);
        // Alice consumes it directly, exactly as she'd receive it over BLE/SMS.
        val bobBundle = bobManager.generateLocalKeyBundle()
        aliceManager.processRemoteKeyBundle(bobName, bobBundle)

        val outgoing = "Hey Bob, this should only be readable by you.".toByteArray(Charsets.UTF_8)
        val envelopeToBob = aliceManager.encrypt(bobName, SIGNAL_DEVICE_ID, outgoing)

        // Bob never had to call processRemoteKeyBundle for Alice -- decrypting Alice's first
        // (PreKey) message is enough for his SessionCipher to build the session using his own
        // already-stored prekeys.
        val decryptedByBob = bobManager.decrypt(aliceName, SIGNAL_DEVICE_ID, envelopeToBob)
        assertEquals(String(outgoing, Charsets.UTF_8), String(decryptedByBob, Charsets.UTF_8))

        // Now the ratchet should carry a reply back the other way too.
        val reply = "Got it, Alice -- loud and clear.".toByteArray(Charsets.UTF_8)
        val envelopeToAlice = bobManager.encrypt(aliceName, SIGNAL_DEVICE_ID, reply)
        val decryptedByAlice = aliceManager.decrypt(bobName, SIGNAL_DEVICE_ID, envelopeToAlice)
        assertEquals(String(reply, Charsets.UTF_8), String(decryptedByAlice, Charsets.UTF_8))
    }
}
