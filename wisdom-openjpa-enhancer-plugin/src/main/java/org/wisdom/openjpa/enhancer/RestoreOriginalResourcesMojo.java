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
     * Restore the original persistence.xml to target/classes/META-INF directory.
     * <br/>.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException if the original persistence.xml is not found. Throwing this
     *                                                        exception causes a "BUILD ERROR" message to be displayed.
     **/
    @Override
    public void execute() throws MojoExecutionException{
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
            FileUtils.deleteQuietly(persistenceOLD);

            if (ormMapping.isFile()){
                getLog().info("Delete orm.xml from  classes/META-INF/ directory ");
                FileUtils.deleteQuietly(ormMapping);
            }
        } catch (IOException e) {
            getLog().warn("IOException when restoring the original persistence.xml");
            throw new MojoExecutionException("Cannot continue the restoring of the persistence.xml", e);
        }

    }
}
