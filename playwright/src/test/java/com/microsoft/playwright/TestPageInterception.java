package com.microsoft.playwright;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class TestPageInterception extends TestBase {
  @Test
  void shouldWorkWithNavigationSmoke() {
    HashMap<String, Request> requests = new HashMap<>();
    page.route("**/*", route -> {
      String[] parts = route.request().url().split("/");
      requests.put(parts[parts.length - 1], route.request());
      route.resume();
    });
    server.setRedirect("/rrredirect", "/frames/one-frame.html");
    page.navigate(server.PREFIX + "/rrredirect");
    assertTrue(requests.get("rrredirect").isNavigationRequest());
    assertTrue(requests.get("frame.html").isNavigationRequest());
    assertFalse(requests.get("script.js").isNavigationRequest());
    assertFalse(requests.get("style.css").isNavigationRequest());
  }

  @Test
  void shouldInterceptAfterAServiceWorker() {
    page.navigate(server.PREFIX + "/serviceworkers/fetchdummy/sw.html");
    page.evaluate("() => window['activationPromise']");

    // Sanity check.
    Object swResponse = page.evaluate("() => window['fetchDummy']('foo')");
    assertEquals("responseFromServiceWorker:foo", swResponse);

    page.route("**/foo", route -> {
      int slash = route.request().url().lastIndexOf("/");
      String name = route.request().url().substring(slash + 1);
      route.fulfill(new Route.FulfillOptions()
        .setStatus(200)
        .setContentType("text/css").setBody("responseFromInterception:" + name));
    });

    // Page route is applied after service worker fetch event.
    Object swResponse2 = page.evaluate("() => window['fetchDummy']('foo')");
    assertEquals("responseFromServiceWorker:foo", swResponse2);

    // Page route is not applied to service worker initiated fetch.
    Object nonInterceptedResponse = page.evaluate("() => window['fetchDummy']('passthrough')");
    assertEquals("FAILURE: Not Found", nonInterceptedResponse);

    // Firefox does not want to fetch the redirect for some reason.
    if (!isFirefox()) {
      // Page route is not applied to service worker initiated fetch with redirect.
      server.setRedirect("/serviceworkers/fetchdummy/passthrough", "/simple.json");
      Object redirectedResponse = page.evaluate("() => window['fetchDummy']('passthrough')");
      assertEquals("{\"foo\": \"bar\"}\n", redirectedResponse);
    }
  }

}
