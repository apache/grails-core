/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

/** Prints every no-argument capability the Hibernate Dialect classes report, one TSV row per dialect and capability. */
public class Dump {
    static final String[][] DIALECTS = {
        {"H2", "org.hibernate.dialect.H2Dialect"},
        {"PostgreSQL", "org.hibernate.dialect.PostgreSQLDialect", "org.hibernate.dialect.PostgreSQL10Dialect"},
        {"MySQL", "org.hibernate.dialect.MySQLDialect", "org.hibernate.dialect.MySQL8Dialect"},
        {"MariaDB", "org.hibernate.dialect.MariaDBDialect"},
        {"Oracle", "org.hibernate.dialect.OracleDialect", "org.hibernate.dialect.Oracle12cDialect"},
        {"SQLServer", "org.hibernate.dialect.SQLServerDialect", "org.hibernate.dialect.SQLServer2012Dialect"},
        {"DB2", "org.hibernate.dialect.DB2Dialect"},
        {"HSQL", "org.hibernate.dialect.HSQLDialect"},
        {"Derby", "org.hibernate.dialect.DerbyDialect"},
        {"Sybase", "org.hibernate.dialect.SybaseASEDialect", "org.hibernate.dialect.SybaseDialect"},
        {"CockroachDB", "org.hibernate.dialect.CockroachDialect"},
    };

    public static void main(String[] args) throws Exception {
        String line = args[0];
        for (String[] d : DIALECTS) {
            Object dialect = null;
            String used = null;
            for (int i = 1; i < d.length && dialect == null; i++) {
                try {
                    dialect = Class.forName(d[i]).getDeclaredConstructor().newInstance();
                    used = d[i];
                } catch (Throwable ignored) { }
            }
            if (dialect == null) {
                System.out.println(line + "\t" + d[0] + "\t(class)\tNOT-AVAILABLE");
                continue;
            }
            System.out.println(line + "\t" + d[0] + "\t(class)\t" + used.substring(used.lastIndexOf('.') + 1));
            Map<String, String> out = new TreeMap<>();
            for (Method m : dialect.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                Class<?> r = m.getReturnType();
                if (!(r == boolean.class || r == int.class || r == long.class || r == String.class || r.isEnum())) continue;
                String n = m.getName();
                if (!(n.startsWith("supports") || n.startsWith("get") || n.startsWith("is") || n.startsWith("has") || n.startsWith("use") || n.startsWith("requires"))) continue;
                if (n.equals("getClass") || n.equals("hashCode") || n.startsWith("getDefaultProperties")) continue;
                try {
                    Object v = m.invoke(dialect);
                    out.put(n, String.valueOf(v));
                } catch (Throwable ignored) { }
            }
            try {
                Method b = dialect.getClass().getMethod("toBooleanValueString", boolean.class);
                out.put("toBooleanValueString(true)", String.valueOf(b.invoke(dialect, true)));
            } catch (Throwable ignored) { }
            out.forEach((k, v) -> System.out.println(line + "\t" + d[0] + "\t" + k + "\t" + v));
        }
    }
}
