package grails.gorm.tests.mappedby

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by graemerocher on 29/05/2017.
 */
class MultipleOneToOneSpec extends GormSpec {


    @Issue('https://github.com/grails/grails-data-mapping/issues/950')
    void "test mappedBy with multiple many-to-one and a single one-to-one"() {
        given:
        Org branch = new Org(id:1, name: "branch a").save()
        new OrgMember(org:branch).save(flush:true)
        def query = OrgMember.where({branch == null})

        expect:
        query.updateAll(branch: branch) == 1
        OrgMember.findByBranch(branch)
    }

    @Override
    List getDomainClasses() {
        [Org, OrgMember]
    }
}


@Entity
class Org {

    String name

    OrgMember member

    static mappedBy = [member: "org"]

    static constraints = {
        member nullable: true
    }

    static mapping = {
        id generator: "assigned"
    }

}

@Entity
class OrgMember {
    static belongsTo = [org:Org]

    Org branch
    Org division
    Org region

    static mappedBy = [branch:"none", division:"none", region:"none"]

    static constraints = {
        org nullable: false
        branch nullable: true
        division nullable: true
        region nullable: true
    }

}