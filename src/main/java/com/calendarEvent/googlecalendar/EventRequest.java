package com.calendarEvent.googlecalendar;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventRequest {
    private String summary;
    private String startDate;
    private String endDate;
    private String description;
    private String organizerEmail;
    private List<String> attendeeEmail;
}

