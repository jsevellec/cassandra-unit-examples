package org.cassandraunit.test.spring.cql;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.spring.CassandraUnit;
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @CassandraUnit} is a composed {@code @EmbeddedCassandra @CassandraDataSet}, both at
 * their defaults: keyspace {@code cassandra_unit_keyspace}, and the dataset found by convention
 * at {@code <package>/<SimpleName>-dataset.cql} on the classpath.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener.class)
@CassandraUnit
class SpringCassandraUnitAnnotationTest {

    @Configuration
    static class Config {
    }

    @Test
    void should_load_the_dataset_found_by_convention() {
        CqlSession session = EmbeddedCassandraServerHelper.getSession();

        Row row = session
                .execute("select label from cassandra_unit_keyspace.widget where id = 1").one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("hello");
    }
}
