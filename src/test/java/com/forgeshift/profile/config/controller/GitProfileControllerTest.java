package com.forgeshift.profile.config.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forgeshift.profile.config.client.GitVerifyClient;
import com.forgeshift.profile.config.domain.GitProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import com.forgeshift.profile.config.exception.GlobalExceptionHandler;
import com.forgeshift.profile.config.repository.GitProfileRepository;
import com.forgeshift.profile.config.service.GitProfileService;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Git profile API from request to JSON - the real controller, service and response
 * mapping, serialised by a mapper built the way the application builds its own - over a mocked
 * repository.
 *
 * <p>Pins two things: no response carries the GitHub token, and an update that does not bring a
 * new one keeps the stored one. The migration service reads that token straight from Mongo to
 * push the generated Kong config, so blanking it, or saving the mask over it, stops every push
 * for the company.</p>
 */
@ExtendWith(MockitoExtension.class)
class GitProfileControllerTest {

    private static final String COMPANY = "acme";
    /** Every token in this test carries SENTINEL, so one search of a body finds any of them. */
    private static final String STORED_PAT = "ghp_SENTINEL_stored";
    private static final String NEW_PAT = "ghp_SENTINEL_new";

    @Mock
    GitProfileRepository repository;

    private final Map<String, GitProfile> documents = new LinkedHashMap<>();
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
        // The real client: the service uses it to derive the organisation. Nothing here may reach GitHub.
        GitVerifyClient verifyClient = new GitVerifyClient(WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError("unexpected call to " + request.url()))));
        mvc = MockMvcBuilders
                .standaloneSetup(new GitProfileController(new GitProfileService(repository, verifyClient)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();

        documents.put("g1", GitProfile.builder()
                .id("g1").profileName("primary").companyName(COMPANY).provider("github")
                .githubUrl("https://github.com/acme").organization("acme").username("octo")
                .repo("acme/acme-kong-config").branch("main").pat(STORED_PAT)
                .status(ProfileStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0)).createdBy("owner@acme.test")
                .lastUpdatedAt(LocalDateTime.of(2026, 9, 1, 10, 0)).lastUpdatedBy("owner@acme.test")
                .lastVerifiedAt(LocalDateTime.of(2026, 9, 2, 9, 0)).lastVerifiedDetail("GitHub token is valid.")
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
        lenient().when(repository.save(any(GitProfile.class))).thenAnswer(call -> {
            GitProfile p = call.getArgument(0);
            if (p.getId() == null) {
                p.setId("g" + (documents.size() + 1));
            }
            documents.put(p.getId(), p);
            return p;
        });
    }

    static Stream<Arguments> everyEndpointThatReturnsAProfile() {
        return Stream.of(
                endpoint("POST /git/profiles", post("/git/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", "\"" + NEW_PAT + "\""))),
                endpoint("PUT /git/profiles/{id} with a new token", put("/git/profiles/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", "\"" + NEW_PAT + "\""))),
                endpoint("PUT /git/profiles/{id} keeping the token", put("/git/profiles/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", null))),
                endpoint("GET /git/profiles/{id}", get("/git/profiles/g1")
                        .param("companyName", COMPANY)),
                endpoint("GET /git/profiles", get("/git/profiles")
                        .param("companyName", COMPANY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEndpointThatReturnsAProfile")
    void noResponseCarriesTheToken(RequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode tree = json.readTree(body);
        assertThat(body).doesNotContain("SENTINEL");
        assertThat(tree.findValues("pat")).isEmpty();
        // Says a token is on file, so an edit form knows it can leave the field blank.
        assertThat(tree.findValuesAsText("patStored")).isNotEmpty().containsOnly("***");
        // The fields the UIs read are all still there.
        assertThat(tree.findValues("organization")).isNotEmpty();
        assertThat(tree.findValues("repo")).isNotEmpty();
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
    void update_withoutNewToken_keepsStoredToken(String patJson) throws Exception {
        mvc.perform(put("/git/profiles/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", patJson).replace("\"main\"", "\"release\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("release"))
                .andExpect(jsonPath("$.patStored").value("***"));

        assertThat(savedDocument().getPat()).isEqualTo(STORED_PAT);
        assertThat(savedDocument().getBranch()).isEqualTo("release");
    }

    @Test
    void update_withNewToken_replacesStoredToken() throws Exception {
        mvc.perform(put("/git/profiles/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", "\"" + NEW_PAT + "\"")))
                .andExpect(status().isOk());

        assertThat(savedDocument().getPat()).isEqualTo(NEW_PAT);
    }

    /** A client that saves back exactly what it read - mask, extra fields and all - loses nothing. */
    @Test
    void savingBackWhatWasRead_keepsStoredToken() throws Exception {
        String read = mvc.perform(get("/git/profiles/g1").param("companyName", COMPANY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectNode edited = (ObjectNode) json.readTree(read);
        edited.put("repo", "acme/kong-config");
        edited.put("userEmail", "editor@acme.test");

        mvc.perform(put("/git/profiles/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(edited)))
                .andExpect(status().isOk());

        assertThat(savedDocument().getPat()).isEqualTo(STORED_PAT);
        assertThat(savedDocument().getRepo()).isEqualTo("acme/kong-config");
    }

    @Test
    void create_withoutToken_isRejected() throws Exception {
        mvc.perform(post("/git/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pat").exists());

        verify(repository, never()).save(any());
    }

    /** Create validates the OnCreate group, which must still carry every ordinary constraint. */
    @Test
    void create_stillRequiresEveryOtherField() throws Exception {
        mvc.perform(post("/git/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pat\":\"" + NEW_PAT + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists())
                .andExpect(jsonPath("$.fieldErrors.githubUrl").exists())
                .andExpect(jsonPath("$.fieldErrors.organization").exists());

        verify(repository, never()).save(any());
    }

    /** Git create calls nothing that would reject the mask, so without this check it would be saved as the token. */
    @Test
    void create_withMaskAsToken_isRejected() throws Exception {
        mvc.perform(post("/git/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", "\"••••••••\"")))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    /** The document the last save wrote. */
    private GitProfile savedDocument() {
        ArgumentCaptor<GitProfile> saved = ArgumentCaptor.forClass(GitProfile.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    /** A request body; {@code patJson} is a JSON literal, or null to leave the field out. */
    private static String body(String profileName, String patJson) {
        return "{\"companyName\":\"" + COMPANY + "\",\"profileName\":\"" + profileName + "\","
                + "\"githubUrl\":\"https://github.com/acme\",\"organization\":\"acme\","
                + "\"username\":\"octo\",\"repo\":\"acme/acme-kong-config\",\"branch\":\"main\","
                + (patJson == null ? "" : "\"pat\":" + patJson + ",")
                + "\"userEmail\":\"editor@acme.test\"}";
    }

    private static Arguments endpoint(String name, RequestBuilder request) {
        return Arguments.of(Named.of(name, request));
    }
}
