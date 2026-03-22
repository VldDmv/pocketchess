package org.pocketchess.core.ai.opening;

import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OpeningBook {
    private final Map<String, String[]> book = new HashMap<>();
    private final Random random = new Random();

    public OpeningBook() {
        initializeBook();
    }

    private void initializeBook() {
        // ========== Italian Game  ==========
        book.put("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w", new String[]{"e2e4", "d2d4", "g1f3", "c2c4"});
        book.put("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b", new String[]{"e7e5", "c7c5", "e7e6", "c7c6", "g8f6", "d7d5"});
        book.put("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"g1f3", "f1c4", "b1c3", "f2f4"});
        book.put("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b", new String[]{"b8c6", "g8f6", "f7f5"});
        book.put("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w", new String[]{"f1c4", "f1b5", "d2d4", "b1c3"});
        book.put("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b", new String[]{"f8c5", "g8f6", "f7f5", "f8e7"});
        book.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w", new String[]{"c2c3", "d2d3", "e1g1", "b2b4"});
        book.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R b", new String[]{"g8f6", "d7d6", "f7f5"});
        //  Giuoco Piano + Italian lines
        book.put("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R w", new String[]{"d2d4", "e1g1", "d1b3"});
        book.put("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2BPP3/2P2N2/PP3PPP/RNBQK2R b", new String[]{"e5d4", "d7d6", "c5b6"});

        // ========== Spanish/Ruy Lopez  ==========
        book.put("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b", new String[]{"a7a6", "g8f6", "f8c5", "f7f5", "d7d6"});
        book.put("r1bqkbnr/1ppp1ppp/p1n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R w", new String[]{"b5a4", "b5c6"});
        book.put("r1bqkbnr/1ppp1ppp/p1n5/4p3/B3P3/5N2/PPPP1PPP/RNBQK2R b", new String[]{"g8f6", "f7f5", "b7b5", "f8c5"});
        book.put("r1bqkb1r/1ppp1ppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQK2R w", new String[]{"e1g1", "d2d3", "a4c6"});
        book.put("r1bqkb1r/1ppp1ppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQ1RK1 b", new String[]{"f8e7", "b7b5", "f6e4"});
        book.put("r1bqk2r/1pppbppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQ1RK1 w", new String[]{"f1e1", "c2c3", "d2d4"});
        book.put("r1bqk2r/2ppbppp/p1n2n2/1p2p3/B3P3/5N2/PPPP1PPP/RNBQ1RK1 w", new String[]{"a4b3", "d2d4", "f1e1"});
        book.put("r1bqk2r/2ppbppp/p1n2n2/1p2p3/4P3/1B3N2/PPPP1PPP/RNBQ1RK1 b", new String[]{"d7d6", "e8g8", "c6a5"});

        // ========== Sicilian Defense  ==========
        book.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"g1f3", "b1c3", "d2d4", "f2f4", "c2c3"});
        book.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b", new String[]{"d7d6", "b8c6", "e7e6", "g7g6"});
        book.put("rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w", new String[]{"d2d4", "f1b5", "c2c3"});
        book.put("rnbqkbnr/pp2pppp/3p4/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R b", new String[]{"c5d4", "g8f6", "b8c6"});
        book.put("rnbqkbnr/pp2pppp/3p4/8/3pP3/5N2/PPP2PPP/RNBQKB1R w", new String[]{"f3d4", "d1d4"});
        book.put("rnbqkbnr/pp2pppp/3p4/8/3NP3/8/PPP2PPP/RNBQKB1R b", new String[]{"g8f6", "b8c6", "g7g6"});
        // Najdorf Sicilian
        book.put("rnbqkb1r/pp2pppp/3p1n2/2p5/4P3/2N2N2/PPPP1PPP/R1BQKB1R w", new String[]{"d2d4", "f1c4", "f1e2", "g2g3"});
        book.put("rnbqkb1r/1p2pppp/p2p1n2/2p5/3PP3/2N2N2/PPP2PPP/R1BQKB1R w", new String[]{"f1e2", "c1e3", "f2f3"});
        book.put("rnbqkb1r/1p2pppp/p2p1n2/2p5/3NP3/2N5/PPP2PPP/R1BQKB1R b", new String[]{"c5d4", "e7e6", "g7g6"});
        // Dragon Variation
        book.put("rnbqkb1r/pp2pp1p/3p1np1/2p5/3PP3/2N2N2/PPP2PPP/R1BQKB1R w", new String[]{"f1e2", "c1e3", "f2f3"});
        book.put("rnbqkb1r/pp2pp1p/3p1np1/2p5/3NP3/2N5/PPP2PPP/R1BQKB1R b", new String[]{"c5d4", "f8g7", "b8c6"});
        // Accelerated Dragon
        book.put("rnbqkb1r/pp1ppp1p/5np1/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w", new String[]{"d2d4", "b1c3", "f1b5"});
        book.put("rnbqkb1r/pp1ppp1p/5np1/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R b", new String[]{"c5d4", "f8g7", "d7d6"});

        // ========== French Defense  ==========
        book.put("rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"d2d4", "d2d3", "b1c3", "g1f3"});
        book.put("rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b", new String[]{"d7d5", "g8f6", "f7f5"});
        book.put("rnbqkbnr/ppp2ppp/4p3/3p4/3PP3/8/PPP2PPP/RNBQKBNR w", new String[]{"e4e5", "e4d5", "b1c3"});
        book.put("rnbqkbnr/ppp2ppp/4p3/3pP3/3P4/8/PPP2PPP/RNBQKBNR b", new String[]{"c7c5", "b8c6", "g8e7"});
        book.put("rnbqkbnr/ppp2ppp/4p3/3pP3/3P4/2N5/PPP2PPP/R1BQKBNR b", new String[]{"c7c5", "g8f6", "b8c6"});
        book.put("rnbqkb1r/ppp1nppp/4p3/3pP3/3P4/2N5/PPP2PPP/R1BQKBNR w", new String[]{"g1f3", "f2f4", "c1e3"});
        book.put("rnbqkb1r/pp3ppp/4pn2/2ppP3/3P4/2N5/PPP2PPP/R1BQKBNR w", new String[]{"d4c5", "c1e3", "g1f3"});
        // Winawer Variation
        book.put("rnbqk1nr/ppp2ppp/4p3/3p4/1b1PP3/2N5/PPP2PPP/R1BQKBNR w", new String[]{"e4e5", "d8c2", "a2a3"});

        // ========== Caro-Kann  ==========
        book.put("rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"d2d4", "b1c3", "g1f3", "f2f3"});
        book.put("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR b", new String[]{"d7d5", "g7g6", "d5e4"});
        book.put("rnbqkbnr/pp2pppp/2p5/3p4/3PP3/8/PPP2PPP/RNBQKBNR w", new String[]{"b1c3", "e4e5", "e4d5"});
        book.put("rnbqkbnr/pp2pppp/2p5/3p4/3PP3/2N5/PPP2PPP/R1BQKBNR b", new String[]{"d5e4", "g7g6", "c8f5", "g8f6"});
        book.put("rn1qkbnr/pp2pppp/2p5/5b2/3PN3/2N5/PPP2PPP/R1BQKB1R w", new String[]{"d4f5", "c1f4", "g1f3"});
        // Advance Variation
        book.put("rnbqkbnr/pp2pppp/2p5/3pP3/3P4/8/PPP2PPP/RNBQKBNR b", new String[]{"c8f5", "e7e6", "c6c5"});

        // ========== Alekhine's Defense  ==========
        book.put("rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"e4e5", "b1c3", "d2d4"});
        book.put("rnbqkb1r/pppppppp/8/4Pn2/8/8/PPPP1PPP/RNBQKBNR b", new String[]{"f6d5", "f6g8", "f6e4"});
        book.put("rnbqkb1r/pppppppp/8/3nP3/8/8/PPPP1PPP/RNBQKBNR w", new String[]{"d2d4", "c2c4", "g1f3"});
        book.put("rnbqkb1r/pppppppp/8/3nP3/3P4/8/PPP2PPP/RNBQKBNR b", new String[]{"d5b6", "d7d6", "d5f6"});
        book.put("rnbqkb1r/ppp1pppp/3p4/3nP3/3P4/8/PPP2PPP/RNBQKBNR w", new String[]{"c2c4", "g1f3", "f2f4"});

        // ========== Scandinavian  ==========
        book.put("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"e4d5", "b1c3"});
        book.put("rnbqkbnr/ppp1pppp/8/3P4/8/8/PPPP1PPP/RNBQKBNR b", new String[]{"d8d5", "g8f6"});
        book.put("rnb1kbnr/ppp1pppp/8/3q4/8/8/PPPP1PPP/RNBQKBNR w", new String[]{"b1c3", "d2d4", "g1f3"});
        book.put("rnb1kbnr/ppp1pppp/8/3q4/8/2N5/PPPP1PPP/R1BQKBNR b", new String[]{"d5a5", "d5d8", "d5d6", "d5e5"});
        book.put("rnb1kbnr/ppp1pppp/8/q7/8/2N5/PPPP1PPP/R1BQKBNR w", new String[]{"d2d4", "g1f3", "e2e4"});

        // ========== English Opening  ==========
        book.put("rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b", new String[]{"e7e5", "g8f6", "c7c5", "e7e6", "g7g6"});
        book.put("rnbqkbnr/pppp1ppp/8/4p3/2P5/8/PP1PPPPP/RNBQKBNR w", new String[]{"b1c3", "g2g3", "g1f3"});
        book.put("rnbqkb1r/pppppppp/5n2/8/2P5/8/PP1PPPPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "g2g3"});
        book.put("rnbqkbnr/pppp1ppp/8/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR b", new String[]{"g8f6", "b8c6", "f8c5"});
        book.put("r1bqkbnr/pppp1ppp/2n5/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR w", new String[]{"g2g3", "g1f3", "e2e3"});
        // Reversed Sicilian
        book.put("rnbqkbnr/pppp1ppp/8/4p3/2P5/5N2/PP1PPPPP/RNBQKB1R b", new String[]{"b8c6", "g8f6", "d7d5"});

        // ========== Queen's Gambit  ==========
        book.put("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b", new String[]{"g8f6", "d7d5", "e7e6", "f7f5", "g7g6"});
        book.put("rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w", new String[]{"c2c4", "g1f3", "b1c3"});
        book.put("rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w", new String[]{"c2c4", "g1f3", "c1f4"});
        book.put("rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b", new String[]{"e7e6", "c7c6", "d5c4", "e7e5"});
        book.put("rnbqkbnr/pp2pppp/2p5/3p4/2PP4/8/PP2PPPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "c1f4"});
        book.put("rnbqkbnr/ppp2ppp/4p3/3p4/2PP4/8/PP2PPPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "c1g5"});
        // QG Declined
        book.put("rnbqkb1r/ppp2ppp/4pn2/3p4/2PP4/8/PP2PPPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "c1g5"});
        book.put("rnbqkb1r/ppp2ppp/4pn2/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR w", new String[]{"g1f3", "c1g5", "c4d5"});
        // QG Accepted
        book.put("rnbqkbnr/ppp1pppp/8/8/2pP4/8/PP2PPPP/RNBQKBNR w", new String[]{"e2e3", "g1f3", "e2e4"});
        book.put("rnbqkbnr/ppp1pppp/8/8/2pP4/4P3/PP3PPP/RNBQKBNR b", new String[]{"g8f6", "e7e5", "c4c3"});
        book.put("rnbqkb1r/ppp1pppp/5n2/8/2pP4/4P3/PP3PPP/RNBQKBNR w", new String[]{"f1c4", "d1a4", "g1f3"});
        // Slav Defense
        book.put("rnbqkb1r/pp3ppp/2p1pn2/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR w", new String[]{"g1f3", "e2e3", "c1d2"});

        // ========== King's Gambit  ==========
        book.put("rnbqkbnr/pppp1ppp/8/4p3/4PP2/8/PPPP2PP/RNBQKBNR b", new String[]{"e5f4", "d7d5", "f8c5"});
        book.put("rnbqkbnr/pppp1ppp/8/8/4Pp2/8/PPPP2PP/RNBQKBNR w", new String[]{"g1f3", "f1c4", "d2d4"});
        book.put("rnbqkbnr/pppp1ppp/8/8/4Pp2/5N2/PPPP2PP/RNBQKB1R b", new String[]{"g7g5", "f8e7", "d7d5"});
        book.put("rnbqkbnr/pppp1p1p/8/6p1/4Pp2/5N2/PPPP2PP/RNBQKB1R w", new String[]{"h2h4", "f1c4", "d2d4"});

        // ========== Queen's Indian ==========
        book.put("rnbqkb1r/p1pppppp/1p3n2/8/2PP4/5N2/PP2PPPP/RNBQKB1R w", new String[]{"g2g3", "e2e3", "b1c3"});
        book.put("rnbqkb1r/p1pppppp/1p3n2/8/2PP4/5NP1/PP2PP1P/RNBQKB1R b", new String[]{"c8b7", "e7e6", "f8a3"});

        // ========== King's Indian Defense  ==========
        book.put("rnbqkb1r/pppppp1p/5np1/8/3P4/2N5/PPP1PPPP/R1BQKBNR w", new String[]{"c2c4", "g1f3", "c1f4"});
        book.put("rnbqkb1r/pppppp1p/5np1/8/2PP4/2N5/PP2PPPP/R1BQKBNR b", new String[]{"d7d5", "f8g7", "e7e5"});
        book.put("rnbqk2r/ppp1ppbp/5np1/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR w", new String[]{"c4d5", "g1f3", "c1g5"});
        book.put("rnbqk2r/ppppppbp/5np1/8/2PP4/2N5/PP2PPPP/R1BQKBNR w", new String[]{"e2e4", "g1f3", "f2f3"});
        book.put("rnbqk2r/ppppppbp/5np1/8/2PPP3/2N5/PP3PPP/R1BQKBNR b", new String[]{"d7d6", "e8g8", "c7c5"});

        // ========== Nimzo-Indian  ==========
        book.put("rnbqk2r/pppp1ppp/4pn2/8/1bPP4/2N5/PP2PPPP/R1BQKBNR w", new String[]{"e2e3", "d8c2", "g1f3"});
        book.put("rnbqk2r/pppp1ppp/4pn2/8/1bPP4/2N1P3/PP3PPP/R1BQKBNR b", new String[]{"b4c3", "b7b6", "e8g8", "c7c5"});
        book.put("rnbqk2r/p1pp1ppp/1p2pn2/8/1bPP4/2N1P3/PP3PPP/R1BQKBNR w", new String[]{"f1d3", "g1f3", "a2a3"});

        // ========== Grünfeld Defense  ==========
        book.put("rnbqk2r/ppp1ppbp/6p1/3n4/2PP4/2N5/PP2PPPP/R1BQKBNR w", new String[]{"e2e4", "d4d5", "c1e3"});

        // ========== London System  ==========
        book.put("rnbqkb1r/pppppppp/5n2/8/3P4/5N2/PPP1PPPP/RNBQKB1R w", new String[]{"c1f4", "e2e3", "b1d2"});
        book.put("rnbqkb1r/pppppppp/5n2/8/3P1B2/5N2/PPP1PPPP/RN1QKB1R b", new String[]{"e7e6", "d7d5", "c7c5"});
        book.put("rnbqkb1r/ppp1pppp/5n2/3p4/3P1B2/5N2/PPP1PPPP/RN1QKB1R w", new String[]{"e2e3", "b1d2", "c2c3"});
        book.put("rnbqkb1r/pp2pppp/2p2n2/3p4/3P1B2/4PN2/PPP2PPP/RN1QKB1R w", new String[]{"b1d2", "f1d3", "c2c3"});

        // ========== Colle System  ==========
        book.put("rnbqkb1r/pppppppp/5n2/8/3P4/4PN2/PPP2PPP/RNBQKB1R b", new String[]{"e7e6", "d7d5", "c7c5", "b7b6"});
        book.put("rnbqkb1r/ppp1pppp/4pn2/3p4/3P4/4PN2/PPP2PPP/RNBQKB1R w", new String[]{"f1d3", "b1d2", "c2c3"});
        book.put("rnbqkb1r/ppp1pppp/4pn2/3p4/3P4/3BPN2/PPP2PPP/RNBQK2R b", new String[]{"f8d6", "b8c6", "e8g8"});

        // ========== Pirc Defense  ==========
        book.put("rnbqkbnr/ppp1pppp/3p4/8/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"d2d4", "b1c3", "g1f3", "f2f4"});
        book.put("rnbqkbnr/ppp1pppp/3p4/8/3PP3/8/PPP2PPP/RNBQKBNR b", new String[]{"g8f6", "g7g6", "b8d7"});
        book.put("rnbqkb1r/ppp1pp1p/3p1np1/8/3PP3/8/PPP2PPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "f2f3"});
        book.put("rnbqkb1r/ppp1pppp/3p1n2/8/3PP3/8/PPP2PPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "f1c4"});
        book.put("rnbqkb1r/ppp1pppp/3p1n2/8/3PP3/2N5/PPP2PPP/R1BQKBNR b", new String[]{"g7g6", "e7e5", "b8d7"});

        // ========== Old Indian Defense ==========
        book.put("rnbqkb1r/pppppp1p/5np1/8/3PP3/2N5/PPP2PPP/R1BQKBNR w", new String[]{"g1f3", "f2f3", "c1e3"});
        book.put("rnbqkb1r/pppppp1p/5np1/8/3PP3/2N2N2/PPP2PPP/R1BQKB1R b", new String[]{"f8g7", "e8g8", "d7d6"});
        book.put("rnbqk2r/ppppppbp/5np1/8/3PP3/2N2N2/PPP2PPP/R1BQKB1R w", new String[]{"f1e2", "c1e3", "d1d2"});

        // ========== Modern Defense ==========
        book.put("rnbqkbnr/pppppp1p/6p1/8/4P3/8/PPPP1PPP/RNBQKBNR w", new String[]{"d2d4", "b1c3", "g1f3"});
        book.put("rnbqkbnr/pppppp1p/6p1/8/3PP3/8/PPP2PPP/RNBQKBNR b", new String[]{"f8g7", "d7d6", "g8f6"});
        book.put("rnbqk1nr/ppppppbp/6p1/8/3PP3/8/PPP2PPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "f2f4"});

        // ========== Dutch Defense  ==========
        book.put("rnbqkbnr/ppppp1pp/8/5p2/3P4/8/PPP1PPPP/RNBQKBNR w", new String[]{"g2g3", "g1f3", "c2c4"});
        book.put("rnbqkbnr/ppppp1pp/8/5p2/2PP4/8/PP2PPPP/RNBQKBNR b", new String[]{"g8f6", "e7e6", "g7g6"});
        book.put("rnbqkb1r/ppppp1pp/5n2/5p2/2PP4/8/PP2PPPP/RNBQKBNR w", new String[]{"g2g3", "g1f3", "b1c3"});
        book.put("rnbqkb1r/ppppp1pp/5n2/5p2/2PP4/6P1/PP2PP1P/RNBQKBNR b", new String[]{"e7e6", "g7g6", "f8e7"});
        book.put("rnbqkb1r/ppp1p1pp/5n2/3p1p2/2PP4/6P1/PP2PP1P/RNBQKBNR w", new String[]{"f1g2", "g1f3", "b1c3"});

        // ========== Bird Opening  ==========
        book.put("rnbqkbnr/pppppppp/8/8/5P2/8/PPPPP1PP/RNBQKBNR b", new String[]{"d7d5", "g8f6", "e7e5"});
        book.put("rnbqkbnr/ppp1pppp/8/3p4/5P2/8/PPPPP1PP/RNBQKBNR w", new String[]{"g1f3", "e2e3", "b2b3"});
        book.put("rnbqkb1r/ppp1pppp/5n2/3p4/5P2/8/PPPPP1PP/RNBQKBNR w", new String[]{"g1f3", "e2e3", "b1c3"});
        book.put("rnbqkb1r/ppp1pppp/5n2/3p4/5P2/5N2/PPPPP1PP/RNBQKB1R b", new String[]{"g7g6", "c7c5", "e7e6"});

        // ========== Réti Opening  ==========
        book.put("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b", new String[]{"d7d5", "g8f6", "c7c5"});
        book.put("rnbqkbnr/ppp1pppp/8/3p4/8/5N2/PPPPPPPP/RNBQKB1R w", new String[]{"g2g3", "c2c4", "e2e3"});
        book.put("rnbqkb1r/ppp1pppp/5n2/3p4/8/5N2/PPPPPPPP/RNBQKB1R w", new String[]{"c2c4", "g2g3", "d2d4"});
        book.put("rnbqkb1r/ppp1pppp/5n2/3p4/2P5/5N2/PP1PPPPP/RNBQKB1R b", new String[]{"e7e6", "g7g6", "c7c6"});

        // ========== Budapest Gambit ==========
        book.put("rnbqkb1r/pppp1ppp/5n2/4p3/3P4/5N2/PPP1PPPP/RNBQKB1R w", new String[]{"d4e5", "e2e4", "b1c3"});
        book.put("rnbqkb1r/pppp1ppp/8/4Pn2/8/5N2/PPP1PPPP/RNBQKB1R b", new String[]{"f6e4", "f6g4", "d7d6"});
        book.put("rnbqkb1r/pppp1ppp/8/4P3/4n3/5N2/PPP1PPPP/RNBQKB1R w", new String[]{"b1d2", "f1d3", "d1e2"});

        // ========== Benoni Defense ==========
        book.put("rnbqkb1r/pp1p1ppp/4pn2/2pP4/8/8/PPP1PPPP/RNBQKBNR w", new String[]{"b1c3", "e2e4", "g1f3"});
        book.put("rnbqkb1r/pp1p1ppp/4pn2/2pP4/2P5/8/PP2PPPP/RNBQKBNR b", new String[]{"e6d5", "d7d6", "g7g6"});
        book.put("rnbqkb1r/pp3ppp/3ppn2/2pP4/2P5/8/PP2PPPP/RNBQKBNR w", new String[]{"b1c3", "e2e4", "g1f3"});
        book.put("rnbqkb1r/pp3ppp/3ppn2/2pP4/2P1P3/8/PP3PPP/RNBQKBNR b", new String[]{"g7g6", "c8g4", "f8g7"});

        // ========== Benko Gambit ==========
        book.put("rnbqkb1r/p2ppppp/5n2/1ppP4/2P5/8/PP2PPPP/RNBQKBNR w", new String[]{"c4b5", "d5b6", "b1c3"});
        book.put("rnbqkb1r/p2ppppp/5n2/1PpP4/8/8/PP2PPPP/RNBQKBNR b", new String[]{"a7a6", "d7d6", "b7b6"});

        // ========== Catalan Opening ==========
        book.put("rnbqkb1r/ppp2ppp/4pn2/3p4/2PP4/6P1/PP2PP1P/RNBQKBNR w", new String[]{"f1g2", "g1f3", "d1b3"});
        book.put("rnbqkb1r/ppp2ppp/4pn2/3p4/2PP4/5NP1/PP2PP1P/RNBQKB1R b", new String[]{"f8e7", "d5c4", "b8c6"});
        book.put("rnbqk2r/ppp1bppp/4pn2/3p4/2PP4/5NP1/PP2PPBP/RNBQK2R w", new String[]{"e1g1", "d1b3", "b1c3"});

        // ========== Trompowsky Attack ==========
        book.put("rnbqkb1r/pppppppp/5n2/6B1/3P4/8/PPP1PPPP/RN1QKBNR b", new String[]{"e7e6", "c7c5", "d7d5", "f6e4"});
        book.put("rnbqkb1r/ppp1pppp/4pn2/6B1/3P4/8/PPP1PPPP/RN1QKBNR w", new String[]{"e2e3", "b1d2", "f1d3"});

        // ========== Torre Attack ==========
        book.put("rnbqkb1r/ppp1pppp/5n2/3p2B1/3P4/5N2/PPP1PPPP/RN1QKB1R b", new String[]{"e7e6", "c7c5", "b8d7"});
        book.put("rnbqkb1r/ppp2ppp/4pn2/3p2B1/3P4/5N2/PPP1PPPP/RN1QKB1R w", new String[]{"e2e3", "b1d2", "f1d3"});

        // ========== Four Knights Game ==========
        book.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/4P3/2N2N2/PPPP1PPP/R1BQKB1R w", new String[]{"f1b5", "d2d4", "f1c4"});
        book.put("r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/2N2N2/PPPP1PPP/R1BQK2R b", new String[]{"f6d4", "b8d4", "f8c5"});
        book.put("r1bqkb1r/1ppp1ppp/p1n2n2/1B2p3/4P3/2N2N2/PPPP1PPP/R1BQK2R w", new String[]{"e1g1", "d2d3", "b5c6"});

        // ========== Scotch Game  ==========
        book.put("r1bqkbnr/pppp1ppp/2n5/4p3/3PP3/5N2/PPP2PPP/RNBQKB1R b", new String[]{"e5d4", "g8f6", "f8c5"});
        book.put("r1bqkbnr/pppp1ppp/2n5/8/3pP3/5N2/PPP2PPP/RNBQKB1R w", new String[]{"f3d4", "d1d4"});
        book.put("r1bqkbnr/pppp1ppp/2n5/8/3NP3/8/PPP2PPP/RNBQKB1R b", new String[]{"f8c5", "g8f6", "d8f6"});
        book.put("r1bqk1nr/pppp1ppp/2n5/2b5/3NP3/8/PPP2PPP/RNBQKB1R w", new String[]{"d4c6", "f1e2", "c2c3"});

        // ========== Vienna Game ==========
        book.put("rnbqkbnr/pppp1ppp/8/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR b", new String[]{"g8f6", "b8c6", "f8c5"});
        book.put("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR w", new String[]{"f2f4", "g1f3", "f1c4"});
        book.put("r1bqkbnr/pppp1ppp/2n5/4p3/4PP2/2N5/PPPP2PP/R1BQKBNR b", new String[]{"e5f4", "d7d5", "g8f6"});

        // ========== Center Game ==========
        book.put("rnbqkbnr/pppp1ppp/8/4p3/3PP3/8/PPP2PPP/RNBQKBNR b", new String[]{"e5d4", "d8h4"});
        book.put("rnbqkbnr/pppp1ppp/8/8/3pP3/8/PPP2PPP/RNBQKBNR w", new String[]{"d1d4", "c2c3"});

        // ========== Danish Gambit ==========
        book.put("rnbqkbnr/pppp1ppp/8/8/3pP3/2P5/PP3PPP/RNBQKBNR b", new String[]{"d4c3", "d8e7", "g8f6"});
        book.put("rnbqkbnr/pppp1ppp/8/8/4P3/2p5/PP3PPP/RNBQKBNR w", new String[]{"b2c3", "f1c4"});

        // ========== Two Knights Defense ==========
        book.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w", new String[]{"d2d3", "b1c3", "e1g1", "d2d4"});
        book.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R b", new String[]{"f8c5", "h7h6", "d7d6"});
        book.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2BPP3/5N2/PPP2PPP/RNBQK2R b", new String[]{"e5d4", "f6e4", "f8c5"});

        // ========== Petroff Defense ==========
        book.put("rnbqkb1r/pppp1ppp/8/4p3/4n3/5N2/PPPP1PPP/RNBQKB1R w", new String[]{"f3e5", "d2d4", "f1d3"});
        book.put("rnbqkb1r/pppp1ppp/5n2/4N3/8/8/PPPP1PPP/RNBQKB1R b", new String[]{"d7d6", "f6e4", "b8c6"});
        book.put("rnbqkb1r/ppp2ppp/3p4/4N3/8/8/PPPP1PPP/RNBQKB1R w", new String[]{"e5f3", "d2d4", "f1d3"});

        // ========== Philidor Defense ==========
        book.put("rnbqkbnr/ppp2ppp/3p4/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w", new String[]{"d2d4", "f1c4", "b1c3"});
        book.put("rnbqkbnr/ppp2ppp/3p4/4p3/3PP3/5N2/PPP2PPP/RNBQKB1R b", new String[]{"e5d4", "g8f6", "b8d7"});

        // ========== Fried Liver Attack ==========
        book.put("r1bqkb1r/ppp2ppp/2n2n2/3pp3/2B1P3/5N2/PPPP1PPP/RNBQK2R w", new String[]{"f3g5", "d2d4", "b1c3"});
        book.put("r1bqkb1r/ppp2ppp/2n5/3ppN2/2B1P3/8/PPPP1PPP/RNBQK2R b", new String[]{"d5d4", "d8g5", "f6d5"});

        // ========== Evans Gambit ==========
        book.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/1PB1P3/5N2/P1PP1PPP/RNBQK2R b", new String[]{"c5b4", "c5a5", "c5b6"});
        book.put("r1bqk1nr/pppp1ppp/2n5/b3p3/1PB1P3/5N2/P1PP1PPP/RNBQK2R w", new String[]{"c2c3", "e1g1", "d2d4"});

        // ========== Smith-Morra Gambit ==========
        book.put("rnbqkbnr/pp2pppp/3p4/8/3pP3/2P2N2/PP3PPP/RNBQKB1R b", new String[]{"d4c3", "g8f6"});
        book.put("rnbqkbnr/pp2pppp/3p4/8/4P3/2p2N2/PP3PPP/RNBQKB1R w", new String[]{"b2c3", "f1c4"});

        // ========== Alapin Sicilian ==========
        book.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/2P5/PP1P1PPP/RNBQKBNR b", new String[]{"g8f6", "d7d5", "e7e6"});
        book.put("rnbqkb1r/pp1ppppp/5n2/2p5/4P3/2P5/PP1P1PPP/RNBQKBNR w", new String[]{"e4e5", "d2d4", "b1d2"});

        // ========== Miscellaneous Important Lines ==========
        book.put("rnbqkb1r/pppppppp/5n2/8/2PP4/8/PP2PPPP/RNBQKBNR b", new String[]{"e7e6", "g7g6", "e7e5", "c7c5", "d7d5"});
        book.put("rnbqkb1r/pppp1ppp/4pn2/8/3PP3/8/PPP2PPP/RNBQKBNR w", new String[]{"b1c3", "g1f3", "c1d2", "f1d3"});
        book.put("rnbqkbnr/pppppppp/8/8/6P1/8/PPPPPP1P/RNBQKBNR b", new String[]{"d7d5", "e7e5", "c7c5"});
        book.put("rnbqkbnr/pppppppp/8/8/8/7N/PPPPPPPP/RNBQKB1R b", new String[]{"e7e5", "d7d5", "g8f6"});
        book.put("rnbqkbnr/pppppppp/8/8/3PP3/8/PPP2PPP/RNBQKBNR b", new String[]{"d7d5", "g8f6", "c7c5", "e7e5", "d7d6"});
        book.put("rnbqkbnr/pppppppp/8/8/1P6/8/P1PPPPPP/RNBQKBNR b", new String[]{"e7e5", "e7e6", "g8f6", "b7b6"});
        book.put("rnbqkbnr/pppppppp/8/8/7P/8/PPPPPPP1/RNBQKBNR b", new String[]{"d7d5", "e7e5", "g8f6"});

        // ========== Advanced King's Indian Lines ==========
        book.put("rnbq1rk1/ppp1ppbp/3p1np1/8/2PPP3/2N2N2/PP2BPPP/R1BQK2R b", new String[]{"c7c5", "b8c6", "a7a6"});
        book.put("r1bqk2r/ppp1ppbp/2np1np1/8/2PPP3/2N2N2/PP2BPPP/R1BQK2R w", new String[]{"e1g1", "d1c2", "c1e3"});
    }

    public String getBookMove(String fen) {
        String[] parts = fen.split(" ");
        if (parts.length < 2) return null;

        String positionKey = parts[0] + " " + parts[1];
        String[] moves = book.get(positionKey);

        if (moves == null) {
            moves = book.get(fen);
        }

        if (moves != null && moves.length > 0) {
            return moves[random.nextInt(moves.length)];
        }
        return null;
    }

    public Move parseMove(String moveStr, Game game) {
        if (moveStr == null || moveStr.length() < 4) return null;
        try {
            int startCol = moveStr.charAt(0) - 'a';
            int startRow = 8 - (moveStr.charAt(1) - '0');
            int endCol = moveStr.charAt(2) - 'a';
            int endRow = 8 - (moveStr.charAt(3) - '0');

            Spot start = game.getBoard().getBox(startRow, startCol);
            Spot end = game.getBoard().getBox(endRow, endCol);
            Piece piece = start.getPiece();

            if (piece != null && game.isMoveLegal(start, end)) {
                boolean wasFirstMove = false;
                if (piece instanceof King) wasFirstMove = !((King) piece).hasMoved();
                if (piece instanceof Rook) wasFirstMove = !((Rook) piece).hasMoved();

                Move move = new Move(start, end, piece, end.getPiece(),
                        false, wasFirstMove, game.getBoard().getEnPassantTargetSquare(), 0, 0, 0);

                if (moveStr.length() == 5) {
                    boolean isWhite = piece.isWhite();
                    move.promotedTo = switch (moveStr.charAt(4)) {
                        case 'q' -> new Queen(isWhite);
                        case 'r' -> new Rook(isWhite);
                        case 'b' -> new Bishop(isWhite);
                        case 'n' -> new Knight(isWhite);
                        default -> null;
                    };
                }
                return move;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}