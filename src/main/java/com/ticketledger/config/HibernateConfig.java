package com.ticketledger.config;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class HibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateJsonCustomizer(JsonMapper jsonMapper) {
        return hibernateProperties -> {
            // Force Hibernate to use our Jackson 3 bridge instead of auto-detected Jackson
            // 2
            hibernateProperties.put("hibernate.type.json_format_mapper", new Jackson3FormatMapper(jsonMapper));
        };
    }

    public static class Jackson3FormatMapper implements FormatMapper {

        private final JsonMapper jsonMapper;

        public Jackson3FormatMapper(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
            if (charSequence == null) {
                return null;
            }
            try {
                return jsonMapper.readValue(charSequence.toString(), javaType.getJavaTypeClass());
            } catch (JacksonException e) {
                throw new RuntimeException("Could not deserialize JSON with Jackson 3", e);
            }
        }

        @Override
        public <T> String toString(T t, JavaType<T> javaType, WrapperOptions wrapperOptions) {
            if (t == null) {
                return null;
            }
            try {
                return jsonMapper.writeValueAsString(t);
            } catch (JacksonException e) {
                throw new RuntimeException("Could not serialize JSON with Jackson 3", e);
            }
        }
    }
}