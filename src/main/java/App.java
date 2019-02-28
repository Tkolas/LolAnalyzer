import Config.Configurer;
import Entities.Match;
import Entities.Participant;
import Entities.ParticipantIdentity;
import Entities.Summoner;
import Utils.JsonUtils;
import Utils.Pair;
import Utils.TimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.val;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class App {
    private static final String baseUrl = "https://euw1.api.riotgames.com";
    private static final String summonerEndpoint = "/lol/summoner/v4/summoners/by-name/";
    private static final String matchEndpoint = "/lol/match/v4/matches/{matchId}";
    private static final String summonerName = "kuraburamaster";
    private static final String keyParameter = "RGAPI-91fce27b-bf95-4909-aaf9-07b911444df4";
    private static final String matchListEndpoint = "/lol/match/v4/matchlists/by-account/";

    private static final ObjectMapper objectMapper = new ObjectMapper();


    public static void main(String[] args) throws UnirestException, IOException {
        Configurer.configure(objectMapper);

        HttpResponse<Summoner> summonerResponse =
                Unirest.get(baseUrl + summonerEndpoint + summonerName)
                        .queryString("api_key", keyParameter)
                        .asObject(Summoner.class);
        Summoner summoner = summonerResponse.getBody();

        List<Match> matches = getMatches(summoner);
        Map<Integer, List<Pair<LocalDateTime, Long>>> times =
                matches.stream()
                .map(match -> new Pair<>(TimeUtils.dateForTimestamp(match.getTimestamp()), match.getGameId()))
                .collect(Collectors.groupingBy(pair -> pair.first.getHour()));

        val statistics = computeStatistics(times, summoner.getAccountId());

        statistics.forEach(
                (hour, statusesList) ->
                    System.out.println("Hour: " + hour +
                            ", winrate = " + (double) statusesList.stream().filter(isWon -> isWon).count() / statusesList.size() +
                            ", games: " + statusesList.size())
                );
    }

    private static HashMap<Integer, List<Boolean>> computeStatistics(Map<Integer, List<Pair<LocalDateTime, Long>>> times, String accountId) {
        HashMap<Integer, List<Boolean>> statistics = new HashMap<>();

        times.forEach((hour, gamesData) -> {
            List<Boolean> gameStatuses = null;
            try {
                gameStatuses = getWinStatuses(gamesData, accountId);
            } catch (IOException | UnirestException | InterruptedException e) {
                e.printStackTrace();
            }
            statistics.put(hour, gameStatuses);
        });

        return statistics;
    }

    private static List<Boolean> getWinStatuses(List<Pair<LocalDateTime, Long>> gamesData, String accountId) throws IOException, UnirestException, InterruptedException {
        List<Boolean> result = new ArrayList<>();
        for (Pair<LocalDateTime, Long> gameData : gamesData) {
            result.add(checkGameResult(gameData.second, accountId));
            Thread.sleep(1200);
        }

        return result;
    }

    private static boolean checkGameResult(Long matchId, String accountId) throws IOException, UnirestException {
        HttpResponse<JsonNode> matchResponse =
                Unirest.get(baseUrl + matchEndpoint)
                .queryString("api_key", keyParameter)
                .routeParam("matchId", String.valueOf(matchId))
                .asJson();
        JSONArray participantsArray = matchResponse.getBody().getObject().getJSONArray("participants");
        JSONArray participantIdentitiesArray = matchResponse.getBody().getObject().getJSONArray("participantIdentities");

        List<Participant> participants = JsonUtils.JSONArrayToList(objectMapper, participantsArray, Participant.class);
        List<ParticipantIdentity> participantsIdentities =
                JsonUtils.JSONArrayToList(objectMapper, participantIdentitiesArray, ParticipantIdentity.class);

        long participantId =
                participantsIdentities.stream()
                        .filter(identity -> identity.getPlayer().getCurrentAccountId().equals(accountId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Ni ma"))
                        .getParticipantId();

        boolean result = participants.stream()
                .filter(participant -> participant.getParticipantId() == participantId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Ni ma2"))
                .getStats()
                .isWin();

        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static List<Match> getMatches(Summoner summoner) throws UnirestException, IOException {
        HttpResponse<JsonNode> exploringResponse =
                Unirest.get(baseUrl + matchListEndpoint + summoner.getAccountId())
                        .queryString("api_key", keyParameter)
                        .queryString("beginIndex", 200)
                        .asJson();

        JSONObject jsonObject = exploringResponse.getBody().getObject();
        int totalGames = jsonObject.getInt("totalGames");
        System.out.println("total games = " + totalGames);

        List<Match> matches = new ArrayList<>();
        for (int beginIndex = 0; beginIndex < totalGames; beginIndex += 100) {
            HttpResponse<JsonNode> jsonNodeHttpResponse =
                    Unirest.get(baseUrl + matchListEndpoint + summoner.getAccountId())
                            .queryString("api_key", keyParameter)
                            .queryString("beginIndex", beginIndex)
                            .asJson();
            JSONArray array = jsonNodeHttpResponse.getBody().getObject().getJSONArray("matches");

            List<Match> matchesFromOnePeriod = JsonUtils.JSONArrayToList(objectMapper, array, Match.class);
            matches.addAll(matchesFromOnePeriod);
        }

        return matches;
    }
}
