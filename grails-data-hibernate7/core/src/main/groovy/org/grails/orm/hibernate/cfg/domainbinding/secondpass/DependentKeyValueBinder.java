package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.mapping.DependantValue;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

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
        Mapping mapping = refDomainClass.getMappedForm();

        property.getCompositeIdentity(mapping).ifPresentOrElse(ci -> {
            compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(property, key, ci, refDomainClass, EMPTY_PATH);
        }, () -> {
            simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH);
        });
    }
}
