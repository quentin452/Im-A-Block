package de.labystudio.game;

import de.labystudio.game.player.Player;
import de.labystudio.game.render.gui.FontRenderer;
import de.labystudio.game.render.gui.GuiRenderer;
import de.labystudio.game.util.BoundingBox;
import de.labystudio.game.util.EnumBlockFace;
import de.labystudio.game.util.EnumWorldBlockLayer;
import de.labystudio.game.util.HitResult;
import de.labystudio.game.util.Timer;
import de.labystudio.game.world.World;
import de.labystudio.game.world.WorldRenderer;
import de.labystudio.game.world.block.Block;
import de.labystudio.game.world.chunk.ChunkSection;
import org.joml.Math;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;

public class Minecraft implements Runnable {

    private final MinecraftWindow gameWindow = new MinecraftWindow(this, new Canvas(), new Frame());
    protected final GuiRenderer gui = new GuiRenderer();

    // Game
    private final Timer timer = new Timer(20.0F);
    private World world;
    private WorldRenderer worldRenderer;
    private FontRenderer fontRenderer;

    // Player
    private Player player;
    private Block pickedBlock = Block.STONE;

    // States
    private boolean paused = false;
    private boolean running = true;
    private int fps;

    public void init() throws IOException {
        // Setup display
        this.gameWindow.init();
        this.gui.init(this.gameWindow);
        this.gui.loadTextures();

        // Setup rendering
        this.world = new World();
        this.worldRenderer = new WorldRenderer(this.world);
        this.player = new Player(this.world);
        this.fontRenderer = new FontRenderer(this.gui, "/font.png");

        // Setup controls
        GLFW.glfwSetKeyCallback(this.gameWindow.getWindow(), (window, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                this.paused = true;
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
            if (key == GLFW.GLFW_KEY_W && action == GLFW.GLFW_PRESS) {
                this.gameWindow.toggleFullscreen();
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        });

        GLFW.glfwSetInputMode(this.gameWindow.getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }


    public void run() {
        try {
            this.init();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }

        long lastTime = System.currentTimeMillis();
        int frames = 0;
        try {
            do {
                this.timer.advanceTime();
                for (int i = 0; i < this.timer.ticks; i++) {
                    this.tick();
                }

                // Limit framerate
                //Thread.sleep(5L);

                GL11.glViewport(0, 0, this.gameWindow.displayWidth, this.gameWindow.displayHeight);
                this.render(this.timer.partialTicks);
                this.gameWindow.update();
                checkError();

                frames++;
                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    this.fps = frames;

                    lastTime += 1000L;
                    frames = 0;
                }

            } while (GLFW.glfwWindowShouldClose(gameWindow.getWindow()) && this.running);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Shutdown
            this.world.save();
            this.gameWindow.destroy();

            GLFW.glfwDestroyWindow(gameWindow.getWindow());
            GLFW.glfwDestroyCursor(gameWindow.getWindow());

            System.exit(0);
        }
    }

    public void shutdown() {
        this.running = false;
    }

    public void tick() {
        this.player.onTick();
        this.world.onTick();

        int cameraChunkX = (int) this.player.x >> 4;
        int cameraChunkZ = (int) this.player.z >> 4;
        this.worldRenderer.onTick(cameraChunkX, cameraChunkZ);
    }

    private void moveCameraToPlayer(float partialTicks) {
        GL11.glTranslatef(0.0F, 0.0F, -0.3F);
        GL11.glRotatef(this.player.pitch, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(this.player.yaw, 0.0F, 1.0F, 0.0F);

        double x = this.player.prevX + (this.player.x - this.player.prevX) * partialTicks;
        double y = this.player.prevY + (this.player.y - this.player.prevY) * partialTicks;
        double z = this.player.prevZ + (this.player.z - this.player.prevZ) * partialTicks;
        GL11.glTranslated(-x, -y, -z);

        // Eye height
        GL11.glTranslatef(0.0F, -this.player.getEyeHeight(), 0.0F);
    }

    private void setupCamera(float partialTicks) {
        double zFar = (WorldRenderer.RENDER_DISTANCE * ChunkSection.SIZE) * (WorldRenderer.RENDER_DISTANCE * ChunkSection.SIZE);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        Matrix4f projMatrix = new Matrix4f();
        projMatrix.perspective((float) Math.toRadians(85.0F + this.player.getFOVModifier()),
                (float) this.gameWindow.displayWidth / (float) this.gameWindow.displayHeight, 0.05F, (float) zFar);

        FloatBuffer projMatrixBuffer = BufferUtils.createFloatBuffer(16);
        projMatrix.get(projMatrixBuffer);

        GL11.glLoadMatrixf(projMatrixBuffer);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        this.moveCameraToPlayer(partialTicks);
    }

    public void render(float partialTicks) {
        GLFW.glfwSetCursorPosCallback(this.gameWindow.getWindow(), (window, xpos, ypos) -> {
            float mouseMoveX = (float) xpos;
            float mouseMoveY = (float) ypos;

            if (!this.paused) {
                this.player.turn(mouseMoveX, mouseMoveY);
            }
        });

        // Calculate the target block of the player
        HitResult hitResult = this.getTargetBlock();

        GLFW.glfwSetMouseButtonCallback(this.gameWindow.getWindow(), (window, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                // Resume game if paused
                if (this.paused) {
                    this.paused = false;
                } else {
                    // Destroy block
                    if (hitResult != null) {
                        this.world.setBlockAt(hitResult.x, hitResult.y, hitResult.z, 0);
                    }
                }
            }

            // Place block
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS) {
                if (hitResult != null) {
                    int x = hitResult.x + hitResult.face.x;
                    int y = hitResult.y + hitResult.face.y;
                    int z = hitResult.z + hitResult.face.z;

                    BoundingBox placedBoundingBox = new BoundingBox(x, y, z, x + 1, y + 1, z + 1);

                    // Don't place blocks if the player is standing there
                    if (!placedBoundingBox.intersects(this.player.boundingBox)) {
                        this.world.setBlockAt(x, y, z, this.pickedBlock.getId());
                    }

                }
            }

            // Pick block
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_PRESS) {
                if (hitResult != null) {
                    short typeId = this.world.getBlockAt(hitResult.x, hitResult.y, hitResult.z);
                    if (typeId != 0) {
                        this.pickedBlock = Block.getById(typeId);
                    }
                }
            }
        });

        while (GLFW.glfwGetKey(gameWindow.getWindow(), GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            if (GLFW.glfwGetKey(gameWindow.getWindow(), GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS) {
                this.world.save();
            }
        }

        // Clear color and depth buffer
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // Camera
        this.setupCamera(partialTicks);

        int cameraChunkX = (int) this.player.x >> 4;
        int cameraChunkZ = (int) this.player.z >> 4;

        // Fog
        GL11.glEnable(GL11.GL_FOG);
        this.worldRenderer.setupFog(this.player.isHeadInWater());

        // Setup rendering for solid blocks
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_CULL_FACE);

        // Render solid blocks
        this.worldRenderer.render(cameraChunkX, cameraChunkZ, EnumWorldBlockLayer.SOLID);

        // Enable alpha and disable face culling
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(519, -1.0F);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // Render cutout blocks (Leaves, glass, water..)
        this.worldRenderer.render(cameraChunkX, cameraChunkZ, EnumWorldBlockLayer.CUTOUT);

        // Render selection
        if (hitResult != null) {
            this.renderSelection(hitResult);
        }

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_FOG);

        this.gui.setupCamera();
        this.gui.renderCrosshair();

        // Enable alpha
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        this.fontRenderer.drawString("FPS: " + this.fps, 2, 2);
    }

    public void renderSelection(HitResult hitResult) {
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 1.0F);
        GL11.glLineWidth(1);
        this.worldRenderer.getBlockRenderer().drawBoundingBox(hitResult.x, hitResult.y, hitResult.z,
                hitResult.x + 1, hitResult.y + 1, hitResult.z + 1);
    }

    private HitResult getTargetBlock() {
        double yaw = Math.toRadians(-this.player.yaw + 90);
        double pitch = Math.toRadians(-this.player.pitch);

        double xzLen = Math.cos(pitch);
        double vectorX = xzLen * Math.cos(yaw);
        double vectorY = Math.sin(pitch);
        double vectorZ = xzLen * Math.sin(-yaw);

        double targetX = this.player.x - vectorX;
        double targetY = this.player.y + this.player.getEyeHeight() - 0.08D - vectorY;
        double targetZ = this.player.z - vectorZ;

        int shift = -1;

        int prevAirX = (int) (targetX < 0 ? targetX + shift : targetX);
        int prevAirY = (int) (targetY < 0 ? targetY + shift : targetY);
        int prevAirZ = (int) (targetZ < 0 ? targetZ + shift : targetZ);

        for (int i = 0; i < 800; i++) {
            targetX += vectorX / 10D;
            targetY += vectorY / 10D;
            targetZ += vectorZ / 10D;

            int hitX = (int) (targetX < 0 ? targetX + shift : targetX);
            int hitY = (int) (targetY < 0 ? targetY + shift : targetY);
            int hitZ = (int) (targetZ < 0 ? targetZ + shift : targetZ);

            EnumBlockFace targetFace = null;
            for (EnumBlockFace type : EnumBlockFace.values()) {
                if (prevAirX == hitX + type.x && prevAirY == hitY + type.y && prevAirZ == hitZ + type.z) {
                    targetFace = type;
                    break;
                }
            }

            if (this.world.isSolidBlockAt(hitX, hitY, hitZ)) {
                if (targetFace == null) {
                    return null;
                }
                return new HitResult(hitX, hitY, hitZ, targetFace);
            } else {
                prevAirX = (int) (targetX < 0 ? targetX + shift : targetX);
                prevAirY = (int) (targetY < 0 ? targetY + shift : targetY);
                prevAirZ = (int) (targetZ < 0 ? targetZ + shift : targetZ);
            }
        }
        return null;
    }

    public static void checkError() {
        int error = GL11.glGetError();
        if (error != 0) {
            throw new IllegalStateException("OpenGL error " + error);
        }
    }

    public static void main(String[] args) throws IOException {
        // Set library path if not available
        if (System.getProperty("org.lwjgl.librarypath") == null) {
            System.setProperty("org.lwjgl.librarypath", new File("run/natives").getAbsolutePath());
        }

        new Thread(new Minecraft(), "Game Thread").start();
    }
}
