package org.cassandraunit.test.spring.boot;

import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.spring.CassandraDataSet;
import org.cassandraunit.spring.CassandraUnitTestExecutionListener;
import org.cassandraunit.spring.EmbeddedCassandra;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code exposeProperties = false}, when the test sets {@code spring.cassandra.*} itself and wants
 * its own values to win. Opting out silences the publishing, not the server.
 *
 * <p>Deliberately not a {@code @SpringBootTest} and defines no {@code CqlSession} bean: with the
 * properties withheld, Boot's session would chase 9042 and fail the refresh instead of asserting
 * anything.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestExecutionListeners(value = CassandraUnitTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@EmbeddedCassandra(exposeProperties = false)
@CassandraDataSet(value = {"cql/widgetSchema.cql", "rows/widget.yaml"},
        keyspace = ExposedPropertiesOptOutTest.KEYSPACE)
@TestPropertySource(properties = {
        "spring.cassandra.port=9042",
        "spring.cassandra.contact-points=example.invalid"})
class ExposedPropertiesOptOutTest {

    static final String KEYSPACE = "springbootoptoutkeyspace";

    /** Hardcoded because the constant is package-private in the library. */
    private static final String PUBLISHED_SOURCE = "cassandra-unit-embedded";

    @Autowired
    private ConfigurableEnvironment environment;

    @Test
    void the_values_this_test_set_are_left_alone() {
        assertThat(environment.getProperty("spring.cassandra.port", Integer.class))
                .isEqualTo(9042);
        assertThat(environment.getProperty("spring.cassandra.contact-points"))
                .isEqualTo("example.invalid");
    }

    @Test
    void the_embedded_node_started_anyway() {
        assertThat(EmbeddedCassandraServerHelper.getNativeTransportPort()).isPositive();

        Row row = EmbeddedCassandraServerHelper.getSession()
                .execute("select label from " + KEYSPACE + ".widget"
                        + " where id = 11111111-1111-1111-1111-111111111111")
                .one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("1");
    }

    @Test
    void no_property_source_was_published() {
        assertThat(environment.getPropertySources().contains(PUBLISHED_SOURCE)).isFalse();
    }

    @Configuration
    static class Config {
    }
}
