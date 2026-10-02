package com.edumentor.booking.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.meeting.provider", havingValue = "google")
public class GoogleMeetingProvider implements MeetingProvider {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String EVENTS_URL =
            "https://www.googleapis.com/calendar/v3/calendars/primary/events?conferenceDataVersion=1";

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final RestClient restClient;

    public GoogleMeetingProvider(@Value("${app.meeting.google.client-id:}") String clientId,
                                 @Value("${app.meeting.google.client-secret:}") String clientSecret,
                                 @Value("${app.meeting.google.refresh-token:}") String refreshToken) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET and GOOGLE_REFRESH_TOKEN "
                    + "must be set when MEETING_PROVIDER=google");
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String createMeeting(MeetingRequest request) {
        try {
            String accessToken = fetchAccessToken();
            Map<String, Object> body = Map.of(
                    "summary", request.title(),
                    "start", Map.of("dateTime", request.start().toString(), "timeZone", "UTC"),
                    "end", Map.of("dateTime", request.end().toString(), "timeZone", "UTC"),
                    "conferenceData", Map.of("createRequest", Map.of(
                            "requestId", "edumentor-booking-" + request.bookingId(),
                            "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));
            JsonNode event = restClient.post()
                    .uri(EVENTS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String link = event == null ? null : event.path("hangoutLink").asText(null);
            if (link == null || link.isBlank()) {
                throw new MeetingException("Google response contained no meeting link");
            }
            return link;
        } catch (RestClientException ex) {
            throw new MeetingException("Google Calendar request failed", ex);
        }
    }

    private String fetchAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        JsonNode token = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String accessToken = token == null ? null : token.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new MeetingException("Google token response contained no access_token");
        }
        return accessToken;
    }
}