package com.bybit.api.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class import_data_to_google_sheet {
    private static final String API_KEY = "xxxxxxxxx";
    private static final String SECRET_KEY = "xxxxxxxxxxxxxxxxxxxxxx";
    private static final String URL = "https://api-testnet.bybit.com";
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String spreadSheetId = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    public static void main(String[] args) throws GeneralSecurityException, IOException {
        getSheetsService();
        String endpoint = "/v5/market/tickers";
        String params = "category=linear";
        var data = HttpRequestData(endpoint, "GET", params, "Market Ticker Data");
        String range = "A1"; // Or any appropriate range
        exportToSheet(Objects.requireNonNull(data), spreadSheetId, range);
    }

    private static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        Credential credential = getCredentials(httpTransport);

        return new Sheets.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("TestGoogleSheetApi")
                .build();
    }

    public static void exportToSheet(JsonNode listData, String spreadsheetId, String range) throws IOException, GeneralSecurityException {
        List<List<Object>> values = new ArrayList<>();

        JsonNode firstItem = listData.get(0);
        List<Object> headers = new ArrayList<>();
        firstItem.fieldNames().forEachRemaining(headers::add);

        values.add(headers); // Adding header to values

        for (int i = 0; i < listData.size(); i++) {
            List<Object> row = new ArrayList<>();
            JsonNode item = listData.get(i);
            for (Object header : headers) {
                row.add(item.get(header.toString()).asText());
            }
            values.add(row);  // Adding each row of data to values
        }

        ValueRange body = new ValueRange().setValues(values);
        AppendValuesResponse result = getSheetsService().spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();

        System.out.println(result);
    }

    private static Credential getCredentials(NetHttpTransport httpTransport) throws IOException {
        // Load client secrets
        File initialFile = new File("credentials.json");
        InputStream targetStream = new FileInputStream(initialFile);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), new InputStreamReader(targetStream));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, GsonFactory.getDefaultInstance(), clientSecrets,
                Collections.singletonList(SheetsScopes.SPREADSHEETS))
                .setDataStoreFactory(new FileDataStoreFactory(new File("tokens")))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static JsonNode HttpRequestData(String endPoint, String method, String payload, String info) {
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
                return null;
            }

            Response response = httpClient.newCall(request).execute();
            if (response.body() != null) {
                String responseBody = response.body().string();
                System.out.println(responseBody);
                System.out.println(info + " Elapsed Time : " + response.receivedResponseAtMillis());

                JsonNode rootNode = objectMapper.readTree(responseBody);
                return rootNode.path("result").path("list");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
