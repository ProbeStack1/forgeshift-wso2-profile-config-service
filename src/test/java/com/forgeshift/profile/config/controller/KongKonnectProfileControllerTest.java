package com.forgeshift.profile.config.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forgeshift.profile.config.client.KongKonnectVerifyClient;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import com.forgeshift.profile.config.exception.GlobalExceptionHandler;
import com.forgeshift.profile.config.repository.KongKonnectProfileRepository;
import com.forgeshift.profile.config.service.KongKonnectProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Kong Konnect profile API from request to JSON - the real controller, service and
 * response mapping, serialised by a mapper built the way the application builds its own - over
 * a mocked repository and Konnect client.
 *
 * <p>Pins two things: no response carries a personal access token, and an update that does not
 * bring a new one keeps the stored one. Discovery, migration and validation read that token
 * straight from Mongo, so blanking it, or saving the mask over it, breaks every run for the
 * company.</p>
 */
@ExtendWith(MockitoExtension.class)
class KongKonnectProfileControllerTest {

    private static final String COMPANY = "acme";
    private static final String ADMIN_URL = "https://us.api.konghq.com";
    /** Every token in this test carries SENTINEL, so one search of a body finds any of them. */
    private static final String STORED_PAT = "kpat_SENTINEL_stored";
    private static final String NEW_PAT = "kpat_SENTINEL_new";

    @Mock
    KongKonnectProfileRepository repository;
    @Mock
    KongKonnectVerifyClient verifyClient;

    private final Map<String, KongKonnectProfile> documents = new LinkedHashMap<>();
    private ObjectMapper json;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // spring.jackson.default-property-inclusion=non_null and JacksonConfig, as the service runs.
        // Boot's builder also leaves FAIL_ON_UNKNOWN_PROPERTIES off, which the round-trip test needs.
        json = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mvc = MockMvcBuilders
                .standaloneSetup(new KongKonnectProfileController(
                        new KongKonnectProfileService(repository, verifyClient)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();

        documents.put("p1", KongKonnectProfile.builder()
                .id("p1").companyName(COMPANY).profileName("primary")
                .adminUrl(ADMIN_URL).konnectPat(STORED_PAT).region("us")
                .controlPlanes(new ArrayList<>(List.of(new KongKonnectControlPlane("cp-1", "default"))))
                .defaultProfile(true).defaultControlPlane("cp-1")
                .status(ProfileStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0)).createdBy("owner@acme.test")
                .lastUpdatedAt(LocalDateTime.of(2026, 9, 1, 10, 0)).lastUpdatedBy("owner@acme.test")
                .build());

        // A small in-memory collection behind the repository. Lenient: each test uses a subset.
        lenient().when(repository.findByIdAndCompanyNameAndStatus(anyString(), anyString(), any()))
                .thenAnswer(call -> Optional.ofNullable(documents.get(call.<String>getArgument(0)))
                        .filter(p -> p.getCompanyName().equals(call.getArgument(1))
                                && p.getStatus() == call.getArgument(2)));
        lenient().when(repository.findAllByCompanyNameAndStatus(anyString(), any()))
                .thenAnswer(call -> documents.values().stream()
                        .filter(p -> p.getCompanyName().equals(call.getArgument(0))
                                && p.getStatus() == call.getArgument(1))
                        .toList());
        lenient().when(repository.existsByProfileNameAndCompanyNameAndStatus(anyString(), anyString(), any()))
                .thenReturn(false);
        lenient().when(repository.save(any(KongKonnectProfile.class))).thenAnswer(call -> {
            KongKonnectProfile p = call.getArgument(0);
            if (p.getId() == null) {
                p.setId("p" + (documents.size() + 1));
            }
            documents.put(p.getId(), p);
            return p;
        });
        lenient().when(verifyClient.fetchControlPlanes(anyString(), anyString()))
                .thenReturn(List.of(new KongKonnectControlPlane("cp-1", "default")));
    }

    static Stream<Arguments> everyEndpointThatReturnsAProfile() {
        return Stream.of(
                endpoint("POST /kong-konnect/profiles", post("/kong-konnect/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", ADMIN_URL, "us", "\"" + NEW_PAT + "\""))),
                endpoint("PUT /kong-konnect/profiles/{id} with a new token", put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", ADMIN_URL, "us", "\"" + NEW_PAT + "\""))),
                endpoint("PUT /kong-konnect/profiles/{id} keeping the token", put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", ADMIN_URL, "us", null))),
                endpoint("GET /kong-konnect/profiles/{id}", get("/kong-konnect/profiles/p1")
                        .param("companyName", COMPANY)),
                endpoint("GET /kong-konnect/profiles", get("/kong-konnect/profiles")
                        .param("companyName", COMPANY)),
                endpoint("PUT /kong-konnect/profiles/{id}/default", put("/kong-konnect/profiles/p1/default")
                        .param("companyName", COMPANY).param("userEmail", "editor@acme.test")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEndpointThatReturnsAProfile")
    void noResponseCarriesTheToken(RequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode tree = json.readTree(body);
        assertThat(body).doesNotContain("SENTINEL");
        assertThat(tree.findValues("konnectPat")).isEmpty();
        // Says a token is on file, so an edit form knows it can leave the field blank.
        assertThat(tree.findValuesAsText("konnectPatStored")).isNotEmpty().containsOnly("***");
        // The fields the UIs read are all still there.
        assertThat(tree.findValues("adminUrl")).isNotEmpty();
        assertThat(tree.findValues("controlPlanes")).isNotEmpty();
    }

    /** What an update may send for the token and still mean "keep it": the v2 UI leaves it out. */
    static Stream<Arguments> tokenValuesThatKeepTheStoredOne() {
        return Stream.of(
                Arguments.of(Named.of("left out", null)),
                Arguments.of(Named.of("null", "null")),
                Arguments.of(Named.of("blank", "\"   \"")),
                Arguments.of(Named.of("the mask a read returns", "\"***\"")),
                Arguments.of(Named.of("the legacy UI's bullets", "\"••••••••\"")));
    }

    @ParameterizedTest(name = "token {0}")
    @MethodSource("tokenValuesThatKeepTheStoredOne")
    void update_withoutNewToken_keepsStoredToken(String konnectPatJson) throws Exception {
        mvc.perform(put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("renamed", ADMIN_URL, "us", konnectPatJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("renamed"))
                .andExpect(jsonPath("$.konnectPatStored").value("***"));

        assertThat(savedDocument().getKonnectPat()).isEqualTo(STORED_PAT);
        assertThat(savedDocument().getProfileName()).isEqualTo("renamed");
        // The control-plane refresh ran with the stored token, not with the placeholder.
        verify(verifyClient).fetchControlPlanes("us", STORED_PAT);
    }

    @Test
    void update_withNewToken_replacesStoredToken() throws Exception {
        mvc.perform(put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", ADMIN_URL, "us", "\"" + NEW_PAT + "\"")))
                .andExpect(status().isOk());

        assertThat(savedDocument().getKonnectPat()).isEqualTo(NEW_PAT);
        verify(verifyClient).fetchControlPlanes("us", NEW_PAT);
    }

    /** The v2 edit form's case: change the region, leave the token blank. */
    @Test
    void update_withoutNewToken_mayChangeRegion() throws Exception {
        mvc.perform(put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", ADMIN_URL, "eu", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("eu"));

        assertThat(savedDocument().getKonnectPat()).isEqualTo(STORED_PAT);
        verify(verifyClient).fetchControlPlanes("eu", STORED_PAT);
    }

    /**
     * Discovery, migration and validation send the stored token to adminUrl. Moving it without the
     * token would hand the company's token to whoever picked the new host.
     */
    @Test
    void update_withoutNewToken_mayNotMoveAdminUrl() throws Exception {
        mvc.perform(put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", "https://collector.example", "us", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("konnectPat is required when adminUrl changes"));

        verifyNoInteractions(verifyClient);
        verify(repository, never()).save(any());
        assertThat(documents.get("p1").getAdminUrl()).isEqualTo(ADMIN_URL);
        assertThat(documents.get("p1").getKonnectPat()).isEqualTo(STORED_PAT);
    }

    @Test
    void update_movingAdminUrl_withNewToken_isAllowed() throws Exception {
        mvc.perform(put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", "https://eu.api.konghq.com", "eu", "\"" + NEW_PAT + "\"")))
                .andExpect(status().isOk());

        assertThat(savedDocument().getAdminUrl()).isEqualTo("https://eu.api.konghq.com");
        assertThat(savedDocument().getKonnectPat()).isEqualTo(NEW_PAT);
    }

    /** A client that saves back exactly what it read - mask, extra fields and all - loses nothing. */
    @Test
    void savingBackWhatWasRead_keepsStoredToken() throws Exception {
        String read = mvc.perform(get("/kong-konnect/profiles/p1").param("companyName", COMPANY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectNode edited = (ObjectNode) json.readTree(read);
        edited.put("profileName", "renamed");
        edited.put("userEmail", "editor@acme.test");

        mvc.perform(put("/kong-konnect/profiles/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(edited)))
                .andExpect(status().isOk());

        assertThat(savedDocument().getKonnectPat()).isEqualTo(STORED_PAT);
        assertThat(savedDocument().getProfileName()).isEqualTo("renamed");
    }

    @Test
    void create_withoutToken_isRejected() throws Exception {
        mvc.perform(post("/kong-konnect/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", ADMIN_URL, "us", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.konnectPat").exists());

        verifyNoInteractions(verifyClient);
        verify(repository, never()).save(any());
    }

    /** Create validates the OnCreate group, which must still carry every ordinary constraint. */
    @Test
    void create_stillRequiresEveryOtherField() throws Exception {
        mvc.perform(post("/kong-konnect/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"konnectPat\":\"" + NEW_PAT + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists())
                .andExpect(jsonPath("$.fieldErrors.profileName").exists())
                .andExpect(jsonPath("$.fieldErrors.adminUrl").exists())
                .andExpect(jsonPath("$.fieldErrors.region").exists())
                .andExpect(jsonPath("$.fieldErrors.userEmail").exists());

        verifyNoInteractions(verifyClient);
    }

    @Test
    void create_withMaskAsToken_isRejected() throws Exception {
        mvc.perform(post("/kong-konnect/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", ADMIN_URL, "us", "\"***\"")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(verifyClient);
        verify(repository, never()).save(any());
    }

    /** The document the last save wrote. */
    private KongKonnectProfile savedDocument() {
        ArgumentCaptor<KongKonnectProfile> saved = ArgumentCaptor.forClass(KongKonnectProfile.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    /** A request body; {@code konnectPatJson} is a JSON literal, or null to leave the field out. */
    private static String body(String profileName, String adminUrl, String region, String konnectPatJson) {
        return "{\"companyName\":\"" + COMPANY + "\",\"profileName\":\"" + profileName + "\","
                + "\"adminUrl\":\"" + adminUrl + "\",\"region\":\"" + region + "\","
                + (konnectPatJson == null ? "" : "\"konnectPat\":" + konnectPatJson + ",")
                + "\"userEmail\":\"editor@acme.test\"}";
    }

    private static Arguments endpoint(String name, RequestBuilder request) {
        return Arguments.of(Named.of(name, request));
    }
}
