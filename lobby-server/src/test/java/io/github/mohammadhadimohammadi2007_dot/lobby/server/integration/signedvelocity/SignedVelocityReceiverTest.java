package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.signedvelocity;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.signedvelocity.SignedVelocityReceiver.Kind;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.signedvelocity.SignedVelocityReceiver.Verdict;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignedVelocityReceiverTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private static byte[] message(String... fields) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (String field : fields) {
                out.writeUTF(field);
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void verdictsAreUsedInOrder() throws IOException {
        SignedVelocityReceiver receiver = new SignedVelocityReceiver();
        receiver.receive(message(PLAYER.toString(), "CHAT_RESULT", "CANCEL"));
        receiver.receive(message(PLAYER.toString(), "CHAT_RESULT", "MODIFY", "hello"));
        receiver.receive(message(PLAYER.toString(), "CHAT_RESULT", "ALLOWED"));

        assertEquals(new Verdict(Kind.CANCEL, null), receiver.nextChatVerdict(PLAYER));
        assertEquals(new Verdict(Kind.MODIFY, "hello"), receiver.nextChatVerdict(PLAYER));
        assertEquals(Verdict.ALLOW, receiver.nextChatVerdict(PLAYER));
    }

    @Test
    void missingVerdictAllows() {
        assertEquals(Verdict.ALLOW, new SignedVelocityReceiver().nextChatVerdict(PLAYER));
    }

    @Test
    void commandVerdictsDoNotAffectChat() throws IOException {
        SignedVelocityReceiver receiver = new SignedVelocityReceiver();
        receiver.receive(message(PLAYER.toString(), "COMMAND_RESULT", "CANCEL"));

        assertEquals(Verdict.ALLOW, receiver.nextChatVerdict(PLAYER));
    }

    @Test
    void badMessagesAreIgnored() throws IOException {
        SignedVelocityReceiver receiver = new SignedVelocityReceiver();
        receiver.receive(message("not-a-uuid", "CHAT_RESULT", "CANCEL"));
        receiver.receive(message(PLAYER.toString(), "CHAT_RESULT", "EXPLODE"));
        receiver.receive(message(PLAYER.toString(), "OTHER", "CANCEL"));
        receiver.receive(message(PLAYER.toString(), "CHAT_RESULT", "MODIFY")); // Missing the new text.
        receiver.receive(new byte[]{1, 2, 3});

        assertEquals(Verdict.ALLOW, receiver.nextChatVerdict(PLAYER));
    }

    @Test
    void oldVerdictsAreDropped() throws IOException {
        SignedVelocityReceiver receiver = new SignedVelocityReceiver();
        receiver.receive(message(PLAYER.toString(), "CHAT_RESULT", "CANCEL"));
        for (int i = 0; i < 40; i++) {
            receiver.receive(message(PLAYER.toString(), "CHAT_RESULT", "ALLOWED"));
        }

        assertEquals(Verdict.ALLOW, receiver.nextChatVerdict(PLAYER));
    }
}
