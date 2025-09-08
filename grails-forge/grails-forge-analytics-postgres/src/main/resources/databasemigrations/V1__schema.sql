DROP TABLE IF EXISTS feature;
DROP TABLE IF EXISTS application;

create table feature
(
    id bigint generated always as identity,
    application_id bigint not null,
    name varchar(255) not null
);

create table application
(
    id bigint generated always as identity,
    type varchar(255) not null,
    gorm varchar(255) not null,
    build_tool varchar(255) not null,
    test_framework varchar(255) not null,
    jdk_version varchar(255) not null,
    grails_version varchar(255) not null,
    date_created timestamp not null
);
