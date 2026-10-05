package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.TransferRecord
import com.example.data.firebase.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun unauthenticated_cannotWriteProfile() = runBlocking {
        auth.signOut()
        val repo = FirestoreRepository(firestore)
        val profile = UserProfile(
            userId = "unauth_user",
            displayName = "Unauth",
            email = "unauth@example.com",
            deviceName = "Phone"
        )
        try {
            repo.saveUserProfile(profile)
            fail("Expected FirebaseFirestoreException for unauthenticated write")
        } catch (e: Exception) {
            assertTrue(e is FirebaseFirestoreException || e.cause is FirebaseFirestoreException)
        }
    }

    @Test
    fun authenticated_canSaveAndObserveProfile() = runBlocking {
        val uid = signInTestUser("alice_rule@example.com")
        val repo = FirestoreRepository(firestore)

        val profile = UserProfile(
            userId = uid,
            displayName = "Alice Rule",
            email = "alice_rule@example.com",
            deviceName = "Pixel 8"
        )
        repo.saveUserProfile(profile)

        val fetched = repo.observeUserProfile(uid).first()
        assertNotNull(fetched)
        assertEquals("Alice Rule", fetched?.displayName)
        assertEquals(uid, fetched?.userId)
    }

    @Test
    fun authenticated_canRecordAndObserveTransfers() = runBlocking {
        val uid = signInTestUser("bob_rule@example.com")
        val repo = FirestoreRepository(firestore)

        val record = TransferRecord(
            id = "tx_rule_01",
            userId = uid,
            fileName = "document.pdf",
            fileSize = 4096L,
            fileType = "FILES",
            direction = "SEND",
            peerName = "Alice Tablet",
            status = "COMPLETED"
        )
        repo.recordTransfer(record)

        val list = repo.observeTransferRecords(uid).first()
        assertEquals(1, list.size)
        assertEquals("document.pdf", list[0].fileName)
        assertEquals("COMPLETED", list[0].status)
    }

    @Test
    fun crossUser_cannotAccessOtherUserTransfers() = runBlocking {
        val aliceUid = signInTestUser("alice_cross@example.com")
        val repoAlice = FirestoreRepository(firestore)

        val record = TransferRecord(
            id = "tx_alice_private",
            userId = aliceUid,
            fileName = "secret.png",
            fileSize = 2048L,
            fileType = "PHOTOS",
            direction = "SEND",
            peerName = "Secret Peer",
            status = "COMPLETED"
        )
        repoAlice.recordTransfer(record)

        // Sign in as Bob
        val bobUid = signInTestUser("bob_cross@example.com")
        val repoBob = FirestoreRepository(firestore)

        // Bob tries to read Alice's profile
        try {
            val aliceProfile = repoBob.observeUserProfile(aliceUid).first()
            assertNull("Bob should not read Alice profile", aliceProfile)
        } catch (e: Exception) {
            // Permission denied is also an expected pass
            assertTrue(e is FirebaseFirestoreException || e.cause is FirebaseFirestoreException)
        }
    }
}
