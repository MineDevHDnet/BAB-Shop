package net.minedevhd.bytebitshop.api;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

public final class PlayerCertificateService {
    private static final Gson GSON = new Gson();

    private volatile String cachedToken;
    private volatile Certificate cached;

    public CompletableFuture<Certificate> get() {
        final String token = Minecraft.getMinecraft().getSession().getToken();
        Certificate local = cached;
        if (local != null && token.equals(cachedToken) && local.expirationTime > System.currentTimeMillis() + 60_000L)
            return CompletableFuture.completedFuture(local);

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.minecraftservices.com/player/certificates").openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Content-Length", "0");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setDoOutput(true);
                connection.getOutputStream().close();

                if (connection.getResponseCode() >= 400)
                    throw new IllegalStateException("Minecraft-Zertifikat HTTP " + connection.getResponseCode());

                InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
                CertResponse response;
                try {
                    response = GSON.fromJson(reader, CertResponse.class);
                } finally {
                    reader.close();
                }

                if (response == null || response.keyPair == null || response.keyPair.privateKey == null || response.keyPair.publicKey == null)
                    throw new IllegalStateException("Ungültige Zertifikatsantwort");

                String privateRaw = stripPem(response.keyPair.privateKey);
                String publicRaw = stripPem(response.keyPair.publicKey);
                PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(privateRaw))
                );

                Certificate certificate = new Certificate(
                    privateKey,
                    publicRaw,
                    response.publicKeySignatureV2,
                    Instant.parse(response.expiresAt).toEpochMilli()
                );
                cachedToken = token;
                cached = certificate;
                return certificate;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static String stripPem(String value) {
        return value
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim();
    }

    public static final class Certificate {
        public final PrivateKey privateKey;
        public final String publicKey;
        public final String keySignature;
        public final long expirationTime;

        Certificate(PrivateKey privateKey, String publicKey, String keySignature, long expirationTime) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.keySignature = keySignature;
            this.expirationTime = expirationTime;
        }
    }

    private static final class CertResponse {
        KeyPairData keyPair;
        String publicKeySignatureV2;
        String expiresAt;
    }

    private static final class KeyPairData {
        String privateKey;
        String publicKey;
    }
}
