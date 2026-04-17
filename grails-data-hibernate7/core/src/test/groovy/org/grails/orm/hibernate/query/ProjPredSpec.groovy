package org.grails.orm.hibernate.query

import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

class ProjPredSpec extends Specification {
    void "test ProjectionPredicate"() {
        given:
        def predicate = new ProjectionPredicate()

        expect:
        predicate.test(new Query.IdProjection())
        predicate.test(new Query.PropertyProjection("foo"))
        predicate.test(new Query.CountProjection())
        predicate.test(new Query.CountDistinctProjection("foo"))
        predicate.test(new Query.MaxProjection("foo"))
        predicate.test(new Query.MinProjection("foo"))
        predicate.test(new Query.SumProjection("foo"))
        predicate.test(new Query.AvgProjection("foo"))
        predicate.test(new Query.DistinctProjection())
        predicate.test(new Query.GroupPropertyProjection("foo"))
        !predicate.test(new Query.Projection() {})
    }
}
