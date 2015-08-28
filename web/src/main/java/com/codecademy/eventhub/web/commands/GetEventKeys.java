package com.codecademy.eventhub.web.commands;

import com.codecademy.eventhub.EventHub;
import com.google.gson.Gson;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Path("/events/keys")
public class GetEventKeys extends Command {
  private final Gson gson;
  private final EventHub eventHub;

  @Inject
  public GetEventKeys(Gson gson, EventHub eventHub) {
    this.gson = gson;
    this.eventHub = eventHub;
  }

  @Override
  public synchronized void execute(final HttpServletRequest request,
      final HttpServletResponse response) throws IOException {
    if(request.getParameter("event_type") == null || "".equals(request.getParameter("event_type"))){
      String event_types = request.getParameter("event_types");
      if(event_types != null){
        String[] eventTypes = event_types.split("\1");
        Map<String, List<String>> map = new TreeMap<>();
        for(String eventType: eventTypes){
          map.put(eventType, eventHub.getEventKeys(eventType));
        }
        response.getWriter().println(gson.toJson(map));
      }
    }else {
      List<String> keys = eventHub.getEventKeys(request.getParameter("event_type"));
      response.getWriter().println(gson.toJson(keys));
    }
  }
}
