package pw.dipix.auth

import java.net.URL

interface IdentifiedStorage {

    /**
     * Uploads data to baking storage, and returns identifier that can be used with [read] to get it back
     */
    fun upload(binary: ByteArray): String

    /**
     * Reads data from baking storage using returned string from [upload]
     */
    fun read(identifier: String): ByteArray
}

object LocalStorage : IdentifiedStorage {
    override fun upload(binary: ByteArray): String {
        val file = config.storage_dir.apply { mkdirs() }.resolve(hash(binary, "SHA-256"))
        file.writeBytes(binary)
        return file.name
    }

    override fun read(identifier: String): ByteArray {
        val file = config.storage_dir.apply { mkdirs() }.resolve(identifier)
        return file.readBytes()
    }

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