package grails.gorm.tests

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.transaction.TransactionStatus
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 16/01/2017.
 */
class CurrentTenantTransformSpec  extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DATABASE,
                    (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
            ),
            [ConnectionSource.DEFAULT, 'one','two'],
            Team,
            Player
    )

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void "Test parsing of @WithoutTenant"() {
        Class testServiceClass = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional

@Transactional
@WithoutTenant
class TestService {

    Integer count() {
        return 10;
    }
   
}

return TestService
''')
        PlayService playService = new PlayService()

        expect:
        testServiceClass.getDeclaredMethod('$mt__count')
        playService.countPlays() == 10
    }

    void "Test parsing of @CurrentTenant"() {
        given:
        Class testServiceClass = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenant
import grails.gorm.transactions.Transactional

@CurrentTenant
class TestService {
    @Tenant({ "two" }) 
    List<String> listTwoTeams() {
        return ["Manchester United"]
    }   
    
    List<String> listTeams() {
        return ["Arsenal"]
    }

    @Transactional
    void addTeam(String name) {
        println "good"
    }
    

}

return TestService
''')
        TeamService teamService = new TeamService()
        teamService.setTargetDatastore(Mock(Datastore))

        expect:
        testServiceClass.getDeclaredMethod('$tt__addTeam', String, TransactionStatus)
        testServiceClass.getDeclaredMethod('$mt__addTeam', String, Serializable)
        testServiceClass.getDeclaredMethod('$mt__listTwoTeams',  Serializable)

    }

    void "Test @CurrentTenant with service"() {
        given:"A service with @CurrentTenant at the class level is used"
        TeamService teamService = new TeamService()

        when:"a method is invoked"
        def results = teamService.listTeams()

        then:"An exception is thrown because no tenant is present"
        thrown(TenantNotFoundException)

        when:"An @Transactional method is invoked"
        teamService.addTeam("test")

        then:"An exception is thrown because no tenant is present"
        thrown(TenantNotFoundException)

        when:"An @Transactional method is invoked which has no GORM logic in it"
        teamService.addTeamCheckTransaction("test")

        then:"An exception is thrown because no tenant is present"
        thrown(TenantNotFoundException)

        when:"A method that uses @Tenant is used"
        results = teamService.allTwoTeams()

        then:"A tenant id was resolved for that method"
        results.isEmpty()

        when:"A tenant is set"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "two")
        results = teamService.listTeams()

        then:"The result is correct"
        results.size() == 0

        when:"A @Transactional method is invoked"
        teamService.addTeam("test")
        teamService.addTeamCheckTransaction("test")
        results = teamService.listTeams()

        then:"The results are correct"
        results.size() == 1

        when:"we try with no tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        teamService.addTeam("test 2")

        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"A method that uses @Tenant is used"
        results = teamService.allTwoTeams()

        then:"A tenant id was resolved for that method"
        results.size() == 1

        when:"A method that used @WithoutTenant is used"
        int playerCount = teamService.countPlayers()

        then:"It executed corretly without throwing an exception"
        playerCount == 0
    }
}

@WithoutTenant
class PlayService {
    int countPlays() {
        return 10;
    }

}

@CurrentTenant
class TeamService {

    @WithoutTenant
    int countPlayers() {
        Player.count()
    }

    @Tenant({"two"})
    List<Team> allTwoTeams() {
        Team.list()
    }

    List<Team> listTeams() {
        Team.list(max:10)
    }

    @Transactional
    void addTeam(String name) {
        new Team(name:name).save(flush:true)
    }

    @Transactional
    void addTeamCheckTransaction(String name) {
        assert transactionStatus.transaction.sessionHolder.sessions.first().datastore.connectionSources.defaultConnectionSource.name == Tenants.currentId()
    }
}
