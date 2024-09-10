package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"github.com/google/uuid"
	"io/ioutil"
	"log"
	"net/http"
	"net/http/httputil"
	"strconv"
	"time"
)

func httpClient() *http.Client {
	client := &http.Client{Timeout: 10 * time.Second}
	return client
}

var url string = "https://api-testnet.bybit.com"
var api_key = "xxxx"
var apiSecret = "xxxxx"
var recv_window = "5000"
var signature = ""

func main() {
	c := httpClient()
	transferId, _ := uuid.NewUUID()
	//POST Method
	postParams := map[string]interface{}{"fromAccountType": "FUND", "toAccountType": "FUND", "amount": "500", "coin": "USDT", "from_member_id": "1637192", "to_member_id": "1558347", "transfer_id": transferId.String()}
	postEndPoint := "/v5/asset/transfer/universal-transfer"
	universalTransferRequest(c, http.MethodPost, postParams, postEndPoint)
}

func universalTransferRequest(client *http.Client, method string, params interface{}, endPoint string) []byte {
	now := time.Now()
	unixNano := now.UnixNano()
	time_stamp := unixNano / 1000000
	jsonData, err := json.Marshal(params)
	if err != nil {
		log.Fatal(err)
	}
	hmac256 := hmac.New(sha256.New, []byte(apiSecret))
	hmac256.Write([]byte(strconv.FormatInt(time_stamp, 10) + api_key + recv_window + string(jsonData[:])))
	signature = hex.EncodeToString(hmac256.Sum(nil))
	request, error := http.NewRequest("POST", url+endPoint, bytes.NewBuffer(jsonData))
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("X-BAPI-API-KEY", api_key)
	request.Header.Set("X-BAPI-SIGN", signature)
	request.Header.Set("X-BAPI-TIMESTAMP", strconv.FormatInt(time_stamp, 10))
	request.Header.Set("X-BAPI-SIGN-TYPE", "2")
	request.Header.Set("X-BAPI-RECV-WINDOW", recv_window)
	reqDump, err := httputil.DumpRequestOut(request, true)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Request Dump:\n%s", string(reqDump))
	response, error := client.Do(request)
	if error != nil {
		panic(error)
	}
	defer response.Body.Close()
	elapsed := time.Since(now).Seconds()
	fmt.Printf("\n%s took %v seconds \n", endPoint, elapsed)
	fmt.Println("response Status:", response.Status)
	fmt.Println("response Headers:", response.Header)
	body, _ := ioutil.ReadAll(response.Body)
	fmt.Println("response Body:", string(body))
	return body
}
