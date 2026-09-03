package org.jeecg.modules.ai.config.jobs;

import java.io.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.jeecg.modules.ai.api.dto.InferenceRequestDto;

/** Applies only to the new inference input, leaving existing application JSON settings intact. */
public final class StrictInferenceJsonConverter extends AbstractHttpMessageConverter<InferenceRequestDto> {
    private final ObjectMapper json=new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    public StrictInferenceJsonConverter() { super(MediaType.APPLICATION_JSON); }
    protected boolean supports(Class<?> type) { return type==InferenceRequestDto.class; }
    public boolean canWrite(Class<?> type,MediaType media) { return false; }
    protected InferenceRequestDto readInternal(Class<? extends InferenceRequestDto> type,HttpInputMessage message) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[1024]; int n;
        while ((n=message.getBody().read(buffer))!=-1) {
            if (bytes.size()+n>16384) throw new HttpMessageNotReadableException("JSON exceeds resource budget",message);
            bytes.write(buffer,0,n);
        }
        byte[] body=bytes.toByteArray();
        int depth=0; boolean string=false,escape=false;
        for (byte b:body) {
            if (string) {
                if (escape) escape=false;
                else if (b=='\\') escape=true;
                else if (b=='"') string=false;
            } else if (b=='"') string=true;
            else if (b=='{' || b=='[') { if (++depth>8) throw new HttpMessageNotReadableException("JSON nesting exceeds budget",message); }
            else if (b=='}' || b==']') depth--;
        }
        try { return json.readValue(body,type); }
        catch (IOException e) { throw new HttpMessageNotReadableException("Invalid inference JSON",message); }
    }
    protected void writeInternal(InferenceRequestDto value,HttpOutputMessage output) { throw new UnsupportedOperationException(); }
}
