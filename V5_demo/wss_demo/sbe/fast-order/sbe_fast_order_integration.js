const WebSocket = require('ws');
const crypto = require('crypto');
const readline = require('readline');

// 配置常量
const MMWS_URL_TESTNET = "wss://stream-testnet.bybits.org/v5/private";
const API_KEY = "xxx";
const API_SECRET = "xxx";
const SUB_TOPICS = ["order.sbe.resp.linear"];

// BigInt 序列化辅助函数
function serializeBigInt(obj) {
    return JSON.stringify(obj, (key, value) => {
        if (typeof value === 'bigint') {
            return value.toString();
        }
        return value;
    });
}

// 日志功能
class Logger {
    constructor() {
        this.rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
    }

    info(message) {
        const timestamp = new Date().toISOString();
        console.log(`[${timestamp}] INFO: ${message}`);
    }

    error(message) {
        const timestamp = new Date().toISOString();
        console.error(`[${timestamp}] ERROR: ${message}`);
    }
}

const logger = new Logger();

// 字节读取辅助函数
function readU8(buf, off) {
    return [buf.readUInt8(off), off + 1];
}

function readI8(buf, off) {
    return [buf.readInt8(off), off + 1];
}

function readU16LE(buf, off) {
    return [buf.readUInt16LE(off), off + 2];
}

function readI64LE(buf, off) {
    // 读取为 BigInt
    return [buf.readBigInt64LE(off), off + 8];
}

function readVarString8(buf, off) {
    const ln = buf.readUInt8(off);
    off += 1;
    if (ln === 0) {
        return ["", off];
    }
    const s = buf.toString('utf8', off, off + ln);
    return [s, off + ln];
}

function applyExp(mantissa, exp) {
    // 将 BigInt 转换为数字进行计算
    const mantissaNum = Number(mantissa);
    if (exp >= 0) {
        return mantissaNum / Math.pow(10, exp);
    } else {
        return mantissaNum * Math.pow(10, -exp);
    }
}

function decodeFastOrderResp(payload, debug = false) {
    let off = 0;

    // 头部 (8 字节)
    if (payload.length < 8) {
        throw new Error("payload too short for SBE header");
    }

    const blockLen = payload.readUInt16LE(off);
    off += 2;
    const templateId = payload.readUInt16LE(off);
    off += 2;
    const schemaId = payload.readUInt16LE(off);
    off += 2;
    const version = payload.readUInt16LE(off);
    off += 2;

    if (debug) {
        console.log(`HEADER: block_len=${blockLen}, template_id=${templateId}, schema_id=${schemaId}, version=${version}`);
        console.log("payload hex:", payload.toString('hex'));
    }

    if (templateId !== 21000) {
        return;
    }

    // 解析固定字段
    let category, side, orderStatus, priceExp, sizeExp, valueExp, rejectReason;
    let price, qty, leavesQty, value, leavesValue, creationTimeUs, updatedTimeUs, seq;

    [category, off] = readU8(payload, off);
    [side, off] = readU8(payload, off);
    [orderStatus, off] = readU8(payload, off);
    [priceExp, off] = readI8(payload, off);
    [sizeExp, off] = readI8(payload, off);
    [valueExp, off] = readI8(payload, off);
    [rejectReason, off] = readU16LE(payload, off);
    [price, off] = readI64LE(payload, off);
    [qty, off] = readI64LE(payload, off);
    [leavesQty, off] = readI64LE(payload, off);
    [value, off] = readI64LE(payload, off);
    [leavesValue, off] = readI64LE(payload, off);
    [creationTimeUs, off] = readI64LE(payload, off);
    [updatedTimeUs, off] = readI64LE(payload, off);
    [seq, off] = readI64LE(payload, off);

    if (debug) {
        console.log("after fixed fields offset:", off);
    }

    // 解析变长字符串
    let symbolName, orderId, orderLinkId;
    [symbolName, off] = readVarString8(payload, off);
    [orderId, off] = readVarString8(payload, off);
    [orderLinkId, off] = readVarString8(payload, off);

    return {
        "_sbe_header": {
            "blockLength": blockLen,
            "templateId": templateId,
            "schemaId": schemaId,
            "version": version,
        },
        "category": category,
        "side": side,
        "orderStatus": orderStatus,
        "priceExponent": priceExp,
        "sizeExponent": sizeExp,
        "valueExponent": valueExp,
        "rejectReason": rejectReason,
        "priceMantissa": price.toString(), // 转换为字符串
        "qtyMantissa": qty.toString(),     // 转换为字符串
        "leavesQtyMantissa": leavesQty.toString(), // 转换为字符串
        "leavesValueMantissa": leavesValue.toString(), // 转换为字符串
        "price": applyExp(price, priceExp),
        "qty": applyExp(qty, sizeExp),
        "leavesQty": applyExp(leavesQty, sizeExp),
        "value": applyExp(value, valueExp),
        "leavesValue": applyExp(leavesValue, valueExp),
        "creationTime": creationTimeUs.toString(), // 转换为字符串
        "updatedTime": updatedTimeUs.toString(),   // 转换为字符串
        "seq": seq.toString(),                     // 转换为字符串
        "symbolName": symbolName,
        "orderId": orderId,
        "orderLinkId": orderLinkId,
        "_raw_offset_end": off
    };
}

// 发送 JSON 数据
function sendJson(ws, obj) {
    ws.send(JSON.stringify(obj));
}

// 心跳任务
function startHeartbeat(ws) {
    setInterval(() => {
        try {
            sendJson(ws, {
                "req_id": String(Date.now()),
                "op": "ping"
            });
        } catch (error) {
            logger.error(`Heartbeat error: ${error.message}`);
        }
    }, 10000);
}

// 主运行函数
async function run(url) {
    const ws = new WebSocket(url);

    ws.on('open', async () => {
        logger.info('WebSocket connected');

        // 鉴权
        const expires = Date.now() + 10000000;
        const val = `GET/realtime${expires}`;
        const signature = crypto
            .createHmac('sha256', API_SECRET)
            .update(val)
            .digest('hex');

        sendJson(ws, {
            "req_id": "10001",
            "op": "auth",
            "args": [API_KEY, expires, signature]
        });
    });

    ws.on('message', (data) => {
        if (Buffer.isBuffer(data)) {
            // 二进制数据
            try {
                const decoded = decodeFastOrderResp(data);
                if (decoded !== null && decoded !== undefined) {
                    // 使用自定义序列化函数处理 BigInt
                    logger.info(serializeBigInt(decoded));
                }
            } catch (error) {
                logger.error(`Binary decode error: ${error.message}`);
            }
        } else {
            // 文本数据
            try {
                const obj = JSON.parse(data);
                if (obj.op && obj.op !== 'pong') {
                    logger.info(JSON.stringify(obj));
                }
            } catch (error) {
                logger.error(`Text non-JSON: ${data}`);
            }
        }
    });

    ws.on('error', (error) => {
        logger.error(`WebSocket error: ${error.message}`);
    });

    ws.on('close', () => {
        logger.info('WebSocket connection closed');
    });

    // 等待连接建立后发送订阅请求
    ws.on('open', () => {
        setTimeout(() => {
            sendJson(ws, {
                "op": "subscribe",
                "args": SUB_TOPICS
            });
            startHeartbeat(ws);
        }, 1000);
    });
}

// 启动程序
run(MMWS_URL_TESTNET).catch(error => {
    logger.error(`Runtime error: ${error.message}`);
    process.exit(1);
});
