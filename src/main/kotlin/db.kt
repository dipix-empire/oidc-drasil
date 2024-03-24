package pw.dipix.auth

import com.fasterxml.jackson.databind.node.ObjectNode
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters
import org.bson.BsonValue
import org.bson.Document
import org.bson.UuidRepresentation
import java.util.*


val db = MongoClients.create(
    MongoClientSettings.builder()
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .applyConnectionString(ConnectionString(config.mongo_url))
        .build()
).getDatabase(config.mongo_db).apply {
    createCollection("profiles")
    createCollection("users")
}

data class Profile(val uuid: UUID, val username: String)

data class User(val sub: String, val defaultProfile: UUID)

fun <T> Document.jsoned(clas: Class<T>): T {
    return jsonMapper.readValue(toJson(), clas)
}

fun Any.documented(): Document {
    return Document.parse(jsonMapper.writeValueAsString(this))
}

fun getProfilesFor(sub: String): List<Profile> {
    println("get profiles")
    return db.getCollection("profiles").apply { println("this") }.find(Filters.eq("owner", sub))
        .apply { println("this") }.map { it.jsoned(Profile::class.java) }.toList()
}

fun insertProfile(profile: Profile, ownerSub: String): BsonValue {
    return db.getCollection("profiles").insertOne(profile.documented().append("owner", ownerSub)).insertedId!!
}

fun setDefaultProfile(profile: Profile, sub: String) {
    val user = db.getCollection("users").find(Filters.eq("sub", sub)).firstOrNull()?.jsoned(User::class.java)
    if (user == null) {
        db.getCollection("users").insertOne(User(sub, profile.uuid).documented())
        return
    }
    db.getCollection("users")
        .findOneAndReplace(Filters.eq("sub", sub), user.copy(defaultProfile = profile.uuid).documented())
}

fun getProfile(uuid: UUID): Profile? {
    return db.getCollection("profiles").find(Filters.eq("uuid", uuid.toString())).firstOrNull()?.jsoned(Profile::class.java)
}

fun getDefaultProfile(sub: String): Profile? {
    return db.getCollection("users").find(Filters.eq("sub", sub)).firstOrNull()
        ?.jsoned(User::class.java)?.defaultProfile?.let { getProfile(it) }
}