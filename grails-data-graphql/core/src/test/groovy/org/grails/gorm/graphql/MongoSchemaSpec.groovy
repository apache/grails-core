package org.grails.gorm.graphql

import com.github.fakemongo.Fongo
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import org.bson.types.ObjectId
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.gorm.graphql.domain.general.GeneralPackage
import org.grails.gorm.graphql.testing.GraphQLSchemaSpec
import org.grails.gorm.graphql.types.GraphQLTypeManager
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Ignored due to https://github.com/fakemongo/fongo/issues/367.
 * Fongo not compatible with the mongo driver being used
 */
@Ignore
class MongoSchemaSpec extends Specification implements GraphQLSchemaSpec {

    @Shared
    @AutoCleanup
    MongoDatastore mongoDatastore
    @Shared
    GraphQLSchema schema
    @Shared
    GraphQLObjectType queryType
    @Shared
    GraphQLObjectType mutationType

    void setupSpec() {
        mongoDatastore = new MongoDatastore(new Fongo(getClass().name).mongo,
                DatastoreUtils.createPropertyResolver(Collections.emptyMap()),
                GeneralPackage.getPackage())

        def gormSchema = new Schema(mongoDatastore.mappingContext)
        gormSchema.initialize()
        GraphQLTypeManager typeManager = gormSchema.typeManager
        // tag::registerObjectId[]
        typeManager.registerType(ObjectId, GraphQLScalarType.newScalar()
                .name("ObjectId").description("Hex representation of a Mongo object id").coercing(new Coercing<ObjectId, ObjectId>() {

            protected Optional<ObjectId> convert(Object input) {
                if (input instanceof ObjectId) {
                    Optional.of((ObjectId) input)
                } else if (input instanceof String) {
                    parseObjectId((String) input)
                } else {
                    Optional.empty()
                }
            }

            @Override
            ObjectId serialize(Object input) {
                convert(input).orElseThrow({
                    throw new CoercingSerializeException("Could not convert ${input.class.name} to an ObjectId")
                })
            }

            @Override
            ObjectId parseValue(Object input) {
                convert(input).orElseThrow({
                    throw new CoercingParseValueException("Could not convert ${input.class.name} to an ObjectId")
                })
            }

            @Override
            ObjectId parseLiteral(Object input) {
                if (input instanceof StringValue) {
                    parseObjectId(((StringValue) input).value).orElse(null)
                } else {
                    null
                }
            }

            protected Optional<ObjectId> parseObjectId(String input) {
                if (ObjectId.isValid(input)) {
                    Optional.of(new ObjectId(input))
                } else {
                    Optional.empty()
                }
            }

        }).build())
        // end::registerObjectId[]
        schema = gormSchema.generate()
        queryType = schema.queryType
        mutationType = schema.mutationType
    }

    void "test it doesn't throw any exceptions"() {
        expect:
        1 == 1
    }
}
