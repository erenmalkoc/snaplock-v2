package com.erenium.snaplock.domain.error

import java.util.UUID

class EntryNotFoundException(uuid: UUID) : Exception("Giriş bulunamadı: $uuid")