package grails.test.app

import grails.compiler.GrailsCompileStatic
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.fetcher.impl.EntityDataFetcher

@CompileStatic
class UsersByRoleDataFetcher extends EntityDataFetcher<List<User>> {

    /**
     * Here we pass the {@link org.grails.datastore.mapping.model.PersistentEntity}
     * of the domain being QUERIED
     *
     * If the query was being done on UserRole and the users were being returned
     * through a projection, the alternate constructor (UserRole.gormPersistentEntity, 'user')
     * should be used instead.
     */
    UsersByRoleDataFetcher() {
        super(User.gormPersistentEntity)
    }

    /**
     * No need to add transactional here since the parent class has it defined
     * in a method that encompasses this one
     */
    @GrailsCompileStatic
    @Override
    protected List executeQuery(DataFetchingEnvironment environment, Map queryArgs) {
        Role role = Role.load((Serializable) environment.getArgument('role'))
        def users = UserRole.where { role == role }.property('user')
        User.where {
            id in users
        }.list(queryArgs)
    }
}
