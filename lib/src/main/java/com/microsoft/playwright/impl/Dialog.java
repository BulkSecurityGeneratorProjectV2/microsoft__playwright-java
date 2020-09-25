/**
 * Copyright (c) Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.playwright.impl;

import com.google.gson.JsonObject;

public class Dialog extends ChannelOwner {
  private boolean handled;
  Dialog(ChannelOwner parent, String type, String guid, JsonObject initializer) {
    super(parent, type, guid, initializer);
  }

  public void accept(String promptText) {
    handled = true;
    JsonObject params = new JsonObject();
    if (promptText != null)
      params.addProperty("promptText", promptText);
    sendMessageNoWait("accept", params);
  }

  public void dismiss() {
    handled = true;
    sendMessageNoWait("dismiss", new JsonObject());
  }

  public String defaultValue() {
    return initializer.get("defaultValue").getAsString();
  }

  public String message() {
    return initializer.get("message").getAsString();
  }

//  public enum Type { Alert, BeforeUnload, Confirm, Prompt }
  public String type() {
     return initializer.get("type").getAsString();
  }

  boolean isHandled() {
    return handled;
  }
}
