package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.query.Query
import spock.lang.Issue

/**
 * Created by graemerocher on 02/11/16.
 */
class DetachedCriteriaAssociationQuerySpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-data-mapping/issues/776')
    void "test that detached nested criteria work for association queries"() {
        when:"an object is queried with a detached association query"
        new BookA(genre: new Genre(description: "horror").save()).save(flush:true)
        DetachedCriteria<BookA> query = BookA.where {
            genre {
                or {
                    eq("id", 0)
                    eq("description", "horror")
                }
            }
        }
        BookA book = query.get()

        then:"The query worked"
        query.criteria.size() == 1
        query.criteria.get(0) instanceof DetachedAssociationCriteria
        query.criteria.get(0).association.name == 'genre'
        query.criteria.get(0).criteria.size() == 1
        query.criteria.get(0).criteria.get(0) instanceof Query.Disjunction
        query.criteria.get(0).criteria.get(0).criteria.size() == 2
        query.criteria.get(0).criteria.get(0).criteria.get(0) instanceof Query.Equals
        query.criteria.get(0).criteria.get(0).criteria.get(0).property == 'id'
        query.criteria.get(0).criteria.get(0).criteria.get(1) instanceof Query.Equals
        query.criteria.get(0).criteria.get(0).criteria.get(1).property == 'description'
        book != null
    }

    @Override
    List getDomainClasses() {
        [BookA, Genre]
    }
}

@Entity
class BookA {
    Genre genre
}

@Entity
class Genre {
    String description
}