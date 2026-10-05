package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(private val db: FirebaseFirestore) {

    // Convenience constructor resolving the named database ID
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        private const val TAG = "FirestoreRepository"
    }

    // --- User Profile ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val docRef = db.collection("users").document(profile.userId)
        val snapshot = docRef.get().await()
        val data = mutableMapOf<String, Any>(
            "userId" to profile.userId,
            "displayName" to profile.displayName,
            "email" to profile.email,
            "deviceName" to profile.deviceName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (profile.photoUrl != null) {
            data["photoUrl"] = profile.photoUrl
        }
        if (!snapshot.exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
        }
        docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        Log.d(TAG, "User profile saved for ${profile.userId}")
    }

    // --- Transfer Records (Cloud Sync) ---

    fun observeTransferRecords(userId: String): Flow<List<TransferRecord>> {
        return db.collection("users").document(userId)
            .collection("transfers")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(TransferRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun recordTransfer(record: TransferRecord) {
        val transferDoc = db.collection("users").document(record.userId)
            .collection("transfers").document(record.id)
        val data = mapOf(
            "id" to record.id,
            "userId" to record.userId,
            "fileName" to record.fileName,
            "fileSize" to record.fileSize,
            "fileType" to record.fileType,
            "direction" to record.direction,
            "peerName" to record.peerName,
            "status" to record.status,
            "createdAt" to FieldValue.serverTimestamp()
        )
        transferDoc.set(data).await()
        Log.d(TAG, "Recorded transfer ${record.id} for user ${record.userId}")
    }

    // --- User Settings ---

    fun observeUserSettings(userId: String): Flow<UserSettings?> {
        return db.collection("users").document(userId)
            .collection("settings").document("preferences")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
    }

    suspend fun saveUserSettings(settings: UserSettings) {
        val doc = db.collection("users").document(settings.userId)
            .collection("settings").document("preferences")
        val data = mapOf(
            "userId" to settings.userId,
            "autoAccept" to settings.autoAccept,
            "customDeviceName" to settings.customDeviceName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        doc.set(data).await()
        Log.d(TAG, "Saved settings for user ${settings.userId}")
    }
}
