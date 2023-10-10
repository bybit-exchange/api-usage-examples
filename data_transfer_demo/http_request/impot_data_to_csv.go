package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/csv"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/http/httputil"
	"os"
	"strconv"
	"time"
)

const apiUrl = "https://api.bybit.com/v5/market/tickers?category=linear"

var url string = "https://api-testnet.bybit.com"
var apiKey = "xxxxxxxxxxxxx"
var apiSecret = "xxxxxxxxxxxxxxxxxx"
var recvWindow = "5000"
var signature = ""

type ResponseData struct {
	RetCode int    `json:"retCode"`
	RetMsg  string `json:"retMsg"`
	Result  struct {
		Category string `json:"category"`
		List     []struct {
			Symbol        string `json:"symbol"`
			Bid1Price     string `json:"bid1Price"`
			Bid1Size      string `json:"bid1Size"`
			Ask1Price     string `json:"ask1Price"`
			Ask1Size      string `json:"ask1Size"`
			LastPrice     string `json:"lastPrice"`
			PrevPrice24h  string `json:"prevPrice24h"`
			Price24hPcnt  string `json:"price24hPcnt"`
			HighPrice24h  string `json:"highPrice24h"`
			LowPrice24h   string `json:"lowPrice24h"`
			Turnover24h   string `json:"turnover24h"`
			Volume24h     string `json:"volume24h"`
			UsdIndexPrice string `json:"usdIndexPrice"`
		} `json:"list"`
	} `json:"result"`
}

func main() {
	// Make API request
	c := httpClient()
	getEndPoint := "/v5/market/tickers"
	getParams := "category=linear"
	body, err := getRequest(c, http.MethodGet, getParams, getEndPoint)
	if err != nil {
		log.Fatal("Failed to make API request:", err)
	}

	// Decode JSON response from the returned byte slice
	var responseData ResponseData
	if err := json.Unmarshal(body, &responseData); err != nil {
		log.Fatal("Failed to decode JSON response:", err)
	}

	// Open a CSV file for writing
	file, err := os.Create("BybitMarketTickerData.csv")
	if err != nil {
		log.Fatal("Cannot create file", err)
	}
	defer func(file *os.File) {
		_ = file.Close()
	}(file)

	// Create a new CSV writer
	writer := csv.NewWriter(file)
	defer writer.Flush()

	// Write the headers to the CSV file
	headers := []string{
		"Symbol", "Bid 1 Price", "Bid 1 Size", "Ask 1 Price", "Ask 1 Size",
		"Last Price", "Previous Price 24h", "Price Change % 24h", "High Price 24h",
		"Low Price 24h", "Turnover 24h", "Volume 24h", "USD Index Price",
	}
	_ = writer.Write(headers)

	// Loop through the responseData and write to the CSV file
	for _, result := range responseData.Result.List {
		record := []string{
			result.Symbol,
			result.Bid1Price,
			result.Bid1Size,
			result.Ask1Price,
			result.Ask1Size,
			result.LastPrice,
			result.PrevPrice24h,
			result.Price24hPcnt,
			result.HighPrice24h,
			result.LowPrice24h,
			result.Turnover24h,
			result.Volume24h,
			result.UsdIndexPrice,
		}
		_ = writer.Write(record)
	}
}

func httpClient() *http.Client {
	client := &http.Client{Timeout: 10 * time.Second}
	return client
}

func getRequest(client *http.Client, method string, params string, endPoint string) ([]byte, error) {
	now := time.Now()
	unixNano := now.UnixNano()
	timeStamp := unixNano / 1000000
	hmac256 := hmac.New(sha256.New, []byte(apiSecret))
	hmac256.Write([]byte(strconv.FormatInt(timeStamp, 10) + apiKey + recvWindow + params))
	signature = hex.EncodeToString(hmac256.Sum(nil))
	request, err := http.NewRequest(method, url+endPoint+"?"+params, nil)
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
	var response, _ = client.Do(request)
	if err != nil {
		panic(err.Error())
	}
	defer func(Body io.ReadCloser) {
		_ = Body.Close()
	}(response.Body)

	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	_ = response.Body.Close()
	return body, nil
}
