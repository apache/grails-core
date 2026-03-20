package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import org.grails.orm.hibernate.query.JpaFromProvider
import grails.orm.HibernateCriteriaBuilder
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class JpaFromProviderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([JpaFromProviderSpecPerson, JpaFromProviderSpecPet, JpaFromProviderSpecFace])
    }

    private JpaFromProvider bare(Class clazz, From root) {
        def dc = new DetachedCriteria(clazz)
        def cq = Mock(org.hibernate.query.criteria.JpaCriteriaQuery) {
            from(clazz) >> root
        }
        return new JpaFromProvider(dc, cq, root)
    }

    def "getFromsByName returns root for 'root' key"() {
        given:
        From root = Mock(From) {
            getJavaType() >> String
        }
        JpaFromProvider provider = bare(String, root)

        expect:
        provider.getFromsByName().get("root") == root
    }

    def "getFullyQualifiedPath returns root for entity name if it matches root"() {
        given:
        From root = Mock(From) {
            getJavaType() >> String
        }
        JpaFromProvider provider = bare(String, root)

        expect:
        provider.getFullyQualifiedPath("String") == root
    }

    def "getFullyQualifiedPath returns root for 'root' prefix"() {
        given:
        Path idPath = Mock(Path)
        From root = Mock(From) {
            getJavaType() >> String
            get("id") >> idPath
        }
        JpaFromProvider provider = bare(String, root)

        expect:
        provider.getFullyQualifiedPath("root.id") == idPath
    }

    def "getFullyQualifiedPath throws for null property name"() {
        given:
        From root = Mock(From)
        JpaFromProvider provider = bare(String, root)

        when:
        provider.getFullyQualifiedPath(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "clone produces an independent copy that does not affect original"() {
        given:
        From root = Mock(From) {
            getJavaType() >> String
        }
        JpaFromProvider provider = bare(String, root)
        From extra = Mock(From)

        when:
        JpaFromProvider clone = provider.clone()
        clone.put("extra", extra)

        then:
        clone.getFromsByName().containsKey("extra")
        !provider.getFromsByName().containsKey("extra")
    }

    def "put overwrites an existing key"() {
        given:
        From root = Mock(From) {
            getJavaType() >> String
        }
        JpaFromProvider provider = bare(String, root)
        From newRoot = Mock(From)

        when:
        provider.put("root", newRoot)

        then:
        provider.getFromsByName().get("root") == newRoot
    }

    def "root alias registered via setAlias is available for dotted lookup"() {
        given:
        Path idPath = Mock(Path)
        From root = Mock(From) {
            getJavaType() >> String
            get("id") >> idPath
        }
        def dc = new DetachedCriteria(String)
        dc.setAlias("myAlias")
        def cq = Mock(org.hibernate.query.criteria.JpaCriteriaQuery) {
            from(_) >> root
        }
        JpaFromProvider provider = new JpaFromProvider(dc, cq, root)

        when:
        Path result = provider.getFullyQualifiedPath("myAlias.id")

        then:
        result == idPath
    }

    def "getFromsByName creates hierarchical joins for projection paths"() {
        given:
        def dc = new DetachedCriteria(String)
        def cq = Mock(org.hibernate.query.criteria.JpaCriteriaQuery)
        From root = Mock(From) {
            getJavaType() >> String
        }
        From teamJoin = Mock(From) {
            getJavaType() >> String
            alias(_) >> it
        }
        From clubJoin = Mock(From) {
            getJavaType() >> String
            alias(_) >> it
        }

        and: "projections with nested paths"
        def projections = [
                new org.grails.datastore.mapping.query.Query.PropertyProjection("team.club.name")
        ]

        when:
        JpaFromProvider provider = new JpaFromProvider(dc, projections, cq, root)

        then: "joins are created hierarchically"
        1 * root.join("team", jakarta.persistence.criteria.JoinType.LEFT) >> teamJoin
        1 * teamJoin.join("club", jakarta.persistence.criteria.JoinType.LEFT) >> clubJoin
        0 * clubJoin.join(_, _)

        and: "paths are registered in provider"
        provider.getFullyQualifiedPath("team") == teamJoin
        provider.getFullyQualifiedPath("team.club") == clubJoin
    }

    def "constructor with parent provider inherits froms and supports correlation"() {
        given:
        From outerRoot = Mock(From) { getJavaType() >> String }
        JpaFromProvider parent = bare(String, outerRoot)

        and: "subquery detached criteria"
        def subDc = new DetachedCriteria(Integer)
        def subCq = Mock(org.hibernate.query.criteria.JpaCriteriaQuery)
        From subRoot = Mock(From) { getJavaType() >> Integer }

        when:
        JpaFromProvider subProvider = new JpaFromProvider(parent, subDc, [], subCq, subRoot)

        then: "subquery provider has its own root"
        subProvider.getFullyQualifiedPath("root") == subRoot

        and: "subquery provider inherits outer paths"
        subProvider.getFullyQualifiedPath("root") != outerRoot // subquery root shadows outer root
    }
}

@Entity
class JpaFromProviderSpecPerson implements GormEntity<JpaFromProviderSpecPerson> {
    Long id
    String firstName
}

@Entity
class JpaFromProviderSpecPet implements GormEntity<JpaFromProviderSpecPet> {
    Long id
    JpaFromProviderSpecPerson owner
}

@Entity
class JpaFromProviderSpecFace implements GormEntity<JpaFromProviderSpecFace> {
    Long id
}
