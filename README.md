# CassandraUnit examples

Testing code that talks to Cassandra usually looks like one of these:

- a `@BeforeEach` full of `INSERT` statements, with uuids, timestamps and blobs quoted by hand —
  and quoted wrong the first three times;
- Testcontainers' `withInitScript`: one CQL file, and nothing at all for asserting what the code
  then wrote;
- a mocked `CqlSession`, which tests the mock.

There is a fourth option, and this repo is 63 runnable tests of it.

```java
@RegisterExtension
static CassandraUnitExtension cassandra = new CassandraUnitExtension(
        CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

@Test
@ExpectedCassandraDataSet(value = "rows/expected-widget.yaml", keyspace = KEYSPACE,
        ignoreColumns = "created")
void shipping_a_widget_changes_its_label(CqlSession session) {
    session.execute("update ... set label = 'shipped' where id = ...");
}
```

A YAML file sets the table up, your code runs, a YAML file says what should be true afterwards.
Values convert through the **real column types** read from the live schema, so nothing is quoted by
hand and nothing silently becomes a number — a `text` column holding `"1"` stays the string `"1"`.
When the expectation does not match, the failure names the row and the column that differ and
echoes the `SELECT` it ran, so it pastes into `cqlsh`.

That snippet is
[`ExpectedCassandraDataSetAnnotationTest`](src/test/java/org/cassandraunit/test/assertion/ExpectedCassandraDataSetAnnotationTest.java),
with the `update` shortened. Everything else about it is real, and it runs:

```bash
git clone https://github.com/jsevellec/cassandra-unit-examples.git
cd cassandra-unit-examples && mvn test    # 63 tests, ~1 min, no Docker
```

A real Apache Cassandra 5.0 starts inside the test JVM — three startups for the whole suite, one
shared and one per example that brings its own `cassandra.yaml`. Everything resolves from Maven
Central; nothing has to be built first. **Use JDK 17**, which the build enforces; the reason is
[below](#use-this-in-your-own-project).

If the node is not yours to start — Testcontainers, a shared CI node, Astra, ScyllaDB — the same
fixtures load through a session you supply, and none of the setup below applies. See
[`CqlDataSetExtensionTest`](src/test/java/org/cassandraunit/test/junit5/CqlDataSetExtensionTest.java).

## Find the example you need

| I want to… | file |
|---|---|
| load rows without hand-writing CQL | [`YamlRowDataSetTest`](src/test/java/org/cassandraunit/test/dataset/YamlRowDataSetTest.java) |
| the same fixture in JSON, XML and CSV | [`RowDataSetFormatsTest`](src/test/java/org/cassandraunit/test/dataset/RowDataSetFormatsTest.java) |
| write the fixture in Java, with no file at all | [`BuiltDataSetTest`](src/test/java/org/cassandraunit/test/dataset/BuiltDataSetTest.java) |
| assert what the database holds afterwards | [`ExpectedCassandraDataSetAnnotationTest`](src/test/java/org/cassandraunit/test/assertion/ExpectedCassandraDataSetAnnotationTest.java) |
| …the same, without the annotation | [`ExpectedDataSetFluentTest`](src/test/java/org/cassandraunit/test/assertion/ExpectedDataSetFluentTest.java) |
| assert one value or one row count, fluently | [`CqlAssertionsTest`](src/test/java/org/cassandraunit/test/assertion/CqlAssertionsTest.java) |
| load into a Cassandra I already run | [`CqlDataSetExtensionTest`](src/test/java/org/cassandraunit/test/junit5/CqlDataSetExtensionTest.java) |
| reset between tests without rebuilding the schema | [`IsolationTest`](src/test/java/org/cassandraunit/test/junit5/IsolationTest.java), [`CleanDataBetweenTestsTest`](src/test/java/org/cassandraunit/test/junit5/CleanDataBetweenTestsTest.java) |
| start from the plain embedded-server case | [`CassandraUnitExtensionTest`](src/test/java/org/cassandraunit/test/junit5/CassandraUnitExtensionTest.java) |
| stay on JUnit 4 | [`CQLScriptLoadWithJunitRuleTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithJunitRuleTest.java), [`CQLScriptLoadWithExpectedDataSetRuleTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithExpectedDataSetRuleTest.java) |
| use Spring Test | [`SpringExpectedCassandraDataSetTest`](src/test/java/org/cassandraunit/test/spring/cql/SpringExpectedCassandraDataSetTest.java) |
| bring my own `cassandra.yaml` | [`StartWithCustomCassandraYamlTest`](src/test/java/org/cassandraunit/test/StartWithCustomCassandraYamlTest.java) |

That table is the shortlist. Every file under `src/test` is a working example — including the
narrower ones it leaves out, such as loading a script from disk, driving the server by hand, or
running on a random port — and all of them are in the suite, so whatever is in this repo passes.

## What the examples cover

### Row datasets — YAML, JSON, XML, CSV

`src/test/java/org/cassandraunit/test/dataset/`

A dataset is either a **CQL script** (`.cql`, any statements, creates the schema) or a **row
dataset** (`.yaml`, `.yml`, `.json`, `.xml`, `.csv` — rows and nothing else, needs the schema to
already exist). The format comes from the **file extension**; there is no `type` attribute to keep
in sync with the filename.

| Example | Shows |
|---|---|
| [`YamlRowDataSetTest`](src/test/java/org/cassandraunit/test/dataset/YamlRowDataSetTest.java) | The whole idea: `uuid`, `set`, `map`, `timestamp`, `blob` written in their natural form, plus `null` vs. absent |
| [`RowDataSetFormatsTest`](src/test/java/org/cassandraunit/test/dataset/RowDataSetFormatsTest.java) | The same rows in JSON, XML and CSV, and where CSV differs |

Because the schema is already in the database when rows load, the loader reads each column's real
type from **there**, and the driver's own codecs do the converting.

Two rules are worth knowing before you write a fixture:

| In the file | Meaning | Effect |
|---|---|---|
| the column is **absent** from that row | unset | not in the generated `INSERT` at all — an existing value is left alone |
| the column is present with **`null`** | explicit null | a tombstone, erasing any existing value |

and **CSV cannot express null**: an empty field means unset. A `NULL` sentinel was deliberately not
invented, because a `text` column can legitimately contain the string `NULL`.

A row dataset needs its schema first, and rule and extension both take exactly **one** dataset, so
chain them:

```java
CQLDataSetFactory.fromClassPathAll("mykeyspace", "cql/schema.cql", "rows/widget.yaml")
```

The keyspace is dropped and created **once**, for the chain. Doing it by hand with two `load` calls
is where people drop the keyspace they have just populated.

Quote anything whose YAML meaning differs from its CQL meaning — `"0x0a0b"` for a `blob`, `"1"` for
a number-shaped value in a `text` column. Counters, `USING TTL`, `USING TIMESTAMP` and `DELETE` are
not expressible as rows; use a CQL script for those.

### Datasets written in Java

[`BuiltDataSetTest`](src/test/java/org/cassandraunit/test/dataset/BuiltDataSetTest.java)

New in 5.2.0, and the sixth format: the same rows, with no file.

```java
RowsCQLDataSet fixtures = CQLDataSetFactory.builder("mykeyspace")
        .named("the widget fixture, built in code")
        .table("widget").columns("id", "label", "quantity", "created")
            .row(id, "ordered", 1, Instant.parse("2026-09-19T10:00:00Z"))
        .build();
```

For three rows a file is a file's worth of ceremony, and it puts the fixture somewhere other than
the test that needs it. This is not a second loader: `build()` returns the same `RowsCQLDataSet` a
`.yaml` parses to, so the column types still come from the live schema and the rule, the extensions
and `CQLDataLoader` take it as they take any dataset.

What it can do that a file cannot is take the object you already have — a `UUID`, an `Instant`, a
`Set<String>` — rather than its string form. The rest of the rules are unchanged: a positional
`row(...)` fills the columns declared for the table, `row(Map)` is for a row of a different shape,
an explicit `null` tombstones and an absent column stays unset. Keyspace creation and deletion
default to **off**, because a builder describes rows and never schema.

Being a `RowsCQLDataSet`, the same object can also state the expectation —
`ExpectedDataSetFactory.of(fixtures, "mykeyspace").verify(session)` — which is why the example
declares it as that concrete type rather than as `CQLDataSet`.

A malformed dataset raises `ParseException` **at the call that malformed it**, not at `build()`, so
the stack trace points at the row that is wrong.

### Isolation — what a load clears

[`IsolationTest`](src/test/java/org/cassandraunit/test/junit5/IsolationTest.java)

| `CQLDataLoader.Isolation` | |
|---|---|
| `DATASET` | Honour the dataset's own keyspace flags — normally drop the keyspace and rebuild it. The default, and what every release before 5.1.0 did |
| `TRUNCATE` | Keep the keyspace and its schema; empty every table instead |
| `NONE` | Clear nothing; the keyspace is still selected if it exists |

`TRUNCATE` is far cheaper — the library measures a median of 940ms against 1.6ms for two tables,
and 1640ms against 10.7ms for fifty, because a schema rebuild is not free. It is not the default
because it is **not** a drop-in: it ignores the dataset's creation and deletion flags, so a
per-test dataset that builds its own schema breaks under it. Pair it with a schema loaded once —
`CQLDataLoader.loadIfKeyspaceAbsent`, or `schemaOnce` on `CqlDataSetExtension` — and keep the
per-test dataset to rows.

```java
new CQLDataLoader(session).load(rows, Isolation.TRUNCATE);
new CassandraUnitExtension(dataSet).withIsolation(Isolation.TRUNCATE);
```

`CqlOperations.truncateKeyspace(session, keyspace, excludedTables...)` is the same primitive
without a load, and `CqlOperations.quote(identifier)` quotes an identifier that needs it — both
public API as of 5.1.0. See
[`CleanDataBetweenTestsTest`](src/test/java/org/cassandraunit/test/junit5/CleanDataBetweenTestsTest.java).

### Assertions — what the database holds afterwards

`src/test/java/org/cassandraunit/test/assertion/`

```java
@Test
@ExpectedCassandraDataSet(value = "rows/expected-widget.yaml", keyspace = "mykeyspace")
void shipping_a_widget_marks_it_dispatched() {
    service.ship(widgetId);
}
```

Verified after the test method, and only if it passed. The load rules and the assert rules are
identical, so one file can state the setup and the expectation.

| Example | Shows |
|---|---|
| [`ExpectedCassandraDataSetAnnotationTest`](src/test/java/org/cassandraunit/test/assertion/ExpectedCassandraDataSetAnnotationTest.java) | The annotation, `ignoreColumns`, `MatchMode.CONTAINS`, `Scope.MENTIONED_PARTITIONS`, `checkClusteringOrder` |
| [`ExpectedDataSetFluentTest`](src/test/java/org/cassandraunit/test/assertion/ExpectedDataSetFluentTest.java) | `ExpectedDataSetFactory` without the annotation, and reading `DataSetMismatchError.getDifferences()` |
| [`CQLScriptLoadWithExpectedDataSetRuleTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithExpectedDataSetRuleTest.java) | JUnit 4, as a chained rule |
| [`SpringExpectedCassandraDataSetTest`](src/test/java/org/cassandraunit/test/spring/cql/SpringExpectedCassandraDataSetTest.java) | Spring, where the listeners check it before dropping the keyspace |

**Strict by default**: a table the file names must hold exactly the rows it lists; a table it does
not name is not asserted at all. That is stricter than DBUnit's usual default, deliberately —
Cassandra is upsert-only and has no unique constraints, so the bug worth catching is a write
landing in the wrong partition, which produces an *extra* row that a contains-style assertion never
sees. `containing()` relaxes it.

Rows are matched on the primary key, only the mentioned columns are selected, and the failure
message renders values as CQL literals with the `SELECT` that was run, so it pastes into `cqlsh`. A
mismatch is a `DataSetMismatchError` (an `AssertionError`) — the code under test is wrong. An
unusable expectation is a `ParseException` — the test is wrong. Engines report the first as a
failure and the second as an error, which is the right way round.

Wiring, by integration: `CassandraUnitExtension` and the Spring listeners pick the annotation up
with nothing added; against a session you supply, register `ExpectedCassandraDataSetExtension`; on
JUnit 4, chain `ExpectedCassandraDataSetRule`.

### Fluent assertions, for one value

[`CqlAssertionsTest`](src/test/java/org/cassandraunit/test/assertion/CqlAssertionsTest.java)

Also new in 5.2.0, and the companion to the annotation rather than a replacement for it. A dataset
file is the right tool for *these are all the rows this table should hold*; this is the right tool
for one value or one row count, where a file would be out of proportion.

```java
import static org.cassandraunit.assertion.CqlAssertions.assertThat;

assertThat(session).keyspace("mykeyspace")
        .table("widget")
            .hasRowCount(3)
            .row("id", widgetId)
                .hasValue("label", "ordered")
                .hasNull("created");
```

Both halves agree on what equal means — they share one value comparison — so an empty `set` reading
back as null, or `1.50` against `1.5`, behaves identically whichever you use, and expected values
take the same forms a row dataset takes (`hasValue("quantity", 1)` against a `bigint`, a `uuid` as
its string form). A row is addressed by its **whole** primary key, so a table with a clustering
column needs the map form.

`assertThat(row)` and `assertThat(resultSet)` cover what you fetched yourself, and
`keyspace(...).matches(expectedDataSet)` hands a whole file-shaped expectation back to the chain.
Needs `assertj-core`, which is `optional` in cassandra-unit; every assert type extends AssertJ's
`AbstractAssert`, so `as()`, `satisfies()` and `SoftAssertions` work as usual.

### Fixtures without the embedded server

[`CqlDataSetExtensionTest`](src/test/java/org/cassandraunit/test/junit5/CqlDataSetExtensionTest.java)

`CqlDataSetExtension` starts nothing. You give it a session — a Testcontainers container, a node CI
already runs, a managed service — and it loads the same datasets through that:

```java
@RegisterExtension
static final CqlDataSetExtension fixtures = CqlDataSetExtension
        .using(() -> CqlSession.builder()
                .addContactPoint(cassandra.getContactPoint())
                .withLocalDatacenter(cassandra.getLocalDatacenter())
                .build())
        .closingSession()
        .schemaOnce(CQLDataSetFactory.fromClassPath("cql/schema.cql", "mykeyspace"))
        .rowsPerTest(CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, "mykeyspace"))
        .build();
```

Build the session **inside the lambda**: Jupiter runs declaratively registered extensions —
`@Testcontainers` among them — before `@RegisterExtension` ones. And the extension never closes a
session it did not create; `closingSession()` opts in.

The fixture layer is its own artifact, with no `cassandra-all`, no jamm agent and no JDK ceiling:

```xml
<dependency>
    <groupId>org.cassandraunit</groupId>
    <artifactId>cassandra-unit-dataset</artifactId>
    <version>5.2.0</version>
    <scope>test</scope>
</dependency>
```

None of the surefire `argLine` above is needed for it — that block is the price of the embedded
server, not of the fixtures. The example here supplies the embedded server's session so that
`mvn test` needs no Docker; against a container only the supplier changes.

### JUnit 5 with the embedded server

`src/test/java/org/cassandraunit/test/junit5/`

| Example | Shows |
|---|---|
| [`CassandraUnitExtensionTest`](src/test/java/org/cassandraunit/test/junit5/CassandraUnitExtensionTest.java) | The normal case: `@RegisterExtension` + a `CqlSession` injected straight into the test method |
| [`CqlDataSetWithoutKeyspaceCreationTest`](src/test/java/org/cassandraunit/test/junit5/CqlDataSetWithoutKeyspaceCreationTest.java) | When the script owns its `CREATE KEYSPACE` |
| [`EmbeddedCassandraManualStartTest`](src/test/java/org/cassandraunit/test/junit5/EmbeddedCassandraManualStartTest.java) | No extension: drive `EmbeddedCassandraServerHelper` and `CQLDataLoader` yourself |
| [`MultipleDataSetsTest`](src/test/java/org/cassandraunit/test/junit5/MultipleDataSetsTest.java) | Schema and data in separate scripts, and what the keyspace flags mean |
| [`CleanDataBetweenTestsTest`](src/test/java/org/cassandraunit/test/junit5/CleanDataBetweenTestsTest.java) | Resetting state without restarting Cassandra: `CqlOperations`, `truncateKeyspace`, `cleanDataEmbeddedCassandra` |
| [`FileCqlDataSetTest`](src/test/java/org/cassandraunit/test/junit5/FileCqlDataSetTest.java) | Loading a script from disk rather than the classpath |

`CassandraUnitExtension` has no no-arg constructor — the dataset comes in through it — so it is used
with `@RegisterExtension` on a `static` field, never `@ExtendWith`.

### JUnit 4

`src/test/java/org/cassandraunit/test/cql/` — still fully supported, kept as the reference for
projects that have not migrated. Needs `junit-vintage-engine` to run alongside Jupiter.

| Example | Shows |
|---|---|
| [`CQLScriptLoadWithJunitRuleTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithJunitRuleTest.java) | The `@Rule` and its public `session` field |
| [`CQLScriptLoadWithAbstractTestCaseTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithAbstractTestCaseTest.java) | `AbstractCassandraUnit4CQLTestCase`, which also cleans up after each method |
| [`CQLScriptLoadWithoutKeyspaceCreationTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithoutKeyspaceCreationTest.java) | `keyspaceCreation = false` |
| [`CQLScriptLoadWithNativeApproachTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithNativeApproachTest.java) | Manual start + load |
| [`CQLScriptLoadWithExpectedDataSetRuleTest`](src/test/java/org/cassandraunit/test/cql/CQLScriptLoadWithExpectedDataSetRuleTest.java) | `@ExpectedCassandraDataSet` through a `RuleChain` |

### Spring

`src/test/java/org/cassandraunit/test/spring/cql/`

| Example | Shows |
|---|---|
| [`SpringCQLScriptLoadTest`](src/test/java/org/cassandraunit/test/spring/cql/SpringCQLScriptLoadTest.java) | `@ExtendWith(SpringExtension.class)` + `CassandraUnitTestExecutionListener`, reloading per test method |
| [`SpringCassandraUnitAnnotationTest`](src/test/java/org/cassandraunit/test/spring/cql/SpringCassandraUnitAnnotationTest.java) | The composed `@CassandraUnit` annotation and dataset-by-convention |
| [`SpringExpectedCassandraDataSetTest`](src/test/java/org/cassandraunit/test/spring/cql/SpringExpectedCassandraDataSetTest.java) | A row dataset as the fixture, and `@ExpectedCassandraDataSet` as the assertion |

There is no cassandra-unit-specific JUnit 5 extension for Spring: use Spring's own
`SpringExtension` and add cassandra-unit as a `TestExecutionListener`. **`@EmbeddedCassandra` is
mandatory** — the listener does a `requireNonNull` on it, so `@CassandraDataSet` alone fails with
an NPE. `@CassandraDataSet` takes several locations and any supported extension, so schema and rows
can be listed together; the first one drops and creates the keyspace.

### Custom server configuration

`src/test/java/org/cassandraunit/test/`

| Example | Shows |
|---|---|
| [`StartWithCustomCassandraYamlTest`](src/test/java/org/cassandraunit/test/StartWithCustomCassandraYamlTest.java) | Your own `cassandra.yaml` — see [`another-cassandra.yaml`](src/test/resources/another-cassandra.yaml) |
| [`StartWithRandomPortTest`](src/test/java/org/cassandraunit/test/StartWithRandomPortTest.java) | `CASSANDRA_RNDPORT_YML_FILE`, so parallel builds on one machine cannot collide |

Both run in their own JVM. **One Cassandra per JVM is a hard constraint**: `DatabaseDescriptor`,
`Schema` and `StorageService` hold static state that cannot be reset in-process, so a JVM is pinned
to the first configuration it starts. See the `isolated-config-tests` surefire execution in
[`pom.xml`](pom.xml) for how the suite is split. For the same reason every example class here owns
its own keyspace: they share one embedded node, and a `DATASET` load drops the keyspace it names.

Write your own yaml by starting from the bundled one — Cassandra 5 **rejects unknown properties**,
so any pre-4.x file (`start_rpc`, `rpc_port`, `thrift_*`, the `*_in_ms` spellings) will not load at
all:

```bash
unzip -p ~/.m2/repository/org/cassandraunit/cassandra-unit/5.2.0/cassandra-unit-5.2.0.jar cu-cassandra.yaml
```

## Use this in your own project

Two dependencies — note there is **no explicit driver dependency**. Since 5.0.0 the driver is a
required dependency of cassandra-unit, so it arrives transitively. Its coordinates also moved:
`com.datastax.oss:java-driver-core` is frozen at 4.17.0 and the driver now releases as
`org.apache.cassandra:java-driver-core`. The Java packages are unchanged
(`com.datastax.oss.driver.*`), so declaring the old coordinates puts two jars with the same
packages on your classpath for no benefit.

```xml
<dependency>
    <groupId>org.cassandraunit</groupId>
    <artifactId>cassandra-unit</artifactId>
    <version>5.2.0</version>
    <scope>test</scope>
</dependency>
<!-- only if you use the Spring integration -->
<dependency>
    <groupId>org.cassandraunit</groupId>
    <artifactId>cassandra-unit-spring</artifactId>
    <version>5.2.0</version>
    <scope>test</scope>
</dependency>
```

JUnit is **optional** in cassandra-unit — declare whichever platform you use (`junit-jupiter`, or
`junit` 4 plus `junit-vintage-engine` for the `@Rule`). Spring is `provided` in
`cassandra-unit-spring`, and provided scope is not transitive, so declare `spring-test` and
`spring-context` yourself. CSV datasets need
`com.fasterxml.jackson.dataformat:jackson-dataformat-csv`, which is `optional` in cassandra-unit
and therefore not transitive; YAML, JSON and XML need nothing. The fluent `CqlAssertions` are
`optional` in the same way and need `assertj-core` — without it, touching that class raises
`NoClassDefFoundError: org/assertj/core/api/AbstractAssert`. Nothing else in the library requires
either.

### Pin jackson — this one is not optional

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>2.22.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

cassandra-unit 5.2.0 otherwise leaves a consumer with a mixed family: `jackson-databind` 2.22.1
arrives through `cassandra-unit-dataset`, while `jackson-core` and `jackson-annotations` come from
`cassandra-all` at 2.19.2 and win on declaration order. The embedded daemon then dies during commit
log initialisation with `NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonSerializeAs`,
which surfaces as **"Cassandra daemon did not start within timeout"** — a message pointing nowhere
near the cause. The BOM settles it, and the CSV dependency then needs no version of its own.

<details>
<summary><b>The surefire argLine — mandatory for the embedded server</b>, or the fork dies with "The forked VM terminated without properly saying goodbye"</summary>

Cassandra 5.0 reaches deep into the JDK, so the embedded daemon needs the same JVM flags a real
node gets. Without them surefire dies with `The forked VM terminated without properly saying
goodbye`, which tells you nothing about the cause. The `jamm` agent path is resolved by
`maven-dependency-plugin`, so no version is baked into a path — jamm arrives transitively with
`cassandra-all`. If you only use `cassandra-unit-dataset` against your own Cassandra, you need none
of it.

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

</details>

<details>
<summary><b>Why JDK 17, exactly</b></summary>

The embedded daemon runs *inside the build JVM*, Cassandra 5.0 supports only JDK 11 and 17, and
spring-test 6.2 needs 17 — and on JDK 24+ Cassandra's `ThreadAwareSecurityManager` calls the
now-removed `System::setSecurityManager` and throws. This build enforces `[17,18)` so you get a
sentence instead of a stack trace.

Check with `mvn -v`, not `java -version` — a `jenv` shim overrides `JAVA_HOME` for Maven only.
There is a `.java-version` file here for that reason.

The bound belongs to the **embedded server**. `cassandra-unit-dataset`, the fixture layer on its
own, runs on 17+ with no ceiling — see
[Fixtures without the embedded server](#fixtures-without-the-embedded-server).

</details>

## Version compatibility

From 5.0.0 the version number leads with the **embedded Apache Cassandra major**; the minor and
patch are cassandra-unit's own. The driver version never appears in it.

| artifact | Embedded Cassandra | CQL driver | JDK |
|---|---|---|---|
| `cassandra-unit-dataset` | none — you supply the session | `org.apache.cassandra:java-driver-core` 4.19.3 | 17+ |
| `cassandra-unit` | 5.0.8 | same | 17 only |
| `cassandra-unit-spring` | via `cassandra-unit` | same | 17 only |

`4.3.1.0` is the trap in the history: it tracked the *driver*, and embeds Cassandra **3.11.5**, not
4. The driver is not managed in cassandra-unit's `dependencyManagement`, so you can pin your own
4.x in yours.

## Coming from CassandraUnit 3.x or 4.x?

| Then | Now |
|---|---|
| `com.datastax.oss:java-driver-core` | `org.apache.cassandra:java-driver-core`, required rather than optional. Java packages unchanged |
| XML / JSON / YAML datasets, `DataSetFileExtensionEnum` | Row datasets in YAML, JSON, XML and CSV are back in 5.1.0 — but they describe **CQL tables**, share nothing with the 4.x Thrift formats, and an old dataset will not load. No `type` attribute: the extension decides |
| Thrift, Hector, `DataLoader` | Gone |
| `@CassandraDataSet(type = ...)` | Gone |
| `getRpcPort()` | `getNativeTransportPort()` |
| `DEFAULT_TMP_DIR` = `target/embeddedCassandra` | `${java.io.tmpdir}/cassandra-unit`, and it now really relocates data, commitlog, hints and caches |
| `cu-loader` / `cu-starter` CLI, `cassandra-unit-shaded` | Gone |
| JUnit 4 and Hamcrest on your classpath whether you wanted them or not | Both optional — declare what you use |
| JUnit 5 | `CassandraUnitExtension` (5.0.0), and `CqlDataSetExtension` for a session you own (5.1.0) |
| `readTimeoutMillis` was stored and ignored | Now actually applied, so queries can time out. Also `setRequestTimeout(Duration)` |
| Nothing asserted the end state | `@ExpectedCassandraDataSet` (5.1.0), and fluent `CqlAssertions` (5.2.0) |
| A fixture had to be a file | It can be a Java builder instead — `CQLDataSetFactory.builder(...)` (5.2.0) |

## Notes

- `[ERROR] 'dependencies.dependency.systemPath' ... ${jmc5.path}` during the build is noise from
  Apache Cassandra's own parent pom, which declares system-scoped JMC and VisualVM artifacts with
  unresolved properties. Harmless, and only upstream Cassandra can fix it.
- The driver refreshes schema metadata asynchronously. Right after startup,
  `session.getMetadata().getKeyspaces()` is legitimately empty — query the server instead of
  asserting on it.
- Cassandra logs a lot. [`logback-test.xml`](src/test/resources/logback-test.xml) holds it at
  `WARN`; raise `org.apache.cassandra` to `DEBUG` when a startup goes wrong.
- "Cassandra daemon did not start within timeout" is a symptom, not a cause. Two things produce it:
  something else already listening on 9042 — another build of your own, most often — or the jackson
  mismatch described under [Pin jackson](#pin-jackson--this-one-is-not-optional). The daemon's real
  error is in the log above the timeout.

## License

CassandraUnit is [MIT](https://opensource.org/licenses/MIT) as of 5.0.0.
