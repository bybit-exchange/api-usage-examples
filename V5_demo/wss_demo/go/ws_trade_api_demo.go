package main

import (
    "crypto/hmac"
    "crypto/sha256"
    "encoding/hex"
    "fmt"
    "log"
    "net/url"
    "strconv"
    "time"

    "github.com/gorilla/websocket"
)

const apiKey = "your_api_key"
const apiSecret = "your_api_secret"
const wsURL = "wss://stream-testnet.bybit.com/v5/trade"

func main() {
    u := url.URL{Scheme: "wss", Host: "stream-testnet.bybit.com", Path: "/v5/trade"}
    log.Printf("connecting to %s", u.String())

    c, _, err := websocket.DefaultDialer.Dial(u.String(), nil)
    if err != nil {
        log.Fatal("dial:", err)
    }
    defer c.Close()

    go func() {
        defer c.Close()
        for {
            _, message, err := c.ReadMessage()
            if err != nil {
                log.Println("read:", err)
                return
            }
            log.Printf("recv: %s", message)
        }
    }()

    // Authentication
    expires := time.Now().Unix()*1000 + 10000
    signature := signMessage(fmt.Sprintf("GET/realtime%d", expires), apiSecret)

    authMessage := fmt.Sprintf(`{"op":"auth","args":["%s",%d,"%s"]}`, apiKey, expires, signature)
    c.WriteMessage(websocket.TextMessage, []byte(authMessage))

    time.Sleep(time.Second * 1)

    // Subscription
    subscriptionMessage := `{
        "reqId": "sub-001",
        "headers": {
            "X-BAPI-TIMESTAMP": "` + strconv.FormatInt(time.Now().Unix()*1000, 10) + `",
            "X-BAPI-RECV-WINDOW": "8000"
        },
        "op": "order.create",
        "args": [{
            "symbol": "XRPUSDT",
            "side": "Buy",
            "orderType": "Market",
            "qty": "10",
            "category": "spot"
        }]
    }`
    c.WriteMessage(websocket.TextMessage, []byte(subscriptionMessage))

    time.Sleep(time.Second * 10) // Keep the connection open to receive messages.
}

func signMessage(message, secret string) string {
    mac := hmac.New(sha256.New, []byte(secret))
    mac.Write([]byte(message))
    return hex.EncodeToString(mac.Sum(nil))
}
