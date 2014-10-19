# OpenJPA in Wisdom sample

This project is a 'toy' project showing how OpenJPA can be integrated in Wisdom applications.

## Entities and persistence.xml

Entities are in `src/main/java/models`, but could be in any packages. They are reference from the `persistence.xml`
located in `src/main/resources/META-INF/persistence.xml`. This location is the default location, but it could be
changed.

In this sample, we have two entity classes: `Todo` and `TodoList`. This sample use the OpenJPA maping tools to drop
and recreate the database on updated:

````
<property name="openjpa.jdbc.SynchronizeMappings"
                      value="buildSchema(SchemaAction='add,deleteTableContents',ForeignKeys=true)"/>
````

## The data source

The OpenJPA support works with any JDBC data source supported by the `Wisdom-JDBC-DataSources` project. In this
sample, we use H2.

In the `application.conf` file, we add this configuration:

````
# Data Source configuration
# ~~~~~~~~~~~~~~~~~~~~~~~~~
db.todo.driver = org.h2.Driver
db.todo.url = jdbc:h2:database/todo.db
db.todo.logStatements = true
````

## Retrieving and playing with entities

The `TodoController` exposes a kind-of-REST API to play with todo lists and todo items. It retrieves the `Crud`
service using:

````
@Model(TodoList.class)
private Crud<TodoList, String> listCrud;

@Model(Todo.class)
private Crud<Todo, String> todoCrud;
````

Initial insertion is done with:
````
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
````

While retrieval just use `findAll` methods. Because of JPA specificity, it's generally required to annotate your
action with the `@Transactional` annotation. If not, retrieved entities are detached immediately.

## Maven dependencies

Here are the list of the Maven dependencies to use:

````
        <!-- OpenJPA provider and dependencies -->
        <dependency>
            <groupId>org.apache.openjpa</groupId>
            <artifactId>openjpa</artifactId>
            <version>2.3.0</version>
        </dependency>
        <dependency>
            <groupId>org.apache.geronimo.specs</groupId>
            <artifactId>geronimo-servlet_2.5_spec</artifactId>
            <version>1.2</version>
        </dependency>
        <dependency>
            <!--Because ot the split packager from rt.jar, JTA is declared as a library -->
            <groupId>org.apache.geronimo.specs</groupId>
            <artifactId>geronimo-jta_1.1_spec</artifactId>
            <version>1.1.1</version>
        </dependency>
        <dependency>
            <groupId>commons-dbcp</groupId>
            <artifactId>commons-dbcp</artifactId>
            <version>1.4</version>
        </dependency>
        <dependency>
            <groupId>org.apache.servicemix.bundles</groupId>
            <artifactId>org.apache.servicemix.bundles.serp</artifactId>
            <version>1.14.1_1</version>
        </dependency>

        <!-- JPA support and data source -->
        <dependency>
            <groupId>org.wisdom-framework</groupId>
            <artifactId>wisdom-jpa-manager</artifactId>
            <version>0.5-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.wisdom-framework</groupId>
            <artifactId>h2</artifactId>
            <version>1.3.172_1</version>
        </dependency>
        <dependency>
            <groupId>org.wisdom-framework</groupId>
            <artifactId>wisdom-jdbc-datasources</artifactId>
            <version>0.5-SNAPSHOT</version>
        </dependency>
````

## Maven Plugin

In addition to the wisdom-maven-plugin, we need to use the wisdom-openjpa-enhancer-plugin _enhancing_ the entity
classes:

````
<plugin>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>wisdom-openjpa-enhancer-plugin</artifactId>
    <version>0.5-SNAPSHOT</version>
    <configuration>
        <includes>**/models/*.class</includes>
        <addDefaultConstructor>true</addDefaultConstructor>
        <enforcePropertyRestrictions>true</enforcePropertyRestrictions>
    </configuration>
    <executions>
        <execution>
            <id>enhancer</id>
            <phase>process-classes</phase>
            <goals>
                <goal>enhance-entities</goal>
            </goals>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>org.apache.openjpa</groupId>
            <artifactId>openjpa</artifactId>
            <version>2.3.0</version>
        </dependency>
    </dependencies>
</plugin>
````

You need to indicates the packages including entities.

Finally, because of the `javax.transaction` packaged being present in a lightweight form in the JRE, we need to
declare JTA as a library:

````
<plugin>
    <groupId>org.wisdom-framework</groupId>
    <artifactId>wisdom-maven-plugin</artifactId>
    <version>0.7-SNAPSHOT</version>
    <extensions>true</extensions>
    <configuration>
        <skipGoogleClosure>true</skipGoogleClosure>
        <libraries>
            <includes>
                <include>:geronimo-jta_1.1_spec</include>
            </includes>
            <resolveTransitive>true</resolveTransitive>
            <excludeFromApplication>true</excludeFromApplication>
        </libraries>
    </configuration>
</plugin>
````

## Launching the project

The usual:

````
mvn wisdom:run
````



