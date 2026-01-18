package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import java.util.Optional;
import java.util.Properties;

import org.grails.orm.hibernate.cfg.Identity;

public class GrailsSequenceStyleGenerator extends SequenceStyleGenerator {

    public GrailsSequenceStyleGenerator(GeneratorCreationContext context, org.grails.orm.hibernate.cfg.Identity mappedId) {
        var generatorProps = Optional.ofNullable(mappedId).map(Identity::getProperties).orElse(new Properties());
        super.configure(context, generatorProps);
    }
}
