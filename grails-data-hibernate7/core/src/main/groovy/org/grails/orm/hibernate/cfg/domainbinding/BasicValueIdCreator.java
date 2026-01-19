package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiFunction;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.Assigned;
import org.hibernate.id.IncrementGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.id.uuid.UuidGenerator;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.Identity;

public class BasicValueIdCreator {

    private final MetadataBuildingContext metadataBuildingContext;
    private final Map<String, BiFunction<GeneratorCreationContext, Identity, Generator>> generatorFactories;

    public BasicValueIdCreator(MetadataBuildingContext metadataBuildingContext) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.generatorFactories = new HashMap<>();
        initializeGeneratorFactories();
    }

    protected BasicValueIdCreator(MetadataBuildingContext metadataBuildingContext, Map<String, BiFunction<GeneratorCreationContext, Identity, Generator>> generatorFactories) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.generatorFactories = generatorFactories;
    }

    private void initializeGeneratorFactories() {
        generatorFactories.put("identity", (context, mappedId) -> new GrailsIdentityGenerator(context, mappedId));

        BiFunction<GeneratorCreationContext, Identity, Generator> sequenceFactory = (context, mappedId) -> new GrailsSequenceStyleGenerator(context, mappedId);
        generatorFactories.put("sequence", sequenceFactory);
        generatorFactories.put("sequence-identity", sequenceFactory);

        generatorFactories.put("increment", (context, mappedId) -> new IncrementGenerator());
        generatorFactories.put("uuid", (context, mappedId) -> new UuidGenerator(context.getType().getReturnedClass()));
        generatorFactories.put("uuid2", (context, mappedId) -> new UuidGenerator(context.getType().getReturnedClass()));
        generatorFactories.put("assigned", (context, mappedId) -> new Assigned());
        generatorFactories.put("table", (context, mappedId) -> new GrailsTableGenerator(context, mappedId));
        generatorFactories.put("enhanced-table", (context, mappedId) -> new GrailsTableGenerator(context,mappedId));
        generatorFactories.put("hilo", (context, mappedId) -> new SequenceStyleGenerator());
    }

    public BasicValue getBasicValueId(RootClass entity, Identity mappedId, boolean useSequence) {
        BasicValue id = new BasicValue(metadataBuildingContext, entity.getTable());
        String generatorName = determineGeneratorName(mappedId, useSequence);
        final String entityName = entity.getEntityName();

        id.setCustomIdGeneratorCreator(context -> {
            // Ensure the ID object knows which entity it belongs to
            if (mappedId != null && mappedId.getName() == null) {
                mappedId.setName(entityName);
            }
            return generatorFactories.getOrDefault(generatorName, (ctx, mid) -> new GrailsNativeGenerator(ctx))
                    .apply(context, mappedId);
        });

        return id;
    }

    private String determineGeneratorName(Identity mappedId, boolean useSequence) {
        return Optional.ofNullable(mappedId)
                .map(Identity::getGenerator)
                .filter(gen -> !("native".equals(gen) && useSequence))
                .orElse(useSequence ? "sequence-identity" : "native");
    }
}