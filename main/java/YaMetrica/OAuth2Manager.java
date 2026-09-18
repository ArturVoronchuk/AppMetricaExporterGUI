package YaMetrica;

/* OAuth2Manager – хранилище OAuth-токенов для разных счётчиков (ЕЛК, МЛК, RT.RU).*/

import java.util.HashMap;
import java.util.Map;

public class OAuth2Manager {
    private static final String YANDEX_METRIKA_TOKEN = "";
    private static final String APP_METRICA_TOKEN = "";

    private static final Map<String, String> TOKEN_MAP = new HashMap<>();

    static {
        TOKEN_MAP.put("ЕЛК", YANDEX_METRIKA_TOKEN);
        TOKEN_MAP.put("RT.RU", YANDEX_METRIKA_TOKEN);
        TOKEN_MAP.put("МЛК", APP_METRICA_TOKEN);
    }

    public static String getAccessToken(String counterName) {
        String token = TOKEN_MAP.get(counterName);
        if (token == null) {
            throw new IllegalArgumentException("Неизвестный счетчик: " + counterName);
        }
        System.out.println("Токен для " + counterName + " получен");
        return token;
    }
}