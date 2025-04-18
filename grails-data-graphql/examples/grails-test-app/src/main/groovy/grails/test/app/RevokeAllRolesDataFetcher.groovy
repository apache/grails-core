package grails.test.app

import grails.compiler.GrailsCompileStatic
import grails.gorm.transactions.TransactionService
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment

@GrailsCompileStatic
class RevokeAllRolesDataFetcher implements DataFetcher {

    @Override
    Object get(DataFetchingEnvironment environment) {
        UserRole.withTransaction {
            User user = User.load((Serializable)environment.getArgument('user'))
            int count = UserRole.where {
                user == user
            }.deleteAll().intValue()

            [success: count > 0]
        }
    }
}
