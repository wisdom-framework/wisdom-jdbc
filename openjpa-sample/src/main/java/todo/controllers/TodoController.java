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
package todo.controllers;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.felix.ipojo.annotations.Validate;
import org.wisdom.api.DefaultController;
import org.wisdom.api.annotations.*;
import org.wisdom.api.http.Result;
import org.wisdom.api.model.Crud;
import org.wisdom.api.model.HasBeenRollBackException;
import org.wisdom.framework.transaction.Transactional;
import todo.models.Todo;
import todo.models.TodoList;

import javax.validation.Valid;
import java.util.Iterator;

import static org.wisdom.api.http.HttpMethod.*;

@Controller
@Path("/list")
public class TodoController extends DefaultController {

    @Model(TodoList.class)
    private Crud<TodoList, String> listCrud;

    @Model(Todo.class)
    private Crud<Todo, String> todoCrud;


    @Validate
    public void start() throws HasBeenRollBackException {
        //Populate the db with some default value
        listCrud.executeTransactionalBlock(new Runnable() {
            @Override
            public void run() {
                if (listCrud.count() == 0) {
                    logger().info("Adding default item");
                    Todo todo = new Todo();
                    todo.setContent("Check out this awesome todo demo!");
                    todo.setDone(true);


                    TodoList list = new TodoList();
                    list.setName("Todo-List");
                    list.setTodos(Lists.newArrayList(todo));
                    list.setOwner("foo");
                    listCrud.save(list);


                    logger().info("Item added:");
                    logger().info("todo : {} - {}", todo, todo.getId());
                    logger().info("list : {} - {}", list, list.getId());

                    for (TodoList l : listCrud.findAll()) {
                        logger().info("List {} with {} items ({})", l.getName(), l.getTodos().size(), l.getOwner());
                    }

                } else {
                    logger().info("Existing items : {}", listCrud.count());
                    for (TodoList list : listCrud.findAll()) {
                        logger().info("List {} with {} items", list.getName(), list.getTodos().size());
                    }
                }
            }
        });

    }

    @Route(method = GET, uri = "/")
    public Result getList() {
        return ok(Iterables.toArray(listCrud.findAll(), TodoList.class)).json();
    }

    @Route(method = PUT, uri = "/")
    public Result putList(@Body TodoList list) {
        return ok(listCrud.save(list)).json();
    }

    @Route(method = DELETE, uri = "/{id}")
    public Result delList(final @Parameter("id") String id) {
        TodoList todoList = listCrud.findOne(id);

        if (todoList == null) {
            return notFound();
        }

        listCrud.delete(todoList);

        return ok();
    }

    @Route(method = GET, uri = "/{id}")
    public Result getTodos(final @Parameter("id") String id) {
        TodoList todoList = null;

        try {
            todoList = listCrud.findOne(id);
        } catch (IllegalArgumentException e) {
            return badRequest();
        }
        if (todoList == null) {
            return notFound();
        }

        return ok(todoList.getTodos()).json();
    }

    @Route(method = PUT, uri = "/{id}")
    @Transactional
    public Result createTodo(final @Parameter("id") String id, @Valid @Body Todo todo) {
        TodoList todoList = listCrud.findOne(id);

        if (todoList == null) {
            return notFound();
        }

        if (todo == null) {
            return badRequest("Cannot create todo, content is null.");
        }

        todoList.getTodos().add(todo);
        todoList = listCrud.save(todoList);
        final Todo last = Iterables.getLast(todoList.getTodos());
        logger().info("Todo created (last) : " + last.getId());
        return ok(last).json();
    }

    @Route(method = POST, uri = "/{id}/{todoId}")
    @Transactional
    public Result updateTodo(@Parameter("id") String listId, @Parameter("todoId") long todoId, @Valid @Body Todo todo) {
        TodoList todoList = listCrud.findOne(listId);

        if (todoList == null) {
            return notFound();
        }

        if (todo == null) {
            return badRequest("The given todo is null");
        }

        if (todoId != todo.getId()) {
            return badRequest("The id of the todo does not match the url one");
        }

        for (Todo item : todoList.getTodos()) {
            if (item.getId() == todoId) {
                item.setDone(todo.getDone());
                return ok(item).json();
            }
        }
        return notFound();
    }

    @Route(method = DELETE, uri = "/{id}/{todoId}")
    @Transactional
    public Result delTodo(@Parameter("id") String listId, @Parameter("todoId") long todoId) {
        TodoList todoList = listCrud.findOne(listId);

        if (todoList == null) {
            return notFound();
        }

        Iterator<Todo> itTodo = todoList.getTodos().iterator();
        while (itTodo.hasNext()) {
            if (itTodo.next().getId() == todoId) {
                itTodo.remove();
                return ok();
            }
        }
        return notFound();
    }
}
