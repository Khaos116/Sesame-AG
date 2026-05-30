package io.github.aoguai.sesameag.util

import android.util.Base64
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object MyCryptoUtils {

  private const val AES_MODE = "AES/GCM/NoPadding"
  private const val TL_KEY_SPEC = "AES"

  // GCM 规范：IV 长度推荐为 12 字节，Tag 长度推荐为 16 字节（128位）
  private const val IV_LENGTH = 12
  private const val TAG_LENGTH_BIT = 128

  /**
   * 加密方法
   * @param plainText 要加密的明文字符串
   * @param secretKey 16字节(128位)或32字节(256位)的自定义密钥
   * @return 加密后并经过 Base64 编码的短字符串
   */
  fun encrypt(plainText: String, secretKey: SecretKey = generateKeyFromString("1234567890123456")): String {
    val cipher = Cipher.getInstance(AES_MODE)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)

    // 获取系统自动生成的随机 IV
    val iv = cipher.iv
    val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

    // 将 IV 和加密后的密文拼接到同一个字节数组中，方便传输
    val byteBuffer = ByteBuffer.allocate(iv.size + encryptedBytes.size)
    byteBuffer.put(iv)
    byteBuffer.put(encryptedBytes)
    val combinedBytes = byteBuffer.array()

    // 使用 Base64.NO_WRAP 去掉换行符，进一步缩小体积
    return Base64.encodeToString(combinedBytes, Base64.NO_WRAP or Base64.URL_SAFE)
  }

  /**
   * 解密方法
   * @param encryptedText 加密后的 Base64 字符串
   * @param secretKey 加密时使用的同一个密钥
   * @return 解密后的明文字符串
   */
  fun decrypt(encryptedText: String, secretKey: SecretKey = generateKeyFromString("1234567890123456")): String {
    // 1. 解码 Base64 字符串
    val combinedBytes = Base64.decode(encryptedText, Base64.NO_WRAP or Base64.URL_SAFE)

    // 2. 分离出 IV 和真正的密文
    val byteBuffer = ByteBuffer.wrap(combinedBytes)
    val iv = ByteArray(IV_LENGTH)
    byteBuffer.get(iv)

    val encryptedBytes = ByteArray(byteBuffer.remaining())
    byteBuffer.get(encryptedBytes)

    // 3. 配置解密参数并解密
    val cipher = Cipher.getInstance(AES_MODE)
    val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

    val decryptedBytes = cipher.doFinal(encryptedBytes)
    return String(decryptedBytes, Charsets.UTF_8)
  }

  /**
   * 辅助方法：将自定义的字符串密码转换为标准的 SecretKey 对象
   * 注意：passwordStr 的长度必须是 16、24 或 32 个字节（对应 AES-128, 192, 256）
   */
  fun generateKeyFromString(passwordStr: String): SecretKey {
    val keyBytes = passwordStr.toByteArray(Charsets.UTF_8)
    require(keyBytes.size == 16 || keyBytes.size == 24 || keyBytes.size == 32) {
      "密钥长度必须是 16, 24 或 32 字节！当前长度: ${keyBytes.size}"
    }
    return SecretKeySpec(keyBytes, TL_KEY_SPEC)
  }
}