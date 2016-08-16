package io.ably.lib.http;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.util.StatusHandler;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by gokhanbarisaker on 2/2/16.
 */
public class HttpTest {

    private static final String PATTERN_HOST_FALLBACK = "(?i)[a-e]\\.ably-realtime.com";

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private static RouterNanoHTTPD server;


    @BeforeClass
    public static void setUp() throws IOException {
        server = new RouterNanoHTTPD(27331);
        server.addRoute("/status/:code", StatusHandler.class);
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

        while (!server.wasStarted()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @AfterClass
    public static void tearDown() {
        server.stop();
    }


    /*******************************************
     * Spec: RSC15
     *******************************************/

    /**
     * <p>
     * Validates {@code Http} performs fallback behavior httpMaxRetryCount number of times at most,
     * when host & fallback hosts are unreachable. Then, finally throws an error.
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_ably_execute_fallback() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.tls = false;
        /* Select a port that will be refused immediately by the production host */
        options.port = 7777;

		/* Create a list to capture the host of URL arguments that get called with httpExecute method.
         * This will later be used to validate hosts used for requests
		 */
        ArrayList<String> urlHostArgumentStack = new ArrayList<>(4);

		/*
         * Extend the http, so that we can capture provided url arguments without mocking and changing its organic behavior.
		 */
        Http http = new Http(options, null) {
            /* Store only string representations to avoid try/catch blocks */
            List<String> urlArgumentStack;

            @Override
            <T> T httpExecute(URL url, String method, Param[] headers, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
                // Store a copy of given argument
                urlArgumentStack.add(url.getHost());

                // Execute the original method without changing behavior
                return super.httpExecute(url, method, headers, requestBody, responseHandler);
            }

            public Http setUrlArgumentStack(List<String> urlArgumentStack) {
                this.urlArgumentStack = urlArgumentStack;
                return this;
            }
        }.setUrlArgumentStack(urlHostArgumentStack);

        http.setHost(Defaults.HOST_REST);

        try {
            http.ablyHttpExecute(
                    "/path/to/fallback", /* Ignore path */
                    Http.GET, /* Ignore method */
                    new Param[0], /* Ignore headers */
                    new Param[0], /* Ignore params */
                    null, /* Ignore requestBody */
                    null /* Ignore requestHandler */
            );
        } catch (AblyException e) {
            /* Verify that,
             * 		- an {@code AblyException} with {@code ErrorInfo} having a `404` status code is thrown.
			 */
            ErrorInfo expectedErrorInfo = new ErrorInfo("Connection failed; no host available", 404, 80000);
            assertThat(e, new ErrorInfoMatcher(expectedErrorInfo));
        }

		/* Verify that,
         * 		- {code Http#httpExecute} have been called with (httpMaxRetryCount + 1) URLs
		 * 		- first call executed against production rest host
		 * 		- other calls executed against a random fallback host
		 */
        int expectedCallCount = options.httpMaxRetryCount + 1;
        assertThat(urlHostArgumentStack.size(), is(equalTo(expectedCallCount)));
        assertThat(urlHostArgumentStack.get(0), is(equalTo(Defaults.HOST_REST)));

        for (int i = 1; i < expectedCallCount; i++) {
            urlHostArgumentStack.get(i).matches(PATTERN_HOST_FALLBACK);
        }
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates http is not using any fallback host when we receive valid response from http's host
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_nofallback() throws Exception {
        Http http = Mockito.spy(new Http(new ClientOptions(), null));

        String responseExpected = "Lorem Ipsum";
        String hostExpected = Defaults.HOST_REST;
        http.setHost(hostExpected);
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock http */
        doReturn(responseExpected) /* Provide response */
                .when(http) /* when following method is executed on {@code Http} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid rest host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );

        String responseActual = (String) http.ablyHttpExecute(
                "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(Http.RequestBody.class), /* Ignore */
                mock(Http.ResponseHandler.class) /* Ignore */
        );


        /* Verify
         *   - http call executed once,
         *   - with given host,
         *   - and delivered expected response */
        verify(http, times(1))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        url.capture(), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );

        assertThat(url.getValue().getHost(), is(equalTo(hostExpected)));
        assertThat(responseActual, is(equalTo(responseExpected)));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates http is using a fallback host when HostFailedException thrown
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_singlefallback() throws Exception {
        Http http = Mockito.spy(new Http(new ClientOptions(), null));

        String hostExpectedPattern = PATTERN_HOST_FALLBACK;
        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);
        http.setHost(Defaults.HOST_REST);

        /* Partially mock http */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception once (1) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with the second call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(http) /* when following method is executed on {@code Http} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid fallback url */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );

        /* Call method with real parameters */
        String responseActual = (String) http.ablyHttpExecute(
                "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(Http.RequestBody.class), /* Ignore */
                mock(Http.ResponseHandler.class) /* Ignore */
        );


        /* Verify
         *   - http call executed twice (one for prod host and 1 for fallback),
         *   - last call performed against a fallback host,
         *   - and fallback host delivered expected response */

        verify(http, times(2))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        url.capture(), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );

        assertThat(url.getValue().getHost().matches(hostExpectedPattern), is(true));
        assertThat(responseActual, is(equalTo(responseExpected)));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates http is using different hosts when HostFailedException happened multiple times.
     * </p>
     * <p>
     * Spec: RSC15a
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_multiplefallback() throws Exception {
        Http http = Mockito.spy(new Http(new ClientOptions(), null));

        String hostExpectedPattern = PATTERN_HOST_FALLBACK;
        String responseExpected = "Lorem Ipsum";
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);
        http.setHost(Defaults.HOST_REST);

        /* Partially mock http */
        Answer answer = new GrumpyAnswer(
                2, /* Throw exception twice (2) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                responseExpected /* Then return a valid response with third call */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(http) /* when following method is executed on {@code Http} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid fallback url */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );

        String responseActual = (String) http.ablyHttpExecute(
                "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(Http.RequestBody.class), /* Ignore */
                mock(Http.ResponseHandler.class) /* Ignore */
        );


        /* Verify
         *   - http call executed thrice,
         *   - with 2 fallback hosts,
         *   - each host having a unique value,
         *   - and delivered expected response */

        assertThat(url.getAllValues().get(1).getHost().matches(hostExpectedPattern), is(true));
        assertThat(url.getAllValues().get(2).getHost().matches(hostExpectedPattern), is(true));
        assertThat(url.getAllValues().get(1), is(not(equalTo(url.getAllValues().get(2)))));

        assertThat(responseActual, is(equalTo(responseExpected)));

        /* Verify call causes captor to capture same arguments twice.
         * Do the validation, after we completed the {@code ArgumentCaptor} related assertions */
        verify(http, times(3))
                .httpExecute( /* Just validating call counter. Ignore following parameters */
                        url.capture(), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@code Http} is using its host (non-fallback-host) first
     * when a consecutive call happens
     * </p>
     * <p>
     * Spec: -
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_consecutivecall() throws Exception {
        Http http = Mockito.spy(new Http(new ClientOptions(), null));

        String hostExpected = Defaults.HOST_REST;
        http.setHost(hostExpected);
        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);

        /* Partially mock http */
        Answer answer = new GrumpyAnswer(
                1, /* Throw exception once (1) */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                "Lorem Ipsum" /* Ignore */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(http) /* when following method is executed on {@code Http} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert fallback behavior executed with valid fallback url */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );

        http.ablyHttpExecute(
                "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(Http.RequestBody.class), /* Ignore */
                mock(Http.ResponseHandler.class) /* Ignore */
        );

        /* Verify there was a fallback with first call */
        assertThat(url.getValue().getHost().matches(PATTERN_HOST_FALLBACK), is(true));

        /* Update behavior to perform a call without a fallback */
        url = ArgumentCaptor.forClass(URL.class);
        doReturn("Lorem Ipsum") /* Return some response string that we will ignore */
                .when(http) /* when following method is executed on {@code Http} instance */
                .httpExecute(
                        url.capture(), /* capture url arguments passed down httpExecute to assert http call successfully executed against `rest.ably.io` host */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );

        http.ablyHttpExecute(
                "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(Http.RequestBody.class), /* Ignore */
                mock(Http.ResponseHandler.class) /* Ignore */
        );

        /* Verify second call was called with http host */
        assertThat(url.getValue().getHost().equals(hostExpected), is(true));
    }

    /**
     * <strong>This method mocks the API behavior</strong>
     * <p>
     * Validates {@code Http} is throwing an exception,
     * when connection to host failed more than allowed count ({@code Defaults.HTTP_MAX_RETRY_COUNT})
     * </p>
     * <p>
     * Spec: -
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_excessivefallback() throws AblyException {
        ClientOptions options = new ClientOptions();
        Http http = Mockito.spy(new Http(options, null));

        ArgumentCaptor<URL> url = ArgumentCaptor.forClass(URL.class);
        int excessiveFallbackCount = options.httpMaxRetryCount + 1;

        /* Partially mock http */
        Answer answer = new GrumpyAnswer(
                excessiveFallbackCount, /* Throw exception more than httpMaxRetryCount number of times */
                AblyException.fromErrorInfo(ErrorInfo.fromResponseStatus("Internal Server Error", 500)), /* That is HostFailedException */
                "Lorem Ipsum" /* Ignore */
        );

        doAnswer(answer) /* Behave as defined above */
                .when(http) /* when following method is executed on {@code Http} instance */
                .httpExecute(
                        url.capture(), /* Ignore */
                        anyString(), /* Ignore */
                        aryEq(new Param[0]), /* Ignore */
                        any(Http.RequestBody.class), /* Ignore */
                        any(Http.ResponseHandler.class) /* Ignore */
                );


        /* Verify
         *   - ably exception with 404 status code is thrown
         */
        ErrorInfo expectedErrorInfo = new ErrorInfo("Connection failed; no host available", 404, 80000);
        thrown.expect(AblyException.class);
        thrown.expect(new ErrorInfoMatcher(expectedErrorInfo));

        http.ablyHttpExecute(
                "", /* Ignore */
                "", /* Ignore */
                new Param[0], /* Ignore */
                new Param[0], /* Ignore */
                mock(Http.RequestBody.class), /* Ignore */
                mock(Http.ResponseHandler.class) /* Ignore */
        );
    }

    /**
     * <p>
     * Validates {@code Http#httpExecute} is throwing an {@code HostFailedException},
     * when api returns a response code between 500 and 504
     * </p>
     * <p>
     * Spec: RSC15d
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_response_50x() throws AblyException, MalformedURLException {
        URL url;
        Http http = new Http(new ClientOptions(), null);
        Http.RequestBody requestBody = new Http.ByteArrayRequestBody(new byte[0], NanoHTTPD.MIME_PLAINTEXT);

        AblyException.HostFailedException hfe;

        for (int statusCode = 500; statusCode <= 504; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);
            hfe = null;

            try {
                http.httpExecute(url, Http.GET, new Param[0], requestBody, null);
            } catch (AblyException.HostFailedException e) {
                hfe = e;
            }

            Assert.assertNotNull("Status code " + statusCode + " should throw an exception", hfe);
        }
    }

    /**
     * <p>
     * Validates {@code Http#httpExecute} isn't throwing an {@code HostFailedException},
     * when api returns a non-server-error response code (Informational 1xx,
     * Multiple Choices 3xx, Client Error 4xx)
     * </p>
     * <p>
     * Spec: RSC15d
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void http_execute_response_non5xx() throws AblyException, MalformedURLException {
        URL url;
        Http http = new Http(new ClientOptions(), null);
        Http.RequestBody requestBody = new Http.ByteArrayRequestBody(new byte[0], NanoHTTPD.MIME_PLAINTEXT);


        /* Informational 1xx */

        for (int statusCode = 100; statusCode <= 101; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);

            try {
                http.httpExecute(url, Http.GET, new Param[0], requestBody, null);
            } catch (AblyException.HostFailedException e) {
                Assert.fail("Informal status code " + statusCode + " shouldn't throw an exception");
            } catch (Exception e) {
                /* non HostFailedExceptions are ignored */
            }
        }


        /* Informational 3xx */

        for (int statusCode = 300; statusCode <= 307; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);

            try {
                http.httpExecute(url, Http.GET, new Param[0], requestBody, null);
            } catch (AblyException.HostFailedException e) {
                Assert.fail("Multiple choices status code " + statusCode + " shouldn't throw an exception");
            } catch (Exception e) {
                /* non HostFailedExceptions are ignored */
            }
        }


        /* Informational 4xx */

        for (int statusCode = 400; statusCode <= 417; statusCode++) {
            url = new URL("http://localhost:" + server.getListeningPort() + "/status/" + statusCode);

            try {
                http.httpExecute(url, Http.GET, new Param[0], requestBody, null);
            } catch (AblyException.HostFailedException e) {
                Assert.fail("Client error status code " + statusCode + " shouldn't throw an exception");
            } catch (Exception e) {
                /* non HostFailedExceptions are ignored */
            }
        }
    }

    /**
     * The header X-Ably-Lib: [lib][.optional variant]?-[version]
     * should be included in all REST requests to the Ably endpoint where
     * <p/>
     * -[lib] is the name of the library such as js for ably-js,
     * -[.optional variant] is an optional library variant,
     * -[version] is the full client library version (ex.0.9.2).
     * <p/>
     * For example, Javascript library would use the header X-Ably-Lib: js-0.9.2
     * <p/>
     * <p/>
     * <p>
     * Spec: RSC7b
     * </p>
     */
    @Test
    public void check_lib_header() {
        try {
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            AblyRest ably = new AblyRest(opts);

            Http http = new Http(ably.options, ably.auth) {
                @Override
                <T> T ablyHttpExecute(String path, String method, Param[] headers, Param[] params, RequestBody requestBody, ResponseHandler<T> responseHandler) throws AblyException {
                    Assert.assertTrue("Expected header X-Ably-Lib", Param.containsKey(headers, "X-Ably-Lib"));
                    for (Param header : headers) {
                        if (header.key.equals("X-Ably-Lib"))
                            Assert.assertEquals(header.value, String.format("java-%s", "0.8.2"));
                    }
                    return super.ablyHttpExecute(path, method, headers, params, requestBody, responseHandler);
                }
            };
            http.setHost(Defaults.HOST_REST);
            http.ablyHttpExecute(
                    "", /* Ignore */
                    "", /* Ignore */
                    new Param[0], /* Empty */ //new Param[]{new Param("X-Ably-Lib", String.format("java-%s", "0.8.2"))},
                    new Param[0], /* Ignore */
                    mock(Http.RequestBody.class), /* Ignore */
                    mock(Http.ResponseHandler.class) /* Ignore */
            );
        } catch (AblyException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void check_lib_header_ably() {
        try {
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            AblyRest ably = new AblyRest(opts);

            String ably_lib_header = ably.options.headers.get("X-Ably-Lib");

            Assert.assertNotNull("Expected header X-Ably-Lib", ably_lib_header);
            Assert.assertEquals(ably_lib_header, String.format("java-%s", "0.8.2"));
        } catch (AblyException e) {
            e.printStackTrace();
        }
    }


    /*********************************************
     * Minions
     *********************************************/


    static class GrumpyAnswer implements Answer<String> {
        private int grumpinessLevel;
        private Throwable nope;
        private String value;

        /**
         * Throws grumpinessLevel number of nope to you and then gives its response properly.
         *
         * @param grumpinessLevel Quantity of nope that will be thrown into your face, each time.
         * @param nope            Expected nope
         * @param value           Expected value that will be returned after grumpiness level goes below or equal to 0.
         */
        public GrumpyAnswer(int grumpinessLevel, Throwable nope, String value) {
            this.grumpinessLevel = grumpinessLevel;
            this.nope = nope;
            this.value = value;
        }

        @Override
        public String answer(InvocationOnMock invocation) throws Throwable {
            if (grumpinessLevel-- > 0) {
                throw nope;
            }

            return value;
        }
    }

    static class ErrorInfoMatcher extends TypeSafeMatcher<AblyException> {
        ErrorInfo errorInfo;

        public ErrorInfoMatcher(ErrorInfo errorInfo) {
            super();
            this.errorInfo = errorInfo;
        }

        @Override
        protected boolean matchesSafely(AblyException item) {
            return errorInfo.code == item.errorInfo.code &&
                    errorInfo.statusCode == item.errorInfo.statusCode;
        }

        @Override
        protected void describeMismatchSafely(AblyException item, Description mismatchDescription) {
            mismatchDescription.appendText(item.errorInfo.toString());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(errorInfo.toString());
        }
    }
}
