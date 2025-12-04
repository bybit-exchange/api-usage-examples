const WebSocket = require('ws');
const fs = require('fs');

// Change symbol/topic as you wish
const TOPIC = "ob.rpi.1.sbe.BTCUSDT";
const WS_URL = "wss://stream-testnet.bybits.org/v5/realtime/spot";

// -------------------------------------------------------------------
// Logging setup
// -------------------------------------------------------------------

function setupLogging() {
    const logStream = fs.createWriteStream('logfile_wrapper.log', { flags: 'a' });

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
// SBE Parser for BestOBRpiEvent (template_id = 20000)
// -------------------------------------------------------------------

class SBEBestOBRpiParser {
    constructor() {
        // Header: blockLength, templateId, schemaId, version
        this.headerSz = 8; // 4 * uint16 = 8 bytes

        // 12 x int64 + 2 x int8
        this.bodySz = 12 * 8 + 2 * 1; // 98 bytes

        this.targetTemplateId = 20000;
    }

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

    parse(data) {
        const headerResult = this._parseHeader(data);
        if (headerResult.templateId !== this.targetTemplateId) {
            //throw new Error(`unsupported template_id=${headerResult.templateId}`);
            return null;
        }

        let offset = headerResult.offset;

        if (offset + this.bodySz > data.length) {
            throw new Error("insufficient data for BestOBRpiEvent body");
        }

        // Parse 12 int64 fields
        const ts = data.readBigInt64LE(offset);
        const seq = data.readBigInt64LE(offset + 8);
        const cts = data.readBigInt64LE(offset + 16);
        const u = data.readBigInt64LE(offset + 24);
        const askNpM = data.readBigInt64LE(offset + 32);
        const askNsM = data.readBigInt64LE(offset + 40);
        const askRpM = data.readBigInt64LE(offset + 48);
        const askRsM = data.readBigInt64LE(offset + 56);
        const bidNpM = data.readBigInt64LE(offset + 64);
        const bidNsM = data.readBigInt64LE(offset + 72);
        const bidRpM = data.readBigInt64LE(offset + 80);
        const bidRsM = data.readBigInt64LE(offset + 88);

        // Parse 2 int8 fields
        const priceExp = data.readInt8(offset + 96);
        const sizeExp = data.readInt8(offset + 97);

        offset += this.bodySz;

        // Parse symbol
        const symbolResult = this._parseVarString8(data, offset);
        const symbol = symbolResult.string;
        offset = symbolResult.offset;

        // Apply exponents
        const askNp = this._applyExponent(askNpM, priceExp);
        const askNs = this._applyExponent(askNsM, sizeExp);
        const askRp = this._applyExponent(askRpM, priceExp);
        const askRs = this._applyExponent(askRsM, sizeExp);
        const bidNp = this._applyExponent(bidNpM, priceExp);
        const bidNs = this._applyExponent(bidNsM, sizeExp);
        const bidRp = this._applyExponent(bidRpM, priceExp);
        const bidRs = this._applyExponent(bidRsM, sizeExp);

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
            symbol: symbol,
            // Normal book (best)
            askNormalPrice: askNp,
            askNormalSize: askNs,
            bidNormalPrice: bidNp,
            bidNormalSize: bidNs,
            // RPI book (best)
            askRpiPrice: askRp,
            askRpiSize: askRs,
            bidRpiPrice: bidRp,
            bidRpiSize: bidRs,
            parsedLength: offset
        };
    }
}

const parser = new SBEBestOBRpiParser();

// -------------------------------------------------------------------
// WebSocket handlers
// -------------------------------------------------------------------

function onMessage(data) {
    try {
        if (Buffer.isBuffer(data)) {
            const decoded = parser.parse(data);
            if (decoded !== null && decoded !== undefined) {
                logger.info(
                    `SBE ${decoded.symbol} seq=${decoded.seq} u=${decoded.u} ` +
                    `NORM bid=${decoded.bidNormalPrice.toFixed(8)}@${decoded.bidNormalSize.toFixed(8)} ` +
                    `ask=${decoded.askNormalPrice.toFixed(8)}@${decoded.askNormalSize.toFixed(8)} ` +
                    `RPI bid=${decoded.bidRpiPrice.toFixed(8)}@${decoded.bidRpiSize.toFixed(8)} ` +
                    `ask=${decoded.askRpiPrice.toFixed(8)}@${decoded.askRpiSize.toFixed(8)} ts=${decoded.ts}`
                );

                console.log(
                    `${decoded.symbol} u=${decoded.u} ` +
                    `NORM: ${decoded.bidNormalPrice.toFixed(8)} x ${decoded.bidNormalSize.toFixed(8)} ` +
                    `| ${decoded.askNormalPrice.toFixed(8)} x ${decoded.askNormalSize.toFixed(8)} ` +
                    `RPI: ${decoded.bidRpiPrice.toFixed(8)} x ${decoded.bidRpiSize.toFixed(8)} ` +
                    `| ${decoded.askRpiPrice.toFixed(8)} x ${decoded.askRpiSize.toFixed(8)} ` +
                    `(seq=${decoded.seq} ts=${decoded.ts})`
                );
            }


        } else {
            try {
                const obj = JSON.parse(data);
                logger.info(`TEXT ${JSON.stringify(obj)}`);
                console.log(obj);
            } catch (jsonError) {
                logger.warning(`non-JSON text frame: ${data}`);
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

function manageSubscription(ws) {
    // demo: unsubscribe/resubscribe once
    setTimeout(() => {
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ op: "unsubscribe", args: [TOPIC] }));
            console.log("unsubscribed:", TOPIC);
        }
    }, 20000);

    setTimeout(() => {
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ op: "subscribe", args: [TOPIC] }));
            console.log("resubscribed:", TOPIC);
        }
    }, 25000);
}

function onOpen(ws) {
    console.log("opened");
    const sub = { op: "subscribe", args: [TOPIC] };
    ws.send(JSON.stringify(sub));
    console.log("subscribed:", TOPIC);

    // Start ping interval
    pingPeriodically(ws);

    // Start subscription management
    manageSubscription(ws);
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
    ws.on('pong', onPing);

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
