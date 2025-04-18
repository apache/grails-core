package grails.test.app

import grails.compiler.GrailsCompileStatic
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.fetcher.impl.ClosureDataFetchingEnvironment

class ArguedField {

    String name

    static constraints = {
    }

    static graphql = GraphQLMapping.build {
        add('withArgument', String) {
            input false
            argument('ping', String)
            dataFetcher { ArguedField af, ClosureDataFetchingEnvironment env ->
                env.getArgument('ping')
            }
        }
        add('withArgumentList', String) {
            input false
            argument('pings', [String])
            dataFetcher { ArguedField af, ClosureDataFetchingEnvironment env ->
                List<String> pings = (List<String>)env.getArgument('pings')
                "${pings.join('-')}"
            }
        }
        add('withCustomArgument', String) {
            input false
            argument('ping', 'PingPong') {
                accepts {
                    field('payload', String)
                }
            }
            dataFetcher { ArguedField af, ClosureDataFetchingEnvironment env ->
                Map<String, String> ping = (Map<String, String>)env.getArgument('ping')
                ping["payload"]
            }
        }
    }
}
