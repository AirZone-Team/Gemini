package com.mojang.realmsclient.gui.screens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record UploadResult(int statusCode, @Nullable String errorMessage) {
    public @Nullable String getSimplifiedErrorMessage() {
        if (this.statusCode >= 200 && this.statusCode < 300) {
            return null;
        } else {
            return this.statusCode == 400 && this.errorMessage != null ? this.errorMessage : String.valueOf(this.statusCode);
        }
    }

    public static class Builder {
        private int statusCode = -1;
        private @Nullable String errorMessage;

        public UploadResult.Builder withStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public UploadResult.Builder withErrorMessage(@Nullable String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public UploadResult build() {
            return new UploadResult(this.statusCode, this.errorMessage);
        }
    }
}
