package de.labystudio.game.world.block;

import de.labystudio.game.util.EnumBlockFace;

public class BlockGrass extends Block {

    public BlockGrass(int id, int textureSlot) {
        super(id, textureSlot);
    }

    @Override
    public int getTextureForFace(EnumBlockFace face) {
        return switch (face) {
            case TOP -> this.textureSlotId;
            case BOTTOM -> this.textureSlotId + 1;
            default -> this.textureSlotId + 2;
        };
    }
}
