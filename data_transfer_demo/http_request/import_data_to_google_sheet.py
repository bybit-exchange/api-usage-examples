import requests
import time
import hashlib
import hmac
import json
import pandas as pd
import os
import pickle
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request

api_key = 'xxxxxxxxxxxxxxx'
secret_key = 'xxxxxxxxxxxxxxxxxxxxxx'
httpClient = requests.Session()
recv_window = str(5000)
url = "https://api-testnet.bybit.com"  # Testnet endpoint
SCOPES = ['https://www.googleapis.com/auth/spreadsheets']

def get_google_auth_token():
    creds = None

    # The file token.pickle stores the user's access and refresh tokens, and is
    # created automatically when the authorization flow completes for the first time.
    if os.path.exists('token.pickle'):
        with open('token.pickle', 'rb') as token:
            creds = pickle.load(token)

    # If there are no (valid) credentials available, prompt the user to log in.
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(
                'credentials.json', SCOPES)
            creds = flow.run_local_server(port=0)
            # Save the credentials for the next run
            with open('token.pickle', 'wb') as token:
                pickle.dump(creds, token)

    return creds.token
def HTTP_Request(endPoint, method, payload, Info):
    global time_stamp
    time_stamp = str(int(time.time() * 10 ** 3))
    signature = genSignature(payload)
    headers = {
        'X-BAPI-API-KEY': api_key,
        'X-BAPI-SIGN': signature,
        'X-BAPI-SIGN-TYPE': '2',
        'X-BAPI-TIMESTAMP': time_stamp,
        'X-BAPI-RECV-WINDOW': recv_window,
        'Content-Type': 'application/json'
    }
    if (method == "POST"):
        response = httpClient.request(method, url + endPoint, headers=headers, data=payload)
    else:
        response = httpClient.request(method, url + endPoint + "?" + payload, headers=headers)
    print(response.text)
    print(Info + " Elapsed Time : " + str(response.elapsed))

    response_json = response.json()
    list_data = response_json['result']['list']
    df = pd.DataFrame(list_data)
    values_to_append = df.values.tolist()
    spreadSheetId = "xxxxxxxxxxxx"  # replace by your sheet id
    append_to_google_sheet(spreadSheetId, "A1", values_to_append)

def genSignature(payload):
    param_str = str(time_stamp) + api_key + recv_window + payload
    hash = hmac.new(bytes(secret_key, "utf-8"), param_str.encode("utf-8"), hashlib.sha256)
    signature = hash.hexdigest()
    return signature


def append_to_google_sheet(spreadsheet_id, range_, values):
    # Define the endpoint URL (replace YOUR_SPREADSHEET_ID and YOUR_RANGE with appropriate values)
    endpoint = f"https://sheets.googleapis.com/v4/spreadsheets/{spreadsheet_id}/values/{range_}:append"

    # The access token you got after OAuth2.0 authorization
    access_token = get_google_auth_token()
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json"
    }
    # Prepare the request data
    body = {
        "values": values  # This should be a list of lists representing rows of your data
    }
    params = {
        "valueInputOption": "RAW",
        "insertDataOption": "INSERT_ROWS"
    }
    response = requests.post(endpoint, headers=headers, params=params, data=json.dumps(body))
    if response.status_code == 200:
        print("Data appended successfully")
    else:
        print("Failed to append data:", response.text)


# Get unfilled wallet balance
endpoint = "/v5/market/tickers"
method = "GET"
params = 'category=linear'
HTTP_Request(endpoint, method, params, "Market Ticker Data")
