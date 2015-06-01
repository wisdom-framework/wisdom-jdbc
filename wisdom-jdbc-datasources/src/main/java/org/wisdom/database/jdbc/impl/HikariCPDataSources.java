/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2015 Wisdom Framework
 * %%
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
 * #L%
 */
package org.wisdom.database.jdbc.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.felix.ipojo.annotations.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jdbc.DataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wisdom.api.configuration.ApplicationConfiguration;
import org.wisdom.api.configuration.Configuration;
import org.wisdom.database.jdbc.service.DataSources;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;


/**
 * Created by homada on 4/17/15.
 */
@Component(immediate = true)
@Provides
@Instantiate
public class HikariCPDataSources implements DataSources {

    private static final Logger LOGGER = LoggerFactory.getLogger(HikariCPDataSources.class);
    public static final String DB_CONFIGURATION_PREFIX = "db";

    private final BundleContext context;
    private ServiceRegistration<DataSource> registration;

    /**
     * A boolean indicating if the wisdom server is running in 'dev' mode.
     */
    private boolean isDev;

    private Map<String, HikariDataSource> sources = new HashMap<>();

    private Map<String, DataSourceFactory> drivers = new HashMap<>();

    @Requires
    private ApplicationConfiguration configuration;

    public HikariCPDataSources(BundleContext context) {
        this.context = context;
        LOGGER.info("HikariCP Pool starting...");
    }

    /**
     * Gets the data source with the given name.
     *
     * @param database the data source name
     * @return the data source with the given name, {@literal null} if none match
     */
    @Override
    public DataSource getDataSource(String database) {
        return sources.get(database);
    }

    @Override
    public DataSource getDataSource() {
        return sources.get(DEFAULT_DATASOURCE);
    }


    /**
     * Gets the set of data sources (name -> data source).
     * It contains the available data sources only.
     *
     * @return the map of name -> data source, empty if none.
     */
    @Override
    public Map<String, DataSource> getDataSources() {
        HashMap<String, DataSource> map = new HashMap<>();
        for (Map.Entry<String, HikariDataSource> entry : sources.entrySet()) {
            if (entry.getValue() != null) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }
    @Override
    public Connection getConnection() {
        return getConnection(DEFAULT_DATASOURCE, true);
    }

    /**
     * Gets a connection on the default data source.
     *
     * @param autocommit enables or disables the auto-commit.
     * @return the connection, {@literal null} if the default data source is not configured,
     * or if the connection cannot be opened.
     */
    @Override
    public Connection getConnection(boolean autocommit) {
        return getConnection(DEFAULT_DATASOURCE, autocommit);
    }

    @Override
    public Connection getConnection(String database) {
        return getConnection(database, true);
    }

    @Override
    public Connection getConnection(String database, boolean autocommit) {
        DataSource ds = getDataSource(database);
        if (ds == null) {
            return null;
        }
        try {
            Connection connection = ds.getConnection();
            connection.setAutoCommit(autocommit);
            return connection;
        } catch (SQLException e) {
            LOGGER.error("Cannot open connection on data source '{}", database, e);
            return null;
        }
    }

    @Validate
    public void onStart() throws SQLException{
        Configuration dbConfiguration = configuration.getConfiguration(DB_CONFIGURATION_PREFIX);
        this.isDev = configuration.isDev();
        // Detect all db configurations and create the data sources.
        if (dbConfiguration == null) {
            LOGGER.info("No data sources configured from the configuration, exiting the data source manager");
            // Remove existing ones
            sources.clear();
            return;
        }
        Set<String> names = dbConfiguration.asMap().keySet();
        LOGGER.info("{} data source(s) identified from the configuration : {}", names.size(), names);

        // Check whether we already have sources
        // Lost ones need to be removed
        // New ones need to be added
        // Remaining one need to be 'reinjected'
        if (!sources.isEmpty()) {
            // Remove lost ones
            for (String k : new HashSet<>(sources.keySet())) {
                if (!names.contains(k)) {
                    // Lost one.
                    LOGGER.info("The data source {} has been removed from configuration");
                    sources.remove(k);
                } else if (names.contains(k)) {
                    // Remaining data source, reconfiguration
                    LOGGER.info("Reconfiguring data source {}", k);
                    HikariDataSource ds = createDataSource(dbConfiguration.getConfiguration(k), k);
                    sources.put(k, ds);
                }
            }
        }

        for (String name : names) {
            if (sources.containsKey(name)) {
                // The data source is already existing.
                continue;
            }

            Configuration conf = dbConfiguration.getConfiguration(name);
            HikariDataSource datasource = createDataSource(conf, name);
            sources.put(name, datasource);
        }

        // Try to open a connection to each data source.
        // Register the data sources as services.
        for (Map.Entry<String, HikariDataSource> entry : sources.entrySet()) {
            try {
                if (entry.getValue() != null) {
                    register(context, entry.getValue(), entry.getKey());
                    entry.getValue().getConnection().close();
                    LOGGER.info("Connection successful to data source '{}'", entry.getKey());
                } else {
                    LOGGER.info("The data source '{}' is pending - no driver available", entry.getKey());
                }
            } catch (SQLException e) {
                LOGGER.error("The data source '{}' is configured but the connection failed", entry.getKey(), e);
            }
        }
    }
    @Invalidate
    public void onStop() {
        // Close all data sources
        for (Map.Entry<String, HikariDataSource> entry : sources.entrySet()) {
            shutdownPool(entry.getValue());
            LOGGER.info("Data source '{}' closed", entry.getKey());
        }
    }
    private HikariDataSource createDataSource(Configuration configuration, String dsName) throws SQLException{

        HikariConfig hikariConfig = toHikariConfig(configuration, dsName);
        boolean registred = registerDriver(configuration);
        if(registred){
            final HikariDataSource datasource = new HikariDataSource(hikariConfig);
            return datasource;
        }
        //we don't create datasource without driver
        return null;
    }

    private HikariConfig toHikariConfig(Configuration configuration, String dataSourceName){

        HikariConfig hikariConfig = new HikariConfig();

        String className = configuration.getWithDefault("dataSourceClassName", null);

        if (className == null) {
            LOGGER.warn("`dataSourceClassName` not present. Will use `jdbcUrl` instead.");
        }
        hikariConfig.setDriverClassName(configuration.get("driver"));

        boolean populated = Patterns.populate(hikariConfig, configuration.get("url"), isDev);
        if (populated) {
            LOGGER.debug("Data source metadata ('{}') populated from the given url", dataSourceName);
        }
        hikariConfig.setJdbcUrl(configuration.get("url"));
        hikariConfig.setUsername(configuration.get("user"));
        hikariConfig.setPassword(configuration.get("password"));

        // Frequently used
        hikariConfig.setAutoCommit(configuration.getBooleanWithDefault("autoCommit", true));
        hikariConfig.setConnectionTimeout(configuration.getIntegerWithDefault("connectionTimeout", 30000));
        hikariConfig.setIdleTimeout(configuration.getIntegerWithDefault("idleTimeout", 1000 * 60 * 10));
        hikariConfig.setMaxLifetime(configuration.getLongWithDefault("maxLifetime", 1000 * 60 * 30L));

        if(configuration.get("connectionTestQuery") != null){
            hikariConfig.setConnectionTestQuery(configuration.get("connectionTestQuery"));
        }

        hikariConfig.setMinimumIdle(configuration.getIntegerWithDefault("minimumIdle", 10));
        hikariConfig.setMaximumPoolSize(configuration.getIntegerWithDefault("maximumPoolSize", 10));

        String poolName = configuration.get("poolName");

        if(poolName != null){
            hikariConfig.setPoolName(configuration.get("poolName"));
        }

        // Infrequently used
        hikariConfig.setInitializationFailFast(configuration.getBooleanWithDefault("initializationFailFast", false));
        hikariConfig.setIsolateInternalQueries(configuration.getBooleanWithDefault("isolateInternalQueries", false));
        hikariConfig.setAllowPoolSuspension(configuration.getBooleanWithDefault("allowPoolSuspension", false));
        hikariConfig.setReadOnly(configuration.getBooleanWithDefault("readOnly", false));
        hikariConfig.setRegisterMbeans(configuration.getBooleanWithDefault("registerMbeans", false));

        String catalog = configuration.get("catalog");
        if(catalog != null){
            hikariConfig.setCatalog(configuration.get("catalog"));
        }

        String initSql = configuration.get("initSQL");
        if(initSql !=null){
            hikariConfig.setConnectionInitSql(configuration.get("initSQL"));
        }

        String isolation = getIsolationLevel(dataSourceName,configuration);
        hikariConfig.setTransactionIsolation(isolation);
        
        hikariConfig.setValidationTimeout(configuration.getIntegerWithDefault("validationTimeout", 5000));
        hikariConfig.setLeakDetectionThreshold(configuration.getIntegerWithDefault("leakDetectionThreshold", 0));

        hikariConfig.validate();

        return hikariConfig;
    }

    private String getIsolationLevel(String dsName, Configuration dbConf) {
        String isolation = dbConf.getWithDefault("isolation", "READ_COMMITTED");
        String isolationLevel = "TRANSACTION_READ_COMMITTED";
        switch (isolation.toUpperCase()) {
            case "NONE":
                isolationLevel = "TRANSACTION_NONE";
                break;
            case "READ_COMMITTED":
                isolationLevel = "TRANSACTION_READ_COMMITTED";
                break;
            case "READ_UNCOMMITTED":
                isolationLevel = "TRANSACTION_READ_UNCOMMITTED";
                break;
            case "REPEATABLE_READ":
                isolationLevel = "TRANSACTION_REPEATABLE_READ";
                break;
            case "SERIALIZABLE":
                isolationLevel = "TRANSACTION_SERIALIZABLE";
                break;
            default:
                LOGGER.error("Unknown transaction isolation  : " + isolation + " for " + dsName);
                break;
        }
        return isolationLevel;
    }

    private boolean registerDriver(Configuration config) throws SQLException {
        String driver = config.getWithDefault("driver", null);
        if (driver == null) {
            LOGGER.error("The data source has not driver classname - 'driverClassName' property not set");
            return false;
        } else {
            Driver instance = getDriver(driver);
            if (instance == null) {
                // The driver is not available
                return false;
            }
            DriverManager.registerDriver(instance);
            return true;
        }
    }

    public synchronized Driver getDriver(String classname) throws SQLException {
        DataSourceFactory factory = drivers.get(classname);
        if (factory != null) {
            return factory.createDriver(null);
        } else {
            return null;
        }
    }

    @Bind(optional = true, aggregate = true)
    public synchronized void bindFactory(DataSourceFactory factory, Map<String, String> properties) {
        String driverClassName = properties.get(DataSourceFactory.OSGI_JDBC_DRIVER_CLASS);
        drivers.put(driverClassName, factory);
        checkPendingDatasource(driverClassName);
    }

    private void checkPendingDatasource(String driverClassName) {
        for (Map.Entry<String, HikariDataSource> entry : sources.entrySet()) {
            if(entry.getValue() == null && driverClassName.equals(getRequiredDriver(entry.getKey()))){
                Configuration dbConfiguration = configuration.getConfiguration(DB_CONFIGURATION_PREFIX).getConfiguration(entry.getKey());
                try {

                    HikariDataSource ds = createDataSource(dbConfiguration, entry.getKey());
                    sources.put(entry.getKey(), ds);
                    if (entry.getValue() != null) {
                        register(context, entry.getValue(), entry.getKey());
                        entry.getValue().getConnection().close();
                        LOGGER.info("Connection successful to data source '{}'", entry.getKey());
                    } else {
                        LOGGER.error("The data source '{}' cannot be created, despite the driver just arrives",
                                entry.getKey());
                    }
                } catch (SQLException e) {
                    LOGGER.error("The data source '{}' is configured but the connection failed", entry.getKey(), e);
                }
            }

        }
    }

    private String getRequiredDriver(String datasourceName) {
        Configuration dbConfiguration = configuration.getConfiguration(DB_CONFIGURATION_PREFIX);
        String driver = dbConfiguration.getConfiguration(datasourceName).get("driver");
        return driver;
    }

    @Unbind
    public synchronized void unbindFactory(DataSourceFactory factory, Map<String, String> properties) {
        String driverClassName = properties.get(DataSourceFactory.OSGI_JDBC_DRIVER_CLASS);
        drivers.remove(driverClassName);
        invalidateDataSources(driverClassName);

    }

    private void invalidateDataSources(String driverClassName) {
        for (Map.Entry<String, HikariDataSource> entry : sources.entrySet()) {
            HikariDataSource ds = entry.getValue();
            if (ds != null && driverClassName.equals(getRequiredDriver(entry.getKey()))) {
                // A used driver just left....
                unregister();
                sources.put(entry.getKey(), null);
                LOGGER.info("Driver {} left the DataSourceFactory", driverClassName);
            }
        }
    }

    private void shutdownPool(HikariDataSource source) {
        LOGGER.info("Shutting down connection pool.");
        if (source instanceof HikariDataSource) {
            source.shutdown();
        } else {
            throw new IllegalArgumentException("Cannot close a data source not managed by the manager :" + source);
        }
    }

    private void register(BundleContext context, DataSource ds, String dbName) {
        if (registration != null) {
            return;
        }
        Dictionary<String, String> props = new Hashtable<>();
        Configuration serviceProperties = configuration.getConfiguration("properties");
        if(serviceProperties!=null){
            Properties properties = serviceProperties.asProperties();
            for(Enumeration<Object> keys = properties.keys(); keys.hasMoreElements(); /* NO-OP */){
                String serviceProp = (String) keys.nextElement();
                props.put(serviceProp, serviceProperties.getOrDie(serviceProp));
            }
        }
        //
        //  "name" property value from application.conf will silently override the value from service properties.
        //
        props.put(DataSources.DATASOURCE_NAME_PROPERTY, dbName);
        registration = context.registerService(DataSource.class, ds, props);
    }

    private void unregister() {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
    }

    /**
     * For testing purpose only. Injects the application configuration.
     *
     * @param configuration the configuration
     * @return the current {@link org.wisdom.database.jdbc.impl.HikariCPDataSources}
     */
    public HikariCPDataSources setApplicationConfiguration(ApplicationConfiguration configuration) {
        this.configuration = configuration;
        return this;
    }

}
