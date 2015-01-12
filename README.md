# Wisdom-JDBC

This project integrates JDBC data sources within Wisdom-Framework. It provides two features:

* The integration of JDBC driver in Wisdom (compatible with OSGi),
* Letting you access to configured data sources.

Basically, for each configured data source, a `javax.sql.DataSource` service is published to
access the data source. This service manages the connections to the data source. The `org.wisdom.database.jdbc
.service.DataSources` service is also exposed to retrieve the list of configured data sources.

## Supported databases

The Wisdom-JDBC project is compatible with any implementation of the `org.osgi.service.jdbc.DataSourceFactory`.
However finding such implementation can be tricky, so we provide implementations for:

 * Apache Derby
 * H2
 * HSQL
 * MySQL
 * PostGreSQL
 * SQLite

To integrate the JDBC driver, just add one of the following dependencies in your `pom.xml` file. You can obviously
pick more than one if you are using several database technologies (update the version to use latest releases):

````
<dependency>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>h2</artifactId>
    <version>1.3.172_1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>derby</artifactId>
    <version>10.11.1.1_1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>hsqldb</artifactId>
    <version>2.3.2_1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.7.2_1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>5.1.11_1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>postgresql</artifactId>
    <version>9.1-901.jdbc4_1-SNAPSHOT</version>
</dependency>
````

## Installing and configuring the data sources

Once you have selected the JDBC driver, add the following dependency to your `pom.xml` file:

````
<dependency>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>wisdom-jdbc-datasources</artifactId>
    <version>${project.version}</version>
</dependency>
````

Then, you need to edit the `src/main/configuration/application.conf` to configure the data sources. The configuration
 can differ depending of the database technology you use. Property name are formed using the following rule: `db
 .datasource_name.key`. For example the driver class for the data source 'default' is set using the `db.default
 .driver` property.

Here are some examples:

```
## Derby - Memory
db.derby.driver="org.apache.derby.jdbc.EmbeddedDriver"
db.derby.url="jdbc:derby:memory:sample;create=true"
db.derby.logStatements=true

## H2 - Memory
db.h2mem.driver="org.h2.Driver"
db.h2mem.url="jdbc:h2:mem:h2-mem-it"
db.h2mem.logStatements=true

## H2 - File
db.h2file.driver="org.h2.Driver"
db.h2file.url="jdbc:h2:./target/db/h2-it.db"
db.h2file.logStatements=true

#
# Note about HSQL : HSQL supports only one catalog per database, so the `defaultCatalog` parameter must not be set.
# http://hsqldb.org/doc/2.0/apidocs/org/hsqldb/jdbc/JDBCConnection.html#setCatalog(java.lang.String)
#

## HSQL - Memory
db.hsqlmem.driver="org.hsqldb.jdbc.JDBCDriver"
db.hsqlmem.url="jdbc:hsqldb:mem:hsql-it"
db.hsqlmem.logStatements=true

## HSQL - File
db.hsqlfile.driver="org.hsqldb.jdbc.JDBCDriver"
db.hsqlfile.url="jdbc:hsqldb:target/db/hsql-it.db"
db.hsqlfile.logStatements=true

## SQLite - Memory
db.sqlite.driver="org.sqlite.JDBC"
db.sqlite.url="jdbc:sqlite:target/sqlite-it"
db.sqlite.logStatements=true
# SQLLite supports only SERIALIZABLE and READ_UNCOMMITTED
db.sqlite.isolation="READ_UNCOMMITTED"
````

Supported key are the following:

|Key|Description|Default value|
|---|----------- |-----|
|driver| the JDBC driver class | _mandatory_
|url| the JDBC url | _mandatory_
|logStatements| if set to true, dump the statements | false
|isolation| the transaction isolation |READ_COMMITTED
|autocommit|if set to true, autocommit is enabled|true
|readOnly|enables / disables the read only mode|false
|defaultCatalog| sets the default catalog|not set
|user|the username|not set
|password|the password|not set
|partitionCount|the number of partitions|1
|maxConnectionsPerPartition|set the maximum of connections per partition|30
|minConnectionsPerPartition|set the minimum of connections per partition|5
|acquireIncrement||1
|acquireRetryAttempts||10
|acquireRetryDelay|in ms|1000
|connectionTimeout|in ms|1000
|idleMaxAge||10min
|maxConnectionAge||1h
|disableJMX|enables / disables JMX|true
|statisticsEnabled|enables / disables statistics|false
|initSQL|the SQL script to execute on connection| |

## Using the DataSources service

Once configured, the data source is exposed as a service(`javax.sql.DataSource`). So you can retrieve it using:

````
@Requires DataSource ds;
````

If you have several data sources configured, you can filter them using:

````
@Requires(filter="(datasource.name=name)") DataSource ds;
````

You can also use the `org.wisdom.database.jdbc.service.DataSources` service that let you retrieve the set of
configured data sources:

````
@Requires DataSources sources;

//...
// Data Source name -> Data Source
Map<String, DataSource>  map = sources.getDataSources();
````

## Integrating another database

To be compatible with the JDBC Integration, the driver must be packaged as an OSGi bundle and an implementation of
`org.osgi.service.jdbc.DataSourceFactory` must be published in the OSGi registry. This architecture let us reduce the
 risk of leaks.

To ease your integration, we recommend you to extend `org.wisdom.jdbc.driver.helpers.AbstractDataSourceFactory`
reducing the implementation difficulties.
