#include <map>
#include <openssl/sha.h>
#include <openssl/hmac.h>
#include <string>


typedef std::map<std::string, std::string> Params;

std::string HmacEncode( const char * key, const char * input) {
        const EVP_MD * engine = NULL;
        engine = EVP_sha256();

        unsigned char *p = (unsigned char*)malloc(1024);
        char buf[1024] = {0};  
        char tmp[3] = {0}; 
        unsigned int output_length = 0;
        p = (unsigned char*)malloc(EVP_MAX_MD_SIZE);
 
        HMAC_CTX ctx;
        HMAC_CTX_init(&ctx);
        HMAC_Init_ex(&ctx, key, strlen(key), engine, NULL);
        HMAC_Update(&ctx, (unsigned char*)input, strlen(input)); 
 
        HMAC_Final(&ctx, p, &output_length);
        HMAC_CTX_cleanup(&ctx);
        for (int i = 0; i<32; i++)  
        {  
            sprintf(tmp, "%02x", p[i]);  
            strcat(buf, tmp);  
        }  
        return std::string(buf);
}

std::string GetSignature(Params param, std::string secret){
    std::string input = "";
    for(Params::iterator it=param.begin();it!=param.end();++it){
        input += it->first+"="+it->second+"&";
    }
    return HmacEncode(secret.c_str(), input.substr(0, input.length()-1).c_str());
}