package pw.dipix.auth

import java.io.File
import java.net.URL

class Config(
    val oauth: Oauth,
    /**
     * Scope for accessing minecraft profiles and auth
     */
    val scope: String,
    val authorize: String,
    val token: String,
    val introspect: String,
    val mongo_url: String,
    val mongo_db: String,
    val storage_dir: File,
    val host: URL
) {
    class Oauth(
        val client_id: String,
        val client_secret: String,
        val scope: String,
        val redirect_url: URL
    )
}
