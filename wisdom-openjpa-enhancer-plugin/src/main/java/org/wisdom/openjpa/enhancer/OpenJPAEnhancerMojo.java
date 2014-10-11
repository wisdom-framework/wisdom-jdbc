/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
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
package org.wisdom.openjpa.enhancer;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.openjpa.enhance.PCEnhancer;
import org.apache.openjpa.lib.util.Options;
import org.wisdom.maven.WatchingException;
import org.wisdom.maven.mojos.AbstractWisdomWatcherMojo;
import org.wisdom.maven.utils.WatcherUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A Mojo enhancing entity classes for OpenJPA.
 */
@Mojo(name = "enhance-entities", threadSafe = false,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresProject = true,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class OpenJPAEnhancerMojo extends AbstractWisdomWatcherMojo {

    /**
     * The working directory for putting persistence.xml and
     * other stuff into if we need to.
     */
    @Parameter(defaultValue = "${project.build.directory}/openjpa-work", required = true)
    protected File workDir;

    /**
     * Location where <code>persistence-enabled</code> classes are located.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    protected File classes;

    /**
     * Comma separated list of includes to scan searchDir to pass to the jobs.
     * This may be used to restrict the OpenJPA tasks to e.g. a single package which
     * contains all the entities.
     */
    @Parameter(defaultValue = "**/*.class")
    private String includes;

    /**
     * Comma separated list of excludes to scan searchDir to pass to the jobs.
     * This option may be used to stop OpenJPA tasks from scanning non-JPA classes
     * (which usually leads to warnings such as "Type xxx has no metadata")
     */
    @Parameter(defaultValue = "")
    private String excludes;

    /**
     * Additional properties passed to the OpenJPA tools.
     */
    @Parameter
    private Properties toolProperties;

    /**
     * Used if a non-default file location for the persistence.xml should be used
     * If not specified, the default one in META-INF/persistence.xml will be used.
     * Since openjpa-2.3.0 this can also be a resource location. In prior releases
     * it was only possible to specify a file location.
     */
    @Parameter(defaultValue = "target/classes/META-INF/persistence.xml")
    private String persistenceXmlFile;

    /**
     * <p>This setting can be used to override any openjpa.ConnectionDriverName set in the
     * persistence.xml. It can also be used if the persistence.xml contains no connection
     * information at all.<P>
     * <p>
     * Sample:
     * <pre>
     * &lt;connectionDriverName&gt;com.mchange.v2.c3p0.ComboPooledDataSource&lt;/connectionDriverName&gt;
     * </pre>
     * <p>
     * This is most times used in conjunction with {@link #connectionProperties}.
     */
    @Parameter
    private String connectionDriverName;

    /**
     * The string used for passing information about the connectionDriverName.
     */
    public static final String OPTION_CONNECTION_DRIVER_NAME = "ConnectionDriverName";

    /**
     * <p>Used to define the credentials or any other connection properties.</p>
     * <p>
     * Sample:
     * <pre>
     * &lt;connectionProperties&gt;
     *   driverClass=com.mysql.jdbc.Driver,
     *   jdbcUrl=jdbc:mysql://localhost/mydatabase,
     *   user=root,
     *   password=,
     *   minPoolSize=5,
     *   acquireRetryAttempts=3,
     *   maxPoolSize=20
     * &lt;/connectionProperties&gt;
     * </pre>
     * <p>
     * This is most times used in conjunction with {@link #connectionDriverName}.
     */
    @Parameter
    private String connectionProperties;

    /**
     * the string used for passing information about the connectionProperties.
     */
    public static final String OPTION_CONNECTION_PROPERTIES = "ConnectionProperties";


    /**
     * List of all class path elements that will be searched for the
     * <code>persistence-enabled</code> classes and resources expected by
     * PCEnhancer.
     */
    @Parameter(defaultValue = "${project.compileClasspathElements}", required = true, readonly = true)
    protected List<String> compileClasspathElements;


    /**
     * The properties option is used for passing information about the persistence.xml file location.
     */
    public static final String OPTION_PROPERTIES_FILE = "propertiesFile";

    /**
     * The JPA spec requires that all persistent classes define a no-arg constructor.
     * This flag tells the enhancer whether to add a protected no-arg constructor
     * to any persistent classes that don't already have one.
     */
    @Parameter(defaultValue = "true")
    protected boolean addDefaultConstructor;
    /**
     * used for passing the addDefaultConstructor parameter to the enhancer tool
     */
    private static final String OPTION_ADD_DEFAULT_CONSTRUCTOR = "addDefaultConstructor";

    /**
     * Whether to throw an exception when it appears that a property access entity
     * is not obeying the restrictions placed on property access.
     */
    @Parameter(defaultValue = "false")
    protected boolean enforcePropertyRestrictions;
    /**
     * used for passing the enforcePropertyRestrictions parameter to the enhnacer tool
     */
    private static final String OPTION_ENFORCE_PROPERTY_RESTRICTION = "enforcePropertyRestrictions";

    /**
     * Tell the PCEnhancer to use a temporary classloader for enhancement.
     * If you enable this feature, then no depending artifacts from the classpath will be used!
     * Please note that you have to disable the tmpClassLoader for some cases in OpenJPA-1.2.1
     * due to an extended parsing strategy.
     */
    @Parameter(defaultValue = "false")
    protected boolean tmpClassLoader;
    /**
     * used for passing the tmpClassLoader parameter to the enhnacer tool
     */
    private static final String OPTION_USE_TEMP_CLASSLOADER = "tcl";

    /**
     * Perform whatever build-process behavior this <code>Mojo</code> implements.
     * <br/>
     * This is the main trigger for the <code>Mojo</code> inside the <code>Maven</code> system, and allows
     * the <code>Mojo</code> to communicate errors.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException if an unexpected problem occurs.
     *                                                        Throwing this exception causes a "BUILD ERROR" message to be displayed.
     * @throws org.apache.maven.plugin.MojoFailureException   if an expected problem (such as a compilation failure) occurs.
     *                                                        Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!classes.exists()) {
            FileUtils.mkdir(classes.getAbsolutePath());
        }

        List<File> entities = findEntityClassFiles();
        enhance(entities);
    }

    /**
     * Checks whether the given file is managed by the current watcher. Notice that implementation must not check
     * for the existence of the file as this method is also called for deleted files.
     *
     * @param file is the file.
     * @return {@literal true} if the watcher is interested in being notified on an event
     * attached to the given file,
     * {@literal false} otherwise.
     */
    @Override
    public boolean accept(File file) {
        return WatcherUtils.hasExtension(file, "java");
    }

    /**
     * Notifies the watcher that a new file is created.
     *
     * @param file is the file.
     * @return {@literal false} if the pipeline processing must be interrupted for this event. Most watchers should
     * return {@literal true} to let other watchers be notified.
     * @throws org.wisdom.maven.WatchingException if the watcher failed to process the given file.
     */
    @Override
    public boolean fileCreated(File file) throws WatchingException {
        try {
            List<File> entities = findEntityClassFiles();
            enhance(entities);
            return true;
        } catch (MojoExecutionException e) {
            throw new WatchingException("OpenJPA Enhancer", "Error while enhancing JPA entities", file, e);
        }
    }

    /**
     * Notifies the watcher that a file has been modified.
     *
     * @param file is the file.
     * @return {@literal false} if the pipeline processing must be interrupted for this event. Most watchers should
     * returns {@literal true} to let other watchers to be notified.
     * @throws org.wisdom.maven.WatchingException if the watcher failed to process the given file.
     */
    @Override
    public boolean fileUpdated(File file) throws WatchingException {
        return fileCreated(file);
    }

    /**
     * Notifies the watcher that a file was deleted.
     *
     * @param file the file
     * @return {@literal false} if the pipeline processing must be interrupted for this event. Most watchers should
     * return {@literal true} to let other watchers be notified.
     * @throws org.wisdom.maven.WatchingException if the watcher failed to process the given file.
     */
    @Override
    public boolean fileDeleted(File file) throws WatchingException {
        return fileCreated(file);
    }

    private File ensurePersistenceXml() throws MojoExecutionException {
        if (persistenceXmlFile != null) {
            File persistence = new File(project.getBasedir(), persistenceXmlFile);
            if (!persistence.isFile()) {
                throw new MojoExecutionException("Cannot find the custom persistence xml file - " +
                        persistence.getAbsolutePath() + " is not a file");
            }
            return persistence;
        } else {
            File persistence = new File(buildDirectory, "classes/META-INF/persistence.xml");
            if (!persistence.isFile()) {
                throw new MojoExecutionException("Cannot find the persistence xml file - " +
                        persistence.getAbsolutePath() + " is not a file");
            }
            return persistence;
        }
    }

    /**
     * This will prepare the current ClassLoader and add all jars and local
     * classpaths (e.g. target/classes) needed by the OpenJPA task.
     *
     * @throws MojoExecutionException on any error inside the mojo
     */
    protected void extendRealmClasspath()
            throws MojoExecutionException {
        List<URL> urls = new ArrayList<>();

        for (String fileName : compileClasspathElements) {
            File pathElem = new File(fileName);
            try {
                URL url = pathElem.toURI().toURL();
                urls.add(url);
                getLog().debug("Added classpathElement URL " + url);
            } catch (MalformedURLException e) {
                throw new MojoExecutionException("Error in adding the classpath " + pathElem, e);
            }
        }

        ClassLoader jpaRealm =
                new URLClassLoader(urls.toArray(new URL[urls.size()]), getClass().getClassLoader());

        // set the new ClassLoader as default for this Thread
        //TODO this should be reverted at some points.
        Thread.currentThread().setContextClassLoader(jpaRealm);
    }

    /**
     * Locates and returns a list of class files found under specified class
     * directory.
     *
     * @return list of class files.
     * @throws MojoExecutionException if there was an error scanning class file
     *                                resources.
     */
    protected List<File> findEntityClassFiles() throws MojoExecutionException {
        try {
            return FileUtils.getFiles(classes, includes, excludes);
        } catch (IOException e) {
            throw new MojoExecutionException("Error while scanning for '" + includes + "' in " + "'"
                    + classes.getAbsolutePath() + "'.", e);
        }
    }

    /**
     * @param files List of files
     * @return the paths of the given files as String[]
     */
    protected String[] getFilePaths(List<File> files) {
        String[] args = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);

            args[i] = file.getAbsolutePath();
        }
        return args;
    }


    /**
     * Get the options for the OpenJPA enhancer tool.
     *
     * @return populated Options
     */
    protected Options getOptions() throws MojoExecutionException {
        File persistence = ensurePersistenceXml();

        Options opts = new Options();
        if (toolProperties != null) {
            opts.putAll(toolProperties);
        }
        opts.put(OPTION_PROPERTIES_FILE, persistence.getAbsolutePath());

        if (connectionDriverName != null) {
            opts.put(OPTION_CONNECTION_DRIVER_NAME, connectionDriverName);
        }

        if (connectionProperties != null) {
            opts.put(OPTION_CONNECTION_PROPERTIES, connectionProperties);
        }

        // put the standard options into the list also
        opts.put(OPTION_ADD_DEFAULT_CONSTRUCTOR, Boolean.toString(addDefaultConstructor));
        opts.put(OPTION_ENFORCE_PROPERTY_RESTRICTION, Boolean.toString(enforcePropertyRestrictions));
        opts.put(OPTION_USE_TEMP_CLASSLOADER, Boolean.toString(tmpClassLoader));

        return opts;
    }

    /**
     * Processes a list of class file resources that are to be enhanced.
     *
     * @param files class file resources to enhance.
     * @throws MojoExecutionException if the enhancer encountered a failure
     */
    private void enhance(List<File> files) throws MojoExecutionException {
        Options opts = getOptions();

        // list of input files
        String[] args = getFilePaths(files);

        boolean ok;

        if (!tmpClassLoader) {
            extendRealmClasspath();
        }

        ok = PCEnhancer.run(args, opts);

        if (!ok) {
            throw new MojoExecutionException("The OpenJPA Enhancer tool detected an error, check log");
        }
    }
}
