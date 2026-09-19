# CassandraUnit examples

Runnable examples for [CassandraUnit](https://github.com/jsevellec/cassandra-unit) 5.0.0 —
embedded Apache Cassandra 5.0 for your tests, with CQL datasets loaded for you.

Every file under `src/test` is a working example. Clone, `mvn test`, read the one closest to
what you need.

## Requirements

| | |
|---|---|
| JDK | **17, exactly** |
| Maven | 3.9+ |
| cassandra-unit | 5.0.0-SNAPSHOT |

The JDK bound is not caution. The embedded daemon runs *inside the build JVM*, Cassandra 5.0
supports only JDK 11 and 17, and spring-test 6.2 needs 17 — and on JDK 24+ Cassandra's
`ThreadAwareSecurityManager` calls the now-removed `System::setSecurityManager` and throws.
The build enforces `[17,18)` so you get a sentence instead of a stack trace.

Check with **`mvn -v`**, not `java -version` — a `jenv` shim overrides `JAVA_HOME` for Maven
only. There is a `.java-version` file here for that reason.

## Running the examples

`5.0.0-SNAPSHOT` is not on Maven Central yet, so build the library first:

```bash
git clone https://github.com/jsevellec/cassandra-unit.git
cd cassandra-unit && mvn install -DskipTests
```

Then:

```bash
cd cassandra-unit-examples
mvn clean test
```

22 tests, three Cassandra startups, around 40 seconds.

## Setting this up in your own project

> The coordinates below show `5.0.0-SNAPSHOT`, which is what exists today and resolves only
> from a local `mvn install` of the library (or the
> [Central snapshots repository](https://central.sonatype.com/repository/maven-snapshots/)).
> Switch to `5.0.0` once it is published to Maven Central.

Two dependencies — note there is **no explicit driver dependency**. Since 5.0.0 the driver is
a required dependency of cassandra-unit, so it arrives transitively. Its coordinates also
moved: `com.datastax.oss:java-driver-core` is frozen at 4.17.0 and the driver now releases as
`org.apache.cassandra:java-driver-core`. The Java packages are unchanged
(`com.datastax.oss.driver.*`), so declaring the old coordinates puts two jars with the same
packages on your classpath for no benefit.

```xml
<dependency>
    <groupId>org.cassandraunit</groupId>
    <artifactId>cassandra-unit</artifactId>
    <version>5.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
<!-- only if you use the Spring integration -->
<dependency>
    <groupId>org.cassandraunit</groupId>
    <artifactId>cassandra-unit-spring</artifactId>
    <version>5.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

JUnit is **optional** in cassandra-unit 5.0.0 — declare whichever platform you use
(`junit-jupiter`, or `junit` 4 plus `junit-vintage-engine` for the `@Rule`). Spring is
`provided` in `cassandra-unit-spring`, and provided scope is not transitive, so declare
`spring-test` and `spring-context` yourself.

### The surefire argLine — mandatory

Cassandra 5.0 reaches deep into the JDK, so the embedded daemon needs the same JVM flags a
real node gets. **Every consumer must copy this block.** Without it surefire dies with

```
The forked VM terminated without properly saying goodbye
```

which tells you nothing about the cause. The `jamm` agent path is resolved by
`maven-dependency-plugin`, so no version is baked into a path — jamm arrives transitively
with `cassandra-all`.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.8.1</version>
    <executions>
        <execution>
            <phase>initialize</phase>
            <goals><goal>properties</goal></goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <argLine>
            -javaagent:${com.github.jbellis:jamm:jar}
            -Djdk.attach.allowAttachSelf=true
            -Dio.netty.tryReflectionSetAccessible=true
            --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
            --add-exports java.management.rmi/com.sun.jmx.remote.internal.rmi=ALL-UNNAMED
            --add-exports java.management/com.sun.jmx.remote.security=ALL-UNNAMED
            --add-exports java.rmi/sun.rmi.registry=ALL-UNNAMED
            --add-exports java.rmi/sun.rmi.server=ALL-UNNAMED
            --add-exports java.sql/java.sql=ALL-UNNAMED
            --add-exports java.base/java.lang.ref=ALL-UNNAMED
            --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED
            --add-opens java.base/java.lang.module=ALL-UNNAMED
            --add-opens java.base/jdk.internal.loader=ALL-UNNAMED
            --add-opens java.base/jdk.internal.ref=ALL-UNNAMED
            --add-opens java.base/jdk.internal.reflect=ALL-UNNAMED
            --add-opens java.base/jdk.internal.math=ALL-UNNAMED
            --add-opens java.base/jdk.internal.module=ALL-UNNAMED
            --add-opens java.base/jdk.internal.util.jar=ALL-UNNAMED
            --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED
            --add-opens java.base/sun.nio.ch=ALL-UNNAMED
            --add-opens java.base/java.io=ALL-UNNAMED
            --add-opens java.base/java.lang.reflect=ALL-UNNAMED
            --add-opens java.base/java.lang=ALL-UNNAMED
            --add-opens java.base/java.util=ALL-UNNAMED
            --add-opens java.base/java.nio=ALL-UNNAMED
        </argLine>
    </configuration>
</plugin>
```

## The examples

### JUnit 5 — start here

`src/test/java/org/cassandraunit/test/junit5/`

| Example | Shows |
|---|---|
| [`CqlDataSetExtensionTest`](src/test/java/org/cassandraunit/test/junit5/CqlDataSetExtensionTest.java) | The normal case: `@RegisterExtension` + a `CqlSession` injected straight into the test method |
| [`CqlDataSetWithoutKeyspaceCreationTest`](src/test/java/org/cassandraunit/test/junit5/CqlDataSetWithoutKeyspaceCreationTest.java) | When the script owns its `CREATE KEYSPACE` |
| [`EmbeddedCassandraManualStartTest`](src/test/java/org/cassandraunit/test/junit5/EmbeddedCassandraManualStartTest.java) | No extension: drive `EmbeddedCassandraServerHelper` and `CQLDataLoader` yourself |
| [`MultipleDataSetsTest`](src/test/java/org/cassandraunit/test/junit5/MultipleDataSetsTest.java) | Schema and data in separate scripts, and what the keyspace flags mean |
| [`CleanDataBetweenTestsTest`](src/test/java/org/cassandraunit/test/junit5/CleanDataBetweenTestsTest.java) | Resetting state without restarting Cassandra: `CqlOperations`, `cleanDataEmbeddedCassandra`, `nonSystemKeyspaces` |
| [`FileCqlDataSetTest`](src/test/java/org/cassandraunit/test/junit5/FileCqlDataSetTest.java) | Loading a script from disk rather than the classpath |

`CassandraUnitExtension` has no no-arg constructor — the dataset comes in through it — so it
is used with `@RegisterExtension` on a `static` field, never `@ExtendWith`.

### JUnit 4

`src/test/java/org/cassandraunit/test/cql/` — still fully supported, kept as the reference for
projects that have not migrated. Needs `junit-vintage-engine` to run alongside Jupiter.

| Example | Shows |
|---|---|
| [`CQLScriptLoadWithJunitRuleTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithJunitRuleTest.java) | The `@Rule` and its public `session` field |
| [`CQLScriptLoadWithAbstractTestCaseTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithAbstractTestCaseTest.java) | `AbstractCassandraUnit4CQLTestCase`, which also cleans up after each method |
| [`CQLScriptLoadWithoutKeyspaceCreationTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithoutKeyspaceCreationTest.java) | `keyspaceCreation = false` |
| [`CQLScriptLoadWithNativeApproachTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithNativeApproachTest.java) | Manual start + load |

### Spring

`src/test/java/org/cassandraunit/test/spring/cql/`

| Example | Shows |
|---|---|
| [`SpringCQLScriptLoadTest`](src/test/java/org/cassandraunit/test/spring/cql/SpringCQLScriptLoadTest.java) | `@ExtendWith(SpringExtension.class)` + `CassandraUnitTestExecutionListener`, reloading per test method |
| [`SpringCassandraUnitAnnotationTest`](src/test/java/org/cassandraunit/test/spring/cql/SpringCassandraUnitAnnotationTest.java) | The composed `@CassandraUnit` annotation and dataset-by-convention |

There is no cassandra-unit-specific JUnit 5 extension for Spring: use Spring's own
`SpringExtension` and add cassandra-unit as a `TestExecutionListener`. **`@EmbeddedCassandra`
is mandatory** — the listener does a `requireNonNull` on it, so `@CassandraDataSet` alone
fails with an NPE.

### Custom server configuration

`src/test/java/org/cassandraunit/test/`

| Example | Shows |
|---|---|
| [`StartWithCustomCassandraYamlTest`](src/test/java/org/cassandraunit/test/StartWithCustomCassandraYamlTest.java) | Your own `cassandra.yaml` — see [`another-cassandra.yaml`](src/test/resources/another-cassandra.yaml) |
| [`StartWithRandomPortTest`](src/test/java/org/cassandraunit/test/StartWithRandomPortTest.java) | `CASSANDRA_RNDPORT_YML_FILE`, so parallel builds on one machine cannot collide |

Both run in their own JVM. **One Cassandra per JVM is a hard constraint**:
`DatabaseDescriptor`, `Schema` and `StorageService` hold static state that cannot be reset
in-process, so a JVM is pinned to the first configuration it starts. See the
`isolated-config-tests` surefire execution in [`pom.xml`](pom.xml) for how the suite is split.

Write your own yaml by starting from the bundled one — Cassandra 5 **rejects unknown
properties**, so any pre-4.x file (`start_rpc`, `rpc_port`, `thrift_*`, the `*_in_ms`
spellings) will not load at all:

```bash
unzip -p ~/.m2/repository/org/cassandraunit/cassandra-unit/5.0.0-SNAPSHOT/cassandra-unit-5.0.0-SNAPSHOT.jar cu-cassandra.yaml
```

## Coming from CassandraUnit 3.x?

| Then | Now |
|---|---|
| `com.datastax.oss:java-driver-core` | `org.apache.cassandra:java-driver-core`, required rather than optional. Java packages unchanged |
| XML / JSON / YAML datasets, `DataSetFileExtensionEnum` | Gone. **CQL datasets only** |
| Thrift, Hector, `DataLoader` | Gone |
| `@CassandraDataSet(type = ...)` | Gone |
| `getRpcPort()` | `getNativeTransportPort()` |
| `DEFAULT_TMP_DIR` = `target/embeddedCassandra` | `${java.io.tmpdir}/cassandra-unit`, and it now really relocates data, commitlog, hints and caches |
| `cu-loader` / `cu-starter` CLI, `cassandra-unit-shaded` | Gone |
| JUnit 4 and Hamcrest on your classpath whether you wanted them or not | Both optional — declare what you use |
| JUnit 5 | New: `CassandraUnitExtension` |
| `readTimeoutMillis` was stored and ignored | Now actually applied, so queries can time out. Also `setRequestTimeout(Duration)` |

## Notes

- `[ERROR] 'dependencies.dependency.systemPath' ... ${jmc5.path}` during the build is noise
  from Apache Cassandra's own parent pom, which declares system-scoped JMC and VisualVM
  artifacts with unresolved properties. Harmless, and only upstream Cassandra can fix it.
- The driver refreshes schema metadata asynchronously. Right after startup,
  `session.getMetadata().getKeyspaces()` is legitimately empty — query the server instead of
  asserting on it.
- Cassandra logs a lot. [`logback-test.xml`](src/test/resources/logback-test.xml) holds it at
  `WARN`; raise `org.apache.cassandra` to `DEBUG` when a startup goes wrong.

## License

Same as CassandraUnit: [LGPL v3](http://www.gnu.org/licenses/lgpl-3.0-standalone.html).
