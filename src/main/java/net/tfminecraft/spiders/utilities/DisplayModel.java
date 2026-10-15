package net.tfminecraft.spiders.utilities;

import java.util.ArrayList;
import java.util.List;

public class DisplayModel {
    private List<BlockDisplayModelPiece> pieces;

    public DisplayModel(List<BlockDisplayModelPiece> pieces) {
        this.pieces = pieces;
    }

    public List<BlockDisplayModelPiece> getPieces() {
        return pieces;
    }

    public void setPieces(List<BlockDisplayModelPiece> pieces) {
        this.pieces = pieces;
    }

    public DisplayModel scale(float scale) {
        for (BlockDisplayModelPiece piece : pieces) piece.scale(scale, scale, scale);
        return this;
    }

    public DisplayModel scale(float x, float y, float z) {
        for (BlockDisplayModelPiece piece : pieces) piece.scale(x, y, z);
        return this;
    }

    public DisplayModel clone() {
        List<BlockDisplayModelPiece> cloned = new ArrayList<>(pieces.size());
        for (BlockDisplayModelPiece piece : pieces) cloned.add(piece.clone());
        return new DisplayModel(cloned);
    }

    public static DisplayModel empty() {
        return new DisplayModel(new ArrayList<>());
    }
}
