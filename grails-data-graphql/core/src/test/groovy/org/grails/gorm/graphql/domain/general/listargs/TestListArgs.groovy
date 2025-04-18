package org.grails.gorm.graphql.domain.general.listargs

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.fetcher.impl.EntityDataFetcher

@Entity
class TestListArgs {

    String name

    static graphql = GraphQLMapping.lazy {
        query("fooBar", [TestListArgs]) {
            defaultListArguments()
            dataFetcher(new EntityDataFetcher(TestListArgs.gormPersistentEntity))
        }
    }
}
