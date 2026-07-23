package net.minecraft.network.chat;

import com.mojang.authlib.GameProfile;
import java.time.Duration;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.SignatureValidator;
import net.minecraft.world.entity.player.ProfilePublicKey;

public record RemoteChatSession(UUID sessionId, ProfilePublicKey profilePublicKey) {
    public SignedMessageValidator createMessageValidator(Duration gracePeriod) {
        return new SignedMessageValidator.KeyBased(this.profilePublicKey.createSignatureValidator(), () -> this.profilePublicKey.data().hasExpired(gracePeriod));
    }

    public SignedMessageChain.Decoder createMessageDecoder(UUID profileId) {
        return new SignedMessageChain(profileId, this.sessionId).decoder(this.profilePublicKey);
    }

    public RemoteChatSession.Data asData() {
        return new RemoteChatSession.Data(this.sessionId, this.profilePublicKey.data());
    }

    public boolean hasExpired() {
        return this.profilePublicKey.data().hasExpired();
    }

    public record Data(UUID sessionId, ProfilePublicKey.Data profilePublicKey) {
        public static RemoteChatSession.Data read(FriendlyByteBuf input) {
            return new RemoteChatSession.Data(input.readUUID(), new ProfilePublicKey.Data(input));
        }

        public static void write(FriendlyByteBuf output, RemoteChatSession.Data data) {
            output.writeUUID(data.sessionId);
            data.profilePublicKey.write(output);
        }

        public RemoteChatSession validate(GameProfile profile, SignatureValidator serviceSignatureValidator) throws ProfilePublicKey.ValidationException {
            return new RemoteChatSession(this.sessionId, ProfilePublicKey.createValidated(serviceSignatureValidator, profile.id(), this.profilePublicKey));
        }
    }
}
