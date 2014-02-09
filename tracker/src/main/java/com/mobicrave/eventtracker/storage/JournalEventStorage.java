package com.mobicrave.eventtracker.storage;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.ByteStreams;
import com.mobicrave.eventtracker.Criterion;
import com.mobicrave.eventtracker.base.BloomFilter;
import com.mobicrave.eventtracker.base.KeyValueCallback;
import com.mobicrave.eventtracker.base.Schema;
import com.mobicrave.eventtracker.list.DmaList;
import com.mobicrave.eventtracker.model.Event;
import org.fusesource.hawtjournal.api.Journal;
import org.fusesource.hawtjournal.api.Location;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.List;

public class JournalEventStorage implements EventStorage {
  private final String directory;
  private final Journal eventJournal;
  private final LoadingCache<Long, Event> eventCache;
  private final DmaList<MetaData> metaDataList;
  private long currentId;
  private long numConditionCheck;
  private long numBloomFilterRejection;

  private JournalEventStorage(String directory, Journal eventJournal,
      LoadingCache<Long, Event> eventCache, DmaList<MetaData> metaDataList, long currentId) {
    this.directory = directory;
    this.eventJournal = eventJournal;
    this.eventCache = eventCache;
    this.metaDataList = metaDataList;
    this.currentId = currentId;
    this.numConditionCheck = 0;
    this.numBloomFilterRejection = 0;
  }

  @Override
  public long addEvent(Event event, int userId, int eventTypeId) {
    try {
      long id = currentId++;
      byte[] location = JournalUtil.locationToBytes(eventJournal.write(event.toByteBuffer(), true));
      final BloomFilter bloomFilter = BloomFilter.build(MetaData.NUM_HASHES, MetaData.BLOOM_FILTER_SIZE);
      event.enumerate(new KeyValueCallback() {
        @Override
        public void callback(String key, String value) {
          bloomFilter.add(getBloomFilterKey(key, value));
        }
      });
      MetaData metaData = new MetaData(userId, eventTypeId, bloomFilter,location);
      metaDataList.add(metaData);
      return id;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Event getEvent(long eventId) {
    return eventCache.getUnchecked(eventId);
  }

  @Override
  public int getEventTypeId(long eventId) {
    return MetaData.SCHEMA.getEventTypeId(metaDataList.getBytes(eventId));
  }

  @Override
  public int getUserId(long eventId) {
    return MetaData.SCHEMA.getUserId(metaDataList.getBytes(eventId));
  }

  @Override
  public boolean satisfy(long eventId, List<Criterion> criteria) {
    if (criteria.isEmpty()) {
      return true;
    }
    numConditionCheck++;

    BloomFilter bloomFilter = metaDataList.get(eventId).getBloomFilter();
    for (Criterion criterion : criteria) {
      String bloomFilterKey = getBloomFilterKey(criterion.getKey(), criterion.getValue());
      if (!bloomFilter.isPresent(bloomFilterKey)) {
        numBloomFilterRejection++;
        return false;
      }
    }

    Event event = getEvent(eventId);
    for (Criterion criterion : criteria) {
      if (!criterion.getValue().equals(event.get(criterion.getKey()))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void close() throws IOException {
    eventJournal.close();
    metaDataList.close();
  }

  @Override
  public String getVarz() {
    return String.format(
        "directory: %s\n" +
            "current id: %d\n" +
            "num condition check: %d\n" +
            "num bloomfilter rejection: %d\n" +
            "eventCache: %s\n" +
            "metaDataList: %s\n",
        directory, currentId, numConditionCheck, numBloomFilterRejection,
        eventCache.stats().toString(), metaDataList.getVarz());
  }

  private static String getBloomFilterKey(String key, String value) {
    return key + value;
  }

  private static String getMetaDataDirectory(String directory) {
    return directory + "/meta_data/";
  }

  private static String getJournalDirectory(String directory) {
    return directory + "/event_journal/";
  }

  public static JournalEventStorage build(String directory) {
    final Journal eventJournal = JournalUtil.createJournal(getJournalDirectory(directory));
    final DmaList<MetaData> metaDataList = DmaList.build(MetaData.getSchema(),
        getMetaDataDirectory(directory), 10 * 1024 * 1024 /* numRecordsPerFile */);
    LoadingCache<Long, Event> eventCache = CacheBuilder.newBuilder()
        .maximumSize(1024 * 1024)
        .recordStats()
        .build(new CacheLoader<Long, Event>() {
          @Override
          public Event load(Long eventId) throws Exception {
            try {
              Location location = new Location();
              MetaData metaData = metaDataList.get(eventId);
              location.readExternal(ByteStreams.newDataInput(metaData.getLocation()));
              return Event.fromByteBuffer(eventJournal.read(location));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });

    return new JournalEventStorage(directory, eventJournal, eventCache, metaDataList,
        metaDataList.getNumRecords());
  }

  private static class MetaData {
    private static final MetaDataSchema SCHEMA = new MetaDataSchema();
    private static final int BLOOM_FILTER_SIZE = 64; // in bytes
    private static final int NUM_HASHES = 5;

    private final int userId;
    private final int eventTypeId;
    private final BloomFilter bloomFilter;
    private final byte[] location;

    public MetaData(int userId, int eventTypeId, BloomFilter bloomFilter, byte[] location) {
      this.userId = userId;
      this.eventTypeId = eventTypeId;
      this.bloomFilter = bloomFilter;
      this.location = location;
    }

    public BloomFilter getBloomFilter() {
      return bloomFilter;
    }

    public byte[] getLocation() {
      return location;
    }

    public static MetaDataSchema getSchema() {
      return SCHEMA;
    }

    private static class MetaDataSchema implements Schema<MetaData> {
      private static final int LOCATION_SIZE = 13; // in bytes

      @Override
      public int getObjectSize() {
        return 8 /* userId + eventTypeId */ + LOCATION_SIZE + BLOOM_FILTER_SIZE;
      }

      @Override
      public byte[] toBytes(MetaData metaData) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(getObjectSize());
        byteBuffer.putInt(metaData.userId)
            .putInt(metaData.eventTypeId)
            .put(metaData.bloomFilter.getBitSet().toByteArray())
            .put(metaData.location);
        return byteBuffer.array();
      }

      @Override
      public MetaData fromBytes(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        int userId = byteBuffer.getInt();
        int eventTypeId = byteBuffer.getInt();
        byte[] bloomFilter = new byte[BLOOM_FILTER_SIZE];
        byteBuffer.get(bloomFilter);
        byte[] location = new byte[LOCATION_SIZE];
        byteBuffer.get(location);
        return new MetaData(userId, eventTypeId,
            new BloomFilter(MetaData.NUM_HASHES, BitSet.valueOf(bloomFilter)), location);
      }

      public int getEventTypeId(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        return byteBuffer.getInt(4 /* the first 4 bytes are userId*/);
      }

      public int getUserId(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        return byteBuffer.getInt();
      }
    }
  }
}