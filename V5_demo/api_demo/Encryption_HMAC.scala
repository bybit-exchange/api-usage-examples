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


/**
 * a sample for how to create & get an order for contract v5 - Linear perpetual
 */
object Encryption {
  val API_KEY = "xxxxxxxxxx"
  val API_SECRET = "xxxxxxxxxxxxxxxx"
  val TIMESTAMP: String = ZonedDateTime.now.toInstant.toEpochMilli.toString
  val RECV_WINDOW = "5000"

  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  def main(args: Array[String]): Unit = {
    val encryptionTest = new Encryption
    encryptionTest.placeOrder()
    encryptionTest.getOpenOrder();
  }

  /**
   * The way to generate the sign for POST requests
   *
   * @param params : Map input parameters
   * @return signature used to be a parameter in the header
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   */
  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  private def genPostSign(params: util.Map[String, AnyRef]) = {
    val sha256_HMAC = Mac.getInstance("HmacSHA256")
    val secret_key = new SecretKeySpec(API_SECRET.getBytes, "HmacSHA256")
    sha256_HMAC.init(secret_key)
    val paramJson = JSON.toJSONString(params)
    val sb = TIMESTAMP + API_KEY + RECV_WINDOW + paramJson
    bytesToHex(sha256_HMAC.doFinal(sb.getBytes))
  }

  /**
   * The way to generate the sign for GET requests
   *
   * @param params : Map input parameters
   * @return signature used to be a parameter in the header
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   */
  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  private def genGetSign(params: util.Map[String, AnyRef]) = {
    val sb = genQueryStr(params)
    val queryStr = TIMESTAMP + API_KEY + RECV_WINDOW + sb
    val sha256_HMAC = Mac.getInstance("HmacSHA256")
    val secret_key = new SecretKeySpec(API_SECRET.getBytes, "HmacSHA256")
    sha256_HMAC.init(secret_key)
    bytesToHex(sha256_HMAC.doFinal(queryStr.getBytes))
  }

  /**
   * To convert bytes to hex
   *
   * @param hash
   * @return hex string
   */
  private def bytesToHex(hash: Array[Byte]) = {
    val hexString = new lang.StringBuilder
    for (b <- hash) {
      val hex = Integer.toHexString(0xff & b)
      if (hex.length == 1) hexString.append('0')
      hexString.append(hex)
    }
    hexString.toString
  }

  /**
   * To generate query string for GET requests
   *
   * @param map
   * @return
   */
  private def genQueryStr(map: util.Map[String, AnyRef]) = {
    val keySet = map.keySet
    val iter = keySet.iterator
    val sb = new lang.StringBuilder
    while (iter.hasNext) {
      val key = iter.next
      sb.append(key).append("=").append(map.get(key)).append("&")
    }
    sb.deleteCharAt(sb.length - 1)
    sb
  }
}

class Encryption {
  /**
   * POST: place a Linear perp order - contract v5
   */
  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  def placeOrder(): Unit = {
    val map = new util.HashMap[String, AnyRef]
    map.put("category", "linear")
    map.put("symbol", "BTCUSDT")
    map.put("side", "Buy")
    map.put("positionIdx", 0: java.lang.Integer)
    map.put("orderType", "Limit")
    map.put("qty", "0.001")
    map.put("price", "18900")
    map.put("timeInForce", "GTC")
    val signature = Encryption.genPostSign(map)
    val jsonMap = JSON.toJSONString(map)
    val client = new OkHttpClient.Builder().build()
    val mediaType = MediaType.parse("application/json")
    val body = RequestBody.create(jsonMap, mediaType)
    val request = new Request.Builder()
      .url("https://api-testnet.bybit.com/v5/order/create")
      .post(body)
      .addHeader("X-BAPI-API-KEY", Encryption.API_KEY)
      .addHeader("X-BAPI-SIGN", signature)
      .addHeader("X-BAPI-SIGN-TYPE", "2")
      .addHeader("X-BAPI-TIMESTAMP", Encryption.TIMESTAMP)
      .addHeader("X-BAPI-RECV-WINDOW", Encryption.RECV_WINDOW)
      .addHeader("Content-Type", "application/json")
      .build()

    val call = client.newCall(request)
    try {
      val response = call.execute()
      assert(response.body() != null)
      println(response.body().string())
    } catch {
      case e: Exception => e.printStackTrace()
    }

  }

  /**
   * GET: query unfilled order
   *
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   */
  @throws[NoSuchAlgorithmException]
  @throws[InvalidKeyException]
  def getOpenOrder(): Unit = {
    val map = new util.HashMap[String, AnyRef]
    map.put("category", "linear")
    map.put("symbol", "BTCUSDT")
    map.put("settleCoin", "USDT")
    val signature = Encryption.genGetSign(map)
    val sb = Encryption.genQueryStr(map)
    val client = new OkHttpClient.Builder().build()

    val request = new Request.Builder()
      .url("https://api-testnet.bybit.com/v5/order/realtime?" + sb.toString)
      .get()
      .addHeader("X-BAPI-API-KEY", Encryption.API_KEY)
      .addHeader("X-BAPI-SIGN", signature)
      .addHeader("X-BAPI-SIGN-TYPE", "2")
      .addHeader("X-BAPI-TIMESTAMP", Encryption.TIMESTAMP)
      .addHeader("X-BAPI-RECV-WINDOW", Encryption.RECV_WINDOW)
      .build()

    val call = client.newCall(request)
    try {
      val response = call.execute()
      assert(response.body() != null)
      println(response.body().string())
    } catch {
      case e: IOException =>
        e.printStackTrace()
    }
  }
}


