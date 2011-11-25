package org.cassandraunit.test.xml;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.SliceQuery;

import org.cassandraunit.DataLoader;
import org.cassandraunit.dataset.xml.ClassPathXmlDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Jeremy Sevellec
 * 
 */
public class ManuallyStartAndLoadSimpleXmlDataSetTest {

	@Before
	public void before() throws Exception {

		/* start an embedded cassandra */
		EmbeddedCassandraServerHelper.startEmbeddedCassandra();

		/* load data */
		DataLoader dataLoader = new DataLoader("TestCluster", "localhost:9171");
		dataLoader.load(new ClassPathXmlDataSet("simpleDataSet.xml"));
	}

	@Test
	public void shouldWork() throws Exception {
		Keyspace keyspace = getBeautifulKeySpace();

		SliceQuery<String, String, String> query = HFactory.createSliceQuery(keyspace, StringSerializer.get(),
				StringSerializer.get(), StringSerializer.get());
		query.setColumnFamily("beautifulColumnFamilyName").setKey("key10");
		query.setRange(null, null, false, 1000);
		QueryResult<ColumnSlice<String, String>> result = query.execute();

		assertThat(result.get().getColumnByName("name11").getValue(), is("value11"));
		assertThat(result.get().getColumnByName("name12").getValue(), is("value12"));

	}

	private Keyspace getBeautifulKeySpace() {
		String clusterName = "TestCluster";
		String host = "localhost:9171";
		Cluster cluster = HFactory.getOrCreateCluster(clusterName, host);
		Keyspace keyspace = HFactory.createKeyspace("beautifulKeyspaceName", cluster);
		return keyspace;
	}

}
