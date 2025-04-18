package org.grails.gorm.graphql.domain.general.custom.property

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.domain.general.custom.OtherDomain
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.types.GraphQLPropertyType

@Entity
class CustomType  {

    String name

    Map getNoFetcher() {
        [value: 'calls the getter', result: true, domain: new OtherDomain(name: 'from getter')]
    }

    static graphql = GraphQLMapping.build {

        add('noFetcher', 'NoFetcher') {
            type {
                field('value', String) {
                    nullable false
                    description 'fadf'
                }
                field('result', Boolean)
                field('domain', OtherDomain)
                collection true
            }
        }
        add('customFetcher', 'CustomFetcher') {
            type {
                field('comment', 'CustomFetcherComment') {
                    field('replies', 'CustomFetcherReply') {
                        field('text', String)
                        collection true
                        description 'The replies to the comment'
                    }
                    description 'A comment'
                }
                field('commentCount', Long)
            }
            dataFetcher { SimpleType c ->
                [comment: [replies: [[text: "reply 1"], [text: "reply 2"]]], commentCount: 1]
            }
        }
        add('notNull', 'NotNull') {
            type {
                field('null', Boolean)
            }
            nullable false
        }
        add('onlyInput', 'OnlyInput') {
            type {
                field('output', Boolean)
            }
            output false
        }
        add('onlyOutput', 'OnlyOutput') {
            type {
                field('input', Boolean)
            }
            input false
        }
        add('deprecatedNoReason', 'DepNoReason') {
            type {
                field('deprecated', Boolean)
            }
            deprecated true
        }
        add('deprecatedWithReason', 'DepReason') {
            type {
                field('deprecated', Boolean)
            }
            deprecationReason 'This is a reason'
        }
        add('described', 'Described') {
            type {
                field('description', String)
            }
            description 'This is a description'
        }
        add('withArgument', 'WithArg') {
            argument('arg', String)
            type {
                field('response', String)
            }
        }
        add('withArgumentList', 'WithArgList') {
            argument('arg', [String])
            type {
                field('response', String)
            }
        }
        add('withCustomArgument', 'WithCustomArg') {
            argument('arg', 'CustomArg') {
                accepts {
                    field('field', String)
                }
            }
            type {
                field('response', String)
            }
        }
    }

}