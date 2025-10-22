package org.pocketchess.ui.gameframepack.piecesandclock;

import org.pocketchess.core.pieces.*;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches chess piece images.
 */
public class ImageLoader {

    // Cache of loaded images
    private static final Map<String, Image> imageCache = new HashMap<>();


    static {
        String[] pieceKeys = {
                "white_king", "white_queen", "white_rook", "white_bishop", "white_knight", "white_pawn",
                "black_king", "black_queen", "black_rook", "black_bishop", "black_knight", "black_pawn"
        };

        String[] fileNames = {
                "wk.png", "wq.png", "wr.png", "wb.png", "wn.png", "wp.png",
                "bk.png", "bq.png", "br.png", "bb.png", "bn.png", "bp.png"
        };

        for (int i = 0; i < pieceKeys.length; i++) {
            String key = pieceKeys[i];
            String fileName = fileNames[i];
            try (InputStream is = ImageLoader.class.getResourceAsStream("/pieces/" + fileName)) {
                if (is == null) {
                    throw new IOException("Resource file not found: /pieces/" + fileName);
                }
                Image image = ImageIO.read(is);
                imageCache.put(key, image);
            } catch (IOException e) {
                System.err.println("Error loading image: " + fileName);
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets the image for a given chess piece.
     */
    public static Image getImageForPiece(Piece piece) {
        if (piece == null) return null;

        // Determine color and type
        String color = piece.isWhite() ? "white" : "black";
        String type = "";
        if (piece instanceof King) type = "king";
        else if (piece instanceof Queen) type = "queen";
        else if (piece instanceof Rook) type = "rook";
        else if (piece instanceof Bishop) type = "bishop";
        else if (piece instanceof Knight) type = "knight";
        else if (piece instanceof Pawn) type = "pawn";

        return imageCache.get(color + "_" + type);
    }
}