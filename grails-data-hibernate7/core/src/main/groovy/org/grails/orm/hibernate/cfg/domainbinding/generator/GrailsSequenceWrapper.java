package org.grails.orm.hibernate.cfg.domainbinding.generator;

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;

import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;

import static org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum.NATIVE;
import static org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum.fromName;

public class GrailsSequenceWrapper {

    public Generator getGenerator(
            String name,
            GeneratorCreationContext context,
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            JdbcEnvironment jdbcEnvironment) {
        return GrailsSequenceGeneratorEnum.getGenerator(fromName(name).orElse(NATIVE), context, mappedId, domainClass, jdbcEnvironment);
    }
}
