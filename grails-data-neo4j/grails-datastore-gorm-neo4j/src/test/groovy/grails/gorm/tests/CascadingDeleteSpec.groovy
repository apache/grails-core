package grails.gorm.tests

import grails.gorm.DetachedCriteria
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CascadingDeleteSpec extends GormDatastoreSpec {

    private static Logger log = LoggerFactory.getLogger(CascadingDeleteSpec.class);

    @Override
    List getDomainClasses() {
        [Pet, PetType, Club, Team]
    }

    def "should belongsTo trigger cascading delete on OneToOne"() {
        when:
            def pet = new Pet(name: 'Cosima', type: new PetType(name: 'Cat')).save()
            pet.save(flush:true)
            session.clear()

        then:
            Pet.count() == 1
            PetType.count() == 1

        when:
            pet.discard()
            pet = Pet.findByName('Cosima')

        then:
            pet.type != null
            pet.type.name == "Cat"

        when:
            pet.delete()

        then:
            Pet.count() == 0
            PetType.count() == 0
    }

    def "should belongsTo trigger cascading delete on OneToMany"() {
        when:
            def club = new Club(name: 'FC Bayern Muenchen')
            club.addToTeams(new Team(name: 'FCB Team 1'))
            club.addToTeams(new Team(name: 'FCB Team 2'))
            def otherClub = new Club(name: 'Borussia Dortmund')
            club.save(validate:false)
            otherClub.addToTeams(new Team(name: 'BVB 1'))
            otherClub.addToTeams(new Team(name: 'BVB 2'))
            otherClub.save(flush:true,validate:false)
            session.clear()

        then:
            Club.count() == 2
            Team.count() == 4

        when:
            club = Club.findByName('FC Bayern Muenchen')
            club.delete()

        then:
            Club.count() == 1
            Team.count() == 2
    }

    def "should deleteAll honor cascade delete on OneToMany"() {
        when:
            def club = new Club(name: 'FC Bayern Muenchen')
            club.addToTeams(new Team(name: 'FCB Team 1'))
            club.addToTeams(new Team(name: 'FCB Team 2'))
            def otherClub = new Club(name: 'Borussia Dortmund')
            club.save(validate:false)
            otherClub.addToTeams(new Team(name: 'BVB 1'))
            otherClub.addToTeams(new Team(name: 'BVB 2'))
            otherClub.save(flush:true,validate:false)
            session.clear()

        then:
            def criteria = new DetachedCriteria(Club).build {}
            criteria.deleteAll()

        then:
            Club.count() == 0
            Team.count() == 0
    }
}







