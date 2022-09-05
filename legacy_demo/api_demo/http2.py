import asyncio
import httpx  # `requests` does not allow us to send HTTP/2 requests
import hmac
import urllib3
import time
from urllib.parse import quote_plus


# This file demonstrates an HTTP/2 request to cancel a spot order. However,
# it can be adapted for the other APIs (derivatives, etc)


api_key = ""
api_secret = ""


# Note that while api.bybit.com does support HTTP/2 E2E,
# api.bytick.com does not
url = "https://api.bybit.com/spot/v1/order"


def auth():
    timestamp = int(time.time() * 10 ** 3)
    headers = {}
    params = {  # delete order request
        "orderId": "1084090149712726016",
        "api_key": api_key,
        "timestamp": str(timestamp),
        "recv_window": "5000"
    }
    param_str = ''
    for key in sorted(params.keys()):
        v = params[key]
        if isinstance(params[key], bool):
            if params[key]:
                v = "true"
            else:
                v = "false"
        param_str += key + "=" + v + "&"
    param_str = param_str[:-1]
    signature = str(hmac.new(
        bytes(api_secret, "utf-8"),
        bytes(param_str, "utf-8"), digestmod="sha256"
    ).hexdigest())
    sign_real = {
        "sign": signature
    }
    param_str = quote_plus(param_str, safe="=&")
    full_param_str = f"{param_str}&sign={sign_real['sign']}"
    urllib3.disable_warnings()
    return f"{url}?{full_param_str}"


async def main():
    client = httpx.AsyncClient(http2=True)
    url_with_params = auth()
    response = await client.delete(url_with_params)
    await client.aclose()
    print(response.http_version)  # "HTTP/1.0", "HTTP/1.1", or "HTTP/2".
    print(response)
    print(response.text)


asyncio.run(main())
