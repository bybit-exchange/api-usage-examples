package main

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"os"
	"os/signal"
	"time"

	"github.com/gorilla/websocket"
)

// ---------- Config ----------
const (
	WSURL = "wss://stream-testnet.bybits.org/v5/realtime/spot"
	TOPIC = "ob.rpi.1.sbe.BTCUSDT"
)

// ---------- SBE Helpers ----------
func readI64LE(buf []byte, off *int) (int64, error) {
	if *off+8 > len(buf) {
		return 0, fmt.Errorf("readI64LE: out of range")
	}
	v := int64(binary.LittleEndian.Uint64(buf[*off : *off+8]))
	*off += 8
	return v, nil
}

func readI8(buf []byte, off *int) (int8, error) {
	if *off+1 > len(buf) {
		return 0, fmt.Errorf("readI8: out of range")
	}
	v := int8(buf[*off])
	*off++
	return v, nil
}

func readVarString8(buf []byte, off *int) (string, error) {
	if *off+1 > len(buf) {
		return "", fmt.Errorf("readVarString8: no length byte")
	}
	length := int(buf[*off])
	*off++
	if *off+length > len(buf) {
		return "", fmt.Errorf("readVarString8: length out of range")
	}
	s := string(buf[*off : *off+length])
	*off += length
	return s, nil
}

func applyExp(value int64, exp int8) float64 {
	if exp >= 0 {
		return float64(value) / math.Pow10(int(exp))
	}
	return float64(value) * math.Pow10(-int(exp))
}

// ---------- SBE Parser ----------
type SBEBestOBRpiParser struct {
	HeaderSize int
	BodySize   int
	TargetID   uint16
}

func NewSBEBestOBRpiParser() *SBEBestOBRpiParser {
	return &SBEBestOBRpiParser{
		HeaderSize: 8,   // 4 fields of uint16
		BodySize:   104, // 12x int64 + 2x int8
		TargetID:   20000,
	}
}

func (p *SBEBestOBRpiParser) Parse(data []byte) (map[string]interface{}, error) {
	if len(data) < p.HeaderSize+p.BodySize {
		return nil, fmt.Errorf("insufficient data for parsing")
	}

	off := 0
	blockLength := binary.LittleEndian.Uint16(data[off : off+2])
	templateID := binary.LittleEndian.Uint16(data[off+2 : off+4])
	off += 8

	if templateID != p.TargetID {
		return nil, fmt.Errorf("unsupported template ID: %d", templateID)
	}

	// Parse body
	fields := make([]int64, 12)
	for i := 0; i < 12; i++ {
		val, err := readI64LE(data, &off)
		if err != nil {
			return nil, err
		}
		fields[i] = val
	}
	priceExp, _ := readI8(data, &off)
	sizeExp, _ := readI8(data, &off)

	symbol, err := readVarString8(data, &off)
	if err != nil {
		return nil, err
	}

	// Apply exponents
	askNormalPrice := applyExp(fields[4], priceExp)
	askNormalSize := applyExp(fields[5], sizeExp)
	bidNormalPrice := applyExp(fields[8], priceExp)
	bidNormalSize := applyExp(fields[9], sizeExp)

	return map[string]interface{}{
		"blockLength":     blockLength,
		"templateID":      templateID,
		"symbol":          symbol,
		"askNormalPrice":  askNormalPrice,
		"askNormalSize":   askNormalSize,
		"bidNormalPrice":  bidNormalPrice,
		"bidNormalSize":   bidNormalSize,
	}, nil
}

// ---------- WebSocket Handlers ----------
func onMessage(parser *SBEBestOBRpiParser, message []byte) {
	if len(message) == 0 {
		log.Println("Empty message received")
		return
	}

	// Parse SBE message
	parsed, err := parser.Parse(message)
	if err != nil {
		log.Printf("Parse error: %v", err)
		return
	}

	log.Printf("Parsed message: %+v", parsed)
}

func onTextMessage(message []byte) {
	var obj map[string]interface{}
	if err := json.Unmarshal(message, &obj); err != nil {
		log.Printf("Invalid JSON: %s", string(message))
		return
	}
	log.Printf("Control message: %v", obj)
}

func heartbeat(ctx context.Context, conn *websocket.Conn) {
	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := conn.WriteJSON(map[string]string{"op": "ping"}); err != nil {
				log.Printf("Error sending ping: %v", err)
				return
			}
		}
	}
}

func run(ctx context.Context) error {
	conn, _, err := websocket.DefaultDialer.Dial(WSURL, nil)
	if err != nil {
		return fmt.Errorf("dial error: %w", err)
	}
	defer conn.Close()

	log.Printf("Connected to %s", WSURL)

	parser := NewSBEBestOBRpiParser()

	// Subscribe to topic
	subMsg := map[string]interface{}{
		"op":   "subscribe",
		"args": []string{TOPIC},
	}
	if err := conn.WriteJSON(subMsg); err != nil {
		return fmt.Errorf("subscribe error: %w", err)
	}
	log.Printf("Subscribed to topic: %s", TOPIC)

	// Start heartbeat
	hbCtx, hbCancel := context.WithCancel(ctx)
	defer hbCancel()
	go heartbeat(hbCtx, conn)

	// Read loop
	for {
		mt, message, err := conn.ReadMessage()
		if err != nil {
			return fmt.Errorf("read error: %w", err)
		}

		switch mt {
		case websocket.BinaryMessage:
			onMessage(parser, message)
		case websocket.TextMessage:
			onTextMessage(message)
		default:
			log.Printf("Unknown message type: %d", mt)
		}
	}
}

// ---------- Main Entry ----------
func main() {
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()

	if err := run(ctx); err != nil {
		log.Fatalf("Error: %v", err)
	}
}