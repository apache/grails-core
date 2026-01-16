package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.GrailsSequenceStyleGenerator;
import org.grails.orm.hibernate.cfg.Identity;

public class BasicValueIdCreator {

    private final  MetadataBuildingContext metadataBuildingContext;

    public BasicValueIdCreator(MetadataBuildingContext metadataBuildingContext ) {
        this.metadataBuildingContext = metadataBuildingContext;
    }

    public BasicValue getBasicValueId(RootClass entity, Identity mappedId, boolean useSequence) {
        BasicValue id = new BasicValue(metadataBuildingContext, entity.getTable());

        String generator;
        if (mappedId == null) {
            generator = useSequence ? "sequence-identity" : "native";
        } else {
            generator = mappedId.getGenerator();
            if ("native".equals(generator) && useSequence) {
                generator = "sequence-identity";
            }
        }

        switch (generator) {
            case "identity" -> id.setCustomIdGeneratorCreator(context -> {
                // Force IdentityGenerator for databases like MySQL/H2
                var gen = new org.hibernate.id.IdentityGenerator();
                context.getProperty().getValue().getColumns().get(0).setIdentity(true);
                return gen;
            });

            case "sequence", "sequence-identity" -> id.setCustomIdGeneratorCreator(context -> {
                return new GrailsSequenceStyleGenerator(context,mappedId);
            });

            case "increment" -> id.setCustomIdGeneratorCreator(context -> {
                return new org.hibernate.id.IncrementGenerator();
            });

            case "uuid", "uuid2" -> id.setCustomIdGeneratorCreator(context -> {
                return new org.hibernate.id.uuid.UuidGenerator(context.getType().getReturnedClass());
            });

            case "assigned" -> id.setCustomIdGeneratorCreator(context -> {
                return new org.hibernate.id.Assigned();
            });

            case "table", "enhanced-table" -> id.setCustomIdGeneratorCreator(context -> {
                return new org.hibernate.id.enhanced.TableGenerator();
            });

            case "hilo" -> id.setCustomIdGeneratorCreator(context -> {
                // Note: Legacy Hilo is often replaced by SequenceStyleGenerator with optimizer
                return new org.hibernate.id.enhanced.SequenceStyleGenerator();
            });

            default -> id.setCustomIdGeneratorCreator(GrailsNativeGenerator::new);
        }
        return id;
    }
}
