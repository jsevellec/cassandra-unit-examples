package org.cassandraunit.test.yaml;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.nio.ByteBuffer;
import java.util.List;

import me.prettyprint.cassandra.model.CqlQuery;
import me.prettyprint.cassandra.model.CqlRows;
import me.prettyprint.cassandra.model.IndexedSlicesQuery;
import me.prettyprint.cassandra.serializers.ByteBufferSerializer;
import me.prettyprint.cassandra.serializers.LongSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.beans.OrderedRows;
import me.prettyprint.hector.api.beans.Row;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.query.QueryResult;

import org.cassandraunit.CassandraUnit;
import org.cassandraunit.dataset.yaml.ClassPathYamlDataSet;
import org.junit.Rule;
import org.junit.Test;

public class SecondaryIndexWithRuleTest {

	@Rule
	public CassandraUnit cassandraUnit = new CassandraUnit(new ClassPathYamlDataSet("extendedDataSet.yaml"));

	@Test
	public void shouldQueryUsingSecondaryIndex() {

		IndexedSlicesQuery<String, String, ByteBuffer> query = HFactory.createIndexedSlicesQuery(
				cassandraUnit.keyspace, StringSerializer.get(), StringSerializer.get(), ByteBufferSerializer.get());
		query.setColumnFamily("columnFamilyWithSecondaryIndex");
		query.setRange(null, null, false, 10000);
		query.addEqualsExpression("firstName", StringSerializer.get().toByteBuffer("me"));
		query.addGtExpression("age", LongSerializer.get().toByteBuffer(31L));
		QueryResult<OrderedRows<String, String, ByteBuffer>> result = query.execute();
		List<Row<String, String, ByteBuffer>> rows = result.get().getList();
		assertThatWeGetOneRowWithKey10(rows);
	}

	private void assertThatWeGetOneRowWithKey10(List<Row<String, String, ByteBuffer>> rows) {
		assertThat(rows.size(), is(1));
		assertThat(rows.get(0).getKey(), is("key10"));
	}
}
