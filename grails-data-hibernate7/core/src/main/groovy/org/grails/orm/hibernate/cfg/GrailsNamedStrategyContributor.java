package org.grails.orm.hibernate.cfg;

import org.checkerframework.checker.units.qual.C;
import org.hibernate.boot.registry.selector.spi.NamedStrategyContributions;
import org.hibernate.boot.registry.selector.spi.NamedStrategyContributor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.property.access.spi.PropertyAccessStrategy;

import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy;

public class GrailsNamedStrategyContributor implements NamedStrategyContributor {

    @Override
    public void contributeStrategyImplementations(NamedStrategyContributions contributions) {
        contributions.contributeStrategyImplementor(
                PropertyAccessStrategy.class,
                TraitPropertyAccessStrategy.class,
                "traitProperty"
        );
    }

    @Override
    public void clearStrategyImplementations(NamedStrategyContributions contributions) {
        contributions.removeStrategyImplementor(PropertyAccessStrategy.class
                , TraitPropertyAccessStrategy.class);
    }

}
