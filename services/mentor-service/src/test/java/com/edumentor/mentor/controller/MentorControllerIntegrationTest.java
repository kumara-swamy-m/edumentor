package com.edumentor.mentor.controller;

import com.edumentor.mentor.client.AuthClient;
import com.edumentor.mentor.client.UserSummary;
import com.edumentor.mentor.repository.MentorProfileRepository;
import com.edumentor.mentor.repository.MentorVerificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorControllerIntegrationTest {

    private static final String SECRET = "test-only-secret-key-that-is-at-least-32-bytes-long";
    private static final String AUTH = "Authorization";
    private static final long MENTOR_USER = 100;
    private static final long OTHER_MENTOR_USER = 101;
    private static final long STUDENT_USER = 200;
    private static final long ADMIN_USER = 1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MentorProfileRepository profileRepository;
    @Autowired
    private MentorVerificationRepository verificationRepository;

    @MockBean
    private AuthClient authClient;

    @BeforeEach
    void cleanDatabase() {
        verificationRepository.deleteAll();
        profileRepository.deleteAll();
    }

    // ---------- Profile creation ----------

    @Test
    void mentorCreatesProfileAsPending() throws Exception {
        mockMvc.perform(post("/api/mentors/me").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(profileBody("KCET", "RVCE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(100))
                .andExpect(jsonPath("$.expertise[0]").value("KCET counselling"));
    }

    @Test
    void secondProfileForSameUserIsRejectedWith409() throws Exception {
        createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));

        mockMvc.perform(post("/api/mentors/me").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(profileBody("KCET", "RVCE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MENTOR_PROFILE_ALREADY_EXISTS"));
    }

    @Test
    void studentCannotCreateMentorProfile() throws Exception {
        mockMvc.perform(post("/api/mentors/me").header(AUTH, bearer(STUDENT_USER, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(profileBody("KCET", "RVCE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/mentors"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void invalidProfileReturnsFieldErrors() throws Exception {
        Map<String, Object> body = profileBody("KCET", "");
        body.put("year", 9);

        mockMvc.perform(post("/api/mentors/me").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.college").exists())
                .andExpect(jsonPath("$.fieldErrors.year").exists());
    }

    // ---------- Verification workflow ----------

    @Test
    void fullVerificationWorkflowMakesMentorVisibleToStudents() throws Exception {
        long mentorId = createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));

        // PENDING mentors are invisible to students
        mockMvc.perform(get("/api/mentors").header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/mentors/" + mentorId).header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MENTOR_NOT_FOUND"));

        submitProof(MENTOR_USER);
        approve(mentorId);

        mockMvc.perform(get("/api/mentors").header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) mentorId))
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].verificationStatus").doesNotExist());
        mockMvc.perform(get("/api/mentors/" + mentorId).header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("RVCE"));
    }

    @Test
    void searchFiltersByExamAndCollege() throws Exception {
        approvedMentor(MENTOR_USER, profileBody("KCET", "RVCE"));
        approvedMentor(OTHER_MENTOR_USER, profileBody("JEE", "IIT Madras"));

        mockMvc.perform(get("/api/mentors?exam=JEE").header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].college").value("IIT Madras"));
        mockMvc.perform(get("/api/mentors?college=rvc").header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].examPath").value("KCET"));
        mockMvc.perform(get("/api/mentors?exam=NOT_AN_EXAM").header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    @Test
    void approvingWithoutProofIsRejected() throws Exception {
        long mentorId = createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));

        mockMvc.perform(post("/api/mentors/admin/" + mentorId + "/approve")
                        .header(AUTH, bearer(ADMIN_USER, "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VERIFICATION_PROOF_MISSING"));
    }

    @Test
    void approvingTwiceIsRejected() throws Exception {
        long mentorId = approvedMentor(MENTOR_USER, profileBody("KCET", "RVCE"));

        mockMvc.perform(post("/api/mentors/admin/" + mentorId + "/approve")
                        .header(AUTH, bearer(ADMIN_USER, "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_VERIFICATION_STATE"));
    }

    @Test
    void rejectionNeedsCommentAndMentorCanResubmit() throws Exception {
        long mentorId = createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));
        submitProof(MENTOR_USER);

        mockMvc.perform(post("/api/mentors/admin/" + mentorId + "/reject")
                        .header(AUTH, bearer(ADMIN_USER, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/mentors/admin/" + mentorId + "/reject")
                        .header(AUTH, bearer(ADMIN_USER, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "ID card is unreadable"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.verificationNote").value("ID card is unreadable"));

        mockMvc.perform(get("/api/mentors/" + mentorId).header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isNotFound());

        submitProof(MENTOR_USER);
        mockMvc.perform(get("/api/mentors/me").header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    @Test
    void adminEndpointsAreForbiddenForStudentsAndMentors() throws Exception {
        mockMvc.perform(get("/api/mentors/admin/applications").header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/mentors/admin/applications").header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mentors/admin/1/approve").header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListsPendingApplications() throws Exception {
        createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));

        mockMvc.perform(get("/api/mentors/admin/applications").header(AUTH, bearer(ADMIN_USER, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].verificationStatus").value("PENDING"));
    }

    @Test
    void credentialChangeSendsApprovedMentorBackToPending() throws Exception {
        approvedMentor(MENTOR_USER, profileBody("KCET", "RVCE"));

        mockMvc.perform(put("/api/mentors/me").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(profileBody("KCET", "PES University"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    // ---------- Availability ----------

    @Test
    void availabilityRequiresApprovalAndRejectsOverlaps() throws Exception {
        long mentorId = createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));
        Map<String, Object> valid = Map.of("windows", List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00")));

        mockMvc.perform(put("/api/mentors/me/availability").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MENTOR_NOT_APPROVED"));

        submitProof(MENTOR_USER);
        approve(mentorId);

        mockMvc.perform(put("/api/mentors/me/availability").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));

        Map<String, Object> overlapping = Map.of("windows", List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"),
                Map.of("dayOfWeek", "MONDAY", "startTime", "11:00", "endTime", "13:00")));
        mockMvc.perform(put("/api/mentors/me/availability").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(overlapping)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_AVAILABILITY"));

        mockMvc.perform(get("/api/mentors/" + mentorId + "/availability")
                        .header(AUTH, bearer(STUDENT_USER, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startTime").value("09:00:00"));
    }

    // ---------- Feign / Resilience4j ----------

    @Test
    void adminDetailIncludesApplicantEmailFromAuthService() throws Exception {
        long mentorId = createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));
        submitProof(MENTOR_USER);
        when(authClient.getUser(eq(MENTOR_USER), anyString(), any()))
                .thenReturn(new UserSummary(MENTOR_USER, "Rahul", "rahul@example.com", "MENTOR"));

        mockMvc.perform(get("/api/mentors/admin/applications/" + mentorId)
                        .header(AUTH, bearer(ADMIN_USER, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantEmail").value("rahul@example.com"))
                .andExpect(jsonPath("$.verifications.length()").value(1))
                .andExpect(jsonPath("$.mentor.id").value((int) mentorId));
    }

    @Test
    void adminDetailStillWorksWhenAuthServiceLookupYieldsNothing() throws Exception {
        long mentorId = createProfile(MENTOR_USER, profileBody("KCET", "RVCE"));
        when(authClient.getUser(eq(MENTOR_USER), anyString(), any())).thenReturn(null);

        mockMvc.perform(get("/api/mentors/admin/applications/" + mentorId)
                        .header(AUTH, bearer(ADMIN_USER, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantEmail").isEmpty())
                .andExpect(jsonPath("$.mentor.id").value((int) mentorId));
    }

    // ---------- helpers ----------

    private long approvedMentor(long userId, Map<String, Object> body) throws Exception {
        long mentorId = createProfile(userId, body);
        submitProof(userId);
        approve(mentorId);
        return mentorId;
    }

    private long createProfile(long userId, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/api/mentors/me").header(AUTH, bearer(userId, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void submitProof(long userId) throws Exception {
        mockMvc.perform(post("/api/mentors/me/verification").header(AUTH, bearer(userId, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("documentType", "COLLEGE_ID",
                                "documentReference", "proofs/" + userId + "/id-card.png", "note", "College ID"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private void approve(long mentorId) throws Exception {
        mockMvc.perform(post("/api/mentors/admin/" + mentorId + "/approve")
                        .header(AUTH, bearer(ADMIN_USER, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));
    }

    private Map<String, Object> profileBody(String exam, String college) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", "Rahul");
        body.put("college", college);
        body.put("course", "B.E.");
        body.put("branch", "CSE");
        body.put("year", 3);
        body.put("examPath", exam);
        body.put("rank", 450);
        body.put("bio", "Final-year student happy to help with counselling");
        body.put("location", "Bengaluru");
        body.put("expertise", List.of("KCET counselling", "Branch selection"));
        return body;
    }

    private String bearer(long userId, String role) {
        Date now = new Date();
        String token = Jwts.builder()
                .subject("user" + userId + "@test.com")
                .claim("userId", userId)
                .claim("email", "user" + userId + "@test.com")
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}