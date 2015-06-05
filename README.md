# Wisdom-JDBC

This project integrates JDBC data sources within Wisdom-Framework. It provides two features:

* The integration of JDBC driver in Wisdom (compatible with OSGi),
* Letting you access to configured data sources.

Basically, for each configured data source, a `javax.sql.DataSource` service is published to
access the data source. This service manages the connections to the data source. The `org.wisdom.database.jdbc
.service.DataSources` service is also exposed to retrieve the list of configured data sources.

**IMPORTANT**: Wisdom-jdbc requires JDK 8 to be built.

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

<dependency>
    <groupId>org.javassist</groupId>
    <artifactId>javassist</artifactId>
    <version>3.19.0-GA</version>
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


## H2 - Memory
db.h2mem.driver="org.h2.Driver"
db.h2mem.url="jdbc:h2:mem:h2-mem-it"


## H2 - File
db.h2file.driver="org.h2.Driver"
db.h2file.url="jdbc:h2:./target/db/h2-it.db"


#
# Note about HSQL : HSQL supports only one catalog per database, so the `defaultCatalog` parameter must not be set.
# http://hsqldb.org/doc/2.0/apidocs/org/hsqldb/jdbc/JDBCConnection.html#setCatalog(java.lang.String)
#

## HSQL - Memory
db.hsqlmem.driver="org.hsqldb.jdbc.JDBCDriver"
db.hsqlmem.url="jdbc:hsqldb:mem:hsql-it"


## HSQL - File
db.hsqlfile.driver="org.hsqldb.jdbc.JDBCDriver"
db.hsqlfile.url="jdbc:hsqldb:target/db/hsql-it.db"


## SQLite - Memory
db.sqlite.driver="org.sqlite.JDBC"
db.sqlite.url="jdbc:sqlite:target/sqlite-it"

# SQLLite supports only SERIALIZABLE and READ_UNCOMMITTED
db.sqlite.isolation="READ_UNCOMMITTED"
````

##Configuration

**Every property is optional, except for the "essentials" marked below.**

##### Essentials

&#128288;``driver``<br/>
This is the name of the class provided by the JDBC driver.
*Default: none*

&#128288;``url``<br/>
This is the JDBC url.
*Default: none*

&#128288;``user``<br/>
This property sets the default authentication username.
*Default: none*

&#128288;``password``<br/>
This property sets the default authentication password.
*Default: none*

##### Frequently used

&#9989;``autoCommit``<br/>
This property controls the default auto-commit behavior of connections returned from the pool.
It is a boolean value.
*Default: true*

&#8986;``connectionTimeout``<br/>
This property controls the maximum number of milliseconds that a client (that's you) will wait
for a connection from the pool.  If this time is exceeded without a connection becoming
available, a SQLException will be thrown.  1000ms is the minimum value.
*Default: 30000 (30 seconds)*

&#8986;``idleTimeout``<br/>
This property controls the maximum amount of time that a connection is allowed to sit idle in the
pool.  Whether a connection is retired as idle or not is subject to a maximum variation of +30
seconds, and average variation of +15 seconds.  A connection will never be retired as idle *before*
this timeout.  A value of 0 means that idle connections are never removed from the pool.
*Default: 600000 (10 minutes)*

&#8986;``maxLifetime``<br/>
This property controls the maximum lifetime of a connection in the pool.  When a connection
reaches this timeout it will be retired from the pool, subject to a maximum variation of +30
seconds.  An in-use connection will never be retired, only when it is closed will it then be
removed.  **We strongly recommend setting this value, and it should be at least 30 seconds less
than any database-level connection timeout.**  A value of 0 indicates no maximum lifetime 
(infinite lifetime), subject of course to the ``idleTimeout`` setting.
*Default: 1800000 (30 minutes)*

&#128288;``connectionTestQuery``<br/>
**If your driver supports JDBC4 we strongly recommend not setting this property.** This is for 
"legacy" databases that do not support the JDBC4 ``Connection.isValid() API``.  This is the query that
will be executed just before a connection is given to you from the pool to validate that the 
connection to the database is still alive. *Again, try running the pool without this property,
HikariCP will log an error if your driver is not JDBC4 compliant to let you know.*
*Default: none*

&#128290;``minimumIdle``<br/>
This property controls the minimum number of *idle connections* that HikariCP tries to maintain
in the pool.  If the idle connections dip below this value, HikariCP will make a best effort to
add additional connections quickly and efficiently.  However, for maximum performance and
responsiveness to spike demands, we recommend *not* setting this value and instead allowing
HikariCP to act as a *fixed size* connection pool.
*Default: same as maximumPoolSize*

&#128290;``maximumPoolSize``<br/>
This property controls the maximum size that the pool is allowed to reach, including both
idle and in-use connections.  Basically this value will determine the maximum number of
actual connections to the database backend.  A reasonable value for this is best determined
by your execution environment.  When the pool reaches this size, and no idle connections are
available, calls to getConnection() will block for up to ``connectionTimeout`` milliseconds
before timing out.
*Default: 10*

&#128288;``poolName``<br/>
This property represents a user-defined name for the connection pool and appears mainly
in logging and JMX management consoles to identify pools and pool configurations.
*Default: auto-generated*

##### Infrequently used

&#9989;``initializationFailFast``<br/>
This property controls whether the pool will "fail fast" if the pool cannot be seeded with
initial connections successfully.  If you want your application to start *even when* the
database is down/unavailable, set this property to ``false``.
*Default: true*

&#10062;``isolateInternalQueries``<br/>
This property determines whether to isolates internal pool queries, such as the
connection alive test, in their own transaction.  Since these are typically read-only
queries, it is rarely necessary to encapsulate them in their own transaction.  This
property only applies if ``autoCommit`` is disabled.
*Default: false*

&#10062;``allowPoolSuspension``<br/>
This property controls whether the pool can be suspended and resumed through JMX.  This is
useful for certain failover automation scenarios.  When the pool is suspended, calls to
``getConnection()`` will *not* timeout and will be held until the pool is resumed.
*Default: false*

&#10062;``readOnly``<br/>
This property controls whether *Connections* obtained from the pool are in read-only mode by
default.  Note some databases do not support the concept of read-only mode, while others provide
query optimizations when the *Connection* is set to read-only.  Whether you need this property
or not will depend largely on your application and database. 
*Default: false*

&#10062;``registerMbeans``<br/>
This property controls whether or not JMX Management Beans ("MBeans") are registered or not.
*Default: false*

&#128288;``catalog``<br/>
This property sets the default *catalog* for databases that support the concept of catalogs.
If this property is not specified, the default catalog defined by the JDBC driver is used.
*Default: driver default*

&#128288;``initSQL``<br/>
This property sets a SQL statement that will be executed after every new connection creation
before adding it to the pool. If this SQL is not valid or throws an exception, it will be
treated as a connection failure and the standard retry logic will be followed.
*Default: none*

&#128288;``isolation``<br/>
This property controls the default transaction isolation level of connections returned from
the pool.  If this property is not specified, the default transaction isolation level defined
by the JDBC driver is used.  Only use this property if you have specific isolation requirements that are
common for all queries.  The value of this property is the constant name from the ``Connection``
class without the word TRANSACTION such as ``READ_COMMITTED``, ``REPEATABLE_READ``, etc.
*Default: driver default*

&#8986;``validationTimeout``<br/>
This property controls the maximum amount of time that a connection will be tested for aliveness.
This value must be less than the ``connectionTimeout``.  The lowest accepted validation timeout is
1000ms (1 second).
*Default: 5000*

&#8986;``leakDetectionThreshold``<br/>
This property controls the amount of time that a connection can be out of the pool before a
message is logged indicating a possible connection leak.  A value of 0 means leak detection
is disabled.  Lowest acceptable value for enabling leak detection is 2000 (2 secs).
*Default: 0*


*Note :The configuration list has been adapted in large part from https://github.com/brettwooldridge/HikariCP.*

##### Log SQL statements
This plugin does not offer (out of the box) a way to log SQL statements because we use HikariCP and HikariCP suggests to you use the log capacities of your database vendor. 
From HikariCP docs:
> **Log Statement Text / Slow Query Logging**
>
> Like Statement caching, most major database vendors support statement logging through properties of their own driver. 
This includes Oracle, MySQL, Derby, MSSQL, and others. Some even support slow query logging. We consider this a "development-time" feature. 
For those few databases that do not support it, jdbcdslog-exp is a good option. Great stuff during development and pre-Production.

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
