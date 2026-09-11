package com.forgeshift.profile.config.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgeshift.profile.config.client.Wso2DcrClient;
import com.forgeshift.profile.config.client.Wso2TenantsClient;
import com.forgeshift.profile.config.client.Wso2VerifyClient;
import com.forgeshift.profile.config.domain.Wso2Profile;
import com.forgeshift.profile.config.dto.Wso2TenantsResponse;
import com.forgeshift.profile.config.exception.GlobalExceptionHandler;
import com.forgeshift.profile.config.repository.Wso2ProfileRepository;
import com.forgeshift.profile.config.service.Wso2ProfileService;
import com.forgeshift.profile.config.validator.Wso2RequestValidator;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The WSO2 profile API from request to JSON - the real controller, validator and service over a
 * mocked repository and WSO2 clients.
 *
 * <p>Pins the update the v2 edit form sends. The form has no tenant field and leaves a blank
 * password out, and the update used to require both, so every one of its saves failed with
 * {@code defaultWso2Tenant: must not be blank}. Leaving them out now keeps the stored ones; create
 * still requires both.</p>
 */
@ExtendWith(MockitoExtension.class)
class Wso2ProfileControllerTest {

    private static final String COMPANY = "acme";
    private static final String BASE_URL = "https://wso2.acme.test:9443";
    /** Every password in this test carries SENTINEL, so one search of a body finds any of them. */
    private static final String STORED_PASSWORD = "SENTINEL-stored";
    private static final String NEW_PASSWORD = "SENTINEL-new";

    @Mock
    Wso2ProfileRepository repository;
    @Mock
    Wso2VerifyClient verifyClient;
    @Mock
    Wso2DcrClient dcrClient;
    @Mock
    Wso2TenantsClient tenantsClient;

    /** The collection behind the repository, keyed by profile name. */
    private final Map<String, Wso2Profile> documents = new LinkedHashMap<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // spring.jackson.default-property-inclusion=non_null, as the service runs.
        ObjectMapper json = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mvc = MockMvcBuilders
                .standaloneSetup(new Wso2ProfileController(new Wso2ProfileService(
                        repository, verifyClient, dcrClient, tenantsClient, new Wso2RequestValidator())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();

        documents.put("primary", Wso2Profile.builder()
                .id(COMPANY + "|primary").companyName(COMPANY).profileName("primary")
                .defaultWso2Tenant("carbon.super").wso2BaseUrl(BASE_URL)
                .username("admin").password(STORED_PASSWORD)
                .clientId("client-1").clientSecret("secret-1")
                .trustSelfSigned(true).status("ACTIVE")
                .discoveredTenants(List.of("carbon.super"))
                .build());

        // Lenient: each test uses a subset.
        lenient().when(repository.findByCompanyNameAndProfileName(anyString(), anyString()))
                .thenAnswer(call -> Optional.ofNullable(documents.get(call.<String>getArgument(1)))
                        .filter(p -> p.getCompanyName().equals(call.getArgument(0))));
        lenient().when(repository.findByCompanyName(anyString()))
                .thenAnswer(call -> documents.values().stream()
                        .filter(p -> p.getCompanyName().equals(call.getArgument(0)))
                        .toList());
        lenient().when(repository.save(any(Wso2Profile.class))).thenAnswer(call -> {
            Wso2Profile p = call.getArgument(0);
            documents.put(p.getProfileName(), p);
            return p;
        });
        lenient().when(dcrClient.register(any())).thenReturn(Wso2DcrClient.DcrCredentials.builder()
                .clientId("client-2").clientSecret("secret-2").build());
        lenient().when(tenantsClient.listTenants(any())).thenReturn(Wso2TenantsResponse.builder()
                .success(true).tenants(List.of()).build());
    }

    /** Exactly what the v2 edit form sends: its fields, no tenant, and the untouched password left out. */
    @Test
    void update_likeTheV2EditForm_keepsStoredTenantAndPassword() throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", BASE_URL, "admin", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultWso2Tenant").value("carbon.super"))
                .andExpect(jsonPath("$.notes").value("edited"));

        assertThat(savedDocument().getDefaultWso2Tenant()).isEqualTo("carbon.super");
        assertThat(savedDocument().getPassword()).isEqualTo(STORED_PASSWORD);
        // The OAuth client was re-registered with the stored password, not with nothing.
        assertThat(dcrRequest().getPassword()).isEqualTo(STORED_PASSWORD);
    }

    static Stream<Arguments> passwordValuesThatKeepTheStoredOne() {
        return Stream.of(
                Arguments.of(Named.of("left out", null)),
                Arguments.of(Named.of("null", "null")),
                Arguments.of(Named.of("empty", "\"\"")),
                Arguments.of(Named.of("blank", "\"   \"")));
    }

    @ParameterizedTest(name = "password {0}")
    @MethodSource("passwordValuesThatKeepTheStoredOne")
    void update_withoutNewPassword_keepsStoredPassword(String passwordJson) throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", BASE_URL, "admin", passwordJson, "\"carbon.super\"")))
                .andExpect(status().isOk());

        assertThat(savedDocument().getPassword()).isEqualTo(STORED_PASSWORD);
        assertThat(dcrRequest().getPassword()).isEqualTo(STORED_PASSWORD);
    }

    static Stream<Arguments> tenantValuesThatKeepTheStoredOne() {
        return Stream.of(
                Arguments.of(Named.of("left out", null)),
                Arguments.of(Named.of("null", "null")),
                Arguments.of(Named.of("blank", "\"  \"")));
    }

    @ParameterizedTest(name = "defaultWso2Tenant {0}")
    @MethodSource("tenantValuesThatKeepTheStoredOne")
    void update_withoutTenant_keepsStoredTenant(String tenantJson) throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", BASE_URL, "admin", quoted(NEW_PASSWORD), tenantJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultWso2Tenant").value("carbon.super"));

        assertThat(savedDocument().getDefaultWso2Tenant()).isEqualTo("carbon.super");
    }

    @Test
    void update_withTenant_setsIt() throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", BASE_URL, "admin", null, "\"acme.com\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultWso2Tenant").value("acme.com"));

        assertThat(savedDocument().getDefaultWso2Tenant()).isEqualTo("acme.com");
    }

    @Test
    void update_withNewPassword_replacesStoredPassword_andDoesNotReturnIt() throws Exception {
        String response = mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", BASE_URL, "admin", quoted(NEW_PASSWORD), null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(savedDocument().getPassword()).isEqualTo(NEW_PASSWORD);
        assertThat(dcrRequest().getPassword()).isEqualTo(NEW_PASSWORD);
        assertThat(response).doesNotContain("SENTINEL").doesNotContain("password");
    }

    /**
     * This update, and every discovery, assessment and migration run after it, sends the password
     * to wso2BaseUrl. Moving the profile without it would hand the stored password to whoever
     * picked the new host.
     */
    @Test
    void update_withoutPassword_mayNotMoveWso2BaseUrl() throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", "https://collector.example:9443", "admin", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password is required when wso2BaseUrl changes"));

        verifyNoInteractions(dcrClient);
        verify(repository, never()).save(any());
        assertThat(documents.get("primary").getWso2BaseUrl()).isEqualTo(BASE_URL);
    }

    /** The same host, written with a capital and a trailing slash, is not a move. */
    @Test
    void update_withoutPassword_acceptsTheSameUrlWrittenDifferently() throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", "https://WSO2.acme.test:9443/", "admin", null, null)))
                .andExpect(status().isOk());

        assertThat(dcrRequest().getPassword()).isEqualTo(STORED_PASSWORD);
    }

    /** A stored password is the stored user's; a different user needs its own. */
    @Test
    void update_withoutPassword_mayNotChangeUsername() throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", BASE_URL, "operator", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password is required when username changes"));

        verifyNoInteractions(dcrClient);
        verify(repository, never()).save(any());
    }

    @Test
    void update_movingWso2BaseUrl_withPassword_isAllowed() throws Exception {
        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", "https://wso2-new.acme.test:9443", "admin", quoted(NEW_PASSWORD), null)))
                .andExpect(status().isOk());

        assertThat(savedDocument().getWso2BaseUrl()).isEqualTo("https://wso2-new.acme.test:9443");
        assertThat(savedDocument().getPassword()).isEqualTo(NEW_PASSWORD);
        assertThat(dcrRequest().getWso2BaseUrl()).isEqualTo("https://wso2-new.acme.test:9443");
    }

    @Test
    void update_withoutPassword_whenNoneIsStored_isRejected() throws Exception {
        documents.get("primary").setPassword(null);

        mvc.perform(put("/wso2/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("primary", BASE_URL, "admin", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password is required: this profile has no stored password"));

        verifyNoInteractions(dcrClient);
    }

    @Test
    void create_withoutTenant_isRejected() throws Exception {
        mvc.perform(post("/wso2/profiles/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", "https://wso2-2.acme.test:9443", "admin", quoted(NEW_PASSWORD), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.defaultWso2Tenant").exists());

        verifyNoInteractions(dcrClient, tenantsClient);
        verify(repository, never()).save(any());
    }

    @Test
    void create_withoutPassword_isRejected() throws Exception {
        mvc.perform(post("/wso2/profiles/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", "https://wso2-2.acme.test:9443", "admin", null, "\"carbon.super\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        verifyNoInteractions(dcrClient, tenantsClient);
        verify(repository, never()).save(any());
    }

    /** Create validates the OnCreate group, which must still carry every ordinary constraint. */
    @Test
    void create_stillRequiresEveryOtherField() throws Exception {
        mvc.perform(post("/wso2/profiles/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + NEW_PASSWORD + "\",\"defaultWso2Tenant\":\"carbon.super\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists())
                .andExpect(jsonPath("$.fieldErrors.profileName").exists())
                .andExpect(jsonPath("$.fieldErrors.wso2BaseUrl").exists())
                .andExpect(jsonPath("$.fieldErrors.username").exists());

        verifyNoInteractions(dcrClient, tenantsClient);
    }

    @Test
    void create_withTenantAndPassword_saves() throws Exception {
        mvc.perform(post("/wso2/profiles/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", "https://wso2-2.acme.test:9443", "admin", quoted(NEW_PASSWORD), "\"carbon.super\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("second"))
                .andExpect(jsonPath("$.defaultWso2Tenant").value("carbon.super"));

        assertThat(savedDocument().getPassword()).isEqualTo(NEW_PASSWORD);
    }

    /** The document the last save wrote. */
    private Wso2Profile savedDocument() {
        ArgumentCaptor<Wso2Profile> saved = ArgumentCaptor.forClass(Wso2Profile.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    /** What the one OAuth client registration was sent. */
    private Wso2DcrClient.DcrRequest dcrRequest() {
        ArgumentCaptor<Wso2DcrClient.DcrRequest> sent = ArgumentCaptor.forClass(Wso2DcrClient.DcrRequest.class);
        verify(dcrClient).register(sent.capture());
        return sent.getValue();
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    /**
     * A request body; {@code passwordJson} and {@code tenantJson} are JSON literals, or null to leave
     * the field out. The other fields are the ones on the v2 edit form.
     */
    private static String body(String profileName, String baseUrl, String username,
                               String passwordJson, String tenantJson) {
        return "{\"companyName\":\"" + COMPANY + "\",\"profileName\":\"" + profileName + "\","
                + "\"wso2BaseUrl\":\"" + baseUrl + "\",\"username\":\"" + username + "\","
                + (passwordJson == null ? "" : "\"password\":" + passwordJson + ",")
                + (tenantJson == null ? "" : "\"defaultWso2Tenant\":" + tenantJson + ",")
                + "\"trustSelfSigned\":true,\"notes\":\"edited\",\"userEmail\":\"editor@acme.test\"}";
    }
}
