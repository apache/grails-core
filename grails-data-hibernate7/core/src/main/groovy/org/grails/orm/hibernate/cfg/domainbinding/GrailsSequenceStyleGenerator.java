package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.MappingException;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class GrailsSequenceStyleGenerator extends SequenceStyleGenerator {

    public GrailsSequenceStyleGenerator(GeneratorCreationContext context, org.grails.orm.hibernate.cfg.Identity mappedId) {
        // Call super's no-arg constructor first
        super();
        Properties generatorProps = new Properties();
        if (mappedId != null && mappedId.getParams() != null) {
            for (Map.Entry entry : (Set<Map.Entry>) mappedId.getParams().entrySet()) {
                generatorProps.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        super.configure(context.getType(), generatorProps, context.getServiceRegistry());
    }
}
