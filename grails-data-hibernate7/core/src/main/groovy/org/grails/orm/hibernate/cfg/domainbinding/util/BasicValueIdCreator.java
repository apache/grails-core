package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper;

public class BasicValueIdCreator {

    private final JdbcEnvironment jdbcEnvironment;
    private final GrailsSequenceWrapper grailsSequenceWrapper;

    public BasicValueIdCreator(JdbcEnvironment jdbcEnvironment) {
        this.jdbcEnvironment = jdbcEnvironment;
        this.grailsSequenceWrapper = new GrailsSequenceWrapper();
    }



    protected BasicValueIdCreator(JdbcEnvironment  jdbcEnvironment
    , GrailsSequenceWrapper grailsSequenceWrapper) {
        this.jdbcEnvironment = jdbcEnvironment;
        this.grailsSequenceWrapper = grailsSequenceWrapper;
    }


    public BasicValue getBasicValueId(Identity mappedId, GrailsHibernatePersistentEntity domainClass, BasicValue id, boolean useSequence) {
        // create a BasicValue for the specific entity table (do not reuse the prototype directly because table differs)
        String generatorName = Identity.determineGeneratorName(mappedId, useSequence);
        id.setCustomIdGeneratorCreator(context -> createGenerator(mappedId, domainClass, context, generatorName));
        return id;
    }

    private Generator createGenerator(Identity mappedId, GrailsHibernatePersistentEntity domainClass, GeneratorCreationContext context, String generatorName) {
        return grailsSequenceWrapper.getGenerator(generatorName, context, mappedId, domainClass, jdbcEnvironment);
    }
}

