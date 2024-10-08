package moe.victorique.blackjack.service.impl;

import lombok.RequiredArgsConstructor;
import moe.victorique.blackjack.constants.Action;
import moe.victorique.blackjack.constants.PlayStatus;
import moe.victorique.blackjack.entity.Game;
import moe.victorique.blackjack.repo.GameRepository;
import moe.victorique.blackjack.service.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class GameService implements IUserService {

    private final Logger logger = LoggerFactory.getLogger(GameService.class);

    private final GameRepository repo;

    private final StatService statService;

    private final List<Character> suites = List.of(
            '♠',
            '♣',
            '♥',
            '♦'
    );

    private final List<String> faces = List.of(
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "10",
            "A",
            "J",
            "Q",
            "K"
    );

    @Override
    public Game newGame(final String deviceId) {
        var newGame = new Game(deviceId, PlayStatus.Playing);
        this.createDeck(newGame);
        this.deal(newGame);
        return this.repo.save(newGame);
    }

    @Override
    public int calculateScore(final List<String> cards) {
        var retVal = 0;
        var hasAce = false;
        for (final var card : cards) {
            retVal += getNumberFromCard(card);
            if (card.contains("J") || card.contains("Q") || card.contains("K")) {
                retVal += 10;
                continue;
            }
            if (card.contains("A")) {
                hasAce = true;
            }
        }
        if (hasAce) {
            for (final var card : cards) {
                if (card.contains("A")) {
                    if (retVal + 11 > 21) {
                        retVal += 1;
                    } else {
                        retVal += 11;
                    }
                }
            }
        }
        return retVal;
    }

    @Override
    public Game hit(final Game game) {
        game.playerCards.add(game.deck.removeLast());
        this.logger.info("HIT: {}", game.token);

        if (this.calculateScore(game.playerCards) > 21) {
            game.status = PlayStatus.Bust;
            this.logger.info("BUST");
        }

        if (game.status == PlayStatus.Bust) {
            return this.doBust(game);
        }

        return this.repo.save(game);
    }

    @Override
    public Pair<Game, Pair<Integer, Integer>> stay(final Game game) {
        while (this.calculateScore(game.dealerCards) < 17) {
            game.dealerCards.add(game.deck.removeLast());
        }

        final var playerScore = this.calculateScore(game.playerCards);
        final var dealerScore = this.calculateScore(game.dealerCards);

        if (dealerScore > 21) {
            game.status = PlayStatus.DealerBust;
            this.logger.info("DEALER BUST");
            this.statService.updateStats(game.device, Action.WIN);
        } else if (playerScore > dealerScore) {
            game.status = PlayStatus.PlayerWins;
            this.logger.info("WIN");
            this.statService.updateStats(game.device, Action.WIN);
        } else if (dealerScore > playerScore) {
            game.status = PlayStatus.DealerWins;
            this.logger.info("LOSE");
            this.statService.updateStats(game.device, Action.LOSE);
        } else {
            game.status = PlayStatus.Draw;
            this.logger.info("DRAW");
            this.statService.updateStats(game.device, Action.DRAW);
        }

        return Pair.of(this.repo.save(game), Pair.of(playerScore, dealerScore));

    }

    @Override
    public Optional<Game> getActiveGame(final String deviceId, final Optional<UUID> token) {
        final PlayStatus status = PlayStatus.Playing;
        return token
                .map(t -> this.repo.findByTokenAndStatus(t, status))
                .orElseGet(() -> this.repo.findByDeviceAndStatus(deviceId, status));
    }

    @Override
    public Optional<Game> getGameFromToken(final UUID token) {
        return this.repo.findById(token);
    }

    @Override
    public List<Game> getAllGames(final String deviceId) {
        return this.repo.findAllByDeviceAndStatusNot(deviceId, PlayStatus.Playing);
    }

    private void createDeck(final Game game) {
        for (final var suite : this.suites) {
            for (final var face : this.faces) {
                game.deck.add(suite + face);
            }
        }
        for (var i = 0; i < game.deck.size(); i++) {
            var j = (int) (Math.random() * game.deck.size());
            var originalCard = game.deck.get(i);
            game.deck.set(i, game.deck.get(j));
            game.deck.set(j, originalCard);
        }
    }

    private void deal(final Game game) {
        game.playerCards.add(game.deck.removeLast());
        game.dealerCards.add(game.deck.removeLast());
        game.playerCards.add(game.deck.removeLast());
        game.dealerCards.add(game.deck.removeLast());
    }

    private int getNumberFromCard(final String card) {
        try {
            return Integer.parseInt(card.replaceAll("[^0-9]", ""));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private Game doBust(final Game game) {
        statService.updateStats(game.device, Action.LOSE);
        return repo.save(game);
    }

}