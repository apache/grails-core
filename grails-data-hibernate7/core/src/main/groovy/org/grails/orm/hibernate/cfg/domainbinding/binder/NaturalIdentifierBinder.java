package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.apache.commons.collections.CollectionUtils;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity;
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.UniqueKey;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class NaturalIdentifierBinder {

    private final UniqueNameGenerator uniqueNameGenerator;

    public NaturalIdentifierBinder(UniqueNameGenerator uniqueNameGenerator) {
        this.uniqueNameGenerator = uniqueNameGenerator;
    }

    public NaturalIdentifierBinder() {
        this(new UniqueNameGenerator());
    }

    public void bindNaturalIdentifier(Mapping mapping, PersistentClass persistentClass) {
        Optional.ofNullable(mapping.getIdentity())
                .map(HibernateIdentity::getNatural)
                .flatMap(naturalId -> naturalId.createUniqueKey(persistentClass))
                .ifPresent(uk -> {
                    uniqueNameGenerator.setGeneratedUniqueName(uk);
                    persistentClass.getTable().addUniqueKey(uk);
                });
    }
}
