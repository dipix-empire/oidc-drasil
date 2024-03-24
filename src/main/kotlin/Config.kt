package pw.dipix.auth

class Config(
    /**
     * Scope for accessing minecraft profiles and auth
     */
    val oauth: Oauth,
    val scope: String,
    val authorize: String,
    val token: String,
    val introspect: String,
    val mongo_url: String,
    val mongo_db: String
) {
    class Oauth(
        val client_id: String,
        val client_secret: String,
        val scope: String
    )
}
