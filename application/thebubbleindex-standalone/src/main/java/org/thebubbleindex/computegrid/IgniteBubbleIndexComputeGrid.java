package org.thebubbleindex.computegrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.cache.processor.EntryProcessorResult;
import javax.swing.SwingUtilities;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import org.apache.logging.log4j.Level;
import org.thebubbleindex.driver.BubbleIndex;
import org.thebubbleindex.logging.Logs;
import org.thebubbleindex.swing.GUI;

/**
 * To set up ignite: 1. Configure xml 2. Copy Bubble_Index.jar into the ignite
 * libs folder 3. Make sure JAVA_HOME is set, i.e. set JAVA_HOME=C:\Program
 * Files\Java\jre1.8.0_161 on WindowsOS 4. run multiple servers
 * 
 * @author thebubbleindex
 *
 */
public class IgniteBubbleIndexComputeGrid implements BubbleIndexComputeGrid {
	private final String BUBBLE_INDEX_TASK_CACHE_NAME = "bubbleIndexTaskCache";
	private final String GUI_DISPLAY_TEXT_TOPIC = "textUnOrderedTopic";
	private final String TERMINATION_EVT_TOPIC = "terminationTriggeredTopic";

	private final String address = "127.0.0.1:47500..47509";
	private Map<Integer, BubbleIndex> bubbleIndexTasks = new TreeMap<Integer, BubbleIndex>();
	private final Ignite ignite;
	private final IgniteCache<Integer, BubbleIndex> bubbleIndexTaskCache;
	private final IgniteMessaging textMessageOutputMessaging;
	private final AtomicInteger numberOfLines = new AtomicInteger();

	public IgniteBubbleIndexComputeGrid() {
		final TcpDiscoveryMulticastIpFinder ipFinder = new TcpDiscoveryMulticastIpFinder();
		ipFinder.setAddresses(Collections.singleton(address));

		final TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();

		final IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
		igniteConfiguration.setClientMode(true);
		igniteConfiguration.setPeerClassLoadingEnabled(true);
		igniteConfiguration.setDiscoverySpi(discoverySpi);

		ignite = Ignition.start(igniteConfiguration);
		bubbleIndexTaskCache = ignite.getOrCreateCache(BUBBLE_INDEX_TASK_CACHE_NAME);

		final ClusterGroup remoteNodeGroup = ignite.cluster().forRemotes();
		textMessageOutputMessaging = ignite.message(remoteNodeGroup);

		textMessageOutputMessaging.localListen(GUI_DISPLAY_TEXT_TOPIC, (nodeId, msg) -> {
			displayGridMessageOutput("Received message [msg=" + msg + ", from=" + nodeId + "]");
			return true;
		});
	}

	@Override
	public void addBubbleIndexTask(final Integer key, final BubbleIndex bubbleIndexTask) {
		bubbleIndexTasks.put(key, bubbleIndexTask);
	}

	@Override
	public void deployTasks() {
		bubbleIndexTaskCache.putAll(bubbleIndexTasks);
	}

	@Override
	public List<BubbleIndex> executeBubbleIndexTasks() {
		final List<BubbleIndex> bubbleIndexResults = new ArrayList<BubbleIndex>(bubbleIndexTasks.size());

		int cacheSize = bubbleIndexTaskCache.size(CachePeekMode.PRIMARY);
		Logs.myLogger.log(Level.INFO, "Cache contains {} tasks.", cacheSize);
		displayGridMessageOutput("Cache contains " + cacheSize + " tasks.");

		// first run the tasks
		final Set<Integer> keys = bubbleIndexTasks.keySet();
		final Map<Integer, EntryProcessorResult<BubbleIndex>> results = bubbleIndexTaskCache.invokeAll(keys,
				new IgniteBubbleIndexCacheEntryProcessor(false), GUI_DISPLAY_TEXT_TOPIC, ignite, TERMINATION_EVT_TOPIC,
				BUBBLE_INDEX_TASK_CACHE_NAME);

		for (final EntryProcessorResult<BubbleIndex> bubbleIndex : results.values()) {
			try {
				final BubbleIndex bubbleIndexResult = bubbleIndex.get();
				bubbleIndexResults.add(bubbleIndexResult);
			} catch (final Exception ex) {
				Logs.myLogger.log(Level.ERROR, ex);
			}
		}

		// reset the processTask indicator to make sure this volatile static
		// object is set to true in all nodes
		bubbleIndexTaskCache.invokeAll(keys, new IgniteBubbleIndexCacheEntryProcessor(true));

		bubbleIndexTaskCache.clearAll(keys);
		bubbleIndexTasks.clear();

		cacheSize = bubbleIndexTaskCache.size(CachePeekMode.PRIMARY);
		displayGridMessageOutput("Cleared tasks from cache. Cache now contains " + cacheSize + " tasks.");

		return bubbleIndexResults;
	}

	@Override
	public void shutdownGrid() {
		Ignition.stop(true);
	}

	@Override
	public String about() {
		return "Ignite Version: " + ignite.version().major() + "." + ignite.version().minor() + ", Number of Nodes: "
				+ ignite.cluster().metrics().getTotalNodes();
	}

	@Override
	public void triggerStopAllTasksMessage() {
		final ClusterGroup rmtGrp = ignite.cluster().forRemotes();
		ignite.message(rmtGrp).send(TERMINATION_EVT_TOPIC, "STOP_ALL_TASKS");
	}

	/**
	 * 
	 * @param displayText
	 * @param resetTextArea
	 */
	private void displayGridMessageOutput(final String displayText) {
		Logs.myLogger.log(Level.INFO, displayText);

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (numberOfLines.incrementAndGet() > 200) {
					GUI.OutputText.setText("");
					numberOfLines.set(0);
				}
				GUI.OutputText.append(displayText + "\n");
			}
		});
	}
}