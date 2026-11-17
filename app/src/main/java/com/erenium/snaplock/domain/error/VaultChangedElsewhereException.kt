package com.erenium.snaplock.domain.error

class VaultChangedElsewhereException(cause: Throwable) :
    Exception("The vault file was changed elsewhere and cannot be merged", cause)
