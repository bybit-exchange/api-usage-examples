import json
import logging
import struct
import threading
import time
from datetime import datetime
from typing import Dict, Any, List, Tuple

import websocket

logging.basicConfig(
    filename='logfile_ob50.log',
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s'
)

# -------------------------------------------------------------------
# Config
# -------------------------------------------------------------------

# L50 SBE order book topic
TOPIC = "ob.50.sbe.BTCUSDT"

# Adjust URL for spot / contract environment as needed:
WS_URL = "wss://stream-testnet.bybits.org/v5/public-sbe/spot"

# -------------------------------------------------------------------
# SBE Parser for OBL50Event (template_id = 20001)
#
# XML schema:
#   ts(int64), seq(int64), cts(int64), u(int64),
#   priceExponent(int8), sizeExponent(int8),
#   pkgType(uint8)   # 0 = SNAPSHOT, 1 = DELTA
#   group asks: blockLen(uint16), numInGroup(uint16),
#               then numInGroup * [ price(int64), size(int64) ]
#   group bids: same as asks
#   symbol(varString8)
# -------------------------------------------------------------------

class SBEOBL50Parser:
    def __init__(self):
        # message header: blockLength, templateId, schemaId, version
        self.header_fmt = "<HHHH"
        self.header_sz = struct.calcsize(self.header_fmt)

        # fixed body fields:
        # ts, seq, cts, u   -> 4 x int64
        # priceExponent, sizeExponent -> 2 x int8
        # pkgType -> uint8
        self.body_fmt = "<qqqqbbB"   # 4*q + 2*b + B
        self.body_sz = struct.calcsize(self.body_fmt)

        # group header for repeating groups: blockLength(uint16), numInGroup(uint16)
        self.group_hdr_fmt = "<HH"
        self.group_hdr_sz = struct.calcsize(self.group_hdr_fmt)

        # each group entry: price(int64), size(int64)
        self.level_fmt = "<qq"
        self.level_sz = struct.calcsize(self.level_fmt)

        self.target_template_id = 20001

    # ---------------- core small helpers ----------------

    def _parse_header(self, data: bytes) -> Dict[str, Any]:
        if len(data) < self.header_sz:
            raise ValueError("insufficient data for SBE header")
        block_length, template_id, schema_id, version = struct.unpack_from(
            self.header_fmt, data, 0
        )
        return {
            "block_length": block_length,
            "template_id": template_id,
            "schema_id": schema_id,
            "version": version,
        }

    @staticmethod
    def _parse_varstring8(data: bytes, offset: int) -> Tuple[str, int]:
        if offset + 1 > len(data):
            raise ValueError("insufficient data for varString8 length")
        (length,) = struct.unpack_from("<B", data, offset)
        offset += 1
        if length == 0:
            return "", offset
        if offset + length > len(data):
            raise ValueError("insufficient data for varString8 bytes")
        s = data[offset: offset + length].decode("utf-8")
        offset += length
        return s, offset

    @staticmethod
    def _apply_exponent(value: int, exponent: int) -> float:
        return value / (10 ** exponent) if exponent >= 0 else value * (10 ** (-exponent))

    def _parse_levels(self, data: bytes, offset: int) -> Tuple[List[Dict[str, float]], int]:
        """
        Parse one repeating group (asks or bids).
        Layout:
           uint16 blockLength
           uint16 numInGroup
           numInGroup * [ price(int64), size(int64) ] (within blockLength)
        """
        if offset + self.group_hdr_sz > len(data):
            raise ValueError("insufficient data for group header")
        block_len, num_in_group = struct.unpack_from(self.group_hdr_fmt, data, offset)
        offset += self.group_hdr_sz

        if block_len < self.level_sz:
            raise ValueError(f"blockLength({block_len}) < level_sz({self.level_sz})")

        levels = []
        for _ in range(num_in_group):
            if offset + block_len > len(data):
                raise ValueError("insufficient data for group entry")
            # we only care about first 16 bytes (price, size)
            price_m, size_m = struct.unpack_from(self.level_fmt, data, offset)
            offset += block_len  # skip the whole block (safe if future adds extra fields)

            levels.append({
                "price_m": price_m,
                "size_m": size_m,
            })
        return levels, offset

    # ---------------- public parse ----------------

    def parse(self, data: bytes) -> Dict[str, Any]:
        hdr = self._parse_header(data)
        if hdr["template_id"] != self.target_template_id:
            raise NotImplementedError(f"unsupported template_id={hdr['template_id']}")

        if len(data) < self.header_sz + self.body_sz:
            raise ValueError("insufficient data for OBL50Event body")

        # parse fixed body
        (ts, seq, cts, u,
         price_exp, size_exp, pkg_type) = struct.unpack_from(
            self.body_fmt, data, self.header_sz
        )

        offset = self.header_sz + self.body_sz

        # asks group
        asks_raw, offset = self._parse_levels(data, offset)
        # bids group
        bids_raw, offset = self._parse_levels(data, offset)
        # symbol
        symbol, offset = self._parse_varstring8(data, offset)

        # apply exponents
        asks = [
            {
                "price": self._apply_exponent(l["price_m"], price_exp),
                "size": self._apply_exponent(l["size_m"], size_exp),
            }
            for l in asks_raw
        ]
        bids = [
            {
                "price": self._apply_exponent(l["price_m"], price_exp),
                "size": self._apply_exponent(l["size_m"], size_exp),
            }
            for l in bids_raw
        ]

        return {
            "header": hdr,
            "ts": ts,
            "seq": seq,
            "cts": cts,
            "u": u,
            "price_exponent": price_exp,
            "size_exponent": size_exp,
            "pkg_type": pkg_type,   # 0 = SNAPSHOT, 1 = DELTA
            "symbol": symbol,
            "asks": asks,
            "bids": bids,
            "parsed_length": offset,
        }


parser = SBEOBL50Parser()

# -------------------------------------------------------------------
# WebSocket handlers
# -------------------------------------------------------------------

def on_message(ws, message):
    try:
        if isinstance(message, (bytes, bytearray)):
            decoded = parser.parse(message)

            pkg_type = decoded["pkg_type"]
            pkg_str = "SNAPSHOT" if pkg_type == 0 else "DELTA" if pkg_type == 1 else f"UNKNOWN({pkg_type})"

            asks = decoded["asks"]
            bids = decoded["bids"]

            best_ask = asks[0] if asks else {"price": 0.0, "size": 0.0}
            best_bid = bids[0] if bids else {"price": 0.0, "size": 0.0}

            logging.info(
                "SBE %s u=%s seq=%s type=%s asks=%d bids=%d "
                "BEST bid=%.8f@%.8f ask=%.8f@%.8f ts=%s",
                decoded["symbol"], decoded["u"], decoded["seq"], pkg_str,
                len(asks), len(bids),
                best_bid["price"], best_bid["size"],
                best_ask["price"], best_ask["size"],
                decoded["ts"],
            )

            print(
                f"{decoded['symbol']}  u={decoded['u']}  seq={decoded['seq']}  {pkg_str}  "
                f"levels: asks={len(asks)} bids={len(bids)}  "
                f"BEST: bid {best_bid['price']:.8f} x {best_bid['size']:.8f}  |  "
                f"ask {best_ask['price']:.8f} x {best_ask['size']:.8f}"
            )

        else:
            # text frame: control / errors / ping-pong
            try:
                obj = json.loads(message)
                logging.info("TEXT %s", obj)
                print("TEXT:", obj)
            except json.JSONDecodeError:
                logging.warning("non-JSON text frame: %r", message)
                print("TEXT(non-json):", message)
    except Exception as e:
        logging.exception("decode error: %s", e)
        print("decode error:", e)


def on_error(ws, error):
    print("WS error:", error)
    logging.error("WS error: %s", error)


def on_close(ws, *_):
    print("### connection closed ###")
    logging.info("connection closed")


def ping_per(ws):
    while True:
        try:
            ws.send(json.dumps({"op": "ping"}))
        except Exception:
            return
        time.sleep(10)


def on_open(ws):
    print("opened")
    sub = {"op": "subscribe", "args": [TOPIC]}
    ws.send(json.dumps(sub))
    print("subscribed:", TOPIC)

    # background ping thread
    threading.Thread(target=ping_per, args=(ws,), daemon=True).start()


def on_pong(ws, *_):
    print("pong received")


def on_ping(ws, *_):
    print("ping received @", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))


def connWS():
    ws = websocket.WebSocketApp(
        WS_URL,
        on_open=on_open,
        on_message=on_message,
        on_error=on_error,
        on_close=on_close,
        on_ping=on_ping,
        on_pong=on_pong,
    )
    ws.run_forever(ping_interval=20, ping_timeout=10)


if __name__ == "__main__":
    websocket.enableTrace(False)
    connWS()