package de.labystudio.game.world.chunk;

import de.labystudio.game.render.Tessellator;
import de.labystudio.game.util.EnumWorldBlockLayer;
import de.labystudio.game.world.World;
import de.labystudio.game.world.WorldRenderer;
import de.labystudio.game.world.block.Block;
import org.lwjgl.opengl.GL11;

public class ChunkSection {
    public static final int SIZE = 16;

    public final World world;

    private final byte[] blocks = new byte[(SIZE * SIZE + SIZE) * SIZE + SIZE];
    private final byte[] blockLight = new byte[(SIZE * SIZE + SIZE) * SIZE + SIZE];

    public int x;
    public int y;
    public int z;

    private final int lists;
    private boolean queuedForRebuild = true;

    public ChunkSection(World world, int x, int y, int z) {
        this.world = world;

        this.x = x;
        this.y = y;
        this.z = z;

        this.lists = GL11.glGenLists(EnumWorldBlockLayer.values().length);

        // Fill chunk with light
        for (int lightX = 0; lightX < SIZE; lightX++) {
            for (int lightY = 0; lightY < SIZE; lightY++) {
                for (int lightZ = 0; lightZ < SIZE; lightZ++) {
                    int index = lightY << 8 | lightZ << 4 | lightX;
                    this.blockLight[index] = 15;
                }
            }
        }
    }

    public void render(EnumWorldBlockLayer renderLayer) {
        // Call list with render layer
        GL11.glCallList(this.lists + renderLayer.ordinal());
    }

    public void rebuild(WorldRenderer renderer) {
        this.queuedForRebuild = false;

        // Rebuild all render layers
        for (EnumWorldBlockLayer layer : EnumWorldBlockLayer.values()) {
            rebuild(renderer, layer);
        }
    }

    public void queueForRebuild() {
        this.queuedForRebuild = true;
    }

    public boolean isQueuedForRebuild() {
        return queuedForRebuild;
    }

    private void rebuild(WorldRenderer renderer, EnumWorldBlockLayer renderLayer) {
        // Create GPU memory list storage
        GL11.glNewList(this.lists + renderLayer.ordinal(), GL11.GL_COMPILE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.textureId);

        // Start rendering
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(7);

        // Render blocks
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    short typeId = getBlockAt(x, y, z);

                    if (typeId != 0) {
                        int absoluteX = this.x * SIZE + x;
                        int absoluteY = this.y * SIZE + y;
                        int absoluteZ = this.z * SIZE + z;

                        Block block = Block.getById(typeId);
                        if (block != null && ((renderLayer == EnumWorldBlockLayer.CUTOUT) == block.isTransparent())) {
                            block.render(renderer, this.world, absoluteX, absoluteY, absoluteZ);
                        }
                    }
                }
            }
        }

        // Stop rendering
        tessellator.draw();

        // End storage
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEndList();
    }

    public boolean isEmpty() {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    int index = y << 8 | z << 4 | x;
                    if (this.blocks[index] != 0) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public boolean isSolidBlockAt(int x, int y, int z) {
        return getBlockAt(x, y, z) != 0;
    }

    public byte getBlockAt(int x, int y, int z) {
        int index = y << 8 | z << 4 | x;
        return this.blocks[index];
    }

    public void setLightAt(int x, int y, int z, int lightLevel) {
        int index = y << 8 | z << 4 | x;
        this.blockLight[index] = (byte) lightLevel;
    }

    public void setBlockAt(int x, int y, int z, int type) {
        int index = (y << 8) | (z << 4) | x;
        this.blocks[index] = (byte) type;
    }

    public int getLightAt(int x, int y, int z) {
        int index = y << 8 | z << 4 | x;
        return this.blockLight[index];
    }
}
