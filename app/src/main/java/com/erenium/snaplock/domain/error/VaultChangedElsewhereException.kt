package com.erenium.snaplock.domain.error

/**
 * The vault file was changed by another app or device in a way this session cannot merge:
 * it now opens with other credentials, holds a different vault or is damaged. Nothing was
 * written, so the other copy is left as it is.
 */
class VaultChangedElsewhereException(cause: Throwable) :
    Exception("The vault file was changed elsewhere and cannot be merged", cause)
