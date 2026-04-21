package com.github.konstantinevashalomidze;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;

public class Main {

    static final HttpClient httpClient = HttpClient.newHttpClient();
    static HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://transit.ttc.com.ge/pis-gateway/api/v2/stops/1:3428/arrival-times?locale=en&ignoreScheduledArrivalTimes=false"))
            .GET()
            .build();
    static final SimpleTelegramBotApi bot = new SimpleTelegramBotApi();

    public static void main(String[] args) {
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 0);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                LocalTime now = LocalTime.now();
                if (now.isAfter(end) || now.isBefore(start)) // I am sleeping!
                    return;

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 400) {
                        // Set-Cookie
                        String cookie = response.headers().firstValue("set-cookie").orElseThrow(() -> new RuntimeException("Missing cookie header"));
                        request = HttpRequest.newBuilder()
                                .uri(URI.create("https://transit.ttc.com.ge/pis-gateway/api/v2/stops/1:3428/arrival-times?locale=en&ignoreScheduledArrivalTimes=false"))
                                .header("Cookie", cookie.split(";")[0])
                                .header("X-Api-Key", "c0a2f304-551a-4d08-b8df-2c53ecd57f9f")
                                .GET()
                                .build();
                        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    }
                    String body = response.body();
                    StringBuilder message = new StringBuilder();
                    List<Map<String, Object>> list = SimpleJsonParser.parseArray(body);

                    for (Map<String, Object> map : list) {
                        String shortName = (String) map.get("shortName");
                        Long realtimeArrivalMinutes = (Long) map.get("realtimeArrivalMinutes");
                        if (realtimeArrivalMinutes <= 15) {
                            message.append("ავტობუსი ").append(shortName).append(" გაჩერებაზე მოვა ").append(realtimeArrivalMinutes).append(" წუთში");
                            message.append("\n");
                        }
                    }

                    // Send into the telegram channel or somewhere else .
                    bot.sendMessage(message.toString());

                } catch (Exception e) {
                    bot.sendMessage(e.getMessage());
                    System.out.println(e.getMessage());
                }

            }
        }, 0, 60 * 1_000);

    }



    static class SimpleTelegramBotApi {
        HttpClient httpClient = HttpClient.newHttpClient();
        String botToken = "8698861100:AAEVfB1x-DRnv_xrT4VXoslEopI4u_K3-rc";
        String chatId = "7752078720";
        boolean muted = false;

        public SimpleTelegramBotApi() {
            try {

                String url = "https://api.telegram.org/bot%s/getUpdates?offset=-1";
                int timeElapsedBetweenPollsSeconds = 10;

                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(String.format(url, botToken)))
                                .GET().build();


                        HttpResponse<String> response = null;
                        try {
                            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        } catch (IOException | InterruptedException e) {
                            sendMessage(e.getMessage());
                            System.out.println(e.getMessage());
                        }
                        Map<String, Object> responseBody = SimpleJsonParser.parseObject(response.body());
                        if (responseBody.get("ok").equals(false)) {
                            sendMessage("მოგვიანებით სცადეთ");
                            return;
                        }

                        List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("result");

                        if (results.size() != 1) {
                            sendMessage("მოგვიანებით სცადეთ");
                            return;
                        }

                        Map<String, Object> result = results.getFirst();
                        Map<String, Object> message = (Map<String, Object>) result.get("message");
                        String instruction = (String) message.get("text");
                        Long timestamp = (Long) message.get("date");
                        long timeElapsedAfterThisMessageWasWrittenSeconds = (System.currentTimeMillis() / 1_000 - timestamp);
                        if (timeElapsedAfterThisMessageWasWrittenSeconds <= timeElapsedBetweenPollsSeconds) {
                            if (instruction.startsWith("/help")) {
                                sendMessage(
                                        """
                                        /help - ყველა შესაძლო ინსტრუქციების ჩამონათვალი.
                                        /mute - მიჩუმება
                                        /unmute - არ მიჩუმება
                                        """
                                );
                            } else if (instruction.startsWith("/mute")) {
                                bot.sendMessage("ბოტი მიჩუმებულია");
                                if (!bot.muted) bot.muted = true;
                            } else if (instruction.startsWith("/unmute")) {
                                if (bot.muted) bot.muted = false;
                                bot.sendMessage("ბოტი აგრძელებს საუბარს");
                            }
                        }
                    }
                }, 0, timeElapsedBetweenPollsSeconds * 1000);

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        public void sendMessage(String message) {
            if (!muted) {
                message = URLEncoder.encode(message, StandardCharsets.UTF_8);

                String formattedUrl = String.format(
                        "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s",
                        botToken,
                        chatId,
                        message
                );
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(formattedUrl))
                        .GET()
                        .build();

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        System.out.println("Status: " + response.statusCode() + " " + response.body());
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        }


    }


    static class SimpleJsonParser {

        public static List<Map<String, Object>> parseArray(String json) {
            json = json.trim();
            if (!json.startsWith("[") || !json.endsWith("]")) {
                throw new RuntimeException("Invalid JSON array");
            }

            List<Map<String, Object>> result = new ArrayList<>();
            int i = 1;

            while (i < json.length() - 1) {
                if (json.charAt(i) == '{') {
                    int end = findClosing(json, i, '{', '}');
                    String objStr = json.substring(i, end + 1);
                    result.add(parseObject(objStr));
                    i = end + 1;
                } else {
                    i++;
                }
            }

            return result;
        }

        public static Map<String, Object> parseObject(String json) {
            Map<String, Object> map = new HashMap<>();
            json = json.trim();

            json = json.substring(1, json.length() - 1); // remove {}

            int i = 0;
            while (i < json.length()) {
                // key
                int keyStart = json.indexOf('"', i) + 1;
                int keyEnd = json.indexOf('"', keyStart);
                String key = json.substring(keyStart, keyEnd);

                // value
                int colon = json.indexOf(':', keyEnd);
                i = colon + 1;

                char c = json.charAt(i);

                Object value;

                if (c == '"') {
                    int valStart = i + 1;
                    int valEnd = json.indexOf('"', valStart);
                    value = json.substring(valStart, valEnd);
                    i = valEnd + 1;

                } else if (c == '{') {
                    int end = findClosing(json, i, '{', '}');
                    value = parseObject(json.substring(i, end + 1));
                    i = end + 1;

                } else if (c == '[') {
                    int end = findClosing(json, i, '[', ']');
                    value = parseArray(json.substring(i, end + 1));
                    i = end + 1;

                } else {
                    int end = findValueEnd(json, i);
                    String raw = json.substring(i, end).trim();

                    if ("true".equals(raw) || "false".equals(raw)) {
                        value = Boolean.parseBoolean(raw);
                    } else if (raw.contains(".")) {
                        value = Double.parseDouble(raw);
                    } else {
                        value = Long.parseLong(raw);
                    }

                    i = end;
                }

                map.put(key, value);

                if (i < json.length() && json.charAt(i) == ',') {
                    i++;
                }
            }

            return map;
        }

        private static int findClosing(String s, int start, char open, char close) {
            int count = 0;
            for (int i = start; i < s.length(); i++) {
                if (s.charAt(i) == open) count++;
                if (s.charAt(i) == close) count--;
                if (count == 0) return i;
            }
            throw new RuntimeException("Unbalanced JSON");
        }

        private static int findValueEnd(String s, int start) {
            int i = start;
            while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != '}' && s.charAt(i) != ']') {
                i++;
            }
            return i;
        }
    }




}