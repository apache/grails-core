package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 10/01/2017.
 */
class MultipleDataSourceSpec extends Specification {

    @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore([ConnectionSource.DEFAULT, "one"], Player)

    void "test multiple datasource support with in-memory GORM"() {
        given:
        new Player(name: "Giggs").save(flush:true)
        new Player(name: "Keane").save(flush:true)
        PlayerService service = new PlayerService()
        IPlayerService dataService = datastore.getService(IPlayerService)
        dataService.savePlayer("Neville")
        expect:
        Player.count() == 2
        new DetachedCriteria<>(Player).count() == 2
        new DetachedCriteria<>(Player).withConnection("one").count() == 1
        Player.one.count() == 1
        service.countPlayers() == 2
        service.countPlayersOne() == 1
        dataService.countPlayers() == 1
    }

    void "test delete on data service"() {

        given:
        PlayerService service = new PlayerService()
        IPlayerService dataService = datastore.getService(IPlayerService)


        when:
        dataService.savePlayer("Neville")

        then:
        Player.count() == 0
        dataService.countPlayers() == 1

        when:
        dataService.deletePlayer("Neville")

        then:
        dataService.countPlayers() == 0
    }
}

@Entity
class Player {
    String name

    static mapping = {
        datasources ConnectionSource.DEFAULT, "one"
    }
}

@Transactional
class PlayerService {

    @Transactional("one")
    Number countPlayersOne() {
        // check the right datastore transaction is being used
        assert transactionStatus.transaction.sessionHolder.sessions.first().datastore.backingMap[Player.name].size() == 1
        Player.one.count()
    }


    Number countPlayers() {
        assert !transactionStatus.transaction.sessionHolder.sessions.first().datastore.backingMap[Player.name].isEmpty()
        Player.count()
    }
}

@Service(Player)
@Transactional('one')
interface IPlayerService {

    Number countPlayers()

    Player savePlayer(String name)

    void deletePlayer(String name)
}