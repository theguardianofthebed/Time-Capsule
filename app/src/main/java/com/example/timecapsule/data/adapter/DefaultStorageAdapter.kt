package com.example.timecapsule.data.adapter

import com.example.timecapsule.data.media.PhotoUploadBackend
import com.example.timecapsule.domain.model.FrozenPhotoUpload
import com.example.timecapsule.domain.model.PhotoUploadAcknowledgment
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Uses authenticated Storage SDK operations; never exposes public download-token URLs. */
class DefaultStorageAdapter(
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : StorageAdapter, PhotoUploadBackend {
    /** Reports an authenticated SDK environment, not server reachability or successful upload. */
    override fun isAvailable(): Boolean = auth.currentUser != null

    override suspend fun downloadPhotoBytes(
        objectPath: String,
        maxBytes: Long
    ): OperationResult<ByteArray> = try {
        val bytes = storage.reference.child(objectPath).getBytes(maxBytes).await()
        OperationResult.Success(bytes)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        OperationResult.Unavailable("Photo download failed: ${e.message}")
    }

    /** Requires the matching sender and compares server size/MD5 with the exact uploaded bytes. */
    override suspend fun upload(file: File, frozen: FrozenPhotoUpload): PhotoUploadAcknowledgment {
        if (auth.currentUser?.uid != frozen.original.ownerUid) throw SecurityException()
        val metadata = StorageMetadata.Builder().setContentType(frozen.original.format.mimeType)
            .setCustomMetadata("sha256", frozen.original.sha256).build()
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { source ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val expectedMd5 = Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        val task = storage.reference.child(frozen.objectPath).putFile(android.net.Uri.fromFile(file), metadata)
        val result = try {
            task.await()
        } catch (cancelled: CancellationException) {
            task.cancel()
            // Cancellation is not proof of remote absence; cleanup remains outcome-gated.
            throw cancelled
        } catch (error: StorageException) {
            if (error.errorCode == StorageException.ERROR_NOT_AUTHORIZED ||
                error.errorCode == StorageException.ERROR_NOT_AUTHENTICATED) throw SecurityException()
            throw error
        }
        val ack = result.metadata ?: throw IOException("Missing upload acknowledgment.")
        if (ack.path != frozen.objectPath || ack.sizeBytes != frozen.original.sizeBytes ||
            ack.md5Hash != expectedMd5 || ack.contentType != frozen.original.format.mimeType) {
            throw IOException("Upload acknowledgment mismatch.")
        }
        return PhotoUploadAcknowledgment(frozen.objectPath, ack.sizeBytes, frozen.original.sha256)
    }

    /** Requests server state explicitly; an unavailable backend never becomes a false absence. */
    override suspend fun isPublished(capsuleId: String): Boolean =
        firestore.collection("capsules").document(capsuleId).get(Source.SERVER).await().exists()

    /** Deletes an unreferenced object only if backend rules permit; missing objects are idempotent. */
    override suspend fun deleteUnpublished(path: String) {
        try {
            storage.reference.child(path).delete().await()
        } catch (error: StorageException) {
            if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
        }
    }
}
