package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.FetchMode;
import org.hibernate.mapping.ManyToOne;

import java.util.Optional;

/**
 * Binds inverse values for unidirectional one-to-many associations.
 */
public class UnidirectionalOneToManyInverseValuesBinder {

    public void bindUnidirectionalOneToManyInverseValues(HibernateToManyProperty property, ManyToOne manyToOne) {
        manyToOne.setIgnoreNotFound(property.getIgnoreNotFound());
        manyToOne.setLazy(!FetchMode.JOIN.equals(property.getFetchMode()));
        Optional.ofNullable(property.getLazy()).ifPresent(manyToOne::setLazy);
        manyToOne.setReferencedEntityName(property.getHibernateAssociatedEntity().getName());
    }
}
