package com.app.carimbai.utils;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ObjectMapperHolder {

    public static final ObjectMapper INSTANCE = JsonMapper.builder().build();

    private ObjectMapperHolder() {}
}
