package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Assigned;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.id.uuid.UuidGenerator;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;

public class BasicValueIdCreator {

    private final JdbcEnvironment jdbcEnvironment;
    private HibernatePersistentEntity domainClass;
    private final Map<String, BiFunction<GeneratorCreationContext, Identity, Generator>> generatorFactories;
    @SuppressWarnings("unused") // kept for tests that want to provide a prototype BasicValue
    private final BasicValue id;

    public BasicValueIdCreator(MetadataBuildingContext metadataBuildingContext, JdbcEnvironment jdbcEnvironment, HibernatePersistentEntity domainClass, RootClass entity) {
        // create a prototype BasicValue (table will be set per-entity when creating the actual BasicValue)
        this(jdbcEnvironment, new BasicValue(metadataBuildingContext, entity.getTable()), new HashMap<>());
        this.domainClass = domainClass;
        initializeGeneratorFactories();
    }

    public BasicValueIdCreator(MetadataBuildingContext metadataBuildingContext, JdbcEnvironment jdbcEnvironment, Map<String, BiFunction<GeneratorCreationContext, Identity, Generator>> generatorFactories) {
        this(jdbcEnvironment, new BasicValue(metadataBuildingContext), generatorFactories);
    }

    protected BasicValueIdCreator(JdbcEnvironment  jdbcEnvironment
                                  , BasicValue prototypeBasicValue
            , Map<String, BiFunction<GeneratorCreationContext
                    , Identity
                    , Generator>> generatorFactories) {
        this.generatorFactories = generatorFactories;
        this.jdbcEnvironment = jdbcEnvironment;
        this.id = prototypeBasicValue;
    }

    private void initializeGeneratorFactories() {
        generatorFactories.put("identity", (context, mappedId) -> new GrailsIdentityGenerator(context, mappedId));

        BiFunction<GeneratorCreationContext, Identity, Generator> sequenceFactory = (context, mappedId) -> new GrailsSequenceStyleGenerator(context, mappedId, jdbcEnvironment);
        generatorFactories.put("sequence", sequenceFactory);
        generatorFactories.put("sequence-identity", sequenceFactory);

        generatorFactories.put("increment", (context, mappedId) -> new GrailsIncrementGenerator(context, mappedId, jdbcEnvironment, domainClass));
        generatorFactories.put("uuid", (context, mappedId) -> new UuidGenerator(context.getType().getReturnedClass()));
        generatorFactories.put("uuid2", (context, mappedId) -> new UuidGenerator(context.getType().getReturnedClass()));
        generatorFactories.put("assigned", (context, mappedId) -> new Assigned());
        generatorFactories.put("table", (context, mappedId) -> new GrailsTableGenerator(context, mappedId, jdbcEnvironment));
        generatorFactories.put("enhanced-table", (context, mappedId) -> new GrailsTableGenerator(context,mappedId, jdbcEnvironment));
        generatorFactories.put("hilo", (context, mappedId) -> new SequenceStyleGenerator());
    }

    public BasicValue getBasicValueId(RootClass entity, Identity mappedId, boolean useSequence) {
        // create a BasicValue for the specific entity table (do not reuse the prototype directly because table differs)
        String generatorName = determineGeneratorName(mappedId, useSequence);
        if (mappedId != null && mappedId.getName() == null) {
            mappedId.setName(entity.getEntityName());
        }
        id.setCustomIdGeneratorCreator(context -> createGenerator(mappedId, context, generatorName));
        return id;
    }

    private Generator createGenerator(Identity mappedId, GeneratorCreationContext context, String generatorName) {
        return generatorFactories.getOrDefault(generatorName, (ctx, mid) -> new GrailsNativeGenerator(ctx))
                .apply(context, mappedId);
    }

    private String determineGeneratorName(Identity mappedId, boolean useSequence) {
        return Optional.ofNullable(mappedId)
                .map(Identity::getGenerator)
                .filter(gen -> !("native".equals(gen) && useSequence))
                .orElse(useSequence ? "sequence-identity" : "native");
    }
}