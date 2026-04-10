package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"net/http/httputil"
	"strconv"
	"time"
)

const (
	url        = "https://api-testnet.bybit.com"
	apiKey     = "XXXXXXXXXX"
	apiSecret  = "XXXXXXXXXX"
	recvWindow = "5000"
)

func httpClient() *http.Client {
	return &http.Client{Timeout: 10 * time.Second}
}

func main() {
	client := httpClient()

	postParams := map[string]interface{}{
		"category":    "linear",
		"symbol":      "BTCUSDT",
		"side":        "Buy",
		"positionIdx": 0,
		"orderType":   "Limit",
		"qty":         "0.001",
		"price":       "10000",
		"timeInForce": "GTC",
	}
	postRequest(client, http.MethodPost, postParams, "/v5/order/create")

	getParams := "category=linear&settleCoin=USDT"
	getRequest(client, http.MethodGet, getParams, "/v5/order/realtime")
}

func getRequest(client *http.Client, method string, params string, endpoint string) []byte {
	now := time.Now()
	timeStamp := now.UnixNano() / int64(time.Millisecond)
	hmac256 := hmac.New(sha256.New, []byte(apiSecret))
	hmac256.Write([]byte(strconv.FormatInt(timeStamp, 10) + apiKey + recvWindow + params))
	signature := hex.EncodeToString(hmac256.Sum(nil))

	request, err := http.NewRequest(method, url+endpoint+"?"+params, nil)
	if err != nil {
		log.Fatal(err)
	}

	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("X-BAPI-API-KEY", apiKey)
	request.Header.Set("X-BAPI-SIGN", signature)
	request.Header.Set("X-BAPI-TIMESTAMP", strconv.FormatInt(timeStamp, 10))
	request.Header.Set("X-BAPI-SIGN-TYPE", "2")
	request.Header.Set("X-BAPI-RECV-WINDOW", recvWindow)

	reqDump, err := httputil.DumpRequestOut(request, true)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Request Dump:\n%s", string(reqDump))

	response, err := client.Do(request)
	if err != nil {
		panic(err)
	}
	defer response.Body.Close()

	elapsed := time.Since(now).Seconds()
	fmt.Printf("\n%s took %v seconds \n", endpoint, elapsed)
	fmt.Println("response Status:", response.Status)
	fmt.Println("response Headers:", response.Header)

	body, _ := ioutil.ReadAll(response.Body)
	fmt.Println("response Body:", string(body))
	return body
}

func postRequest(client *http.Client, method string, params interface{}, endpoint string) []byte {
	now := time.Now()
	timeStamp := now.UnixNano() / int64(time.Millisecond)
	jsonData, err := json.Marshal(params)
	if err != nil {
		log.Fatal(err)
	}

	hmac256 := hmac.New(sha256.New, []byte(apiSecret))
	hmac256.Write([]byte(strconv.FormatInt(timeStamp, 10) + apiKey + recvWindow + string(jsonData)))
	signature := hex.EncodeToString(hmac256.Sum(nil))

	request, err := http.NewRequest(method, url+endpoint, bytes.NewBuffer(jsonData))
	if err != nil {
		log.Fatal(err)
	}

	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("X-BAPI-API-KEY", apiKey)
	request.Header.Set("X-BAPI-SIGN", signature)
	request.Header.Set("X-BAPI-TIMESTAMP", strconv.FormatInt(timeStamp, 10))
	request.Header.Set("X-BAPI-SIGN-TYPE", "2")
	request.Header.Set("X-BAPI-RECV-WINDOW", recvWindow)

	reqDump, err := httputil.DumpRequestOut(request, true)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Request Dump:\n%s", string(reqDump))

	response, err := client.Do(request)
	if err != nil {
		panic(err)
	}
	defer response.Body.Close()

	elapsed := time.Since(now).Seconds()
	fmt.Printf("\n%s took %v seconds \n", endpoint, elapsed)
	fmt.Println("response Status:", response.Status)
	fmt.Println("response Headers:", response.Header)

	body, _ := ioutil.ReadAll(response.Body)
	fmt.Println("response Body:", string(body))
	return body
}
