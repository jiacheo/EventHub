package com.codecademy.eventhub.index;

import com.codecademy.eventhub.list.DmaIdList;
import com.codecademy.eventhub.list.IdList;

import java.io.Closeable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedMap;

public class EventIndex implements Closeable {
  private final String directory;
  private final DmaIdList.Factory dmaIdListFactor;
  // from date string to IdList of eventId
  private final SortedMap<String, IdList> eventIdListMap;

  public EventIndex(String directory, DmaIdList.Factory dmaIdListFactor,
      SortedMap<String, IdList> eventIdListMap) {
    this.directory = directory;
    this.dmaIdListFactor = dmaIdListFactor;
    this.eventIdListMap = eventIdListMap;
  }

  public void enumerateEventIds(String startDate, String endDate, Callback callback) {
     endDate = nextDate(endDate);
    //这里endDate是不包含的，不科学
     for (IdList idList : eventIdListMap.subMap(startDate, endDate).values()) {
      IdList.Iterator eventIdIterator = idList.iterator();
      while (eventIdIterator.hasNext()) {
        callback.onEventId(eventIdIterator.next());
      }
    }
  }

  private String nextDate(String date){
    try{
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
      Date dt = dateFormat.parse(date);
      dt = new Date(dt.getTime() + 86400000L);
      return dateFormat.format(dt);
    }catch (Exception e){
      //impossible
      return "";
    }
  }

  public void addEvent(long eventId, String date) {
    IdList idList = eventIdListMap.get(date);
    if (idList == null) {
      idList = dmaIdListFactor.build(getEventIdListFilename(directory, date));
      eventIdListMap.put(date, idList);
    }
    idList.add(eventId);
  }

  @Override
  public void close() throws IOException {
    for (IdList idList : eventIdListMap.values()) {
      idList.close();
    }
  }

  public static String getEventIdListFilename(String directory, String date) {
    return String.format("%s/%s.ser", directory, date);
  }

  public interface Factory {
    EventIndex build(String eventType);
  }

  public interface Callback {
    void onEventId(long eventId);
  }
}
