package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.id.enhanced.StandardOptimizerDescriptor;
import org.grails.orm.hibernate.cfg.Identity;

import java.util.Optional;
import java.util.Properties;

public class GrailsTableGenerator extends TableGenerator {

    public GrailsTableGenerator(GeneratorCreationContext context, Identity mappedId) {
        Properties generatorProps = Optional.ofNullable(mappedId)
                .map(Identity::getProperties)
                .orElse(new Properties());

        if (!generatorProps.containsKey(SEGMENT_VALUE_PARAM)) {
            String propertyName = context.getProperty().getName();

            // Use the name we just ensured exists in BasicValueIdCreator
            String entityName = (mappedId != null && mappedId.getName() != null)
                    ? mappedId.getName()
                    : "default";

            generatorProps.put(SEGMENT_VALUE_PARAM, entityName + "." + propertyName);
        }

        // Standard Pooled-lo defaults
        if (!generatorProps.containsKey(INCREMENT_PARAM)) {
            generatorProps.put(INCREMENT_PARAM, "50");
        }
        if (!generatorProps.containsKey(OPT_PARAM)) {
            generatorProps.put(OPT_PARAM, "pooled-lo");
        }

        // Fixes the "SQL to format should not be null" error
        this.configure(context, generatorProps);

        // Ensures the hibernate_sequences table and initial rows are in the DDL
//        this.registerExportables(context.getDatabase());
    }
}