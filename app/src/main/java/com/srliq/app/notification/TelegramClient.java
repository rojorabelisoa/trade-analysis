package com.srliq.app.notification;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appel de {@code sendMessage} de l'API Bot Telegram (HTTPS, {@link RestClient}).
 *
 * <p>Le jeton fait partie de l'URL de l'API : il ne doit apparaître ni dans les journaux ni dans les
 * exceptions. Toute erreur est donc convertie en {@link SendException} dont le message est nettoyé
 * ({@link #redact}) et qui ne garde PAS l'exception d'origine (sa trace contiendrait l'URL).
 */
@Component
public class TelegramClient {

    /** Échec d'envoi ; message sans jeton, sans cause. */
    public static class SendException extends Exception {
        public SendException(String message) {
            super(message, null, false, false);
        }
    }

    private final TelegramProperties props;
    private final RestClient rest;

    public TelegramClient(TelegramProperties props) {
        this.props = props;
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(props.getReadTimeoutSeconds()));
        // Client dédié, sans observation ni intercepteur partagé : l'URL (avec le jeton) ne sort pas d'ici.
        this.rest = RestClient.builder().requestFactory(factory).build();
        if (props.enabled()) {
            props.validatedBaseUrl(); // URL invalide : l'application ne démarre pas
        }
    }

    public boolean enabled() {
        return props.enabled();
    }

    /** Envoie {@code text} (texte brut) à la discussion configurée. */
    public void send(String text) throws SendException {
        if (!props.enabled()) {
            throw new SendException("Telegram non configuré");
        }
        String token = props.getBotToken();
        try {
            URI uri = URI.create(props.validatedBaseUrl() + "/bot" + token + "/sendMessage");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", props.getChatId());
            body.put("text", text);
            body.put("disable_web_page_preview", true);
            Map<?, ?> response = rest.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
                Object desc = response == null ? null : response.get("description");
                throw new SendException("Réponse Telegram négative : " + redact(String.valueOf(desc), token));
            }
        } catch (SendException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SendException(e.getClass().getSimpleName() + " : " + redact(e.getMessage(), token));
        }
    }

    /** Retire le jeton d'un texte (URL, message d'erreur) et limite sa longueur. */
    static String redact(String text, String token) {
        if (text == null) {
            return "";
        }
        String out = text;
        if (token != null && !token.isEmpty()) {
            out = out.replace(token, "***");
        }
        out = out.replaceAll("/bot[^/\\s\"]+/", "/bot***/");
        return out.length() > 400 ? out.substring(0, 400) + "…" : out;
    }
}
