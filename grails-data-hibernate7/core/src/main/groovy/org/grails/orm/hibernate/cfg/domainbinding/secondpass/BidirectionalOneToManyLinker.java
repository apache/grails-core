package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.PersistentClass;

/**
 * Links bidirectional one-to-many associations by copying columns.
 */
public class BidirectionalOneToManyLinker {

    private final GrailsPropertyResolver grailsPropertyResolver;

    public BidirectionalOneToManyLinker(GrailsPropertyResolver grailsPropertyResolver) {
        this.grailsPropertyResolver = grailsPropertyResolver;
    }

    public void link(Collection collection, PersistentClass associatedClass, DependantValue key, GrailsHibernatePersistentProperty otherSide) {
        collection.setInverse(true);

        for (Column column : grailsPropertyResolver.getProperty(associatedClass, otherSide.getName()).getValue().getColumns()) {
            Column mappingColumn = new Column();
            mappingColumn.setName(column.getName());
            mappingColumn.setLength(column.getLength());
            mappingColumn.setNullable(otherSide.isNullable());
            mappingColumn.setSqlType(column.getSqlType());

            mappingColumn.setValue(key);
            key.addColumn(mappingColumn);
            key.getTable().addColumn(mappingColumn);
        }
    }
}
