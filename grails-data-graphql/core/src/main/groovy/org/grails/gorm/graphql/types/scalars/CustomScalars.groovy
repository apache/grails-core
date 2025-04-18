package org.grails.gorm.graphql.types.scalars

import graphql.schema.GraphQLScalarType
import org.grails.gorm.graphql.types.scalars.coercing.*

/**
 * Custom scalars
 */
class CustomScalars {

    public static final GraphQLScalarType GraphQLByteArray = GraphQLScalarType.newScalar()
            .name('ByteArray').description('Built-in ByteArray').coercing(new ByteArrayCoercion()).build()
    public static final GraphQLScalarType GraphQLCharacterArray = GraphQLScalarType.newScalar()
            .name('CharacterArray').description('Built-in CharacterArray').coercing(new CharacterArrayCoercion()).build()
    public static final GraphQLScalarType GraphQLCurrency = GraphQLScalarType.newScalar()
            .name('Currency').description('Accepts a string currency code').coercing(new CurrencyCoercion()).build()
    public static final GraphQLScalarType GraphQLSqlDate = GraphQLScalarType.newScalar()
            .name('SqlDate').description('Accepts a number or a string in the format "yyyy-[m]m-[d]d"').coercing(new SqlDateCoercion()).build()
    public static final GraphQLScalarType GraphQLTime = GraphQLScalarType.newScalar()
            .name('Time').description('Accepts a number or string in the format "hh:mm:ss"').coercing(new TimeCoercion()).build()
    public static final GraphQLScalarType GraphQLTimestamp = GraphQLScalarType.newScalar()
            .name('Timestamp').description('Accepts a numer or a string in the format "yyyy-[m]m-[d]d hh:mm:ss[.f...]"').coercing(new TimestampCoercion()).build()
    public static final GraphQLScalarType GraphQLTimeZone = GraphQLScalarType.newScalar()
            .name('TimeZone').description('Accepts a string time zone id').coercing(new TimeZoneCoercion()).build()
    public static final GraphQLScalarType GraphQLURI = GraphQLScalarType.newScalar()
            .name('URI').description('Accepts a string in the form of a URI').coercing(new URICoercion()).build()
    public static final GraphQLScalarType GraphQLURL = GraphQLScalarType.newScalar()
            .name('URL').description('Accepts a string in the form of a URL').coercing(new URLCoercion()).build()
    public static final GraphQLScalarType GraphQLUUID = GraphQLScalarType.newScalar()
            .name('UUID').description('Accepts a string to be converted to a UUID').coercing(new UUIDCoercion()).build()

}
