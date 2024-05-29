package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"github.com/gorilla/websocket"
	"log"
	"time"
)

func main() {
	connect()
}

func connect() {
	address := "wss://stream-testnet.bybit.com/v5/private"
	c, _, err := websocket.DefaultDialer.Dial(address, nil)
	if err != nil {
		log.Fatal("Failed to connect:", err)
	}
	defer c.Close()

	fmt.Println("Connected.")
	onOpen(c)

	ticker := time.NewTicker(20 * time.Second)
	go func() {
		for range ticker.C {
			err := c.WriteMessage(websocket.TextMessage, []byte("ping"))
			if err != nil {
				log.Println("Failed to send ping:", err)
			}
			fmt.Println("Ping sent.")
		}
	}()

	for {
		_, message, err := c.ReadMessage()
		if err != nil {
			log.Println("Failed to read message:", err)
			return
		}
		receive(string(message))
	}
}

func onOpen(c *websocket.Conn) {
	fmt.Println("Opened.")
	sendAuth(c)
	sendSubscription(c, "order")
}

func sendAuth(c *websocket.Conn) {
	key := "xxxxxxxxxxxxxx"
	secret := "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
	expires := time.Now().UnixNano()/1e6 + 10000
	val := fmt.Sprintf("GET/realtime%d", expires)

	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(val))
	signature := hex.EncodeToString(mac.Sum(nil))

	authMessage := map[string]interface{}{
		"op":   "auth",
		"args": []interface{}{key, expires, signature},
	}

	message, err := json.Marshal(authMessage)
	if err != nil {
		log.Println("Failed to marshal auth message:", err)
		return
	}
	err = c.WriteMessage(websocket.TextMessage, message)
	if err != nil {
		log.Println("Failed to send auth message:", err)
		return
	}
}

func sendSubscription(c *websocket.Conn, topic string) {
	subMessage := map[string]interface{}{
		"op":   "subscribe",
		"args": []string{topic},
	}

	message, err := json.Marshal(subMessage)
	if err != nil {
		log.Println("Failed to marshal subscription message:", err)
		return
	}
	err = c.WriteMessage(websocket.TextMessage, message)
	if err != nil {
		log.Println("Failed to send subscription message:", err)
		return
	}
	fmt.Println("Subscription sent for topic:", topic)
}

func receive(message string) {
	var data map[string]interface{}
	err := json.Unmarshal([]byte(message), &data)
	if err != nil {
		log.Println("Failed to unmarshal message:", err)
		return
	}
	fmt.Println(data)
}
