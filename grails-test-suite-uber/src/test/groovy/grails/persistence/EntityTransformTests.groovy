package grails.persistence

import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class EntityTransformTests extends Specification {

    // test for http://jira.codehaus.org/browse/GRAILS-5238
    void testGRAILS_5238() {

        given:
        def p = new GroovyShell().evaluate('''
            import grails.persistence.*
            
            @Entity
            class Permission {
            
                String permission
            
                static belongsTo = [ user: User ]
            
                void setOwner(User owner) {
                    this.user = owner
                }
                User getOwner() {
                    return user
                }
            }
            
            @Entity
            class User {
                String username
            }
            
            u = new User(username: 'bob')
            p = new Permission(user: u, permission: 'uber')

            return p
        ''')

        expect:
        'User' == p['user'].class.name
        'User' == p.class.methods.find { it.name == 'getUser' }.returnType.name
    }

    void testDefaultConstructorBehaviourNotOverridden() {
        given:
        def entity = new GroovyShell().evaluate('''
            import grails.persistence.*
            
            @Entity
            class EntityTransformTest {
    
                  boolean enabled
                  int cash
                  
                  EntityTransformTest() {
                      enabled = true
                      cash = 30
                  }
            }
            
            return new EntityTransformTest()
        ''')

        expect:
        entity != null
        entity['enabled']
        entity['cash'] == 30
    }

    void testConstructorBehaviourNotOverridden() {
        given:
        def entity = new GroovyShell().evaluate('''
            import grails.persistence.*
            
            @Entity
            class EntityTransformTest2 {

                boolean enabled
                int cash
                
                EntityTransformTest2() {
                    enabled = true
                    cash = 30
                }
            }
            return new EntityTransformTest2(cash: 42)
        ''')

        expect:
        entity != null
        entity['enabled']
        entity['cash'] == 42
    }

    void testAnnotatedEntity() {
        given:
        def entity = new GroovyShell().evaluate('''
            import grails.persistence.*
          
            @Entity
            class EntityTransformTest3 {

               static belongsTo = [one: EntityTransformTest3]
               static hasMany = [many: EntityTransformTest3]

               static constraints = {
                   id bindable: true
               }
            }
            return new EntityTransformTest3()
        ''')

        expect:
        entity['id'] == null
        entity['version'] == null

        when:
        entity['many'] = new HashSet()

        then:
        0 == (entity['many'] as HashSet).size()

        when:
        entity['one'] = entity.class.getDeclaredConstructor().newInstance()

        then:
        entity['one'] != null
    }

    void testToStringOverrideTests() {
        given:
        def entities = new GroovyShell().evaluate('''

            import grails.persistence.*
    
            @Entity
            class Personnel {
                String lastName
                String firstName
                String toString() {"${firstName}, ${lastName}"}
            }
    
            @Entity
            class Approver extends Personnel {}
            
            return [new Approver(firstName: 'joe', lastName: 'bloggs'), new Personnel(firstName: 'jack', lastName: 'dee') ]
        ''') as List

        expect:
        'joe, bloggs' == entities[0].toString()
        'jack, dee' == entities[1].toString()
    }
}
