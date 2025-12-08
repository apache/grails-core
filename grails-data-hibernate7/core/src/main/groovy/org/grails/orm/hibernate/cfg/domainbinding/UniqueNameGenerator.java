package org.grails.orm.hibernate.cfg.domainbinding;

import io.micrometer.common.util.StringUtils;
import jakarta.validation.constraints.NotNull;
import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.UniqueKey;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class UniqueNameGenerator {

    public void setGeneratedUniqueName(@NotNull  UniqueKey uk) {
        if(uk.getTable() == null) {
            throw new MappingException(String.format("Unique Key %s does not have a table associated with it", uk.getName()));
        }

        try {
            var fields = new ArrayList<>(List.of(uk.getTable().getName()));
            uk.getColumns()
                    .stream()
                    .map(Column::getName)
                    .filter(StringUtils::isNotBlank)
                    .forEach(fields::add);
            var ukString = String.join("_", fields);
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(ukString.getBytes(StandardCharsets.UTF_8));
            String name = "UK" + new BigInteger(1, md.digest()).toString(16);
            if (name.length() > 30) {
                name = name.substring(0, 30);
            }
            uk.setName(name);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }
}
