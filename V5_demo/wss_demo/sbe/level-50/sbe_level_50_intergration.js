const WebSocket = require('ws');
const fs = require('fs');
const crypto = require('crypto');

// -------------------------------------------------------------------
// Config
// -------------------------------------------------------------------

// L50 SBE order book topic
const TOPIC = "ob.50.sbe.BTCUSDT";

// Adjust URL for spot / contract environment as needed:
const WS_URL = "wss://stream-testnet.bybits.org/v5/public-sbe/spot";

// -------------------------------------------------------------------
// Logging setup
// -------------------------------------------------------------------

function setupLogging() {
    const logStream = fs.createWriteStream('logfile_ob50.log', { flags: 'a' });

    function log(level, message) {
        const timestamp = new Date().toISOString();
        const logMessage = `${timestamp} ${level} ${message}\n`;
        logStream.write(logMessage);
        console.log(`[${level}] ${message}`);
    }

    return {
        info: (msg) => log('INFO', msg),
        warning: (msg) => log('WARNING', msg),
        error: (msg) => log('ERROR', msg),
        exception: (error, context) => {
            const msg = `${context}: ${error.message}\n${error.stack}`;
            log('ERROR', msg);
        }
    };
}

const logger = setupLogging();

// -------------------------------------------------------------------
// SBE Parser for OBL50Event (template_id = 20001)
// -------------------------------------------------------------------

class SBEOBL50Parser {
    constructor() {
        // message header: blockLength, templateId, schemaId, version
        this.headerSz = 8; // 4 * uint16 = 8 bytes

        // fixed body fields: 4 x int64 + 2 x int8 + 1 x uint8
        this.bodySz = 4 * 8 + 2 * 1 + 1; // 35 bytes

        // group header for repeating groups: blockLength(uint16), numInGroup(uint16)
        this.groupHdrSz = 4; // 2 * uint16 = 4 bytes

        // each group entry: price(int64), size(int64)
        this.levelSz = 16; // 2 * int64 = 16 bytes

        this.targetTemplateId = 20001;
    }

    // ---------------- core small helpers ----------------

    _parseHeader(data, offset = 0) {
        if (data.length < offset + this.headerSz) {
            throw new Error("insufficient data for SBE header");
        }

        const blockLength = data.readUInt16LE(offset);
        const templateId = data.readUInt16LE(offset + 2);
        const schemaId = data.readUInt16LE(offset + 4);
        const version = data.readUInt16LE(offset + 6);

        return {
            blockLength,
            templateId,
            schemaId,
            version,
            offset: offset + this.headerSz
        };
    }

    _parseVarString8(data, offset) {
        if (offset + 1 > data.length) {
            throw new Error("insufficient data for varString8 length");
        }

        const length = data.readUInt8(offset);
        offset += 1;

        if (length === 0) {
            return { string: "", offset };
        }

        if (offset + length > data.length) {
            throw new Error("insufficient data for varString8 bytes");
        }

        const string = data.toString('utf8', offset, offset + length);
        offset += length;

        return { string, offset };
    }

    _applyExponent(value, exponent) {
        if (exponent >= 0) {
            return Number(value) / Math.pow(10, exponent);
        } else {
            return Number(value) * Math.pow(10, -exponent);
        }
    }

    _parseLevels(data, offset) {
        if (offset + this.groupHdrSz > data.length) {
            throw new Error("insufficient data for group header");
        }

        const blockLength = data.readUInt16LE(offset);
        const numInGroup = data.readUInt16LE(offset + 2);
        offset += this.groupHdrSz;

        if (blockLength < this.levelSz) {
            throw new Error(`blockLength(${blockLength}) < levelSz(${this.levelSz})`);
        }

        const levels = [];
        for (let i = 0; i < numInGroup; i++) {
            if (offset + blockLength > data.length) {
                throw new Error("insufficient data for group entry");
            }

            // Read price and size (first 16 bytes of the block)
            const priceM = data.readBigInt64LE(offset);
            const sizeM = data.readBigInt64LE(offset + 8);
            offset += blockLength; // skip the whole block

            levels.push({
                priceM: priceM,
                sizeM: sizeM
            });
        }

        return { levels, offset };
    }

    // ---------------- public parse ----------------

    parse(data) {
        const headerResult = this._parseHeader(data);
        if (headerResult.templateId !== this.targetTemplateId) {
            //throw new Error(`unsupported template_id=${headerResult.templateId}`);
            return;
        }

        let offset = headerResult.offset;

        if (offset + this.bodySz > data.length) {
            throw new Error("insufficient data for OBL50Event body");
        }

        // Parse fixed body
        const ts = data.readBigInt64LE(offset);
        const seq = data.readBigInt64LE(offset + 8);
        const cts = data.readBigInt64LE(offset + 16);
        const u = data.readBigInt64LE(offset + 24);
        const priceExp = data.readInt8(offset + 32);
        const sizeExp = data.readInt8(offset + 33);
        const pkgType = data.readUInt8(offset + 34);

        offset += this.bodySz;

        // Parse asks group
        const asksResult = this._parseLevels(data, offset);
        const asksRaw = asksResult.levels;
        offset = asksResult.offset;

        // Parse bids group
        const bidsResult = this._parseLevels(data, offset);
        const bidsRaw = bidsResult.levels;
        offset = bidsResult.offset;

        // Parse symbol
        const symbolResult = this._parseVarString8(data, offset);
        const symbol = symbolResult.string;
        offset = symbolResult.offset;

        // Apply exponents
        const asks = asksRaw.map(level => ({
            price: this._applyExponent(level.priceM, priceExp),
            size: this._applyExponent(level.sizeM, sizeExp)
        }));

        const bids = bidsRaw.map(level => ({
            price: this._applyExponent(level.priceM, priceExp),
            size: this._applyExponent(level.sizeM, sizeExp)
        }));

        return {
            header: {
                blockLength: headerResult.blockLength,
                templateId: headerResult.templateId,
                schemaId: headerResult.schemaId,
                version: headerResult.version
            },
            ts: ts.toString(),
            seq: seq.toString(),
            cts: cts.toString(),
            u: u.toString(),
            priceExponent: priceExp,
            sizeExponent: sizeExp,
            pkgType: pkgType, // 0 = SNAPSHOT, 1 = DELTA
            symbol: symbol,
            asks: asks,
            bids: bids,
            parsedLength: offset
        };
    }
}

const parser = new SBEOBL50Parser();

// -------------------------------------------------------------------
// WebSocket handlers
// -------------------------------------------------------------------

function onMessage(data) {
    try {
        if (Buffer.isBuffer(data)) {
            const decoded = parser.parse(data);
            if (decoded !== null && decoded !== undefined) {
                const pkgType = decoded.pkgType;
                const pkgStr = pkgType === 0 ? "SNAPSHOT" : pkgType === 1 ? "DELTA" : `UNKNOWN(${pkgType})`;

                const asks = decoded.asks;
                const bids = decoded.bids;

                const bestAsk = asks.length > 0 ? asks[0] : { price: 0.0, size: 0.0 };
                const bestBid = bids.length > 0 ? bids[0] : { price: 0.0, size: 0.0 };

                const logMessage = `SBE ${decoded.symbol} u=${decoded.u} seq=${decoded.seq} type=${pkgStr} ` +
                    `asks=${asks.length} bids=${bids.length} ` +
                    `BEST bid=${bestBid.price.toFixed(8)}@${bestBid.size.toFixed(8)} ` +
                    `ask=${bestAsk.price.toFixed(8)}@${bestAsk.size.toFixed(8)} ts=${decoded.ts}`;

                logger.info(logMessage);

                console.log(
                    `${decoded.symbol}  u=${decoded.u}  seq=${decoded.seq}  ${pkgStr}  ` +
                    `levels: asks=${asks.length} bids=${bids.length}  ` +
                    `BEST: bid ${bestBid.price.toFixed(8)} x ${bestBid.size.toFixed(8)}  |  ` +
                    `ask ${bestAsk.price.toFixed(8)} x ${bestAsk.size.toFixed(8)}`
                );
            }


        } else {
            // text frame: control / errors / ping-pong
            try {
                const obj = JSON.parse(data);
                logger.info(`TEXT ${JSON.stringify(obj)}`);
                console.log("TEXT:", obj);
            } catch (jsonError) {
                logger.warning(`non-JSON text frame: ${data}`);
                console.log("TEXT(non-json):", data);
            }
        }
    } catch (error) {
        logger.exception(error, "decode error");
        console.log("decode error:", error.message);
    }
}

function onError(error) {
    console.log("WS error:", error);
    logger.error(`WS error: ${error.message}`);
}

function onClose() {
    console.log("### connection closed ###");
    logger.info("connection closed");
}

function pingPeriodically(ws) {
    setInterval(() => {
        try {
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ op: "ping" }));
            }
        } catch (error) {
            console.log("Ping error:", error.message);
        }
    }, 10000);
}

function onOpen(ws) {
    console.log("opened");
    const sub = { op: "subscribe", args: [TOPIC] };
    ws.send(JSON.stringify(sub));
    console.log("subscribed:", TOPIC);

    // Start ping interval
    pingPeriodically(ws);
}

function onPong() {
    console.log("pong received");
}

function onPing() {
    const now = new Date();
    console.log("ping received @", now.toISOString().replace('T', ' ').substring(0, 19));
}

function connWS() {
    const ws = new WebSocket(WS_URL);

    ws.on('open', () => onOpen(ws));
    ws.on('message', onMessage);
    ws.on('error', onError);
    ws.on('close', onClose);
    ws.on('pong', onPing); // Note: In ws library, 'pong' event is for received pongs

    // Set up ping interval
    setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
            ws.ping();
        }
    }, 20000);

    return ws;
}

// -------------------------------------------------------------------
// Main execution
// -------------------------------------------------------------------

if (require.main === module) {
    try {
        const ws = connWS();

        // Handle graceful shutdown
        process.on('SIGINT', () => {
            console.log('Shutting down...');
            ws.close();
            process.exit(0);
        });

    } catch (error) {
        console.error('Failed to start WebSocket connection:', error);
        process.exit(1);
    }
}
