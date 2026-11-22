package com.cl0udshifter.pdfeasy.core.data.repository

import java.security.MessageDigest

internal object DocumentIdGenerator {
    fun fromUri(uriString: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uriString.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
