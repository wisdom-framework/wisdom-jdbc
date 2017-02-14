/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2016 Wisdom Framework
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
package todo.models;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wisdom.api.model.Crud;
import org.wisdom.test.parents.Filter;
import org.wisdom.test.parents.WisdomTest;

import javax.inject.Inject;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check how crud can be tested
 */
public class TodoListIT extends WisdomTest {

    @BeforeClass
    @AfterClass
    public static void removeDatabase() {
        // Just remove the database files
        FileUtils.deleteQuietly(new File("database"));
    }

    @Inject
    @Filter("(entity.classname=todo.models.TodoList)")
    private Crud<TodoList, Long> crud;

    @Test
    public void testAvailability() {
        assertThat(crud).isNotNull();
    }

    @Test
    public void testManipulation() {
        TodoList list = new TodoList();
        list.setName("my todo list");
        list.setOwner("clement");
        list = crud.save(list);

        // There is a default list, so we have two list
        assertThat(crud.findAll()).hasSize(2);

        TodoList list2 = crud.findOne(list.getId());
        assertThat(list2).isNotNull();
        assertThat(list2.getName()).isEqualToIgnoringCase("my todo list");
        assertThat(list2.getOwner()).isEqualToIgnoringCase("clement");

        crud.delete(list2.getId());

        assertThat(crud.findAll()).hasSize(1);
    }



}