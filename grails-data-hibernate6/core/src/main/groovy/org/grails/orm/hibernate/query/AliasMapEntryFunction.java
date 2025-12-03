package org.grails.orm.hibernate.query;

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;

import java.util.Map;
import java.util.function.Function;

public class AliasMapEntryFunction
        implements
        Function<DetachedAssociationCriteria,
                Map.Entry<String, DetachedAssociationCriteria>> {
    @Override
    public Map.Entry<String, DetachedAssociationCriteria> apply(DetachedAssociationCriteria detachedAssociationCriteria) {
        return Map.entry(detachedAssociationCriteria.getAssociationPath(), detachedAssociationCriteria);
    }
}
