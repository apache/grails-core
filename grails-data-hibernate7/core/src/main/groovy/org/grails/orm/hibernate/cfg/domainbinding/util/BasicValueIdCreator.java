package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper;

public class BasicValueIdCreator {

    private final JdbcEnvironment jdbcEnvironment;
    private GrailsHibernatePersistentEntity domainClass;
    @SuppressWarnings("unused") // kept for tests that want to provide a prototype BasicValue
    private final BasicValue id;
    private final GrailsSequenceWrapper grailsSequenceWrapper;

    public BasicValueIdCreator(MetadataBuildingContext metadataBuildingContext, JdbcEnvironment jdbcEnvironment, GrailsHibernatePersistentEntity domainClass, RootClass entity) {
        // create a prototype BasicValue (table will be set per-entity when creating the actual BasicValue)
        this.id =  new BasicValue(metadataBuildingContext, entity.getTable());
        this.jdbcEnvironment = jdbcEnvironment;
        this.domainClass = domainClass;
        this.grailsSequenceWrapper = new GrailsSequenceWrapper();
    }



    protected BasicValueIdCreator(JdbcEnvironment  jdbcEnvironment
            , BasicValue prototypeBasicValue
    , GrailsSequenceWrapper grailsSequenceWrapper) {
        this.jdbcEnvironment = jdbcEnvironment;
        this.id = prototypeBasicValue;
        this.grailsSequenceWrapper = grailsSequenceWrapper;
    }


    public BasicValue getBasicValueId(Identity mappedId, boolean useSequence) {
        // create a BasicValue for the specific entity table (do not reuse the prototype directly because table differs)
        String generatorName = Identity.determineGeneratorName(mappedId, useSequence);
        id.setCustomIdGeneratorCreator(context -> createGenerator(mappedId, context, generatorName));
        return id;
    }

    private Generator createGenerator(Identity mappedId, GeneratorCreationContext context, String generatorName) {
        return grailsSequenceWrapper.getGenerator(generatorName, context, mappedId, domainClass, jdbcEnvironment);
    }
}

