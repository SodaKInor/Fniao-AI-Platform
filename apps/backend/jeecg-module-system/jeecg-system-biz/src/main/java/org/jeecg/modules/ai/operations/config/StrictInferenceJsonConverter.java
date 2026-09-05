package org.jeecg.modules.ai.operations.config;

import org.jeecg.modules.ai.image.api.dto.InferenceRequestDto;
import org.jeecg.modules.ai.stream.api.dto.StreamSessionRequestDto;
import org.jeecg.modules.ai.video.api.dto.VideoJobRequestDto;

import java.io.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.*;
import org.springframework.http.converter.*;

/** Applies only to bounded AI submissions, leaving existing application JSON settings intact. */
public final class StrictInferenceJsonConverter extends AbstractHttpMessageConverter<Object> {
    private final ObjectMapper json=new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    public StrictInferenceJsonConverter() { super(MediaType.APPLICATION_JSON); }
    protected boolean supports(Class<?> type) {
        return type==InferenceRequestDto.class || type==VideoJobRequestDto.class || type==StreamSessionRequestDto.class;
    }
    public boolean canWrite(Class<?> type,MediaType media) { return false; }
    protected Object readInternal(Class<?> type,HttpInputMessage message) throws IOException {
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
    protected void writeInternal(Object value,HttpOutputMessage output) { throw new UnsupportedOperationException(); }
}
