package grails.test.app

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.hibernate.SessionFactory
import spock.lang.Specification

@Integration
@Rollback
class ArtistIntegrationSpec extends Specification implements GraphQLSpec {

    SessionFactory sessionFactory

    void "test listing artists and paintings"() {
        given:
        def a = new Artist(name: "Picasso").save(flush: true, failOnError: true)
        sessionFactory.currentSession.flush()
        sessionFactory.currentSession.transaction.commit()

        when:
        def resp = graphQL.graphql("""
            {
              artistList {
                id
                name
                paintings {
                  name
                  heightCm
                  widthCm
                }
              }
            }
        """)
        def json = resp.body()
        println json.toString()
        def artists = json.data.artistList
        def artist = artists[0]

        then:
        artists.size() == 1
        artist.id == a.id
        artist.name == "Picasso"
        artist.paintings.size() == 1
        artist.paintings[0].name == "test"
        artist.paintings[0].heightCm == 60
        artist.paintings[0].widthCm == 120
    }

}
