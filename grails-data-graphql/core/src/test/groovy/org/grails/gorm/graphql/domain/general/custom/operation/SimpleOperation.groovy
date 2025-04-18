package org.grails.gorm.graphql.domain.general.custom.operation

import grails.gorm.annotation.Entity
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.domain.general.custom.OtherDomain
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

@Entity
class SimpleOperation {

    static graphql = GraphQLMapping.build {

        query('getData', [OtherDomain]) {
            dataFetcher new DataFetcher() {
                @Override
                Object get(DataFetchingEnvironment environment) {
                    null
                }
            }
        }

        query('getMoreData', pagedResult(OtherDomain)) {
            dataFetcher new DataFetcher() {
                @Override
                Object get(DataFetchingEnvironment environment) {
                    null
                }
            }
        }
    }
}
