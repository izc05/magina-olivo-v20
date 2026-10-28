package com.isivoltpro.maginaolivo.account.reservation.persistence

import java.io.File
import java.util.concurrent.CountDownLatch

/** Child process fixture. The parent forcibly terminates it at a published commit boundary. */
object ReservationProcessWriter {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val file = File(args[0])
        val checkpoint = args[1]
        require(checkpoint in setOf("prepared", "receipt-before-rename", "receipt-committed"))
        val fixture = ProcessReservationFixture
        DurableReservationJournal(file).prepare(fixture.scope, fixture.request, fixture.receipt.remoteWorkspaceId)
        fun reachedBoundary(): Nothing {
            println("CHECKPOINT:$checkpoint")
            System.out.flush()
            CountDownLatch(1).await()
            error("Only forced termination should finish the writer")
        }
        if (checkpoint == "prepared") reachedBoundary()
        val journal = if (checkpoint == "receipt-before-rename") {
            DurableReservationJournal(file) { reachedBoundary() }
        } else DurableReservationJournal(file)
        journal.attempt(fixture.scope, { fixture.actor }, fixture.transport)
        reachedBoundary()
    }
}
