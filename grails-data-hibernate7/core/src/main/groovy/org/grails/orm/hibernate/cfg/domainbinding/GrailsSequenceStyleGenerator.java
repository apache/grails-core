package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.grails.orm.hibernate.cfg.Identity;

import java.util.Optional;
import java.util.Properties;

public class GrailsSequenceStyleGenerator extends SequenceStyleGenerator {

    public GrailsSequenceStyleGenerator(GeneratorCreationContext context, Identity mappedId, JdbcEnvironment jdbcEnvironment) {
        Properties generatorProps = Optional.ofNullable(mappedId)
                .map(Identity::getProperties)
                .orElse(new Properties());

        generatorProps.putIfAbsent(INCREMENT_PARAM, "50");
        generatorProps.putIfAbsent(OPT_PARAM, "pooled-lo");

        super.configure(context, generatorProps);

        if (jdbcEnvironment != null) {
            var database = context.getDatabase();
            if (getDatabaseStructure() != null) {
                this.registerExportables(database);
            }

            var physicalName = database.getDefaultNamespace().getPhysicalName();

            String catalog = (physicalName.catalog() != null)
                    ? physicalName.catalog().getCanonicalName()
                    : null;

            String schema = (physicalName.schema() != null)
                    ? physicalName.schema().getCanonicalName()
                    : null;

            if (getDatabaseStructure() != null) {
                SqlStringGenerationContext sqlContext = SqlStringGenerationContextImpl.fromExplicit(
                        jdbcEnvironment,
                        database,
                        catalog,
                        schema
                );
                this.initialize(sqlContext);
            }
        }
    }

    @Override
    public void initialize(SqlStringGenerationContext context) {
        super.initialize(context);
    }
}
