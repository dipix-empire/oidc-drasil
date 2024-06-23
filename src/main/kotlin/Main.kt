package pw.dipix.auth

import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.*

val config = jsonMapper.readValue(File("./drasil.conf.json"), Config::class.java)

val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson()
    }
}

suspend fun isTokenValid(token: String): Boolean {
    return client.post(config.introspect) {
        basicAuth(config.oauth.client_id, config.oauth.client_secret) // ?
        setBody(FormDataContent(Parameters.build {
            set("token", token)
            set("scope", config.oauth.scope)
        }))
    }.body<ObjectNode>().apply { println("is token valid"); println(this); println(token) }
        .run { get("active").asBoolean() && get("scope").textValue().contains(config.scope) }
}

fun getTokenClaims(token: String): ObjectNode {
    val payload = jsonMapper.readValue(token.split(".")[1].decodeBase64String(), ObjectNode::class.java)
    return payload.apply { println("get token claims"); println(this); println(token) }
}

suspend fun refreshToken(refreshToken: String): Pair<String, String> {
    return client.post(config.token) {
        setBody(FormDataContent(Parameters.build {
            set("client_id", config.oauth.client_id)
            set("client_secret", config.oauth.client_secret)
            set("grant_type", "refresh_token")
            set("refresh_token", refreshToken)
        }))
    }.body<ObjectNode>().apply { println("refresh"); println(this) }
        .run { this["access_token"].asText() to this["refresh_token"].asText() }
}

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    embeddedServer(Netty, port = 8080) {
        install(CallLogging)
        install(IgnoreTrailingSlash)
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            jackson { }
        }
        routing {
            get("/") {
                println(
                    call.request.headers.toMap().asIterable()
                        .joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" })
//                if (call.request.userAgent()?.lowercase()?.contains("java") != true) {
//                    call.respond("uwu")
//                    return@get
//                }
//                    call.response.header("X-Authlib-Injector-API-Location", "/api")
                println("Responding with json!")
                call.respond(jsonMapper.createObjectNode().apply {
                    putObject("meta").apply {
                        put("serverName", "oidc-drasil")
                        put("implementationName", "oidc-drasil")
                        put("implementationVersion", "snapshot")
                        put("feature.non_email_login", true)
                    }
                    putArray("skinDomains").apply {
                        add(config.host.host)
                    }
                })
            }
//            get("/api") {
//                println("Responding with json!")
//                call.respond(jsonMapper.createObjectNode().apply {
//                    putObject("meta").apply {
//                        put("serverName", "oidc-drasil")
//                        put("implementationName", "oidc-drasil")
//                        put("implementationVersion", "snapshot")
//                        put("feature.non_email_login", true)
//                    }
//                })
//            }
            post("/authserver/authenticate") {
                val body = call.receive<ObjectNode>()
                println(body)
                val profileUsername = body["username"].asText()
                val (token, refresh_token) = refreshToken(body["password"].asText())
                if (!isTokenValid(refresh_token)) {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respond("Invalid password: invalid access token")
                    System.err.println("Invalid password: invalid access token $refresh_token")
                    return@post
                }
                val claims = getTokenClaims(token)
                val profiles = getProfilesFor(claims["sub"].asText())
                if (profiles.isEmpty()) {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respond("No profiles")
                    System.err.println("No profiles for ${claims["sub"].asText()}")
                    return@post
                }
                val res = jsonMapper.createObjectNode().apply {
                    putObject("user").put("username", profileUsername)
                        .put("id", claims["sub"].asText()).putArray("properties")
                    put(
                        "accessToken",
                        "$token:$refresh_token"
                    ) // we include both cus yggdrasil doesn't have concept of refresh tokens
                    put("clientToken", body["clientToken"].asText())
                    putArray("availableProfiles").apply {
                        profiles.forEach {
                            add(
                                jsonMapper.createObjectNode()
                                    .apply { put("name", it.username); put("id", it.uuid.dashless()) })
                        }
                    }
                    putObject("selectedProfile").apply {
                        profiles.first { it.username == profileUsername }
                            .let {
                                setDefaultProfile(it, claims["sub"].asText());
                                put("name", it.username);
                                put(
                                    "id",
                                    it.uuid.dashless()
                                )
                            }
                    }
                }
                call.response.header("Content-Type", "application/json")
                println(jsonMapper.writeValueAsString(res))
                call.respond(jsonMapper.writeValueAsString(res))
            }
            post("/authserver/refresh") {
                val body = call.receive<ObjectNode>()
                println(body)
                val clientToken = body["clientToken"].asText()!!
                val (token, refreshToken) = body["accessToken"].asText().split(":").let { println("refresh ole: ${it[1]}"); refreshToken(it[1]) }
                println("new refresh: $refreshToken")
                val claims = getTokenClaims(token)
                val res = jsonMapper.createObjectNode()
                res.put("clientToken", clientToken)
                res.put("accessToken", "$token:$refreshToken")
                res.putObject("selectedProfile").apply {
                    getDefaultProfile(claims["sub"].asText())!!.let {
                        put("name", it.username);
                        put(
                            "id",
                            it.uuid.dashless()
                        )
                    }
                }
                println(res)
                call.respond(res)
            }
            get("/sessionserver/session/minecraft/profile/{id}") {
                val profile = getProfile(parseDashlessUUID(call.parameters["id"]!!))!!
                val res = jsonMapper.createObjectNode().apply {
                    put("id", profile.uuid.dashless())
                    put("name", profile.username)
                    putArray("properties").addObject().apply {
                        put("name", "textures")
                        put("value", texturesForProfile(profile).encodeBase64())
                    }
                }
                call.respond(res)
            }
            get("/oauth") {
                call.respondRedirect(
                    Url(
                        "${config.authorize}?response_type=code&client_id=${config.oauth.client_id}&scope=${config.oauth.scope}&redirect_uri=${
                            URLEncoder.encode(
                                config.oauth.redirect_url.toString(),
                                "UTF-8"
                            )
                        }"
                    )
                )
            }
            get("/oauth/callback") {
                val code = call.request.queryParameters["code"]!!
                val res = client.post(config.token) {
                    setBody(FormDataContent(Parameters.build {
                        set("grant_type", "authorization_code")
                        set("code", code)
                        set("redirect_uri", "http://localhost:8080/oauth/callback")
                        set("client_id", config.oauth.client_id)
                        set("client_secret", config.oauth.client_secret)
                        set("scope", config.oauth.scope)
                    }))
                }
                println("Callback: ${res.bodyAsText()}")
                call.respond(
                    """
                    Server response:
                    ${res.bodyAsText()}
                    Do not share your access and refresh token with anyone else - it can be used for login to your Minecraft profile!
                """.trimIndent()
                )
            }
            post("/oauth/check") {
                val token = call.request.authorization()!!.replace("Bearer", "").trim()
                call.respondText(isTokenValid(token).toString())
            }
            post("/api/create-profile") {
                val token = call.request.authorization()!!.replace("Bearer", "").trim()
                val name = call.request.queryParameters["name"]!!
                if (!isTokenValid(token)) {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respond("Invalid access token")
                    return@post
                }
                val claims = getTokenClaims(token)
                val sub = claims["sub"].asText()!!
                val profiles = getProfilesFor(sub)
                if(profiles.size >= claims["minecraft"]["max_profiles"].asInt(1)) {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respond("Profile maximum reached. No more profiles allowed.")
                    return@post
                }
                call.respond(insertProfile(Profile(UUID.randomUUID(), username = name, owner = sub)).asObjectId().value.toHexString())
            }
            post("/api/{profile}/upload-skin") {
                val token = call.request.authorization()!!.replace("Bearer", "").trim()
                if (!isTokenValid(token)) {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respond("Invalid access token")
                    return@post
                }
                val claims = getTokenClaims(token)
                val profileId = call.parameters["profile"]!!
                val profile = getProfile(parseDashlessUUID(profileId))!!
                if(claims["sub"].asText() != profile.owner) {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respond("Not a profile owner.")
                    return@post
                }
                val data = call.receive<ByteArray>()
                val model = call.request.queryParameters["model"] ?: "classic"
                if(model != "classic" && model != "slim") {
                    call.response.status(HttpStatusCode.BadRequest)
                    call.respond("Model must be classic or slim")
                    return@post
                }
                val id = LocalStorage.upload(data)
                updateProfile(profile.copy(skinId = id, skinModel = model))
                call.respond("")
            }
            get("/textures/{id}") {
                call.respond(LocalStorage.read(call.parameters["id"]!!))
            }
            post("/sessionserver/session/minecraft/join") {
                val body = call.receive<ObjectNode>()
                val (token, refreshToken) = body["accessToken"].asText().split(":")
                if(!isTokenValid(token)) {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respond("invalid token!")
                    return@post
                }
                call.response.status(HttpStatusCode.NoContent)
                call.respond("")
            }
            get("/sessionserver/session/minecraft/hasJoined") {
                val username = call.request.queryParameters["username"]!!
                val profile = getProfileByUsername(username)!!
                val res = jsonMapper.createObjectNode().apply {
                    put("id", profile.uuid.dashless())
                    put("name", profile.username)
                    putArray("properties").addObject().apply {
                        put("name", "textures")
                        put("value", texturesForProfile(profile).encodeBase64())
                    }
                }
                call.respond(res)
            }
        }
    }.start(wait = true)
}
