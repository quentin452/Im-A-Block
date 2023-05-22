package de.labystudio.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import de.labystudio.game.player.Player;
import de.labystudio.game.render.gui.FontRenderer;
import de.labystudio.game.render.gui.GuiRenderer;
import de.labystudio.game.util.*;
import de.labystudio.game.world.World;
import de.labystudio.game.world.WorldRenderer;
import de.labystudio.game.world.block.Block;
import de.labystudio.game.world.chunk.ChunkSection;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.glu.GLU;

import java.io.File;
import java.io.IOException;

public class Minecraft extends Game {

    private final Minecraft game;
    protected final GuiRenderer gui = new GuiRenderer();
    // Game
    private final Timer timer = new Timer(20.0F);
    private World world;
    private WorldRenderer worldRenderer;
    private FontRenderer fontRenderer;
    public int DEFAULT_WIDTH = 1280;
    public int DEFAULT_HEIGHT = 720;
    public int displayWidth = DEFAULT_WIDTH;
    public int displayHeight = DEFAULT_HEIGHT;
    protected boolean fullscreen;
    protected boolean enableVsync;

    // Player
    private Player player;
    private Block pickedBlock = Block.STONE;

    // States
    private boolean paused = false;
    private boolean running = true;
    private int fps;
    private GLContext glContext;

    public Minecraft() {
        this.game = this;
        glContext = new GLContext();
    }
    public void init() throws LWJGLException, IOException {
        // Make the OpenGL context current
        Display.makeCurrent();

        // Get the best available resolution from the display
        DisplayMode desktopDisplayMode = Display.getDesktopDisplayMode();
        this.displayWidth = Display.getWidth();
        this.displayHeight = Display.getWidth();

        // Setup display
        this.gui.init(this.game);
        this.gui.loadTextures();

        // Setup rendering
        this.world = new World();
        this.worldRenderer = new WorldRenderer(this.world);
        this.player = new Player(this.world);
        this.fontRenderer = new FontRenderer(this.gui, "/font.png");

        // Setup controls
        Keyboard.create();
        Mouse.create();
        Mouse.setGrabbed(true);

        // Initialize gameWindow variable
        Display.setFullscreen(this.fullscreen);

        Display.swapBuffers();

    }

    public void run() {

        try {
            // Initialize game before calling init
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

                GL11.glViewport(0, 0, this.game.displayWidth, this.game.displayHeight);
                this.render(this.timer.partialTicks);
                this.update();
                checkError();

                frames++;
                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    this.fps = frames;

                    lastTime += 1000L;
                    frames = 0;
                }

                // Escape
                if (Keyboard.isKeyDown(1) || !Display.isActive()) {
                    this.paused = true;
                    Mouse.setGrabbed(false);
                }

                // Toggle fullscreen
                if (Keyboard.isKeyDown(87)) {
                    this.toggleFullscreen();
                }

            } while (!Display.isCloseRequested() && this.running);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Shutdown
            this.world.save();
            this.game.destroy();

            Mouse.destroy();
            Keyboard.destroy();

            System.exit(0);
        }
    }

    public void shutdown() {
        this.running = false;
    }

    public void tick() {
        this.player.onTick();
        this.world.onTick();
        this.worldRenderer.onTick();
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
        double zFar = Math.pow(WorldRenderer.RENDER_DISTANCE * ChunkSection.SIZE, 2);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        // Calculate the aspect ratio
        float aspectRatio = (float) this.game.displayWidth / (float) this.game.displayHeight;

        GLU.gluPerspective(85.0F + this.player.getFOVModifier(), aspectRatio, 0.05F, (float) zFar);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        this.moveCameraToPlayer(partialTicks);
    }

    public void render(float partialTicks) {
        float mouseMoveX = Mouse.getDX();
        float mouseMoveY = Mouse.getDY();

        if (!this.paused) {
            this.player.turn(mouseMoveX, mouseMoveY);
        }

        // Calculate the target block of the player
        HitResult hitResult = this.getTargetBlock();

        while (Mouse.next()) {
            if ((Mouse.getEventButton() == 0) && (Mouse.getEventButtonState())) {
                // Resume game if paused
                if (this.paused) {
                    this.paused = false;

                    Mouse.setGrabbed(true);
                } else {
                    // Destroy block
                    if (hitResult != null) {
                        this.world.setBlockAt(hitResult.x, hitResult.y, hitResult.z, 0);
                    }
                }
            }

            // Place block
            if ((Mouse.getEventButton() == 1) && (Mouse.getEventButtonState())) {
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
            if ((Mouse.getEventButton() == 2) && (Mouse.getEventButtonState())) {
                if (hitResult != null) {
                    short typeId = this.world.getBlockAt(hitResult.x, hitResult.y, hitResult.z);
                    if (typeId != 0) {
                        this.pickedBlock = Block.getById(typeId);
                    }
                }
            }
        }
        while (Keyboard.next()) {
            if ((Keyboard.getEventKey() == 28) && (Keyboard.getEventKeyState())) {
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
        this.fontRenderer.drawString("X: " + String.format("%.2f", this.player.x), 2, 16);
        this.fontRenderer.drawString("Y: " + String.format("%.2f", this.player.y), 2, 30);
        this.fontRenderer.drawString("Z: " + String.format("%.2f", this.player.z), 2, 44);
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

    public void toggleFullscreen() {
        try {
            this.fullscreen = !this.fullscreen;
            LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();

            if (this.fullscreen) {
              //  config.setFromDisplayMode(Display.getDesktopDisplayMode());
                config.fullscreen = true;
            } else {
                config.width = DEFAULT_WIDTH;
                config.height = DEFAULT_HEIGHT;
            }

            // Get the best available resolution from the display
            org.lwjgl.opengl.DisplayMode desktopDisplayMode = Display.getDesktopDisplayMode();
            int displayWidth = Display.getWidth();
            int displayHeight = Display.getWidth();

            // Set the resolution based on the actual display capabilities
            if (displayWidth > 0 && displayHeight > 0) {
                config.width = displayWidth;
                config.height = displayHeight;
            }

            new LwjglApplication(this, config);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void update() {
        Display.update();

        int width = Display.getWidth();
        int height = Display.getHeight();

        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }

        // Update the display width and height
        this.displayWidth = width;
        this.displayHeight = height;

        // Resize the window
        this.resize(this.displayWidth, this.displayHeight);
    }

    @Override
    public void create() {
        run();
    }

    public void resize(int width, int height) {
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }

        this.displayWidth = width;
        this.displayHeight = height;

        this.game.gui.init(this);
    }

    public static void checkError() {
        int error = Gdx.gl.glGetError();
        if (error != GL20.GL_NO_ERROR) {
            String errorMessage = getGLErrorString(error);
            throw new IllegalStateException(errorMessage);
        }
    }

    private static String getGLErrorString(int error) {
        String errorMessage;
        switch (error) {
            case GL20.GL_INVALID_ENUM:
                errorMessage = "GL_INVALID_ENUM";
                break;
            case GL20.GL_INVALID_VALUE:
                errorMessage = "GL_INVALID_VALUE";
                break;
            case GL20.GL_INVALID_OPERATION:
                errorMessage = "GL_INVALID_OPERATION";
                break;
            case GL20.GL_INVALID_FRAMEBUFFER_OPERATION:
                errorMessage = "GL_INVALID_FRAMEBUFFER_OPERATION";
                break;
            case GL20.GL_OUT_OF_MEMORY:
                errorMessage = "GL_OUT_OF_MEMORY";
                break;
            default:
                errorMessage = "Unknown error";
                break;
        }
        return errorMessage;
    }

    public static void main(String[] args) throws LWJGLException {
        // Set library path if not available
        if (System.getProperty("org.lwjgl.librarypath") == null) {
            System.setProperty("org.lwjgl.librarypath", new File("run/natives").getAbsolutePath());
        }

        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.title = "Im A Block";
        config.width = 1280;
        config.height = 720;
        config.vSyncEnabled = false;

       new LwjglApplication(new Minecraft(), config);
    }

    public void destroy() {
        Display.destroy();
    }
}
