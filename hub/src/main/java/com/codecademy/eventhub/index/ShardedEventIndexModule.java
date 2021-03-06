package com.codecademy.eventhub.index;

import com.codecademy.eventhub.list.DmaIdList;
import com.codecademy.eventhub.list.IdList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.PatternFilenameFilter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public class ShardedEventIndexModule extends AbstractModule {
  @Override
  protected void configure() {}

  @Provides
  @Named("eventhub.shardedeventindex.directory")
  public String getEventIndexDirectory(
      @Named("eventhub.directory") String eventHubDirectory) {
    return eventHubDirectory + "/event_index/";
  }

  @Provides
  @Named("eventhub.shardedeventindex.filename")
  public String getEventIndexFile(
      @Named("eventhub.shardedeventindex.directory") String eventIndexDirectory) {
    return eventIndexDirectory + "/event_index.ser";
  }

  @Provides
  @Named("eventhub.shardedeventindex.datedeventindex.filename")
  public String getDatedEventIndexFile(
      @Named("eventhub.shardedeventindex.directory") String eventIndexDirectory) {
    return eventIndexDirectory + "/dated_event_index.ser";
  }

  @Provides
  public EventIndex.Factory getEventIndexFactory(
      final @Named("eventhub.shardedeventindex.directory") String shardedEventIndexDirectory,
      final @Named("eventhub.eventindex.initialNumEventIdsPerDay") int initialNumEventIdsPerDay,
      final DmaIdList.Factory dmaIdListFactory) {
    dmaIdListFactory.setDefaultCapacity(initialNumEventIdsPerDay);
    return new EventIndex.Factory() {
      @Override
      public EventIndex build(String eventType) {
        String eventIndexDirectory =
            String.format("%s/%s/", shardedEventIndexDirectory, eventType);

        List<String> dates = Lists.newArrayList();
        File[] files = new File(eventIndexDirectory).listFiles(new PatternFilenameFilter("[0-9]{8}\\.ser"));
        if (files != null) {
          for (File file : files){
            dates.add(file.getName().substring(0, 8));
          }
        }
        SortedMap<String, IdList> eventIdListMap = Maps.newTreeMap();
        for (String date : dates) {
          eventIdListMap.put(date, dmaIdListFactory.build(
              EventIndex.getEventIdListFilename(
                  eventIndexDirectory, date)));
        }
        return new EventIndex(eventIndexDirectory,
            dmaIdListFactory, eventIdListMap);
      }
    };
  }

  @Provides
  public ShardedEventIndex getShardedEventIndex(
      @Named("eventhub.shardedeventindex.filename") String eventIndexFilename,
      EventIndex.Factory individualEventIndexFactory) {
    File file = new File(eventIndexFilename);
    if (file.exists()) {
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
        @SuppressWarnings("unchecked")
        Map<String, Integer> eventTypeIdMap = (Map<String, Integer>) ois.readObject();
        Map<String, EventIndex> eventIndexMap = Maps.newHashMap();
        for (String eventType : eventTypeIdMap.keySet()) {
          eventIndexMap.put(eventType, individualEventIndexFactory.build(eventType));
        }
        clearBadData(eventIndexMap);
        clearBadData(eventTypeIdMap);

        return new ShardedEventIndex(eventIndexFilename, individualEventIndexFactory, eventIndexMap,
            eventTypeIdMap);
      } catch (IOException | ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return new ShardedEventIndex(eventIndexFilename, individualEventIndexFactory,
        Maps.<String,EventIndex>newHashMap(), Maps.<String, Integer>newHashMap());
  }

  private void clearBadData(Map eventIndexMap) {
    if(eventIndexMap == null || eventIndexMap.isEmpty()){
      return;
    }

    Iterator<Map.Entry> entryIterator = eventIndexMap.entrySet().iterator();
    while(entryIterator.hasNext()){
      Map.Entry entry = entryIterator.next();
      String key = String.valueOf(entry.getKey());
      if(key != null){
        if("".equals(key.trim())){
          entryIterator.remove();
        }else{
          if(key.contains("noload")){
            entryIterator.remove();
          }
        }
      }

    }

  }

  public static void main(String[] args) throws IOException {
//    File path = new File("/data/event_hub/event_index");
//    File[] files = path.listFiles(new FileFilter() {
//      @Override
//      public boolean accept(File file) {
//        return file.isDirectory() && file.getName().contains("__");
//      }
//    });
//
//    for(File file: files){
//      String name = file.getName();
//      String newName = name.replace("__", ">>");
//      FileUtils.copyDirectory(file, new File(path, newName));
//      System.out.println(name+"\t---->>>>\t"+newName);
//    }

    Map<String, String> map = Maps.newTreeMap();
    map.put("1", "1");
    map.put("2", "1");
    map.put("3", "1");
    map.put("4", "1");
    map.put("5", "1");

    System.out.println(map);

    Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
    while(iterator.hasNext()){
      Map.Entry<String, String> next = iterator.next();
      String key = next.getKey();
      if("1".equals(key)){
        iterator.remove();
      }
    }

    System.out.println(map);
  }


}
