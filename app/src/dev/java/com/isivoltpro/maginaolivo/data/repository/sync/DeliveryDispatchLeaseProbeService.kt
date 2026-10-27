package com.isivoltpro.maginaolivo.data.repository.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import java.io.File
import kotlin.concurrent.thread

/**
 * DEV-ONLY test aid for the B2 gate: a SECOND OS process that competes for the same
 * [DeliveryDispatchLease]. It exists because the lease's `ownedPaths` set guards a single
 * JVM only; nothing but a real second process can prove that the OS `FileLock` excludes
 * another sender.
 *
 * Declared in `src/dev/AndroidManifest.xml` with `android:process=":leaseprobe"`, so it runs
 * under the app's own UID (it can reach the app's private directories) in its own process, and
 * it does not exist in staging or production builds. It issues no HTTP request, opens no
 * database, and never deletes the lock file.
 *
 * Protocol, deliberately file based so no AIDL or Messenger timing is involved:
 *  - [EXTRA_DIRECTORY] absolute path of the lock directory, chosen by the caller.
 *  - [EXTRA_RESULT] absolute path of a file this process appends its verdict to, one token per
 *    line: `PID:<pid>`, then `ACQUIRED` or `BLOCKED`, then `RELEASED`.
 *  - [EXTRA_MODE] [MODE_TRY] releases at once; [MODE_HOLD] keeps ownership until
 *    [EXTRA_RELEASE] exists, so the caller can observe being excluded itself.
 */
internal class DeliveryDispatchLeaseProbeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val directory = intent?.getStringExtra(EXTRA_DIRECTORY)
        val result = intent?.getStringExtra(EXTRA_RESULT)
        if (directory == null || result == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TRY
        val release = intent.getStringExtra(EXTRA_RELEASE)
        val report = File(result)

        thread(name = "lease-probe") {
            fun say(token: String) = synchronized(report) { report.appendText(token + "\n") }
            say("PID:" + Process.myPid())
            try {
                val lease = DeliveryDispatchLease.tryAcquire(File(directory))
                if (lease == null) {
                    say("BLOCKED")
                } else {
                    lease.use {
                        say("ACQUIRED")
                        if (mode == MODE_HOLD && release != null) {
                            val sentinel = File(release)
                            val deadline = System.currentTimeMillis() + HOLD_TIMEOUT_MILLIS
                            while (!sentinel.exists() && System.currentTimeMillis() < deadline) {
                                Thread.sleep(50L)
                            }
                        }
                    }
                    say("RELEASED")
                }
            } catch (error: Throwable) {
                say("ERROR:" + error.javaClass.simpleName)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_DIRECTORY = "directory"
        const val EXTRA_RESULT = "result"
        const val EXTRA_MODE = "mode"
        const val EXTRA_RELEASE = "release"
        const val MODE_TRY = "TRY"
        const val MODE_HOLD = "HOLD"

        /** A probe never outlives one test, even if the caller crashes before releasing it. */
        private const val HOLD_TIMEOUT_MILLIS = 60_000L
    }
}
