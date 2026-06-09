import com.alibaba.fastjson.JSON
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.io.IOException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.time.ZonedDateTime
import java.{lang, util}

object Encryption {
  val ApiKey = "xxxxxxxxxx"
  val ApiSecret = "xxxxxxxxxxxxxxxx"
  val Timestamp: String = ZonedDateTime.now.toInstant.toEpochMilli.toString
  val RecvWindow = "5000"

  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  def main(args: Array[String]): Unit = {
    val encryption = new Encryption
    encryption.placeOrder()
    encryption.getOpenOrder()
  }

  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  private def generatePostSignature(params: util.Map[String, AnyRef]): String = {
    val sha256Hmac = Mac.getInstance("HmacSHA256")
    val secretKey = new SecretKeySpec(ApiSecret.getBytes, "HmacSHA256")
    sha256Hmac.init(secretKey)
    val paramJson = JSON.toJSONString(params)
    val payload = Timestamp + ApiKey + RecvWindow + paramJson
    bytesToHex(sha256Hmac.doFinal(payload.getBytes))
  }

  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  private def generateGetSignature(params: util.Map[String, AnyRef]): String = {
    val queryString = generateQueryString(params)
    val payload = Timestamp + ApiKey + RecvWindow + queryString
    val sha256Hmac = Mac.getInstance("HmacSHA256")
    val secretKey = new SecretKeySpec(ApiSecret.getBytes, "HmacSHA256")
    sha256Hmac.init(secretKey)
    bytesToHex(sha256Hmac.doFinal(payload.getBytes))
  }

  private def bytesToHex(hash: Array[Byte]): String = {
    val hexString = new lang.StringBuilder
    for (value <- hash) {
      val hex = Integer.toHexString(0xff & value)
      if (hex.length == 1) {
        hexString.append('0')
      }
      hexString.append(hex)
    }
    hexString.toString
  }

  private def generateQueryString(map: util.Map[String, AnyRef]): lang.StringBuilder = {
    val iterator = map.keySet.iterator
    val query = new lang.StringBuilder
    while (iterator.hasNext) {
      val key = iterator.next
      query.append(key).append("=").append(map.get(key)).append("&")
    }
    query.deleteCharAt(query.length - 1)
    query
  }
}

class Encryption {
  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  def placeOrder(): Unit = {
    val params = new util.HashMap[String, AnyRef]
    params.put("category", "linear")
    params.put("symbol", "BTCUSDT")
    params.put("side", "Buy")
    params.put("positionIdx", 0: java.lang.Integer)
    params.put("orderType", "Limit")
    params.put("qty", "0.001")
    params.put("price", "18900")
    params.put("timeInForce", "GTC")

    val signature = Encryption.generatePostSignature(params)
    val jsonPayload = JSON.toJSONString(params)
    val client = new OkHttpClient.Builder().build()
    val mediaType = MediaType.parse("application/json")
    val body = RequestBody.create(jsonPayload, mediaType)
    val request = new Request.Builder()
      .url("https://api-testnet.bybit.com/v5/order/create")
      .post(body)
      .addHeader("X-BAPI-API-KEY", Encryption.ApiKey)
      .addHeader("X-BAPI-SIGN", signature)
      .addHeader("X-BAPI-SIGN-TYPE", "2")
      .addHeader("X-BAPI-TIMESTAMP", Encryption.Timestamp)
      .addHeader("X-BAPI-RECV-WINDOW", Encryption.RecvWindow)
      .addHeader("Content-Type", "application/json")
      .build()

    val call = client.newCall(request)
    try {
      val response = call.execute()
      assert(response.body() != null)
      println(response.body().string())
    } catch {
      case exception: Exception => exception.printStackTrace()
    }
  }

  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  def getOpenOrder(): Unit = {
    val params = new util.HashMap[String, AnyRef]
    params.put("category", "linear")
    params.put("symbol", "BTCUSDT")
    params.put("settleCoin", "USDT")

    val signature = Encryption.generateGetSignature(params)
    val queryString = Encryption.generateQueryString(params)
    val client = new OkHttpClient.Builder().build()
    val request = new Request.Builder()
      .url("https://api-testnet.bybit.com/v5/order/realtime?" + queryString.toString)
      .get()
      .addHeader("X-BAPI-API-KEY", Encryption.ApiKey)
      .addHeader("X-BAPI-SIGN", signature)
      .addHeader("X-BAPI-SIGN-TYPE", "2")
      .addHeader("X-BAPI-TIMESTAMP", Encryption.Timestamp)
      .addHeader("X-BAPI-RECV-WINDOW", Encryption.RecvWindow)
      .build()

    val call = client.newCall(request)
    try {
      val response = call.execute()
      assert(response.body() != null)
      println(response.body().string())
    } catch {
      case exception: IOException => exception.printStackTrace()
    }
  }
}
