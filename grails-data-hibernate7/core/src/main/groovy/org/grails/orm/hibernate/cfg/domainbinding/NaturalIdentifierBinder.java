package org.grails.orm.hibernate.cfg.domainbinding;

import org.apache.commons.collections.CollectionUtils;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.UniqueKey;

import java.util.List;
import java.util.Objects;
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
                .filter(Identity.class::isInstance)
                .map(Identity.class::cast)
                .map(Identity::getNatural)
                .ifPresent(naturalId -> {
                    if(CollectionUtils.isEmpty(naturalId.getPropertyNames())) {
                        return;
                    }
                    var uk = new UniqueKey();
                    uk.setTable(persistentClass.getTable());
                    Stream<String> stringStream = naturalId.getPropertyNames()
                            .stream()
                            .filter(persistentClass::hasProperty);
                    List<String> list = stringStream.toList();
                    Integer pks = list.stream()
                            .map(persistentClass::getProperty)
                            .map(property -> {
                                property.setNaturalIdentifier(true);
                                property.setUpdateable(naturalId.isMutable());
                                uk.addColumns(property.getValue());
                                return 1;
                            })
                            .reduce(0, Integer::sum);
                    if (pks > 0) {
                        uniqueNameGenerator.setGeneratedUniqueName(uk);
                        persistentClass.getTable().addUniqueKey(uk);
                    }
        });
    }
}
