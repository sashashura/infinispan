package org.infinispan.lucene.cacheloader.configuration;

import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;
import org.infinispan.commons.configuration.attributes.AttributeDefinition;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.lucene.cacheloader.LuceneCacheLoader;

/**
 * Configuration bean for the {@link org.infinispan.lucene.cacheloader.LuceneCacheLoader}.
 *
 * @author navssurtani
 * @since 6.0.0
 */
@BuiltBy(LuceneLoaderConfigurationBuilder.class)
@ConfigurationFor(LuceneCacheLoader.class)
public class LuceneLoaderConfiguration extends AbstractStoreConfiguration {
   static final AttributeDefinition<Integer> AUTO_CHUNK_SIZE = AttributeDefinition.builder("autoChunkSize", Integer.MAX_VALUE / 64).immutable().build();
   static final AttributeDefinition<Integer> AFFINITY_SEGMENT_ID = AttributeDefinition.builder("affinitySegmentId", Integer.valueOf(-1)).immutable().build();
   static final AttributeDefinition<String> LOCATION = AttributeDefinition.builder("location", "Infinispan-IndexStore").immutable().build();

   public LuceneLoaderConfiguration(AttributeSet attributes, AsyncStoreConfiguration async) {
      super(attributes, async);
   }

   public static AttributeSet attributeDefinitionSet() {
      return new AttributeSet(LuceneLoaderConfiguration.class, AbstractStoreConfiguration.attributeDefinitionSet(), AUTO_CHUNK_SIZE, LOCATION, AFFINITY_SEGMENT_ID);
   }

   /**
    * When the segment size is larger than this number of bytes, separate segments will be created
    * of this particular size.
    *
    * @return the segmentation size.
    */
   public int autoChunkSize() {
      return attributes.attribute(AUTO_CHUNK_SIZE).get();
   }

   /**
    * The location of the root directory of the index.
    *
    * @return the index location root directory.
    */
   public String location() {
      return attributes.attribute(LOCATION).get();
   }

   /**
    * When Index Affinity is enabled, this returns the segment id to which the current index is bound to.
    */
   public int affinitySegmentId() {
      return attributes.attribute(AFFINITY_SEGMENT_ID).get();
   }

   @Override
   public String toString() {
      return "LuceneLoaderConfiguration [attributes=" + attributes + ", async=" + async() + "]";
   }

}
