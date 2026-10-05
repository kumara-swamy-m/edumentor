package com.edumentor.ai.controller;

import com.edumentor.ai.client.MentorClient;
import com.edumentor.ai.client.PageDto;
import com.edumentor.ai.client.PublicMentor;
import com.edumentor.ai.provider.AiRecommendationProvider;
import com.edumentor.ai.store.VectorStore;
import feign.FeignException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationFlowIntegrationTest {

    private static final String SECRET = "test-only-secret-key-that-is-at-least-32-bytes-long";
    private static final String AUTH = "Authorization";

    private static final PublicMentor RAHUL = new PublicMentor(1L, "Rahul", "RVCE", "B.E.", "CSE", 3, "KCET", 450,
            "Happy to help with KCET seat choices", "Bengaluru", 4.5, 10,
            List.of("KCET counselling", "Branch selection"));
    private static final PublicMentor ANIL = new PublicMentor(2L, "Anil", "PES University", "B.E.",
            "Mechanical Engineering", 2, "KCET", 1200, null, "Mysuru", 4.0, 3,
            List.of("Mechanical branch guidance"));
    private static final PublicMentor MEERA = new PublicMentor(3L, "Meera", "IIT Madras", "B.Tech",
            "Computer Science", 4, "JEE", 900, null, "Chennai", 4.8, 20, List.of("JEE Advanced counselling"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private VectorStore store;
    @Autowired
    private AiRecommendationProvider provider;

    @MockitoBean
    private MentorClient mentorClient;

    @BeforeEach
    void setUp() {
        store.allIds().forEach(store::delete);
    }

    @Test
    void relevantMentorIsRankedFirstWithAFactBasedReasonAndOtherExamsAreExcluded() throws Exception {
        listing(RAHUL, ANIL, MEERA);
        reindex().andExpect(status().isOk()).andExpect(jsonPath("$.indexed").value(3));

        mockMvc.perform(post("/api/ai/recommendations").header(AUTH, bearer(200, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exam\":\"KCET\",\"rank\":12000,\"preferredColleges\":[\"RVCE\"],"
                                + "\"preferredBranches\":[\"Computer Science\"],\"location\":\"Bengaluru\","
                                + "\"goal\":\"Which branch should I choose with my rank?\",\"maxBudget\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreType").value("COSINE_SIMILARITY"))
                .andExpect(jsonPath("$.scoreMeaning").value(org.hamcrest.Matchers.containsString("not a probability")))
                .andExpect(jsonPath("$.recommendations.length()").value(2))
                .andExpect(jsonPath("$.recommendations[0].mentorId").value(1))
                .andExpect(jsonPath("$.recommendations[0].matchScore").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.recommendations[0].matchScore").value(org.hamcrest.Matchers.lessThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.recommendations[0].reason").value(org.hamcrest.Matchers.containsString("RVCE")))
                .andExpect(jsonPath("$.recommendations[0].reason").value(org.hamcrest.Matchers.containsString("Bengaluru")))
                .andExpect(jsonPath("$.recommendations[?(@.mentorId==3)]").isEmpty())
                .andExpect(jsonPath("$.recommendations[0].probability").doesNotExist());
    }

    @Test
    void emptyIndexGivesAnEmptyListNotAnError() throws Exception {
        mockMvc.perform(post("/api/ai/recommendations").header(AUTH, bearer(200, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exam\":\"NEET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(0));
    }

    @Test
    void rolesAreEnforced() throws Exception {
        mockMvc.perform(post("/api/ai/recommendations").header(AUTH, bearer(100, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exam\":\"KCET\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ai/admin/reindex").header(AUTH, bearer(200, "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ai/mentors/1/embedding").header(AUTH, bearer(200, "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai/admin/index").header(AUTH, bearer(100, "MENTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ai/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exam\":\"KCET\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestsReturnFieldErrors() throws Exception {
        mockMvc.perform(post("/api/ai/recommendations").header(AUTH, bearer(200, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rank\":-5,\"limit\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.exam").exists())
                .andExpect(jsonPath("$.fieldErrors.rank").exists())
                .andExpect(jsonPath("$.fieldErrors.limit").exists());
    }

    @Test
    void singleMentorIndexingLifecycle() throws Exception {
        when(mentorClient.getMentor(eq(1L), anyString(), any())).thenReturn(RAHUL);
        mockMvc.perform(post("/api/ai/mentors/1/embedding").header(AUTH, bearer(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDEXED"));
        assertThat(store.count(provider.modelId())).isEqualTo(1);

        // The mentor is no longer approved: mentor-service answers 404, the stale entry must go
        when(mentorClient.getMentor(eq(1L), anyString(), any())).thenThrow(mock(FeignException.NotFound.class));
        mockMvc.perform(post("/api/ai/mentors/1/embedding").header(AUTH, bearer(1, "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MENTOR_NOT_FOUND"));
        assertThat(store.count(provider.modelId())).isZero();

        when(mentorClient.getMentor(eq(1L), anyString(), any())).thenThrow(new RuntimeException("down"));
        mockMvc.perform(post("/api/ai/mentors/1/embedding").header(AUTH, bearer(1, "ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("MENTOR_SERVICE_UNAVAILABLE"));
    }

    @Test
    void reindexRemovesMentorsThatAreNoLongerApproved() throws Exception {
        listing(RAHUL, ANIL, MEERA);
        reindex().andExpect(jsonPath("$.indexed").value(3)).andExpect(jsonPath("$.removed").value(0));

        listing(RAHUL, MEERA);
        reindex().andExpect(jsonPath("$.indexed").value(2)).andExpect(jsonPath("$.removed").value(1));

        mockMvc.perform(get("/api/ai/admin/index").header(AUTH, bearer(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedMentors").value(2))
                .andExpect(jsonPath("$.dimensions").value(512));
        assertThat(store.allIds()).containsExactlyInAnyOrder(1L, 3L);
    }

    // ---------- helpers ----------

    private void listing(PublicMentor... mentors) {
        when(mentorClient.search(eq(0), anyInt(), anyString(), any()))
                .thenReturn(new PageDto<>(List.of(mentors), 0, 50, mentors.length, 1));
    }

    private org.springframework.test.web.servlet.ResultActions reindex() throws Exception {
        return mockMvc.perform(post("/api/ai/admin/reindex").header(AUTH, bearer(1, "ADMIN")));
    }

    private String bearer(long userId, String role) {
        Date now = new Date();
        return "Bearer " + Jwts.builder()
                .subject("user" + userId + "@test.com")
                .claim("userId", userId)
                .claim("email", "user" + userId + "@test.com")
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}