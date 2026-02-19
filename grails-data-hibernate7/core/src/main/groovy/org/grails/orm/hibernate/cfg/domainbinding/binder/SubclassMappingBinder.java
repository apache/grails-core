package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.JoinedSubclass;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SingleTableSubclass;
import org.hibernate.mapping.Subclass;
import org.hibernate.mapping.UnionSubclass;

import java.util.Optional;

public class SubclassMappingBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final JoinedSubClassBinder joinedSubClassBinder;
    private final UnionSubclassBinder unionSubclassBinder;
    private final SingleTableSubclassBinder singleTableSubclassBinder;
    private final ClassPropertiesBinder classPropertiesBinder;

    public SubclassMappingBinder(
            MetadataBuildingContext metadataBuildingContext,
            JoinedSubClassBinder joinedSubClassBinder,
            UnionSubclassBinder unionSubclassBinder,
            SingleTableSubclassBinder singleTableSubclassBinder,
            ClassPropertiesBinder classPropertiesBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.joinedSubClassBinder = joinedSubClassBinder;
        this.unionSubclassBinder = unionSubclassBinder;
        this.singleTableSubclassBinder = singleTableSubclassBinder;
        this.classPropertiesBinder = classPropertiesBinder;
    }

    public @NonNull Subclass createSubclassMapping(@NonNull GrailsHibernatePersistentEntity subEntity
            , PersistentClass parent
            , @NonNull InFlightMetadataCollector mappings
            , Mapping m) {
        Subclass subClass;
        subEntity.configureDerivedProperties();
        if (!m.getTablePerHierarchy() && !m.isTablePerConcreteClass()) {
            var joined = new JoinedSubclass(parent, this.metadataBuildingContext);
            joinedSubClassBinder.bindJoinedSubClass(subEntity, joined, mappings);
            subClass = joined;
        }
        else if(m.isTablePerConcreteClass()) {
            var union  = new UnionSubclass(parent, this.metadataBuildingContext);
            unionSubclassBinder.bindUnionSubclass(subEntity, union, mappings);
            subClass = union;
        }
        else {
            var singleTableSubclass = new SingleTableSubclass(parent, this.metadataBuildingContext);

            singleTableSubclassBinder.bindSubClass(subEntity, singleTableSubclass, mappings);
            subClass = singleTableSubclass;
        }
        subClass.setBatchSize(Optional.ofNullable(m.getBatchSize()).orElse(-1));
        subClass.setDynamicUpdate(m.getDynamicUpdate());
        subClass.setDynamicInsert(m.getDynamicInsert());
        subClass.setCached(parent.isCached());
        subClass.setAbstract(subEntity.isAbstract());
        subClass.setEntityName(subEntity.getName());
        subClass.setJpaEntityName(GrailsHibernateUtil.unqualify(subEntity.getName()));
        classPropertiesBinder.bindClassProperties(subEntity, subClass, mappings);
        return subClass;
    }
}
