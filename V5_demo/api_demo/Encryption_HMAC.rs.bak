use std::collections::HashMap;
use std::error::Error;
use hmac::{Hmac, Mac, NewMac};
use sha2::Sha256;
use serde_json::{json, Value};
use reqwest;
use chrono::{Utc};
use hex;

type HmacSha256 = Hmac<Sha256>;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let api_key = "8wYkmpLsMg10eNQyPm";
    let api_secret = "Ouxc34myDnXvei54XsBZgoQzfGxO4bkr2Zsj";
    let timestamp = Utc::now().timestamp_millis().to_string();
    let recv_window = "5000";

    place_order(api_key, api_secret, &timestamp, recv_window)?;

    get_open_order(api_key, api_secret, &timestamp, recv_window)?;
    Ok(())
}

fn get_open_order(api_key: &str, api_secret: &str, timestamp: &String, recv_window: &str) -> Result<(), Box<dyn Error>> {
    let client = reqwest::blocking::Client::new();
    let mut params = HashMap::new();
    params.insert("category", "linear");
    params.insert("symbol", "BTCUSDT");
    params.insert("settleCoin", "USDT");

    let signature = generate_get_signature(&timestamp, api_key, recv_window, &params, api_secret)?;
    let query_str = generate_query_str(&params);

    let response = client.get(&format!("https://api-testnet.bybit.com/v5/order/realtime?{}", query_str))
        .header("X-BAPI-API-KEY", api_key)
        .header("X-BAPI-SIGN", signature)
        .header("X-BAPI-SIGN-TYPE", "2")
        .header("X-BAPI-TIMESTAMP", timestamp.clone())
        .header("X-BAPI-RECV-WINDOW", recv_window)
        .send()?;

    println!("Response: {:?}", response.text()?);
    Ok(())
}

fn place_order(api_key: &str, api_secret: &str, timestamp: &String, recv_window: &str) -> Result<(), Box<dyn Error>> {
    let client = reqwest::blocking::Client::new();
    let mut params = serde_json::Map::new();
    params.insert("category".to_string(), json!("linear"));
    params.insert("symbol".to_string(), json!("BTCUSDT"));
    params.insert("side".to_string(), json!("Buy"));
    params.insert("positionIdx".to_string(), json!(0));
    params.insert("orderType".to_string(), json!("Limit"));
    params.insert("qty".to_string(), json!("0.001"));
    params.insert("price".to_string(), json!("18900"));
    params.insert("timeInForce".to_string(), json!("GTC"));

    let signature = generate_post_signature(&timestamp, api_key, recv_window, &params, api_secret)?;

    let response = client.post("https://api-testnet.bybit.com/v5/order/create")
        .json(&params)
        .header("X-BAPI-API-KEY", api_key)
        .header("X-BAPI-SIGN", &signature)
        .header("X-BAPI-SIGN-TYPE", "2")
        .header("X-BAPI-TIMESTAMP", timestamp.clone())
        .header("X-BAPI-RECV-WINDOW", recv_window)
        .header("Content-Type", "application/json")
        .send()?;

    println!("Response: {:?}", response.text()?);
    Ok(())
}

fn generate_post_signature(timestamp: &str, api_key: &str, recv_window: &str, params: &serde_json::Map<String, Value>, api_secret: &str) -> Result<String, Box<dyn std::error::Error>> {
    let mut mac = HmacSha256::new_from_slice(api_secret.as_bytes()).expect("HMAC can take key of any size");
    mac.update(timestamp.as_bytes());
    mac.update(api_key.as_bytes());
    mac.update(recv_window.as_bytes());
    mac.update(serde_json::to_string(&params)?.as_bytes());

    let result = mac.finalize();
    let code_bytes = result.into_bytes();
    Ok(hex::encode(code_bytes))
}

fn generate_get_signature(timestamp: &str, api_key: &str, recv_window: &str, params: &HashMap<&str, &str>, api_secret: &str) -> Result<String, Box<dyn std::error::Error>> {
    let mut mac = HmacSha256::new_from_slice(api_secret.as_bytes()).expect("HMAC can take key of any size");
    mac.update(timestamp.as_bytes());
    mac.update(api_key.as_bytes());
    mac.update(recv_window.as_bytes());
    mac.update(generate_query_str(params).as_bytes());

    let result = mac.finalize();
    let code_bytes = result.into_bytes();
    Ok(hex::encode(code_bytes))
}

fn generate_query_str(params: &HashMap<&str, &str>) -> String {
    params.iter()
        .map(|(key, value)| format!("{}={}", key, value))
        .collect::<Vec<String>>()
        .join("&")
}