package org.grails.orm.hibernate.query;

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class DetachedAssociationFunction implements Function<Query.Criterion, List<DetachedAssociationCriteria>> {
    @Override
    public List<DetachedAssociationCriteria> apply(Query.Criterion o) {
        List<Query.Criterion> criteria;
        if (o instanceof Query.In c && Objects.nonNull(c.getSubquery()) ) {
            criteria = c.getSubquery().getCriteria();
        } else if (o instanceof Query.Exists c && Objects.nonNull(c.getSubquery()) ) {
            criteria = c.getSubquery().getCriteria();
        } else if (o instanceof Query.NotExists c && Objects.nonNull(c.getSubquery()) ) {
            criteria = c.getSubquery().getCriteria();
        } else if (o instanceof Query.SubqueryCriterion c && Objects.nonNull(c.getValue()) ) {
            criteria =  c.getValue().getCriteria();
        } else {
            criteria = List.of(o);
        }
        return criteria.stream()
                .filter(DetachedAssociationCriteria.class::isInstance)
                .map(DetachedAssociationCriteria.class::cast)
                .toList();
    }
}
