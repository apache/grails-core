package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.id.enhanced.StandardOptimizerDescriptor;
import org.grails.orm.hibernate.cfg.Identity;

import java.util.Optional;
import java.util.Properties;

public class GrailsTableGenerator extends TableGenerator {

    public GrailsTableGenerator(GeneratorCreationContext context, Identity mappedId, JdbcEnvironment jdbcEnvironment) {
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
        var database = context.getDatabase();
        this.registerExportables(database);
        // Get the Name record from the physical name
        var physicalName = database.getDefaultNamespace().getPhysicalName();

        // Use the record component accessors (catalog() and schema())
        // instead of the deprecated getCatalog()/getSchema()
        String catalog = (physicalName.catalog() != null)
                ? physicalName.catalog().getCanonicalName()
                : null;

        String schema = (physicalName.schema() != null)
                ? physicalName.schema().getCanonicalName()
                : null;

        // Build the context and initialize templates
        SqlStringGenerationContext context1 = SqlStringGenerationContextImpl.fromExplicit(
                jdbcEnvironment,
                database,
                catalog,
                schema
        );
        this.initialize(context1);
     }
}