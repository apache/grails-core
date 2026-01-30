package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.spi.InFlightMetadataCollector;

import jakarta.annotation.Nonnull;

import java.util.Optional;
import java.util.function.Function;

public class NamespaceNameExtractor {


    public String getCatalogName(@Nonnull InFlightMetadataCollector mappings) {
        return getNamespaceName(mappings, Namespace.Name::getCatalog);
    }

    public String getSchemaName(@Nonnull InFlightMetadataCollector mappings) {
        return getNamespaceName(mappings, Namespace.Name::getSchema);
    }




    public String  getNamespaceName(
            @Nonnull InFlightMetadataCollector mappings, Function<Namespace.Name, Identifier> function
    ) {
        return Optional.ofNullable(mappings.getDatabase())
                .map(Database::getDefaultNamespace)
                .map(Namespace::getName)
                .map(function)
                .map(Identifier::getCanonicalName)
                .orElse(null)
                ;
    }
}
