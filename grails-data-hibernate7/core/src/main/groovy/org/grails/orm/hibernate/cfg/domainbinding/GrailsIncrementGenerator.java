package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.orm.hibernate.cfg.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.IncrementGenerator;
import java.util.Properties;

public class GrailsIncrementGenerator extends IncrementGenerator {

    public GrailsIncrementGenerator(GeneratorCreationContext context, Identity mappedId, JdbcEnvironment jdbcEnvironment, HibernatePersistentEntity domainClass) {
        Properties params = new Properties();
        if (mappedId != null && mappedId.getProperties() != null) {
            params.putAll(mappedId.getProperties());
        }

        // Fix the blank FROM clause: Resolve table name
        String tableName = domainClass.getMappedForm().getTableName();

        if (tableName == null || tableName.isEmpty()) {
            tableName = (mappedId != null && mappedId.getName() != null)
                    ? mappedId.getName()
                    : domainClass.getJavaClass().getSimpleName();
        }
        params.put("table", tableName);

        // Resolve column name
        if (!params.containsKey("column")) {
            params.put("column", context.getProperty().getName());
        }

        // Initialize the internal Hibernate state
        this.configure(context.getType(), params, context.getServiceRegistry());
    }

    @Override
    public void registerExportables(Database database) {
        // Hibernate 7 IncrementGenerator tries to register table exportables
        // which causes the "Unique index or primary key violation" in H2.
        // Overriding this to be empty prevents the duplicate DDL.
    }
}