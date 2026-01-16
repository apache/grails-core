package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.IdentityGenerator;

public class GrailsIdentityGenerator extends IdentityGenerator {

    public GrailsIdentityGenerator(GeneratorCreationContext context) {
        super();
        context.getProperty().getValue().getColumns().get(0).setIdentity(true);
    }
}
