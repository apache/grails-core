package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.spockframework.util.StringMessagePrintStream
import spock.lang.Shared
import spock.lang.Specification

@Integration
class TagIntegrationSpec extends Specification implements GraphQLSpec {

    @Shared Long grailsId

    @OnceBefore
    void createPosts() {
        def resp = graphQL.graphql("""
            mutation {
              postCreate(post: {
                title: "Grails 3.3 Release",
                tags: [
                  {name: "Grails"},
                  {name: "Groovy"},
                  {name: "Java"}
                ]
              }) {
                id
                tags {
                  id
                  name
                }
              }
            }
        """)
        List obj = resp.body().data.postCreate.tags
        def grails = obj.find { it.name == 'Grails' }.id
        grailsId = grails
        def groovy = obj.find { it.name == 'Groovy' }.id

        resp = graphQL.graphql("""
            mutation {
              postCreate(post: {
                title: "Grails 3.4 Release",
                tags: [
                  {id: ${grails}},
                  {id: ${groovy}},
                  {name: "Kotlin Ken"}
                ]
              }) {
                id
                tags {
                  id 
                  name
                }
              }
            }
        """)
        assert resp.body().data.postCreate.tags.size() == 3
    }

    void "test getting the count"() {
        when:
        def resp = graphQL.graphql("""
            {
              tagCount
            }
        """)
        def obj = resp.body().data.tagCount

        then:
        obj == 4
    }

    void "test a custom property can reference a domain"() {
        when:
        def resp = graphQL.graphql("""
            {
              tagList(sort: "id") {
                id
                name
                posts {
                  id
                }
              }
            }
        """)
        List obj = resp.body().data.tagList

        then:
        obj.size() == 4
        obj.find { it.name == 'Grails' }.posts.size() == 2
        obj.find { it.name == 'Groovy' }.posts.size() == 2
        obj.find { it.name == 'Java' }.posts.size() == 1
        obj.find { it.name == 'Kotlin Ken' }.posts.size() == 1
    }

    void "test a custom property can reference a domain with using joins"() {
        given:
        PrintStream originalOut = System.out
        List<String> queries = []
        System.setOut(new StringMessagePrintStream() {
            @Override
            protected void printed(String message) {
                queries.add(message)
            }
        })

        when:
        def resp = graphQL.graphql("""
            {
              tag(id: ${grailsId}) {
                id
                name
                posts {
                  id  
                  tags {
                    name
                  }
                }
              }
            }
        """)
        Map obj = resp.body().data.tag

        then:
        //queries.size() == 2 ignored due to GORM issue https://github.com/grails/grails-data-mapping/issues/989
        queries[0] ==~ 'Hibernate: select this_.id as id[0-9]+_[0-9]+_[0-9]+_, this_.version as version[0-9]+_[0-9]+_[0-9]+_, this_.name as name[0-9]+_[0-9]+_[0-9]+_ from tag this_ where this_.id=\\? limit \\?\n'
        queries[1] ==~ 'Hibernate: select this_.id as id[0-9]+_[0-9]+_[0-9]+_, this_.version as version[0-9]+_[0-9]+_[0-9]+_, this_.title as title[0-9]+_[0-9]+_[0-9]+_, this_.date_created as date_cre[0-9]+_[0-9]+_[0-9]+_, this_.last_updated as last_upd[0-9]+_[0-9]+_[0-9]+_, tags3_.post_tags_id as post_tag[0-9]+_[0-9]+_, tags_alias1_.id as tag_id[0-9]+_[0-9]+_, tags_alias1_.id as id[0-9]+_[0-9]+_[0-9]+_, tags_alias1_.version as version[0-9]+_[0-9]+_[0-9]+_, tags_alias1_.name as name[0-9]+_[0-9]+_[0-9]+_ from post this_ inner join post_tag tags3_ on this_.id=tags3_.post_tags_id inner join tag tags_alias1_ on tags3_.tag_id=tags_alias1_.id where tags_alias1_.id=\\?\n'

        cleanup:
        System.setOut(originalOut)
    }

    void "test optimistic locking"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              tagUpdate(id: ${grailsId}, tag: {version: -1, name: "Grails 3"}) {
                id
                name
                errors {
                  field 
                  message
                }  
              }
            }
        """)
        Map obj = resp.body().data.tagUpdate

        then:
        obj.id == grailsId
        obj.name == "Grails" //Updated data not bound
        obj.errors.size() == 1
        obj.errors[0].field == "version"
        obj.errors[0].message == "Another user has updated this Tag while you were editing"
    }

    void cleanupSpec() {
        def resp = graphQL.graphql("""
            { 
              postList {
                id
              }
            }
        """)
        def posts = resp.body().data.postList
        assert posts.size() == 2
        posts.each {
            resp = graphQL.graphql("""
              mutation {
                postDelete(id: ${it.id}) {
                  success
                }
              }
            """)
            assert resp.body().data.postDelete.success
        }
        resp = graphQL.graphql("""
            { 
              tagList {
                id
              }
            }
        """)
        def tags = resp.body().data.tagList
        assert tags.size() == 4
        tags.each {
            resp = graphQL.graphql("""
              mutation {
                tagDelete(id: ${it.id}) {
                  success
                }
              }
            """)
            assert resp.body().data.tagDelete.success
        }
    }
}
