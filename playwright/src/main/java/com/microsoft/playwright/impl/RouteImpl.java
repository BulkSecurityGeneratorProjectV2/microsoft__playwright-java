/*
 * Copyright (c) Microsoft Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.playwright.impl;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.microsoft.playwright.impl.Utils.convertType;

public class RouteImpl extends ChannelOwner implements Route {
  private boolean handled;

  public RouteImpl(ChannelOwner parent, String type, String guid, JsonObject initializer) {
    super(parent, type, guid, initializer);
  }

  @Override
  public void abort(String errorCode) {
    startHandling();
    withLogging("Route.abort", () -> {
      JsonObject params = new JsonObject();
      params.addProperty("errorCode", errorCode);
      sendMessageAsync("abort", params);
    });
  }

  boolean isHandled() {
    return handled;
  }

  @Override
  public void resume(ResumeOptions options) {
    startHandling();
    applyOverrides(convertType(options, FallbackOptions.class));
    withLogging("Route.resume", () -> resumeImpl(request().fallbackOverridesForResume()));
  }

  @Override
  public void fallback(FallbackOptions options) {
    if (handled) {
      throw new PlaywrightException("Route is already handled!");
    }
    applyOverrides(options);
  }

  private void applyOverrides(FallbackOptions options) {
    if (options == null) {
      return;
    }
    RequestImpl.FallbackOverrides overrides = new RequestImpl.FallbackOverrides();
    overrides.url = options.url;
    overrides.method = options.method;
    overrides.headers = options.headers;
    if (options.postData != null) {
      overrides.postData = getPostDataBytes(options.postData);
    }
    request().applyFallbackOverrides(overrides);
  }

  private void resumeImpl(RequestImpl.FallbackOverrides options) {
    JsonObject params = new JsonObject();
    if (options != null) {
      if (options.url != null) {
        params.addProperty("url", options.url);
      }
      if (options.method != null) {
        params.addProperty("method", options.method);
      }
      if (options.headers != null) {
        params.add("headers", Serialization.toProtocol(options.headers));
      }
      if (options.postData != null) {
        String base64 = Base64.getEncoder().encodeToString(options.postData);
        params.addProperty("postData", base64);
      }
    }
    sendMessageAsync("continue", params);
  }

  private static byte[] getPostDataBytes(Object postData) {
    if (postData instanceof byte[]) {
      return (byte[]) postData;
    }
    if (postData instanceof String) {
      return ((String) postData).getBytes(StandardCharsets.UTF_8);
    }
    throw new PlaywrightException("postData must be either String or byte[], found: " + postData.getClass().getName());
  }

  @Override
  public void fulfill(FulfillOptions options) {
    startHandling();
    withLogging("Route.fulfill", () -> fulfillImpl(options));
  }

  private void fulfillImpl(FulfillOptions options) {
    if (options == null) {
      options = new FulfillOptions();
    }

    Integer status = options.status;
    Map<String, String> headersOption = options.headers;
    String fetchResponseUid = null;

    if (options.response != null) {
      if (status == null) {
        status = options.response.status();
      }
      if (headersOption == null) {
        headersOption = options.response.headers();
      }
    }
    if (status == null) {
      status = 200;
    }
    String body = null;
    boolean isBase64 = false;
    int length = 0;
    if (options.path != null) {
      try {
        byte[] buffer = Files.readAllBytes(options.path);
        body = Base64.getEncoder().encodeToString(buffer);
        isBase64 = true;
        length = buffer.length;
      } catch (IOException e) {
        throw new PlaywrightException("Failed to read from file: " + options.path, e);
      }
    } else if (options.body != null) {
      body = options.body;
      isBase64 = false;
      length = body.getBytes().length;
    } else if (options.bodyBytes != null) {
      body = Base64.getEncoder().encodeToString(options.bodyBytes);
      isBase64 = true;
      length = options.bodyBytes.length;
    } else if (options.response != null) {
      APIResponseImpl response = (APIResponseImpl) options.response;
      if (response.context.connection == connection) {
        fetchResponseUid = response.fetchUid();
      } else {
        byte[] bodyBytes = response.body();
        body = Base64.getEncoder().encodeToString(bodyBytes);
        isBase64 = true;
        length = bodyBytes.length;
      }
    }


    Map<String, String> headers = new LinkedHashMap<>();
    if (headersOption != null) {
      for (Map.Entry<String, String> h : headersOption.entrySet()) {
        headers.put(h.getKey().toLowerCase(), h.getValue());
      }
    }
    if (options.contentType != null) {
      headers.put("content-type", options.contentType);
    } else if (options.path != null) {
      headers.put("content-type", Utils.mimeType(options.path));
    }
    if (length != 0 && !headers.containsKey("content-length")) {
      headers.put("content-length", Integer.toString(length));
    }
    JsonObject params = new JsonObject();
    params.addProperty("status", status);
    params.add("headers", Serialization.toProtocol(headers));
    params.addProperty("isBase64", isBase64);
    params.addProperty("body", body);
    if (fetchResponseUid != null) {
      params.addProperty("fetchResponseUid", fetchResponseUid);
    }
    sendMessageAsync("fulfill", params);
  }

  @Override
  public RequestImpl request() {
    return connection.getExistingObject(initializer.getAsJsonObject("request").get("guid").getAsString());
  }

  private void startHandling() {
    if (handled) {
      throw new PlaywrightException("Route is already handled!");
    }
    handled = true;
  }
}
