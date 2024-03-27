package pw.dipix.auth

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

val jsonMapper = jacksonObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

fun parseDashlessUUID(string: String): UUID {
    return try {
        UUID.fromString(string)
    } catch (e: IllegalArgumentException) {
        UUID.fromString(string.replace(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]+)".toRegex(), "$1-$2-$3-$4-$5"
        ))
    }
}

fun UUID.dashless() = this.toString().replace("-", "")

@OptIn(ExperimentalStdlibApi::class)
fun hash(data: ByteArray, algo: String): String {
    val hasher = MessageDigest.getInstance(algo)
    return hasher.digest(data).toHexString(HexFormat.Default)
}