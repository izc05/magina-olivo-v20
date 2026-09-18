package com.isivoltpro.maginaolivo.core.id

import java.util.UUID

interface IdGenerator {
    fun newId(): UUID
}

class UuidGenerator : IdGenerator {
    override fun newId(): UUID = UUID.randomUUID()
}
