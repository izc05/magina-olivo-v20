package com.isivoltpro.maginaolivo.data.repository.sync

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.security.MessageDigest

/**
 * Nonblocking, process-lifetime ownership for the future Delivery sender. Every sender
 * and recovery participant must use the SAME private no-backup directory and hold the
 * lease across claim, HTTP completion/cancellation and receipt persistence.
 *
 * The lock file must never be deleted/renamed to recover it. OS process death releases
 * the lock; file age does not. This does not authorize HTTP or reset PROCESSING rows.
 */
internal class DeliveryDispatchLease private constructor(
    private val path: String,
    private val file: RandomAccessFile,
    private val lock: FileLock,
) : Closeable {
    private var closed = false

    fun requireHeld() = synchronized(guard) {
        check(!closed && lock.isValid && file.channel.isOpen) { "Delivery dispatch ownership was released" }
    }

    /** Durable namespace binding; a lease from another directory cannot reclaim this work. */
    fun claimMarker(): String {
        requireHeld()
        return "DELIVERY_LEASE_V1:" + MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    override fun close() = synchronized(guard) {
        if (!closed) {
            closed = true
            try {
                if (lock.isValid) lock.release()
            } finally {
                try { file.close() } finally { ownedPaths.remove(path) }
            }
        }
    }

    companion object {
        private val guard = Any()
        // Do not open/close a second channel for a file already locked by this JVM.
        // On some systems that close would release the first channel's OS lock too.
        private val ownedPaths = mutableSetOf<String>()

        fun tryAcquire(directory: File): DeliveryDispatchLease? = synchronized(guard) {
            val root = directory.canonicalFile
            if (!root.mkdirs() && !root.isDirectory) throw IOException("Delivery lock directory unavailable")
            val target = File(root, "delivery-dispatch.lock")
            val path = target.canonicalPath
            if (path in ownedPaths) return@synchronized null
            val file = RandomAccessFile(target, "rw")
            try {
                val lock = file.channel.tryLock()
                if (lock == null) {
                    file.close()
                    return@synchronized null
                }
                ownedPaths.add(path)
                DeliveryDispatchLease(path, file, lock)
            } catch (error: Exception) {
                try { file.close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
                throw error
            }
        }
    }
}
