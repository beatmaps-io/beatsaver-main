package io.beatmaps.api.user

import io.beatmaps.common.dbo.UserDao
import io.jsonwebtoken.security.Keys
import io.ktor.util.hex
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object UserCrypto {
    private const val defaultSecretEncoded = "ZsEgU9mLHT1Vg+K5HKzlKna20mFQi26ZbB92zILrklNxV5Yxg8SyEcHVWzkspEiCCGkRB89claAWbFhglykfUA=="
    private val secret = Base64.getDecoder().decode(System.getenv("BSHASH_SECRET") ?: defaultSecretEncoded)
    private val sessionSecret = Base64.getDecoder().decode(System.getenv("SESSION_ENCRYPT_SECRET") ?: defaultSecretEncoded)
    private val secretEncryptKey = SecretKeySpec(sessionSecret, "AES")
    private val ephemeralIv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    private val envIv = System.getenv("BSIV")?.let { Base64.getDecoder().decode(it) } ?: ephemeralIv

    fun md5(input: String) =
        MessageDigest.getInstance("MD5").let { md ->
            BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
        }

    fun keyForUser(user: UserDao) = key(user.password ?: "")

    fun key(pwdHash: String = ""): java.security.Key =
        Keys.hmacShaKeyFor(secret + pwdHash.toByteArray())

    private fun encryptDecrypt(mode: Int, input: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(mode, secretEncryptKey, IvParameterSpec(iv))
        return cipher.doFinal(input)
    }

    fun encrypt(input: String, iv: ByteArray = envIv) = hex(encryptDecrypt(Cipher.ENCRYPT_MODE, input.toByteArray(), iv))
    fun decrypt(input: String, iv: ByteArray = envIv) = String(encryptDecrypt(Cipher.DECRYPT_MODE, hex(input), iv))

    fun getHash(userId: String, salt: ByteArray = secret) = MessageDigest.getInstance("SHA1").let {
        it.update(salt + userId.toByteArray())
        hex(it.digest())
    }
}
