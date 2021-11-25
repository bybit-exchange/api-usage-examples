import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

public class Encryption {
    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        TreeMap map = new TreeMap<String, String>(
                new Comparator<String>() {
                    public int compare(String obj1, String obj2) {
                        //sort in alphabet order
                        return obj1.compareTo(obj2);
                    }
                });
        map.put("symbol", "BTCUSD");
        map.put("order_type", "Market");
        map.put("qty", "1");
        map.put("side", "Buy");
        map.put("time_in_force", "GoodTillCancel");
        map.put("timestamp", ZonedDateTime.now().toInstant().toEpochMilli()+"");

        String apiKey = "Your API Key";
        String secret = "Your API Secret";
        map.put("api_key", apiKey);

        String queryString = genQueryString(map, secret);
        OkHttpClient client = new OkHttpClient();
        RequestBody body=RequestBody.create(null,new byte[0]);
        Request request = new Request.Builder()
                .post(body)
                .url("https://api-testnet.bybit.com/v2/private/order/create?"+queryString)
                .build();
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            System.out.println(response.body().string());
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     *
     * @param params
     *        Map<String, String> params = new TreeMap<String, String>(
     *                 new Comparator<String>() {
     *                     public int compare(String obj1, String obj2) {
     *                         //sort in alphabet order
     *                         return obj1.compareTo(obj2);
     *                     }
     *                 });
     * @param secret
     * @return
     */
    private static String genQueryString(TreeMap<String, String> params, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Set<String> keySet = params.keySet();
        Iterator<String> iter = keySet.iterator();
        StringBuilder sb = new StringBuilder();
        while (iter.hasNext()) {
            String key = iter.next();
            sb.append(key + "=" + params.get(key));
            sb.append("&");
        }
        sb.deleteCharAt(sb.length() - 1);
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        return sb+"&sign="+bytesToHex(sha256_HMAC.doFinal(sb.toString().getBytes()));
    }

    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
