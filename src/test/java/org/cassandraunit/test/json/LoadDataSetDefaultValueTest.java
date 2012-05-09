package org.cassandraunit.test.json;

import java.util.List;

import me.prettyprint.cassandra.serializers.BytesArraySerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.SliceQuery;

import org.apache.commons.codec.binary.Hex;
import org.cassandraunit.DataLoader;
import org.cassandraunit.dataset.DataSet;
import org.cassandraunit.dataset.json.ClassPathJsonDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Test;

/**
 * 
 * @author Jeremy Sevellec
 * 
 */
public class LoadDataSetDefaultValueTest {

	@Test
	public void shouldHaveLoadASimpleDataSet() throws Exception {

		DataSet dataSet = new ClassPathJsonDataSet("dataSetDefaultValues.json");
		String clusterName = "TestCluster";
		String host = "localhost:9171";
		EmbeddedCassandraServerHelper.startEmbeddedCassandra();

		/* create structure and load data */
		DataLoader dataLoader = new DataLoader(clusterName, host);
		dataLoader.load(dataSet);

		/* get hector client object to query data in your test */
		Cluster cluster = HFactory.getOrCreateCluster(clusterName, host);
		Keyspace keyspace = HFactory.createKeyspace("beautifulKeyspaceName", cluster);

		SliceQuery<byte[], byte[], byte[]> sq = HFactory.createSliceQuery(keyspace, BytesArraySerializer.get(),
				BytesArraySerializer.get(), BytesArraySerializer.get());

		sq.setKey(Hex.decodeHex("01".toCharArray()));
		sq.setColumnFamily("columnFamily1");
		sq.setColumnNames(Hex.decodeHex("02".toCharArray()));

		QueryResult<ColumnSlice<byte[], byte[]>> result = sq.execute();
		List<HColumn<byte[], byte[]>> columns = result.get().getColumns();
		System.out.println("slice:" + result.get() + " slice.length:" + columns.size());
	}
}
