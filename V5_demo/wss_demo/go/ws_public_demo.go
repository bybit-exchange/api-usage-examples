package main

import (
	"encoding/json"
	"fmt"
	"github.com/gorilla/websocket"
	"log"
	"time"
)

func main() {
	topic := "orderbook.50.BTCUSDT"
	connectWS(topic)
}

func connectWS(topic string) {
	address := "wss://stream-testnet.bybit.com/v5/public/linear"
	c, _, err := websocket.DefaultDialer.Dial(address, nil)
	if err != nil {
		log.Fatal("Failed to connect:", err)
	}
	defer c.Close()

	fmt.Println("Connected.")

	subscribe(c, topic)

	for {
		_, message, err := c.ReadMessage()
		if err != nil {
			log.Println("Failed to read message:", err)
			return
		}
		receive(string(message))
	}
}

func subscribe(c *websocket.Conn, topic string) {
	subscribeRequest := map[string]interface{}{
		"op":   "subscribe",
		"args": []string{topic},
	}
	message, err := json.Marshal(subscribeRequest)
	if err != nil {
		log.Println("Failed to marshal JSON:", err)
		return
	}
	err = c.WriteMessage(websocket.TextMessage, message)
	if err != nil {
		log.Println("Failed to send subscribe message:", err)
		return
	}
}

func receive(message string) {
	var data map[string]interface{}
	err := json.Unmarshal([]byte(message), &data)
	if err != nil {
		log.Println("Failed to unmarshal JSON:", err)
		return
	}
	fmt.Println(data)
}

func keepAlive(c *websocket.Conn) {
	for {
		err := c.WriteMessage(websocket.PingMessage, nil)
		if err != nil {
			log.Println("Ping failed:", err)
			return
		}
		time.Sleep(time.Second * 10)
	}
}
