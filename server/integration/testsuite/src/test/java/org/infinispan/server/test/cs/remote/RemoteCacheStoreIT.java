package org.infinispan.server.test.cs.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.arquillian.core.RunningServer;
import org.infinispan.arquillian.core.WithRunningServer;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.junit.Cleanup;
import org.infinispan.server.test.category.CacheStore;
import org.infinispan.server.test.client.memcached.MemcachedClient;
import org.infinispan.server.test.util.ClassRemoteCacheManager;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Tests remote cache store under the following circumstances:
 * <p/>
 * passivation == true --cache entries should get to the remote cache store only when evicted
 * preload == false --after server restart, entries should be be preloaded to the cache
 * purge == false --all entries should remain in the cache store after server restart
 * (must be false so that we can test preload)
 * <p/>
 * Other attributes like singleton, shared, fetch-state do not make sense in single node cluster.
 *
 * @author <a href="mailto:mgencur@redhat.com">Martin Gencur</a>
 * @author <a href="mailto:tsykora@redhat.com">Tomas Sykora</a>
 */
@RunWith(Arquillian.class)
@Category(CacheStore.class)
@WithRunningServer({@RunningServer(name = "standalone-rcs-remote")})
public class RemoteCacheStoreIT {

    private final String CONTAINER_LOCAL = "standalone-rcs-local"; // manual container
    private final String CONTAINER_REMOTE = "standalone-rcs-remote"; // suite container
    public static final String LOCAL_CACHE_MANAGER = "local";
    private final String LOCAL_CACHE_NAME = "memcachedCache";
    private final String READONLY_CACHE_NAME = "readOnlyCache";

    private MemcachedClient mc;

    @InfinispanResource(CONTAINER_LOCAL)
    RemoteInfinispanServer server1;

    @InfinispanResource(CONTAINER_REMOTE)
    RemoteInfinispanServer server2;

    @ArquillianResource
    ContainerController controller;

    RemoteCacheManager rcm1;
    RemoteCacheManager rcm2;

   @ClassRule
   public static ClassRemoteCacheManager classRCM1 = new ClassRemoteCacheManager();

   @ClassRule
   public static ClassRemoteCacheManager classRCM2 = new ClassRemoteCacheManager();

   @Rule
   public Cleanup cleanup = new Cleanup();

    @Before
    public void setUp() throws Exception {
       rcm1 = classRCM1.cacheRemoteCacheManager(server1);
       rcm2 = classRCM2.cacheRemoteCacheManager(server2);
    }

    /**
     * Test for read-only attribute of store - if true, no entries will be written into store
     */
    @Test
    @WithRunningServer({@RunningServer(name = CONTAINER_LOCAL)})
    public void testReadOnly() throws Exception {
        RemoteCache<String, String> rc1 = rcm1.getCache(READONLY_CACHE_NAME);
        // put 3 keys, one entry is evicted, but not stored
        Set<String> allKeys = new HashSet<>();
        for (int i = 1; i < 4; i++) {
            String key = "k"+i;
            rc1.put(key, "v"+i);
            allKeys.add(key);
        }
        assertEquals(0, server2.getCacheManager(LOCAL_CACHE_MANAGER).getDefaultCache().getNumberOfEntriesInMemory());
        assertEquals(2, server1.getCacheManager(LOCAL_CACHE_MANAGER).getCache(READONLY_CACHE_NAME).getNumberOfEntries());
        assertEquals(2, server1.getCacheManager(LOCAL_CACHE_MANAGER).getCache(READONLY_CACHE_NAME).getNumberOfEntriesInMemory());

        Set<String> storedKeys = rc1.keySet();
        allKeys.removeAll(storedKeys);
        for (String key : allKeys)
            assertNull(rc1.get(key));

        for (String key : storedKeys) {
            int i = Integer.parseInt(key.substring(1));
            assertEquals("v"+i, rc1.get(key));
        }
    }

    /*
     * 1. store 3 entries in the local cache
     * 2. verify that there are only 2 in the local cache (third one evicted)
     * 3. verify the evicted entry (and not anything else) is in the remote cache
     * 4. retrieve the evicted entry from local cache (should call remote cache internally)
     * 5. verify the evicted entry was removed from the remote cache
     */
    @Test
    @WithRunningServer({@RunningServer(name = CONTAINER_LOCAL)})
    public void testPassivateAfterEviction() throws Exception {
        mc = new MemcachedClient(server1.getMemcachedEndpoint().getInetAddress().getHostName(), server1.getMemcachedEndpoint()
                .getPort());
        cleanup.add(MemcachedClient::close, mc);
        assertCleanCacheAndStore();
        mc.set("k1", "v1");
        mc.set("k2", "v2");
        // not yet in store (eviction.max-entries=2, LRU)
        assertEquals(0, server2.getCacheManager(LOCAL_CACHE_MANAGER).getDefaultCache().getNumberOfEntries());
        mc.set("k3", "v3");
        // now k1 evicted and stored in store
        assertEquals(2, server1.getCacheManager(LOCAL_CACHE_MANAGER).getCache(LOCAL_CACHE_NAME).getNumberOfEntriesInMemory());
        assertEquals(1, server2.getCacheManager(LOCAL_CACHE_MANAGER).getDefaultCache().getNumberOfEntriesInMemory());
        // retrieve from store to cache and remove from store, another key must be evicted (k2)
        assertEquals("v1", mc.get("k1"));
        assertEquals("v2", mc.get("k2"));
        assertEquals("v3", mc.get("k3"));
        mc.delete("k1");
        mc.delete("k2");
        mc.delete("k3");
        mc.close();
    }

    private void assertCleanCacheAndStore() throws Exception {
        mc.delete("k1");
        mc.delete("k2");
        mc.delete("k3");
        rcm2.getCache().clear();
        assertEquals(0, server1.getCacheManager(LOCAL_CACHE_MANAGER).getCache(LOCAL_CACHE_NAME).getNumberOfEntries());
        assertEquals(0, server2.getCacheManager(LOCAL_CACHE_MANAGER).getDefaultCache().getNumberOfEntries());
    }
}
