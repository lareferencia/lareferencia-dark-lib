package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import net.sf.saxon.functions.Min;

import java.io.Serializable;

public interface MinterHttpResponse<T> extends Serializable {


    @SneakyThrows
    static <T extends MinterHttpResponse> T fromString(String body, Class<T> clazz) {
        ObjectMapper objectMapper = new ObjectMapper();
        return (T) objectMapper.readValue(body, (clazz));
    }

}
