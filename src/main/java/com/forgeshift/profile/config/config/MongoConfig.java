package com.forgeshift.profile.config.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Mongo customisations for this service.
 *
 * <p>Removes the {@code _class} field Spring Data Mongo writes by default
 * (polymorphic type hint). All {@code @Document} entities in this service
 * are concrete, non-polymorphic — so the hint is dead weight on disk and
 * leaks the Java package name to API callers and external readers (notably
 * the discovery and migration services, which have their own concrete
 * mapping classes).
 *
 * <p><b>If you ever add inheritance/abstract types to a {@code @Document},
 * revisit this</b> — Spring Data won't be able to pick the right subclass
 * on read without the type hint.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory,
                                                       MongoMappingContext context,
                                                       MongoCustomConversions conversions) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, context);
        converter.setCustomConversions(conversions);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }
}
