package com.bybit.api.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class import_data_to_csv {
    private static final String API_KEY = "xxxxxxxxxxxxxxxx";
    private static final String SECRET_KEY = "xxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    private static final String URL = "https://api-testnet.bybit.com";
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        String endpoint = "/v5/market/tickers";
        String params = "category=linear";
        HTTP_Request(endpoint, "GET", params, "Market Ticker Data");
    }

    public static void HTTP_Request(String endPoint, String method, String payload, String info) {
        try {
            long timestamp = Instant.now().toEpochMilli();
            String signature = genSignature(payload, timestamp);

            Request request;
            if ("GET".equals(method)) {
                HttpUrl.Builder httpBuilder = Objects.requireNonNull(HttpUrl.parse(URL + endPoint)).newBuilder();
                httpBuilder.addQueryParameter("category", "linear");
                request = new Request.Builder()
                        .url(httpBuilder.build())
                        .addHeader("X-BAPI-API-KEY", API_KEY)
                        .addHeader("X-BAPI-SIGN", signature)
                        .addHeader("X-BAPI-SIGN-TYPE", "2")
                        .addHeader("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                        .addHeader("X-BAPI-RECV-WINDOW", "5000")
                        .addHeader("Content-Type", "application/json")
                        .build();
            } else {
                // Add POST logic here if needed
                return;
            }

            Response response = httpClient.newCall(request).execute();
            if (response.body() != null) {
                String responseBody = response.body().string();
                System.out.println(responseBody);
                System.out.println(info + " Elapsed Time : " + response.receivedResponseAtMillis());

                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode listData  = rootNode.path("result").path("list");

                try (CSVWriter writer = new CSVWriter(new FileWriter("BybitMarketDataTicker.csv"))) {
                    if (listData.size() > 0) {
                        JsonNode firstItem = listData.get(0);
                        List<String> headers = new ArrayList<>();
                        firstItem.fieldNames().forEachRemaining(headers::add);
                        writer.writeNext(headers.toArray(new String[0]));

                        for (JsonNode item : listData) {
                            List<String> values = new ArrayList<>();
                            for (String header : headers) {
                                values.add(item.get(header).asText());
                            }
                            writer.writeNext(values.toArray(new String[0]));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String genSignature(String payload, long timestamp) throws Exception {
        String paramStr = timestamp + API_KEY + "5000" + payload;
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(paramStr.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
