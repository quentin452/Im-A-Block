package de.labystudio.game;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.awt.*;
import java.io.IOException;

public class MinecraftWindow {
    private long window;

    public long getWindow() {
        return window;
    }
    public static final int DEFAULT_WIDTH = 854;
    public static final int DEFAULT_HEIGHT = 480;

    private final Minecraft game;

    protected final Canvas canvas;
    protected final Frame frame;

    protected boolean fullscreen;
    protected boolean enableVsync;

    public int displayWidth = DEFAULT_WIDTH;
    public int displayHeight = DEFAULT_HEIGHT;

    public MinecraftWindow(Minecraft game, Canvas canvas, Frame frame) {
        this.game = game;
        this.canvas = canvas;
        this.frame = frame;

        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        // Create the window
        window = GLFW.glfwCreateWindow(DEFAULT_WIDTH, DEFAULT_HEIGHT, "3DGame", 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Center the window
        GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        GLFW.glfwSetWindowPos(window, (vidmode.width() - DEFAULT_WIDTH) / 2, (vidmode.height() - DEFAULT_HEIGHT) / 2);

        // Make the OpenGL context current
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        // Enable v-sync
        GLFW.glfwSwapInterval(1);

        // Show the window
        GLFW.glfwShowWindow(window);
    }


    public void init() throws IOException {
        Graphics g = this.canvas.getGraphics();
        if (g != null) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, this.displayWidth, this.displayHeight);
            g.dispose();
        }

        // Init game
    }

    public void toggleFullscreen() {
        this.fullscreen = !this.fullscreen;

        System.out.println("Toggle fullscreen!");

        GLFW.glfwSetWindowMonitor(window, this.fullscreen ? GLFW.glfwGetPrimaryMonitor() : 0, 0, 0, this.displayWidth, this.displayHeight, GLFW.GLFW_DONT_CARE);

        if (this.fullscreen) {
            GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
            this.displayWidth = vidmode.width();
            this.displayHeight = vidmode.height();
        } else {
            this.displayWidth = DEFAULT_WIDTH;
            this.displayHeight = DEFAULT_HEIGHT;
        }

        System.out.println("Size: " + this.displayWidth + ", " + this.displayHeight);
    }

    public void update() {
        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();

        if (!this.fullscreen && (this.canvas.getWidth() != this.displayWidth || this.canvas.getHeight() != this.displayHeight)) {
            this.displayWidth = this.canvas.getWidth();
            this.displayHeight = this.canvas.getHeight();

            if (this.displayWidth <= 0) {
                this.displayWidth = 1;
            }

            if (this.displayHeight <= 0) {
                this.displayHeight = 1;
            }

            this.resize(this.displayWidth, this.displayHeight);
        }
    }

    private void resize(int width, int height) {
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

    public void destroy() {
        GLFW.glfwTerminate();
    }
}
