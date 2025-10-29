package com.softpath.riverpath.custom.event;

// EventDispatcher.java

import javafx.event.EventHandler;

import java.util.HashMap;
import java.util.Map;

public class EventDispatcher {

    private static EventDispatcher instance;

    // Map to store event handlers for different event types
    private final Map<EventEnum, EventHandler<CustomEvent>> eventHandlers = new HashMap<>();

    private EventDispatcher() {
    }

    // Singleton pattern
    public static EventDispatcher getInstance() {
        if (instance == null) {
            instance = new EventDispatcher();
        }
        return instance;
    }

    // A method to add an event handler for a specific event type
    public void addEventHandler(EventEnum eventEnum, EventHandler<CustomEvent> eventHandler) {
        eventHandlers.put(eventEnum, eventHandler);
    }

    // A method to dispatch an event to its handlers
    public void dispatchEvent(CustomEvent event) {
        EventHandler<CustomEvent> eventHandler = eventHandlers.get(event.getEventEnum());
        if (eventHandler != null) {
            eventHandler.handle(event);
        }
    }
}

