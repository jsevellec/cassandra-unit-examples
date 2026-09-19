package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JUnit 4 way: a {@code @Rule} that starts the embedded server and loads the dataset.
 *
 * <p>Still fully supported in 5.0.0 - it is kept here as the reference for projects that have
 * not moved to JUnit 5. New code should prefer
 * {@link org.cassandraunit.test.junit5.CqlDataSetExtensionTest}.
 *
 * <p>Running this alongside the Jupiter examples needs {@code junit-vintage-engine} on the
 * test classpath; see the pom.
 */
public class CQLScriptLoadWithJunitRuleTest {

    @Rule
    public CassandraCQLUnit cassandraCQLUnit =
            new CassandraCQLUnit(new ClassPathCQLDataSet("simple.cql", "keyspaceNameToCreate"));

    @Test
    public void should_have_started_and_execute_cql_script() {
        Row row = cassandraCQLUnit.session.execute("select value from mytable where id = 'myKey01'").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("value")).isEqualTo("myValue01");
    }
}
