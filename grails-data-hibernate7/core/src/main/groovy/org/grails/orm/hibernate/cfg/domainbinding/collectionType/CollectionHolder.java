package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.hibernate.boot.spi.MetadataBuildingContext;

public record CollectionHolder(Map<Class<?>, CollectionType> map) {

    public CollectionHolder(MetadataBuildingContext buildingContext) {
        this(Map.ofEntries(
                Map.entry(Set.class, new SetCollectionType(buildingContext)),
                Map.entry(SortedSet.class, new SetCollectionType(buildingContext)),
                Map.entry(List.class, new ListCollectionType(buildingContext)),
                Map.entry(Collection.class, new BagCollectionType(buildingContext)),
                Map.entry(Map.class, new MapCollectionType(buildingContext))
        ));
    }

    public CollectionType get(Class<?> collectionClass) {
        return map.get(collectionClass);
    }
}

