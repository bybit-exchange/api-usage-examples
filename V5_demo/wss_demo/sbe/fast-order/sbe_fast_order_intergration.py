import asyncio
import json
import hmac
import time
import struct
from typing import Tuple, Dict, Any
import websockets
import logging

logger = logging.getLogger("fast_order")
logger.setLevel(logging.INFO)
handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter("[%(asctime)s] %(levelname)s: %(message)s"))
logger.addHandler(handler)

MMWS_URL_TESTNET = "wss://stream-testnet.bybits.org/v5/private"
API_KEY = "xxx"
API_SECRET = "xxx"
SUB_TOPICS = ["order.sbe.resp.linear"]


def read_u8(buf: memoryview, off: int) -> Tuple[int, int]:
    return buf[off], off + 1


def read_i8(buf: memoryview, off: int) -> Tuple[int, int]:
    b = struct.unpack_from("<b", buf, off)[0]
    return b, off + 1


def read_u16_le(buf: memoryview, off: int) -> Tuple[int, int]:
    v = struct.unpack_from("<H", buf, off)[0]
    return v, off + 2


def read_i64_le(buf: memoryview, off: int) -> Tuple[int, int]:
    v = struct.unpack_from("<q", buf, off)[0]
    return v, off + 8


def read_varstring8(buf: memoryview, off: int) -> Tuple[str, int]:
    ln = buf[off]  # uint8 length
    off += 1
    if ln == 0:
        return "", off
    s = bytes(buf[off: off + ln]).decode("utf-8", "replace")
    return s, off + ln


def apply_exp(mantissa: int, exp: int) -> float:
    if exp >= 0:
        return mantissa / (10 ** exp)
    else:
        return mantissa * (10 ** (-exp))


def decode_fast_order_resp(payload: bytes, debug: bool = False) -> Dict[str, Any]:
    mv = memoryview(payload)
    off = 0
    # header (8 bytes)
    if len(mv) < 8:
        raise ValueError("payload too short for SBE header")
    block_len, template_id, schema_id, version = struct.unpack_from("<HHHH", mv, off)
    off += 8
    if debug:
        print(f"HEADER: block_len={block_len}, template_id={template_id}, schema_id={schema_id}, version={version}")
        print("payload hex:", payload.hex())
    if template_id != 21000:
        return {"_warn": f"unexpected_template_id:{template_id}", "_raw": payload.hex()}
    # --- fixed fields in the correct (schema) order ---
    category, off = read_u8(mv, off)  # 1
    side, off = read_u8(mv, off)  # 2
    order_status, off = read_u8(mv, off)  # 3
    price_exp, off = read_i8(mv, off)  # 4
    size_exp, off = read_i8(mv, off)  # 5
    value_exp, off = read_i8(mv, off)  # 6
    reject_reason, off = read_u16_le(mv, off)  # 7
    price, off = read_i64_le(mv, off)  # 8
    qty, off = read_i64_le(mv, off)  # 9
    leaves_qty, off = read_i64_le(mv, off)  # 10
    value, off = read_i64_le(mv, off)  # 11
    leaves_value, off = read_i64_le(mv, off)  # 12
    creation_time_us, off = read_i64_le(mv, off)  # 13
    updated_time_us, off = read_i64_le(mv, off)  # 14
    seq, off = read_i64_le(mv, off)  # 15
    # seq1, off = read_i64_le(mv, off)
    if debug:
        print("after fixed fields offset:", off)
    symbol_name, off = read_varstring8(mv, off)
    order_id, off = read_varstring8(mv, off)
    order_link_id, off = read_varstring8(mv, off)
    # order_link_id1, off = read_varstring8(mv, off)
    out = {
        "_sbe_header": {
            "blockLength": block_len,
            "templateId": template_id,
            "schemaId": schema_id,
            "version": version,
        },
        "category": category,
        "side": side,
        "orderStatus": order_status,
        "priceExponent": price_exp,
        "sizeExponent": size_exp,
        "valueExponent": value_exp,
        "rejectReason": reject_reason,
        "priceMantissa": price,
        "qtyMantissa": qty,
        "leavesQtyMantissa": leaves_qty,
        "leavesValueMantissa": leaves_value,
        "price": apply_exp(price, price_exp),
        "qty": apply_exp(qty, size_exp),
        "leavesQty": apply_exp(leaves_qty, size_exp),
        "value": apply_exp(value, value_exp),  # 期货永远赋值为0，因为撮合中拿不到这个值
        "leavesValue": apply_exp(leaves_value, value_exp),  # 期货永远赋值为0，因为撮合中拿不到这个值
        "creationTime": creation_time_us,
        "updatedTime": updated_time_us,
        "seq": seq,
        # "seq1": seq1,
        "symbolName": symbol_name,
        "orderId": order_id,
        "orderLinkId": order_link_id,
        # "orderLinkId1": order_link_id1,
        "_raw_offset_end": off
    }
    return out


async def send_json(ws, obj):
    await ws.send(json.dumps(obj, separators=(",", ":")))


async def heartbeat(ws):
    while True:
        await asyncio.sleep(10)
        try:
            await send_json(ws, {"req_id": str(int(time.time() * 1000)), "op": "ping"})
        except Exception:
            return


async def run(url: str):
    async with websockets.connect(url, max_size=None) as ws:
        # 鉴权
        expires = int((time.time() + 10000) * 1000)
        _val = f'GET/realtime{expires}'
        signature = str(hmac.new(
            bytes(API_SECRET, 'utf-8'),
            bytes(_val, 'utf-8'), digestmod='sha256'
        ).hexdigest())
        await send_json(ws, {"req_id": "10001", "op": "auth", "args": [API_KEY, expires, signature]})
        # 鉴权回包
        ack = await ws.recv()
        # print("auth-ack:", ack)
        logger.info(ack)
        # 订阅 topic
        await send_json(ws, {"op": "subscribe", "args": SUB_TOPICS})
        # 发送心跳包
        asyncio.create_task(heartbeat(ws))
        while True:
            frame = await ws.recv()
            # logger.error(frame)  # 输出二进制包
            if isinstance(frame, (bytes, bytearray)):
                try:
                    decoded = decode_fast_order_resp(frame)
                    # print(json.dumps(decoded, ensure_ascii=False))
                    logger.info(json.dumps(decoded, ensure_ascii=False))
                except Exception as e:
                    print("binary-decode-error:", e)
            else:
                # text JSON
                try:
                    obj = json.loads(frame)
                    # print("control:", obj)
                    # 不输出心跳回包
                    if 'op' in obj:
                        if obj["op"] != "pong":
                            logger.info(obj)
                    else:
                        logger.info(obj)
                except Exception:
                    print("text-nonjson:", frame)


if __name__ == "__main__":
    asyncio.run(run(MMWS_URL_TESTNET))