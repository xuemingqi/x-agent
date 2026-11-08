# Third-party notices

X Agent's original code is licensed under the [MIT License](LICENSE). The following adapted files retain their upstream Apache-2.0 license. Dependencies and software installed into the optional sandbox retain their own licenses.

## Knife4j

- Upstream: [Knife4j v4.5.0](https://github.com/xiaoymin/knife4j/tree/v4.5.0)
- File: `x-services/x-gateway/src/main/java/com/github/xiaoymin/knife4j/spring/gateway/utils/PathUtils.java`
- Copyright © 2017-2023 Knife4j (xiaoymin@foxmail.com)
- Original author: milo.xiaomeng@gmail.com
- License: [Apache License 2.0](licenses/Apache-2.0.txt)

The class is adapted from Knife4j's gateway utility as a Spring Framework 7 compatibility override. The adapted copy retains its original package and class name so the gateway can load it in place of the dependency's implementation. String checks, constants and logger initialization have been updated.

## Quartz

- Upstream: [Quartz v2.5.2 MySQL InnoDB schema](https://github.com/quartz-scheduler/quartz/blob/v2.5.2/quartz/src/main/resources/org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql)
- Author attribution in the upstream schema: Ron Cordell (roncordell)
- Files: `x-services/x-ai/src/main/resources/db/quartz-schema.sql` and its generated section in `config/sql/x-agent-init.sql`
- License: [Apache License 2.0](licenses/Apache-2.0.txt)

Table and index prefixes are changed from `QRTZ_` to `AGENT_QRTZ_`, destructive drop statements are omitted, and formatting is adjusted for the project's fresh-database initialization bundle.
