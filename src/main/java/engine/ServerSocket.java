package engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import entity.Achievement;
import entity.Wallet;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerSocket implements HttpHandler {
    /** Server that response to http requests */
    HttpServer server;
    /** Player's name. */
    String nickname = "";
    String ip;
    AchievementManager achievementManager;
    Achievement achievement;
    Wallet wallet;
    String filePath = "out/production/serverconfig";
    /** ServerSocket Constructor  */
    public ServerSocket() throws IOException {
        server = HttpServer.create(new InetSocketAddress(7777), 0);
        server.createContext("/userUpdate", this);
        server.setExecutor(null);
        server.start();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    ip = parts[1].trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        achievementManager = Core.getAchievementManager();
        achievement = achievementManager.getAchievement();
        wallet = new Wallet();
    }

    /** Mapping this handler method to POST request from server */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method)) {
            InputStream requestBody = exchange.getRequestBody();
            String requestJson = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("받은 JSON 데이터: " + requestJson);

            // 응답 데이터 생성
            String responseJson = "{\"message\": \"JSON 데이터를 성공적으로 받았습니다!\", \"status\": \"success\"}";

            // 응답 설정
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseJson.getBytes(StandardCharsets.UTF_8).length);

            // 응답 보내기
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseJson.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            // 지원하지 않는 메서드 응답
            String response = "{\"error\": \"지원하지 않는 메서드입니다.\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(405, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** Send Signup Information to Server */
    public int requestSignup() throws IOException {
        String targetUrl = String.format("http://%s:8080/user/signup", ip);
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to Signup Screen. Please Enter Your Information");

        System.out.print("Enter Nickname :");
        String nickname = scanner.nextLine();
        System.out.print("Enter ID :");
        String username = scanner.nextLine();
        System.out.print("Enter Password :");
        String password = scanner.nextLine();

        String signUpData = String.format("%s,%s,%s", nickname, username, password);
        return sendPostRequest(targetUrl, signUpData);
    }

    /** Send Login Information to Server */
    public int requestLogin() throws IOException {
        String targetUrl = String.format("http://%s:8080/user/login", ip);
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to Login Screen. Please Enter Your Information");

        System.out.print("Enter ID :");
        String username = scanner.nextLine();
        System.out.print("Enter Password :");
        String password = scanner.nextLine();

        String loginData = String.format("%s,%s", username, password);

        return sendLoginRequest(targetUrl, loginData);
    }

    /** Send the jsonData to targetURL And initialize the nickname field and return response code */
    private int sendLoginRequest(String targetUrl, String jsonData) throws IOException {
        URL url = new URL(targetUrl);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            System.out.println("Login Successful! Welcome to Space Invaders");
        } else {
            System.out.println("Response Code: " + responseCode);
        }


        StringBuilder responseBody = new StringBuilder();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
        }

        String regex = "\\{\"nickname\":\"(.*?)\",\\s*\"currentCoin\":(\\d+),\\s*\"totalPlay\":(\\d+),\\s*\"totalScore\":(\\d+),\\s*\"maxCombo\":(\\d+),\\s*\"currentPerfectStage\":(\\d+),\\s*\"flawlessFailure\":(true|false)\\}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find()) {
            nickname = matcher.group(1);
            wallet.setCoin(Integer.parseInt(matcher.group(2)));
            achievement.setTotalPlayTime(Integer.parseInt(matcher.group(3)));
            achievement.setTotalScore(Integer.parseInt(matcher.group(4)));
            achievement.setHighMaxcombo(Integer.parseInt(matcher.group(5)));
            achievement.setCurrentPerfectStage(Integer.parseInt(matcher.group(6)));
            achievement.setFlawlessFailure(Boolean.parseBoolean(matcher.group(7)));
        }
        wallet.writeWallet();
        achievementManager.updateAllAchievements();
        return responseCode;
    }

    /** Send User score and currentCoin value */
    public int sendUserState(String nickname, int score, int currentCoin, int totalPlayTime, int totalScore, int maxCombo, int perfectStage, boolean flawlessFailure) throws IOException {
        String targetUrl = String.format("http://%s:8080/userstate", ip);
        String userStateData = String.format("%s,%d,%d,%d,%d,%d,%d,%b", nickname, score, currentCoin, totalPlayTime, totalScore, maxCombo, perfectStage, flawlessFailure);

        return sendPostRequest(targetUrl,userStateData);
    }

    /** Send Post Request And return the response code */
    private int sendPostRequest(String targetUrl, String jsonData) throws IOException {
        URL url = new URL(targetUrl);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain ; charset=UTF-8");

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        System.out.println("Response Code: " + responseCode);
        return responseCode;
    }
    public String askSignupOrLogin() throws IOException {
        System.out.println("If you already have your account and want to login, please enter [YES]");
        System.out.println("Or if you don't have any account, please enter [NO]");
        Scanner scanner = new Scanner(System.in);

        String yesOrNo = scanner.nextLine();

        return yesOrNo;
    }
}
