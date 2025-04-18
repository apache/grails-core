package org.grails.gorm.graphql.domain.general.custom.property

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

@Entity
class SimpleType {

    String name

    String getNoFetcher() {
        'calls the getter'
    }

    static graphql = GraphQLMapping.build {
        add('noFetcher', String)

        add('customFetcher', String) {
            dataFetcher { SimpleType c ->
                'inside custom fetcher'
            }
        }
        add('notNull', String) {
            nullable false
        }
        add('onlyInput', String) {
            output false
        }
        add('onlyOutput', String) {
            input false
        }
        add('deprecatedNoReason', String) {
            deprecated true
        }
        add('deprecatedWithReason', String) {
            deprecationReason 'This is a reason'
        }
        add('described', String) {
            description 'This is a description'
        }

        add('list', [String])

        add('withArgument', String) {
            argument('arg', String)
        }
        add('withArgumentList', String) {
            argument('arg', [String])
        }
        add('withCustomArgument', String) {
            argument('arg', 'SimpleArg') {
                accepts {
                    field('field', String)
                }
            }
        }
    }

    def methodMissing(String name, Object[] args) {
        if (graphql.additional.find { it.name == name }) {
            return name
        } else {
            throw new MissingMethodException(name, SimpleType, args)
        }
    }
}
