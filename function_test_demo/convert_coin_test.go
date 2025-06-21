package main

import (
	"context"
	"errors"
	"fmt"
	bybit "github.com/bybit-exchange/bybit.go.api"
)

func main() {
	client := bybit.NewBybitHttpClient("xxx", "xxx", bybit.WithBaseURL(bybit.TESTNET))
	GetConvertCoinList(client)
	response, err := RequestConvertQuote(client)
	if err != nil {
		fmt.Println("Error requesting convert quote:", err)
		return
	}
	quoteTxId, err := GetQuoteTxId(response)
	if err != nil {
		fmt.Println("Error getting quote Tx ID:", err)
		return
	}
	ConfirmConvertQuote(client, quoteTxId)
	GetConvertStatus(client, quoteTxId)
	GetConvertHistory(client)
}

func GetQuoteTxId(response *bybit.ServerResponse) (quoteTxId string, err error) {
	result, ok := response.Result.(map[string]interface{})
	if !ok {
		errMsg := "Conversion of response.Result to map[string]interface{} failed"
		fmt.Println(errMsg)
		return "", errors.New(errMsg)
	}

	// Now you can safely retrieve the quoteTxId
	quoteTxId, ok = result["quoteTxId"].(string)
	if !ok {
		errMsg := "Retrieval of quoteTxId failed"
		fmt.Println(errMsg)
		return "", errors.New(errMsg)
	}

	fmt.Println("quoteTxId: ", quoteTxId)
	return quoteTxId, nil
}

func GetConvertCoinList(client *bybit.Client) {
	params := map[string]interface{}{"coin": "USDT", "accountType": "eb_convert_uta"}
	response, err := client.NewUtaBybitServiceWithParams(params).GetConvertCoinList(context.Background())
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(bybit.PrettyPrint(response))
}

func GetConvertStatus(client *bybit.Client, quoteTxId string) {
	params := map[string]interface{}{"quoteTxId": quoteTxId, "accountType": "eb_convert_uta"}
	response, err := client.NewUtaBybitServiceWithParams(params).GetConvertStatus(context.Background())
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(bybit.PrettyPrint(response))
}

func GetConvertHistory(client *bybit.Client) {
	params := map[string]interface{}{"accountType": "eb_convert_uta"}
	response, err := client.NewUtaBybitServiceWithParams(params).GetConvertHistory(context.Background())
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(bybit.PrettyPrint(response))
}

func RequestConvertQuote(client *bybit.Client) (response *bybit.ServerResponse, err error) {
	params := map[string]interface{}{"fromCoin": "BTC", "toCoin": "ETH", "requestCoin": "BTC", "requestAmount": "1", "accountType": "eb_convert_uta"}
	return client.NewUtaBybitServiceWithParams(params).RequestConvertQuote(context.Background())
}

func ConfirmConvertQuote(client *bybit.Client, quoteTxId string) {
	params := map[string]interface{}{"quoteTxId": quoteTxId}
	response, err := client.NewUtaBybitServiceWithParams(params).ConfirmConvertQuote(context.Background())
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(bybit.PrettyPrint(response))
}
