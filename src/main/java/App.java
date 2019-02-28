import Config.Configurer;
import Entities.Match;
import Entities.Participant;
import Entities.ParticipantIdentity;
import Entities.Summoner;
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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class App {
    private static final String baseUrl = "https://euw1.api.riotgames.com";
    private static final String summonerEndpoint = "/lol/summoner/v4/summoners/by-name/";
    private static final String matchEndpoint = "/lol/match/v4/matches/{matchId}";
    private static final String summonerName = "kuraburamaster";
    private static final String keyParameter = "RGAPI-91fce27b-bf95-4909-aaf9-07b911444df4";
    private static final String matchlistEndpoint = "/lol/match/v4/matchlists/by-account/";


    public static void main(String[] args) throws UnirestException, IOException {
        Configurer.configure();

        List<Match> matches = getMatches(summonerName);
        val times =
                matches.stream()
                        .map(match -> new Pair<>(TimeUtils.dateForTimestamp(match.getTimestamp()), match.getGameId()))
                        .collect(Collectors.groupingBy(el -> el.first.getDayOfWeek()));

        val results = new HashMap<DayOfWeek, List<Boolean>>();
        times.forEach((e, f) -> {
            List<Boolean> gameStatuses = getWinStatuses(f);
            results.put(e, gameStatuses);
        });

        results.forEach((e, f) -> System.out.println("Hour: " + e + ", winrate = " + (double) f.stream().filter(bool -> bool).count() / f.size() + ", games: " + f.size()));

        System.out.println(times);
    }

    private static List<Boolean> getWinStatuses(List<Pair<LocalDateTime, Long>> f) {
        List<Boolean> result = new ArrayList<>();
        for (Pair<LocalDateTime, Long> pair : f) {
            result.add(findResult(pair.second));
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    private static boolean findResult(Long matchId) {
        boolean result = false;
        try {
            HttpResponse<JsonNode> matchResponse =
                    Unirest.get(baseUrl + matchEndpoint)
                    .queryString("api_key", keyParameter)
                    .routeParam("matchId", String.valueOf(matchId))
                    .asJson();
            JSONArray participantsArray = matchResponse.getBody().getObject().getJSONArray("participants");
            JSONArray participantIdentitiesArray = matchResponse.getBody().getObject().getJSONArray("participantIdentities");

            ObjectMapper objectMapper = new ObjectMapper();
            List<Participant> participants =
                    objectMapper.readValue(
                            participantsArray.toString(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Participant.class)
                    );
            System.out.println("participants:" + participants);


            List<ParticipantIdentity> participantsIdentities =
                    objectMapper.readValue(
                            participantIdentitiesArray.toString(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ParticipantIdentity.class)
                    );
            System.out.println("participant identities:" + participantsIdentities);


            long participantId =
                    participantsIdentities.stream()
                            .filter(identity -> identity.getPlayer().getSummonerName().equalsIgnoreCase(summonerName))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Ni ma"))
                            .getParticipantId();

            result = participants.stream()
                    .filter(participant -> participant.getParticipantId() == participantId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Ni ma2"))
                    .getStats()
                    .isWin();

        } catch (UnirestException | IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static List<Match> getMatches(String summonerName) throws UnirestException, IOException {
        HttpResponse<Summoner> summonerResponse =
                Unirest.get(baseUrl + summonerEndpoint + summonerName)
                        .queryString("api_key", keyParameter)
                        .asObject(Summoner.class);
        Summoner summoner = summonerResponse.getBody();

        HttpResponse<JsonNode> exploringResponse =
                Unirest.get(baseUrl + matchlistEndpoint + summoner.getAccountId())
                        .queryString("api_key", keyParameter)
                        .queryString("beginIndex", 200)
                        .asJson();

        List<Match> matches = new ArrayList<>();
        JSONObject jsonObject = exploringResponse.getBody().getObject();
        int totalGames = jsonObject.getInt("totalGames");
        System.out.println("total games = " + totalGames);

        for (int beginIndex = 0; beginIndex < totalGames; beginIndex += 100) {
            HttpResponse<JsonNode> jsonNodeHttpResponse =
                    Unirest.get(baseUrl + matchlistEndpoint + summoner.getAccountId())
                            .queryString("api_key", keyParameter)
                            .queryString("beginIndex", beginIndex)
                            .asJson();
            JSONArray array = jsonNodeHttpResponse.getBody().getObject().getJSONArray("matches");

            ObjectMapper objectMapper = new ObjectMapper();
            List<Match> matchesFromOnePeriod =
                    objectMapper.readValue(
                            array.toString(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Match.class)
                    );
            matches.addAll(matchesFromOnePeriod);
        }

        return matches;
    }
}
