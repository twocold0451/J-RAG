package com.example.qarag.config;

import com.pgvector.PGvector;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Override
    public List<Object> userConverters() {
        return Arrays.asList(
            new Converter<PGobject, PGvector>() {
                @Override
                public PGvector convert(PGobject source) {
                    String value = source.getValue();
                    if (value == null) {
                        return null;
                    }
                    try {
                        return new PGvector(value);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            },
            new Converter<PGvector, PGobject>() {
                @Override
                public PGobject convert(PGvector source) {
                    PGobject jsonObject = new PGobject();
                    jsonObject.setType("vector");
                    try {
                        jsonObject.setValue(source.toString());
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return jsonObject;
                }
            }
        );
    }
}
