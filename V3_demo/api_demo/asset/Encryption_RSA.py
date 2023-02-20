import requests
import time
import hashlib
import uuid
from Crypto.Hash import SHA256  # install pycryptodome libaray
from Crypto.Signature import PKCS1_v1_5
from Crypto.PublicKey import RSA

api_key='XXXXXXXXX'
rsa_private_key_path = '/user/private.pem' # use absolute path

httpClient=requests.Session()
recv_window=str(5000)
url="https://api-testnet.bybit.com" # Testnet endpoint

def HTTP_Request(endPoint,method,payload,Info):
    global time_stamp
    time_stamp=str(int(time.time() * 10 ** 3))
    signature=genSignature(payload, rsa_private_key_path)
    headers = {
        'X-BAPI-API-KEY': api_key,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': time_stamp,
        'X-BAPI-RECV-WINDOW': recv_window,
        'Content-Type': 'application/json'
    }
    if(method=="POST"):
        response = httpClient.request(method, url+endPoint, headers=headers, data=payload)
    else:
        response = httpClient.request(method, url+endPoint+"?"+payload, headers=headers)
    print(response.text)
    print(Info + " Elapsed Time : " + str(response.elapsed))


"""
Load private_key.pem, then generate base64 signature
"""
def genSignature(payload, rsa_private_key_path):
    param_str= str(time_stamp) + api_key + recv_window + payload

    with open(rsa_private_key_path, "r") as private_key_obj:
        private_key_str = private_key_obj.read()
    private_key = RSA.importKey(private_key_str)
    encoded_param = SHA256.new(param_str.encode("utf-8"))
    signature = PKCS1_v1_5.new(private_key).sign(encoded_param)

    return base64.b64encode(signature).decode()

#Create Internal Transfer ( SPOT to UNIFIED )
endpoint="/asset/v3/private/transfer/inter-transfer"
method="POST"
transferId=uuid.uuid4()
params='{"transferId": "' + str(transferId) +  '","coin": "USDT","amount": "1","from_account_type": "SPOT","to_account_type": "UNIFIED"}'
HTTP_Request(endpoint,method,params,"InternalTransfer")

#Query Internal Transfer List
endpoint="/asset/v3/private/transfer/inter-transfer/list/query"
method="GET"
params='coin=USDT'
HTTP_Request(endpoint,method,params,"InternalTransferList")
