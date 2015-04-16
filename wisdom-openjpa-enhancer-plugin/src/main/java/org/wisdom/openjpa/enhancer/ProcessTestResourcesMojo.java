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
package org.wisdom.openjpa.enhancer;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wisdom.maven.mojos.AbstractWisdomMojo;

import java.io.File;
import java.io.IOException;

/**
 * A Mojo that copy persistence xml file from test directory to be used
 * for integration tests.
 *
 * Created by homada on 4/15/15.
 */
@Mojo(name = "process-persistence-test", threadSafe = false,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresProject = true,
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class ProcessTestResourcesMojo extends AbstractWisdomMojo
{

    /**
     * Perform a copy of the original persistence.xml and use the persistence.xml file from test/resources/META-INF directory.
     * <br/>.
     * If it found and orm.xml in test/resources/META-INF then it will be copied as well.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException if the persistence.xml dedicated for tests is not found. Throwing this
     *                                                        exception causes a "BUILD ERROR" message to be displayed.
     **/
    @Override
    public void execute() throws MojoExecutionException{
        copyTestPersistenceXmlFile();
    }

    private void copyTestPersistenceXmlFile() throws MojoExecutionException {

        File persistenceOLD = new File(buildDirectory, "classes/META-INF/persistence.xml");
        File persistenceForTest = new File(buildDirectory, "test-classes/META-INF/persistence.xml");
        File ormMapping =  new File(buildDirectory, "test-classes/META-INF/orm.xml");

        if (!persistenceForTest.isFile()) {
            throw new MojoExecutionException("Cannot find the persistence xml file - " +
                    persistenceForTest.getAbsolutePath() + " is not a file");
        }
        try {
            getLog().info("Start saving original persistence.xml from :" + persistenceOLD.getAbsolutePath());
            FileUtils.copyFile(persistenceOLD, new File(buildDirectory, "classes/META-INF/persistence.xml.proper"));
            FileUtils.deleteQuietly(persistenceOLD);
            getLog().info("Copy persistence.xml from test/resources/META-INF/ directory to classes/META-INF/ directory ");
            FileUtils.copyFile(persistenceForTest, persistenceOLD);

            if (ormMapping.isFile()){
                getLog().debug("Copy orm.xml test/resources/META-INF/ directory to classes/META-INF/ directory ");
                FileUtils.copyFile(ormMapping, new File(buildDirectory, "classes/META-INF/orm.xml"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
