package com.isivoltpro.maginaolivo.feature.account

import java.io.File
import java.io.ByteArrayOutputStream
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

class AndroidAccountSessionStore(private val file: File, private val keyAlias: String, private val scope: String) : AccountSessionStore {
    private val atomic=AtomicFile(file)
    override fun read():AccountRecord = secure {
        if(!file.exists() && !File(file.path+".bak").exists()) return@secure AccountRecord()
        val bytes=atomic.openRead().use { input ->
            val output=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true) { val count=input.read(buffer);if(count<0)break;check(output.size()+count<=65_536);output.write(buffer,0,count) }
            output.toByteArray()
        }
        check(bytes.size>=30 && bytes[0]==1.toByte() && bytes[1]==12.toByte())
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,bytes.copyOfRange(2,14)))
        cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        decode(String(cipher.doFinal(bytes.copyOfRange(14,bytes.size)),Charsets.UTF_8))
    }
    override fun write(record: AccountRecord):Unit = secure {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        check(cipher.iv.size==12)
        val bytes=byteArrayOf(1,12)+cipher.iv+cipher.doFinal(encode(record).toByteArray(Charsets.UTF_8))
        check(bytes.size<=65_536)
        val output=atomic.startWrite()
        try { output.write(bytes);atomic.finishWrite(output) }
        catch(e:Exception) { atomic.failWrite(output);throw e }
    }
    override fun clear():Unit = secure {
        atomic.delete()
        check(!file.exists() && !File(file.path+".bak").exists() && !File(file.path+".new").exists())
    }
    private fun key(create:Boolean):SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias,null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build())
        }.generateKey()
    }
    private inline fun <T> secure(action:()->T):T = try { action() }
        catch (_:Exception) { throw AuthFailure(AuthFailure.Kind.STORAGE) }
    private fun encode(record:AccountRecord)=buildJsonObject {
        put("version",1)
        record.session?.let { put("session",sessionJson(it)) }
        record.recovery?.let { put("recovery",sessionJson(it)) }
        record.pending?.let { link -> put("pending",buildJsonObject {
            put("email",link.email);put("purpose",link.purpose.name);put("verifier",link.verifier);put("created_at",link.createdAt.toString())
        }) }
    }.toString()
    private fun sessionJson(value:AuthSession)=buildJsonObject {
        put("id",value.userId.toString());put("email",value.email);put("access",value.accessToken)
        put("refresh",value.refreshToken);put("expires_at",value.expiresAt.toString())
    }
    private fun decode(value:String):AccountRecord {
        val json=Json.parseToJsonElement(value).jsonObject;check(json.getValue("version").jsonPrimitive.int==1)
        fun session(key:String):AuthSession?=json[key]?.jsonObject?.let {
            AuthSession(UUID.fromString(it.text("id")),it.text("email").also { email -> check(AccountValidation.email(email)) },
                it.text("access").also { token -> check(token.isNotBlank()) },it.text("refresh").also { token -> check(token.isNotBlank()) },Instant.parse(it.text("expires_at")))
        }
        val pending=json["pending"]?.jsonObject?.let {
            PendingAuthLink(it.text("email").also { email -> check(AccountValidation.email(email)) },LinkPurpose.valueOf(it.text("purpose")),
                it.text("verifier").also { verifier -> check(Regex("^[A-Za-z0-9_-]{43,128}$").matches(verifier)) },Instant.parse(it.text("created_at")))
        }
        return AccountRecord(session("session"),pending,session("recovery"))
    }
    private fun JsonObject.text(key:String)=getValue(key).jsonPrimitive.content
}
