package pw.dipix.auth

import java.net.URL

fun uploadTexture(binary: ByteArray): String {
    val file = config.textures_dir.apply { mkdirs() }.resolve(hash(binary, "SHA-256"))
    file.writeBytes(binary)
    return file.name
}

fun readTexture(hash: String): ByteArray {
    val file = config.textures_dir.apply { mkdirs() }.resolve(hash)
    return file.readBytes()
}

fun texturesForProfile(profile: Profile): String =
    jsonMapper.writeValueAsString(jsonMapper.createObjectNode().apply {
        put("timestamp", System.currentTimeMillis())
        put("profileId", profile.uuid.dashless())
        put("profileName", profile.username)
        putObject("textures").apply {
            if (profile.skinId != null && profile.skinModel != null) {
                putObject("SKIN").apply {
                    put("url", URL(config.host, "/textures/${profile.skinId}").toString())
                    putObject("metadata").apply {
                        put("model", profile.skinModel)
                    }
                }
            }
        }
    })