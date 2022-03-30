use hex;
use hmac_sha256::HMAC;

pub const API_KEY: &str = "JIDOjapfjOIjood11L";
const API_SECRET: &str = "dkl3l5k4i26jhsehiklfgjpoihiosghiopsh";

fn sign(payload: &str) -> String {
    let mac = HMAC::mac(payload.as_bytes(), API_SECRET.as_bytes());

    hex::encode(mac)
}

pub fn sign_query(params: &Vec<(&str, &str)>) -> (String, String) {
    let query_str = params.iter().fold(String::new(), |acc, &tuple| {
        if acc.is_empty() {
            return acc + tuple.0 + "=" + tuple.1;
        }
        return acc + "&" + tuple.0 + "=" + tuple.1;
    });

    let mut params: Vec<String> = query_str.split('&').map(|s| s.to_owned()).collect();
    params.sort();

    let query_str_sort = params.join("&");
    let sign = sign(&query_str_sort);
    let query_str_sign = format!("{}&sign={}", query_str_sort, sign);

    (sign, query_str_sign)
}

#[cfg(test)]
mod sign {
    use super::*;

    // The golden are generated from python example code
    #[test]
    fn test_sign_query() {
        let params = vec![
            ("api_key", API_KEY),
            ("price", "1.0"),
            ("qty", "100"),
            ("side", "BUY"),
            ("symbol", "BITUSDT"),
            ("type", "LIMIT"),
            ("timeInForce", "IOC"),
        ];
        let (sign, query_string) = sign_query(&params);

        assert_eq!(
            sign,
            "1894512a258140b38f669373e285a9e5dd947fe2d9cbd57d69b40980e793c93c"
        );
        assert_eq!(query_string, 
            "api_key=JIDOjapfjOIjood11L&price=1.0&qty=100&side=BUY&symbol=BITUSDT&timeInForce=IOC&type=LIMIT&sign=1894512a258140b38f669373e285a9e5dd947fe2d9cbd57d69b40980e793c93c"
        );
    }

    #[test]
    fn test_hmac_sign() {
        let sign = sign("test-query-string");

        assert_eq!(
            sign,
            "b77be1457a66446d9ba547d0dba70fcb1c5f5f27bfac7a264e4330999e261079"
        );
    }
}
