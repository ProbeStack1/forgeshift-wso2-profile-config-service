package com.forgeshift.profile.config.exception;

import com.forgeshift.profile.config.controller.GitProfileController;
import com.forgeshift.profile.config.dto.GitProfileResponse;
import com.forgeshift.profile.config.service.GitProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A failed request must say why, in JSON, to a caller that asked for something else.
 *
 * <p>Without a preset content type Spring negotiated the error body against the
 * request's Accept header, found no converter that writes it as
 * {@code application/octet-stream}, and abandoned the handler. The exception fell
 * through to the container's error page, which the security chain answered with
 * 401 {@code invalid_jwt_token} - so a profile that does not exist looked exactly
 * like being signed out.</p>
 *
 * <p>A read that did not fail, but whose answer the caller cannot accept, is a 406. The
 * catch-all used to report it as a 500, as though the service had broken.</p>
 */
class GlobalExceptionHandlerTest {

    private final GitProfileService profiles = mock(GitProfileService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new GitProfileController(profiles))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void aMissingProfileIsA404WithItsReasonEvenWhenBytesWereAskedFor() throws Exception {
        when(profiles.get("g1", "forgecrux")).thenThrow(new ProfileNotFoundException("Git profile not found"));

        mvc.perform(readProfile())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Git profile not found"));
    }

    @Test
    void anUnreachableStoreIsA500WithItsReasonEvenWhenBytesWereAskedFor() throws Exception {
        when(profiles.get("g1", "forgecrux"))
                .thenThrow(new DataAccessResourceFailureException(
                        "Timed out after 1500 ms while waiting to connect"));

        mvc.perform(readProfile())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(containsString("Timed out")));
    }

    @Test
    void aProfileTheCallerCannotAcceptIsA406NamingWhatItCanSend() throws Exception {
        when(profiles.get("g1", "forgecrux")).thenReturn(GitProfileResponse.builder().id("g1").build());

        mvc.perform(readProfile())
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.message").value(containsString("application/json")));
    }

    /** A profile read from a caller that accepts only bytes. */
    private static MockHttpServletRequestBuilder readProfile() {
        return get("/git/profiles/g1")
                .param("companyName", "forgecrux")
                .accept(MediaType.APPLICATION_OCTET_STREAM);
    }
}
