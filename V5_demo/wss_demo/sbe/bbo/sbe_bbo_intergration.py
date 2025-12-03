import json
import logging
import struct
import threading
import time
from datetime import datetime
from typing import Dict, Any

import websocket

logging.basicConfig(
    filename="logfile_wrapper.log",
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)

# Change symbol/topic as you wish
TOPIC = "ob.rpi.1.sbe.BTCUSDT"
WS_URL = "wss://stream-testnet.bybits.org/v5/realtime/spot"


class SBEBestOBRpiParser:
    """
    Parser for BestOBRpiEvent (template_id = 20000) per XML schema:

    ts(int64), seq(int64), cts(int64), u(int64),
    askNormalPrice(int64), askNormalSize(int64),
    askRpiPrice(int64), askRpiSize(int64),
    bidNormalPrice(int64), bidNormalSize(int64),
    bidRpiPrice(int64), bidRpiSize(int64),
    priceExponent(int8), sizeExponent(int8),
    symbol(varString8)

    All values are little-endian.
    """

    def __init__(self) -> None:
        # Header: blockLength, templateId, schemaId, version
        self.header_fmt = "<HHHH"
        self.header_sz = struct.calcsize(self.header_fmt)

        # 12 x int64 + 2 x int8:
        # ts, seq, cts, u,
        # askNormalPrice, askNormalSize, askRpiPrice, askRpiSize,
        # bidNormalPrice, bidNormalSize, bidRpiPrice, bidRpiSize,
        # priceExponent, sizeExponent
        self.body_fmt = "<" + ("q" * 12) + "bb"
        self.body_sz = struct.calcsize(self.body_fmt)

        self.target_template_id = 20000

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
    def _parse_varstring8(data: bytes, offset: int) -> tuple[str, int]:
        if offset + 1 > len(data):
            raise ValueError("insufficient data for varString8 length")

        (length,) = struct.unpack_from("<B", data, offset)
        offset += 1

        if offset + length > len(data):
            raise ValueError("insufficient data for varString8 bytes")

        s = data[offset : offset + length].decode("utf-8")
        offset += length
        return s, offset

    @staticmethod
    def _apply_exponent(value: int, exponent: int) -> float:
        # Exponent is for decimal point positioning.
        # If exponent = 2 and value=1060342500 -> 10603425.00
        return value / (10 ** exponent) if exponent >= 0 else value * (
            10 ** (-exponent)
        )

    def parse(self, data: bytes) -> Dict[str, Any]:
        hdr = self._parse_header(data)
        if hdr["template_id"] != self.target_template_id:
            raise NotImplementedError(
                f"unsupported template_id={hdr['template_id']}"
            )

        if len(data) < self.header_sz + self.body_sz:
            raise ValueError("insufficient data for BestOBRpiEvent body")

        fields = struct.unpack_from(self.body_fmt, data, self.header_sz)
        (
            ts,
            seq,
            cts,
            u,
            ask_np_m,
            ask_ns_m,
            ask_rp_m,
            ask_rs_m,
            bid_np_m,
            bid_ns_m,
            bid_rp_m,
            bid_rs_m,
            price_exp,
            size_exp,
        ) = fields

        offset = self.header_sz + self.body_sz
        symbol, offset = self._parse_varstring8(data, offset)

        # Apply exponents
        ask_np = self._apply_exponent(ask_np_m, price_exp)
        ask_ns = self._apply_exponent(ask_ns_m, size_exp)
        ask_rp = self._apply_exponent(ask_rp_m, price_exp)
        ask_rs = self._apply_exponent(ask_rs_m, size_exp)
        bid_np = self._apply_exponent(bid_np_m, price_exp)
        bid_ns = self._apply_exponent(bid_ns_m, size_exp)
        bid_rp = self._apply_exponent(bid_rp_m, price_exp)
        bid_rs = self._apply_exponent(bid_rs_m, size_exp)

        return {
            "header": hdr,
            "ts": ts,
            "seq": seq,
            "cts": cts,
            "u": u,
            "price_exponent": price_exp,
            "size_exponent": size_exp,
            "symbol": symbol,
            # Normal book (best)
            "ask_normal_price": ask_np,
            "ask_normal_size": ask_ns,
            "bid_normal_price": bid_np,
            "bid_normal_size": bid_ns,
            # RPI book (best)
            "ask_rpi_price": ask_rp,
            "ask_rpi_size": ask_rs,
            "bid_rpi_price": bid_rp,
            "bid_rpi_size": bid_rs,
            "parsed_length": offset,
        }


parser = SBEBestOBRpiParser()


# --------------------------- WebSocket handlers ---------------------------

def on_message(ws, message):
    try:
        # Binary SBE frames; text frames for control/acks/errors
        if isinstance(message, (bytes, bytearray)):
            decoded = parser.parse(message)
            logging.info(
                "SBE %s seq=%s u=%s "
                "NORM bid=%.8f@%.8f ask=%.8f@%.8f "
                "RPI bid=%.8f@%.8f ask=%.8f@%.8f ts=%s",
                decoded["symbol"],
                decoded["seq"],
                decoded["u"],
                decoded["bid_normal_price"],
                decoded["bid_normal_size"],
                decoded["ask_normal_price"],
                decoded["ask_normal_size"],
                decoded["bid_rpi_price"],
                decoded["bid_rpi_size"],
                decoded["ask_rpi_price"],
                decoded["ask_rpi_size"],
                decoded["ts"],
            )
            print(
                f"{decoded['symbol']} u={decoded['u']} "
                f"NORM: {decoded['bid_normal_price']:.8f} x {decoded['bid_normal_size']:.8f} "
                f"| {decoded['ask_normal_price']:.8f} x {decoded['ask_normal_size']:.8f} "
                f"RPI: {decoded['bid_rpi_price']:.8f} x {decoded['bid_rpi_size']:.8f} "
                f"| {decoded['ask_rpi_price']:.8f} x {decoded['ask_rpi_size']:.8f} "
                f"(seq={decoded['seq']} ts={decoded['ts']})"
            )
        else:
            try:
                obj = json.loads(message)
                logging.info("TEXT %s", obj)
                print(obj)
            except json.JSONDecodeError:
                logging.warning("non-JSON text frame: %r", message)
    except Exception as e:
        logging.exception("decode error: %s", e)
        print("decode error:", e)


def on_error(ws, error):
    print("WS error:", error)
    logging.error("WS error: %s", error)


def on_close(ws, *_):
    print("### connection closed ###")
    logging.info("connection closed")


def on_open(ws):
    print("opened")
    sub = {"op": "subscribe", "args": [TOPIC]}
    ws.send(json.dumps(sub))
    print("subscribed:", TOPIC)

    threading.Thread(target=ping_per, args=(ws,), daemon=True).start()
    threading.Thread(target=manage_subscription, args=(ws,), daemon=True).start()


def manage_subscription(ws):
    # demo: unsubscribe/resubscribe once
    time.sleep(20)
    ws.send(json.dumps({"op": "unsubscribe", "args": [TOPIC]}))
    print("unsubscribed:", TOPIC)
    time.sleep(5)
    ws.send(json.dumps({"op": "subscribe", "args": [TOPIC]}))
    print("resubscribed:", TOPIC)


def ping_per(ws):
    while True:
        try:
            ws.send(json.dumps({"op": "ping"}))
        except Exception:
            return
        time.sleep(10)


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