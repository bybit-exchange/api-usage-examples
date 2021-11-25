package example

import (
	"crypto/hmac"
	"crypto/sha256"
	"fmt"
	"io"
	"sort"
)

func GetSignature(params map[string]string, key string) string {
	keys := make([]string, len(params))
	i := 0
	_val := ""
	for k, _ := range params {
		keys[i] = k
		i++
	}
	sort.Strings(keys)
	for _, k := range keys {
		_val += k + "=" + params[k] + "&"
	}
	_val = _val[0 : len(_val)-1]
	h := hmac.New(sha256.New, []byte(key))
	io.WriteString(h, _val)
	return fmt.Sprintf("%x", h.Sum(nil))
}
