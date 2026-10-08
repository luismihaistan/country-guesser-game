package com.countryguesser.game.game;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping("/next")
    public ResponseEntity<?> next(@AuthenticationPrincipal Long userId) {
        StartRoundResult result = gameService.startRound(userId);

        return switch (result.status()) {
            case STARTED -> ResponseEntity.ok(result.round());
            case ALREADY_ACTIVE -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("You already have an active round.");
            case POOL_EMPTY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("No locations available right now, try again shortly.");
        };
    }

    @GetMapping("/current")
    public ResponseEntity<?> current(@AuthenticationPrincipal Long userId) {
        Optional<RoundResponse> round = gameService.getCurrentRound(userId);
        if (round.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(round.get());
    }

    @PostMapping("/guess")
    public ResponseEntity<?> guess(
            @AuthenticationPrincipal Long userId,
            @RequestBody GuessRequest request
    ) {
        GuessResult result = gameService.submitGuess(userId, request.countryCode());

        return switch (result.status()) {
            case NO_ACTIVE_ROUND -> ResponseEntity.status(HttpStatus.GONE)
                    .body("No active round found. Request a new location.");
            case CORRECT -> ResponseEntity.ok(new GuessResponse(true, result.streak(), null));
            case INCORRECT -> ResponseEntity.ok(new GuessResponse(false, result.streak(), result.actualCountryCode()));
        };
    }

    public record NextResponse(String panoId) {}
    public record GuessRequest(@NotBlank String countryCode) {}
    public record GuessResponse(boolean correct, int streak, String actualCountryCode) {}
}