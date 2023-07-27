package com.calendarEvent.googlecalendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Value;
import com.google.api.services.calendar.*;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.calendar.Calendar;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar.Builder;
import com.google.api.services.calendar.model.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.sun.jdi.request.EventRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;




@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private final static Log logger = LogFactory.getLog(CalendarController.class);
    private static final String APPLICATION_NAME = "MyCalendarIntegration";
    private static HttpTransport httpTransport;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static com.google.api.services.calendar.Calendar client;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;


    GoogleAuthorizationCodeFlow flow;
    Credential credential;
    GoogleClientSecrets clientSecrets;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectURI;
    private Set<Event> events = new HashSet<>();

    DateTime startDate = new DateTime(System.currentTimeMillis());
    DateTime endDate = new DateTime("2023-07-15T00:00:00.000+05:30");

    @GetMapping(value="/authorize")
    public ResponseEntity<List<Event>> authorize(OAuth2AuthenticationToken authentication) throws GeneralSecurityException, IOException {
       List< com.google.api.services.calendar.model.Event> eventList = null;
           try
           {
               OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                       authentication.getAuthorizedClientRegistrationId(),
                       authentication.getName());

               // Obtain the access token from the authorized client
               String accessToken = authorizedClient.getAccessToken().getTokenValue();

               // Create the necessary objects for interacting with the Calendar API
               HttpTransport httpTransport=GoogleNetHttpTransport.newTrustedTransport();
               JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

               //GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, new Date()));
               // GoogleCredential credentials = new GoogleCredential().setAccessToken(accessToken);
               GoogleCredential credentials = new GoogleCredential();
               credentials.setAccessToken(accessToken);

               List<String> scopes = Collections.singletonList("https://www.googleapis.com/auth/calendar");

               System.out.println("Credential---> "+credentials);
               //HttpRequestInitializer httpRequestInitializer = new HttpCredentialsAdapter(credentials);
               String apiKey = "AIzaSyBFZNWYp-O7aCmFjuljn63Y8b5nPVxe86w";
               // credentials = credentials.createScoped(Collections.singletonList(CalendarScopes.CALENDAR));
               Calendar service = new Calendar.Builder(httpTransport, jsonFactory, credentials)
                       .setApplicationName("Calendar App")
                       .build();
               System.out.println("Build completed...");


               //return String.valueOf(event);
//               Calendar.Events events = service.events();
//               eventList = events.list("mahesh.mane@humancloud.co.in").setTimeMin(startDate).setTimeMax(endDate).execute();
               String calendarId = "primary"; // Replace with the desired email ID or calendar ID

               DateTime startDate = new DateTime(System.currentTimeMillis());
               DateTime endDate = new DateTime("2023-07-15T00:00:00.000+05:30");

               Events events = service.events().list(calendarId)
                       .setKey(apiKey)
                       .setTimeMin(startDate)
                       .setTimeMax(endDate)
                       .execute();
               System.out.println("events-->"+events);
               eventList = events.getItems();
               for (com.google.api.services.calendar.model.Event event : eventList) {
                   System.out.println("Events--->"+event);
               }

           }
           catch (Exception e)
           {
               System.out.println("Error------------>"+e);
           }
        return new ResponseEntity<>(eventList, HttpStatus.OK);
    }

    Drive driveService;
    @PostMapping(value = "/addEvent", params = "code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> addEventWithAttachment(@RequestParam(value = "code") String code,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam("json") String json) throws IOException {
        com.google.api.services.calendar.model.Events eventList;
        String message;
        try {

            // create event object using object mapper
            ObjectMapper mapper = new ObjectMapper();
            EventRequest request = mapper.readValue(json, EventRequest.class);

            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectURI).execute();
            credential = flow.createAndStoreCredential(response, "userID");

            // Build Google Calendar API client
            client = new Calendar.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();

            // Build Google Gmail API client
            Gmail gmailService = new Gmail.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();

            // Build Google Drive API client
            driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();

            // Create a new instance of the Event class
            Event event = new Event();

            // Set the values of the Event object using the values from the request body
            event.setSummary(com.calendarEvent.googlecalendar.EventRequest.class.cast(request).getSummary());
            event.setDescription(com.calendarEvent.googlecalendar.EventRequest.class.cast(request).getDescription());

            // Set start time
            DateTime startDateTime = new DateTime(com.calendarEvent.googlecalendar.EventRequest.class.cast(request).getStartDate());
            EventDateTime startEventDateTime = new EventDateTime().setDateTime(startDateTime).setTimeZone("Asia/Kolkata");
            event.setStart(startEventDateTime);

            // Set end time
            DateTime endDateTime = new DateTime("2015-05-28T17:00:00-07:00");
            EventDateTime end = new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("America/Los_Angeles");
            event.setEnd(end);



            // Set reminders for the event
            EventReminder[] reminderOverrides = new EventReminder[]{
                    new EventReminder().setMethod("email").setMinutes(30)
            };
            Event.Reminders reminders = new Event.Reminders().setUseDefault(false).setOverrides(Arrays.asList(reminderOverrides));
            event.setReminders(reminders);

            // Add attendees
            List<String> attendeeEmails = com.calendarEvent.googlecalendar.EventRequest.class.cast(request).getAttendeeEmail();
            List<EventAttendee> attendees = new ArrayList<>();

            for (String email : attendeeEmails) {
                EventAttendee attendee = new EventAttendee().setEmail(email);
                attendees.add(attendee);
            }
            event.setAttendees(attendees);

            // Set up the conference data
            ConferenceSolutionKey conferenceSolution = new ConferenceSolutionKey().setType("hangoutsMeet");
            CreateConferenceRequest createConferenceRequest = new CreateConferenceRequest().setRequestId(UUID.randomUUID().toString()).setConferenceSolutionKey(conferenceSolution);
            ConferenceData conferenceData = new ConferenceData().setCreateRequest(createConferenceRequest);
            event.setConferenceData(conferenceData);

            // Upload file to Google Drive
            String fileId = uploadFileToDrive(file);

            // Get the name of the uploaded file
            String fileName = file.getOriginalFilename();

            // Add attachment to the event
            EventAttachment eventAttachment = new EventAttachment().setFileUrl("https://drive.google.com/uc?id=" + fileId).setMimeType(file.getContentType()).setTitle(fileName);
            List<EventAttachment> attachments = new ArrayList<>();
            attachments.add(eventAttachment);
            event.setAttachments(attachments);

            // Modify the file's permission settings to allow anyone with the link to access the file
            Permission permission = new Permission()
                    .setType("anyone")
                    .setRole("reader")
                    .setAllowFileDiscovery(false);
            driveService.permissions().create(fileId, permission).execute();

            // Insert the new event into the calendar
            Event createdEvent = client.events().insert("primary", event).setConferenceDataVersion(1).setSendNotifications(true).setSendUpdates("all").setSupportsAttachments(true).execute();

            System.out.println("Event created: " + createdEvent);

            // Retrieve the Google Meet link from the created event
            String meetLink = createdEvent.getHangoutLink();

            System.out.println("Google Meet Link :" + meetLink);

            return new ResponseEntity<>("Event added successfully.", HttpStatus.OK);

        } catch (IOException e) {
            logger.error("Exception while adding event (" + e.getMessage() + ").");
            return new ResponseEntity<>("Error while adding event.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper method to upload file to Google Drive
    private String uploadFileToDrive(MultipartFile file) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(file.getOriginalFilename());
        fileMetadata.setParents(Collections.singletonList("root"));

        InputStreamContent mediaContent = new InputStreamContent(file.getContentType(), file.getInputStream());

        Drive.Files.Create create = driveService.files().create(fileMetadata, mediaContent);
        create.setFields("id");

        File uploadedFile = create.execute();
        return uploadedFile.getId();
    }


    public Set<Event> getEvents() throws IOException {
        return this.events;
    }

    private String authorize() throws Exception {
        AuthorizationCodeRequestUrl authorizationUrl;
        if (flow == null) {
            GoogleClientSecrets.Details web = new GoogleClientSecrets.Details();
            web.setClientId(clientId);
            web.setClientSecret(clientSecret);
            clientSecrets = new GoogleClientSecrets().setWeb(web);
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            Set<String> scopes = new HashSet<>();
            scopes.add(CalendarScopes.CALENDAR);
            scopes.add(GmailScopes.GMAIL_SEND);
            scopes.add(DriveScopes.DRIVE_FILE);
            flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, scopes).build();
        }
        authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectURI);
        System.out.println("cal authorizationUrl->" + authorizationUrl);
        return authorizationUrl.build();
    }



}
