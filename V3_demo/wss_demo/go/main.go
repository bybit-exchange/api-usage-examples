package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"time"

	//run: go get github.com/waxdred/bybit_websocket_go
	Bwss "github.com/waxdred/bybit_websocket_go"
)

func main() {
	// Replace these values with your API key and secret
	apiKey := "XXXXXXXXXXXXXX"
	apiSecret := "XXXXXXXXXXXXXXXX"

	// Create a new WebSocket client instance
	wss := new(Bwss.WssBybit).New(true)
	defer wss.Close()

	// Add a private connection and subscribe to the wallet and position topics
	wss.AddConnPrivate(wss.WssUrl.Private(Bwss.Testnet), apiKey, apiSecret).
		AddPrivateSubs([]string{"wallet", "position"},
			func(wss *Bwss.WssBybit, sockk *Bwss.SocketMessage) {
				// Handler function for wallet
				// receive messages on sockk
				wallet := Bwss.Wallet{}
				sockk.Unmarshal(&wallet)
				log.Println(wallet.PrettyFormat())
			},
			func(wss *Bwss.WssBybit, sockk *Bwss.SocketMessage) {
				// Handler function position
				// receive messages on sockk
				position := Bwss.Position{}
				sockk.Unmarshal(&position)
				log.Println(position.PrettyFormat())
			}).Listen()

	// Add a public connection and subscribe to the orderbook.1.BTCUSDT topic
	id, _ := wss.AddConnPublic(wss.WssUrl.Perpetual(Bwss.Mainnet)).
		AddPublicSubs([]string{"orderbook.1.BTCUSDT" /*, add more subscribe*/},
			func(wss *Bwss.WssBybit, sockk *Bwss.SocketMessage) {
				// Handler function for orderbook.1.BTCUSDT messages
				fmt.Println(string(sockk.Msg))
			} /*, add more subscribe function*/).Listen()

	// Wait for 10 seconds before closing the connection to orderbook.1.BTCUSDT
	time.Sleep(time.Duration(time.Second * 10))
	// close connection id
	wss.CloseConn(id)

	// Wait for an interrupt signal before exiting the program
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt)
	// Ctrl+c stop the programme
	<-stop
}
