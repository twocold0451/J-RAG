package com.twocold.jrag.config;

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
            // PGvector converters
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
            },
            // JSONB to String converter (for source_meta field)
                (Converter<PGobject, String>) source -> {
                    if (source == null) {
                        return null;
                    }
                    return source.getValue();
                },
                (Converter<String, PGobject>) source -> {
                    if (source == null) {
                        return null;
                    }
                    PGobject pgObject = new PGobject();
                    pgObject.setType("jsonb");
                    try {
                        pgObject.setValue(source);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return pgObject;
                },
            // tsvector to String converter (for content_search field - read only)
                (Converter<PGobject, String>) source -> {
                    if (source == null) {
                        return null;
                    }
                    return source.getValue();
                }
        );
    }
}
