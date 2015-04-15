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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.utils.io.FileUtils;
import org.wisdom.maven.mojos.AbstractWisdomMojo;

import java.io.File;
import java.io.IOException;

/**
 * A Mojo that restore original persistence xml file.
 *
 * Created by homada on 4/15/15.
 */
@Mojo(name = "restore", threadSafe = false,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresProject = true,
        defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class RestoreOriginalResourcesMojo extends AbstractWisdomMojo {
    /**
     * Perform whatever build-process behavior this <code>Mojo</code> implements.
     * <br/>
     * This is the main trigger for the <code>Mojo</code> inside the <code>Maven</code> system, and allows
     * the <code>Mojo</code> to communicate errors.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException if an unexpected problem occurs. Throwing this
     *                                                        exception causes a "BUILD ERROR" message to be displayed.
     * @throws org.apache.maven.plugin.MojoFailureException   if an expected problem (such as a compilation
     *                                                        failure) occurs. Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        restorePersistenceXmlFile();
    }

    private void restorePersistenceXmlFile() throws MojoExecutionException {

        File persistenceOLD = new File(buildDirectory, "classes/META-INF/persistence.xml.proper");
        File persistenceNew = new File(buildDirectory, "classes/META-INF/persistence.xml");
        File ormMapping =  new File(buildDirectory, "classes/META-INF/orm.xml");

        if (!persistenceOLD.isFile()) {
            throw new MojoExecutionException("Cannot find the original persistence.xml.proper file - " +
                    persistenceOLD.getAbsolutePath() + " is not a file");
        }
        try {
            getLog().info("Start restoring the original persistence.xml from :" + persistenceOLD.getAbsolutePath());
            FileUtils.forceDelete(persistenceNew);
            FileUtils.copyFile(persistenceOLD, new File(buildDirectory, "classes/META-INF/persistence.xml"));
            FileUtils.forceDelete(persistenceOLD);

            if (ormMapping.isFile()){
                getLog().debug("Delete orm.xml from  classes/META-INF/ directory ");
                FileUtils.forceDelete(ormMapping);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
