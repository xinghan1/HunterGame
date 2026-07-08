package com.xigua.cumulus.form;

import java.util.function.Consumer;

public final class SimpleForm {
   private SimpleForm() {
   }

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder {
      public Builder title(String title) {
         return this;
      }

      public Builder content(String content) {
         return this;
      }

      public Builder button(String text) {
         return this;
      }

      public Builder validResultHandler(Consumer<Response> handler) {
         return this;
      }
   }

   public static final class Response {
      public int clickedButtonId() {
         return -1;
      }
   }
}
