package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.io.Serial;
import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

public class MapSecondPass implements org.hibernate.boot.spi.SecondPass, GrailsSecondPass {
    @Serial
    private static final long serialVersionUID = -3244991685626409031L;

    private final MapSecondPassBinder mapSecondPassBinder;
    protected final HibernateToManyProperty property;
    protected final @Nonnull InFlightMetadataCollector mappings;
    protected final Collection collection;

    public MapSecondPass(MapSecondPassBinder mapSecondPassBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                         @Nonnull Collection coll) {
        this.mapSecondPassBinder = mapSecondPassBinder;
        this.property = property;
        this.mappings = mappings;
        this.collection = coll;
    }



    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        mapSecondPassBinder.bindMapSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.Map) collection);
        createCollectionKeys(collection);
    }


}
