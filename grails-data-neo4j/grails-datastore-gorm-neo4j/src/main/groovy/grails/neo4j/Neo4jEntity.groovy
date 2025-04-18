/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.neo4j

import grails.gorm.MultiTenant
import grails.gorm.api.GormAllOperations
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.neo4j.GraphPersistentEntity
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.gorm.neo4j.api.Neo4jGormStaticApi
import org.grails.datastore.gorm.neo4j.engine.DynamicAssociationSupport
import org.grails.datastore.gorm.schemaless.DynamicAttributes
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.neo4j.driver.Result
import org.neo4j.driver.QueryRunner

/**
 * Extends the default {@org.grails.datastore.gorm.GormEntity} trait, adding new methods specific to Neo4j
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
trait Neo4jEntity<D> implements GormEntity<D>, DynamicAttributes {

    /**
     * Allows accessing to dynamic properties with the dot operator
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    def propertyMissing(String name) {
        return getAt(name)
    }

    /**
     * Allows setting a dynamic property via the dot operator
     * @param instance The instance
     * @param name The property name
     * @param val The value
     */
    def propertyMissing(String name, val) {
        DynamicAttributes.super.putAt(name, val)
    }


    /**
     * Obtains a dynamic attribute
     *
     * @param name The name of the attribute
     * @return The value of the attribute
     */
    @Override
    def getAt(String name) {
        def val = DynamicAttributes.super.getAt(name)
        if(val == null) {
            GormStaticApi staticApi = GormEnhancer.findStaticApi(getClass())
            GraphPersistentEntity entity = (GraphPersistentEntity) staticApi.gormPersistentEntity
            if(entity.hasDynamicAssociations()) {
                def id = ident()
                if(id != null) {
                    staticApi.withSession { Neo4jSession session ->
                        DynamicAssociationSupport.loadDynamicAssociations(session, entity, this, id)
                    }
                    return DynamicAttributes.super.getAt(name)
                }
            }
        }
        return val
    }

    /**
     * Perform a cypher query. The id if this entity will be included within a parameter called "this" passed to the query execution
     *
     * @param cypher The cypher query
     * @param params The parameters
     * @return The statement result
     */
    Result cypher(CharSequence cypher, Map params) {
        GormEnhancer.findDatastore(getClass()).withSession { Neo4jSession session ->
            QueryRunner boltSession = getStatementRunner(session)

            String queryString
            if(cypher instanceof GString) {
                queryString = Neo4jGormStaticApi.buildNamedParameterQueryFromGString((GString) cypher, params)
            }
            else {
                queryString = cypher.toString()
            }
            params['this'] = session.getObjectIdentifier(this)
            includeTenantIdIfNecessary(session, queryString, params)
            boltSession.run(queryString, (Map<String, Object>) params)
        }
    }

    /**
     * Perform a cypher query. The id if this entity will be included within a parameter called "this" passed to the query execution
     *
     * @param cypher The cypher query
     * @param params The parameters
     * @return The statement result
     */
    Result cypher(String cypher, List params) {
        GormEnhancer.findDatastore(getClass()).withSession { Neo4jSession session ->
            QueryRunner boltSession = getStatementRunner(session)

            Map<String, Object> paramsMap = new LinkedHashMap()
            paramsMap.put("this", session.getObjectIdentifier(this))

            includeTenantIdIfNecessary(session, cypher, paramsMap)

            int i = 0
            for (p in params) {
                paramsMap.put(String.valueOf(++i), p)
            }
            boltSession.run(cypher, paramsMap)
        }
    }


    /**
     * perform a cypher query
     * @param queryString
     * @return
     */
    Result cypher(String queryString) {
        GormEnhancer.findDatastore(getClass()).withSession { Neo4jSession session ->
            Map<String, Object> arguments
            if (session.getDatastore().multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
                if (!queryString.contains("\$tenantId")) {
                    throw new TenantNotFoundException("Query does not specify a tenant id, but multi tenant mode is DISCRIMINATOR!")
                } else {
                    arguments = new LinkedHashMap<String, Object>()
                    arguments.put(GormProperties.TENANT_IDENTITY, Tenants.currentId(Neo4jDatastore))
                    arguments.put("this", session.getObjectIdentifier(this))
                }
            } else {
                arguments = (Map<String, Object>) Collections.singletonMap("this", session.getObjectIdentifier(this))
            }
            QueryRunner boltSession = getStatementRunner(session)
            boltSession.run(queryString, arguments)
        }
    }

    /**
     * perform a cypher query
     *
     * @param queryString
     * @return
     * @deprecated Use {@link #executeCypher(java.lang.CharSequence, java.util.Map)} intead
     */
    @Deprecated
    static Result cypherStatic(CharSequence queryString, Map params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).cypherStatic(queryString, params)
    }

    /**
     * perform a cypher query
     *
     * @param queryString
     * @return
     * @deprecated Use {@link #executeCypher(java.lang.CharSequence, java.util.Map)} intead
     */
    @Deprecated
    static Result cypherStatic(CharSequence queryString, List params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).cypherStatic(queryString, params)
    }

    /**
     * perform a cypher query
     *
     * @param queryString
     * @return
     * @deprecated Use {@link #executeCypher(java.lang.CharSequence)} intead
     */
    @Deprecated
    static Result cypherStatic(CharSequence queryString) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).cypherStatic(queryString)
    }

    /**
     * perform a cypher query
     *
     * @param queryString
     * @return The statement result
     */
    static Result executeCypher(CharSequence queryString, Map params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).cypherStatic(queryString, params)
    }

    /**
     * perform a cypher query
     *
     * @param queryString
     * @return The statement result
     */
    static Result executeCypher(CharSequence queryString) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).cypherStatic(queryString)
    }
    /**
     * Varargs version of {@link #findAll(java.lang.String, java.util.Collection, java.util.Map)}
     */
    static List<D> findAll(CharSequence query, Object[] params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findAll(query, Arrays.asList(params))
    }

    /**
     * Varargs version of {@link #findAll(java.lang.String, java.util.Collection, java.util.Map)}
     */
    static List<D> findAll(CharSequence query, Map params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findAll(query, params)
    }
    /**
     * Varargs version of {@link #findAll(java.lang.String, java.util.Collection, java.util.Map)}
     */
    static D find(CharSequence query, Object[] params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).find(query, Arrays.asList(params))
    }

    /**
     * Varargs version of {@link #findAll(java.lang.String, java.util.Collection, java.util.Map)}
     */
    static D find(CharSequence query, Map params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).find(query, params)
    }
    /**
     * Perform an operation with the given connection
     *
     * @param connectionName The name of the connection
     * @param callable The operation
     * @return The return value of the closure
     */
    static <T> T withConnection(String connectionName, @DelegatesTo(GormAllOperations) Closure callable) {
        def staticApi = GormEnhancer.findStaticApi(this, connectionName)
        return (T) staticApi.withNewSession {
            callable.setDelegate(staticApi)
            return callable.call()
        }
    }

    private QueryRunner getStatementRunner(Neo4jSession session) {
        return session.hasTransaction() ? session.getTransaction().getNativeTransaction() : session.getNativeInterface()
    }

    private void includeTenantIdIfNecessary(Neo4jSession session, String queryString, Map<String, Object> paramsMap) {
        if ((this instanceof MultiTenant) && session.getDatastore().multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            if (!queryString.contains("\$tenantId")) {
                throw new TenantNotFoundException("Query does not specify a tenant id, but multi tenant mode is DISCRIMINATOR!")
            } else {
                paramsMap.put(GormProperties.TENANT_IDENTITY, Tenants.currentId(Neo4jDatastore))
            }
        }
    }
}