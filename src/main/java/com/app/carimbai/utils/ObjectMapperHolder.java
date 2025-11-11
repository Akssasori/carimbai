package com.app.carimbai.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectMapperHolder {

    public static final ObjectMapper INSTANCE = new ObjectMapper();

    private ObjectMapperHolder() {}
}
