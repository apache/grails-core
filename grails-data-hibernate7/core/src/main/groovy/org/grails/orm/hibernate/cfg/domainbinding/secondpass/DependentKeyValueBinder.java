package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.mapping.DependantValue;

import java.util.Optional;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

/**
 * Binds a dependent key value for collection associations.
 */
public class DependentKeyValueBinder {

    private final SimpleValueBinder simpleValueBinder;
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;

    public DependentKeyValueBinder(SimpleValueBinder simpleValueBinder, CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder) {
        this.simpleValueBinder = simpleValueBinder;
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
    }

    public void bind(HibernateToManyProperty property, DependantValue key) {
        GrailsHibernatePersistentEntity refDomainClass = property.getHibernateOwner();

        Optional<CompositeIdentity> compositeIdentity = property.supportsJoinColumnMapping() ? refDomainClass.getHibernateCompositeIdentity() : Optional.empty();

        compositeIdentity.ifPresentOrElse(ci -> {
            compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(property, key, ci, refDomainClass, EMPTY_PATH);
        }, () -> {
            simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH);
        });
    }
}
