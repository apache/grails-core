package org.grails.gorm.graphql.domain.general.custom.operation

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

@Entity
class ComplexOperation {

    static graphql = GraphQLMapping.build {

        query('awesomeQuery', 'AwesomeType') {
            argument('firstArg', 'FirstArgument') {
                accepts {
                    field('subArg', String)
                    field('subArg2', 'SubArgument2') {
                        field('threeDeep', Long) {
                            defaultValue 4
                            description 'Three deep'
                        }
                        collection true
                    }
                }
                nullable true
            }

            returns {
                field('awesome', Boolean)
            }

            dataFetcher {
                [awesome: true]
            }
        }
    }
}
