import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Player2 - Animated sprite for Player 2 with 4-frame flight animation.
 * 
 * ==================== HOW THE SPRITE ANIMATION WORKS ====================
 * 
 * Identical animation system to Player1, but uses Player 2 sprites and
 * is controlled with the Up/Down arrow keys. Player 2 only appears when
 * multiplayer mode is enabled.
 * 
 * SETUP:
 * 1. Four PNG sprite frames are loaded from res/New Graphics/Player2Sprites/
 *    (sprite-2-1.png through sprite-2-4.png, each ~116x48 pixels).
 * 2. The sprites are drawn at 150x60 (same scale as Player 1).
 * 3. Player 2 spawns in the left quadrant, 100 pixels below Player 1's centre.
 * 
 * PER-FRAME UPDATE:
 * 1. Animation cycles through 4 frames at ~12.5 FPS (every 8 game ticks).
 * 2. Up arrow held → moves up. Down arrow held → moves down.
 * 3. Y clamped to stay on screen.
 * 
 * RENDERING:
 * Drawn after Player 1 in the level panel's paintComponent.
 * =========================================================================
 */
public class Player2 {
	
	// Sprite frames (4 frames of flight animation)
	private BufferedImage[] frames = new BufferedImage[4];
	
	// Current animation frame index (0-3)
	private int currentFrame = 0;
	
	// Tick counter for controlling animation speed
	private int animationTick = 0;
	
	// How many game ticks between frame changes
	private static final int ANIMATION_DELAY = 8;
	
	// Display size (same as Player 1)
	private static final int DISPLAY_WIDTH = 150;
	private static final int DISPLAY_HEIGHT = 60;
	
	// Position on screen
	private int x;
	private int y;
	
	// Movement speed in pixels per frame (same as Player 1)
	private static final int MOVE_SPEED = 10;
	
	// Movement state - set by keyboard input from MainWindow
	private boolean movingUp = false;
	private boolean movingDown = false;
	
	// Panel dimensions for clamping
	private int panelWidth = 1000;
	private int panelHeight = 1000;
	
	public Player2() {
		loadFrames();
		// Spawn in the left quadrant (x = 100), 100 pixels below Player 1's centre
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2 + 100;
	}
	
	/**
	 * Loads the 4 sprite frames from the Player2Sprites folder.
	 */
	private void loadFrames() {
		String basePath = "res/New Graphics/Player2Sprites/";
		try {
			frames[0] = ImageIO.read(new File(basePath + "sprite-2-1.png"));
			frames[1] = ImageIO.read(new File(basePath + "sprite-2-2.png"));
			frames[2] = ImageIO.read(new File(basePath + "sprite-2-3.png"));
			frames[3] = ImageIO.read(new File(basePath + "sprite-2-4.png"));
		} catch (IOException e) {
			System.err.println("Error loading Player 2 sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Called once per frame from the game loop.
	 * Advances the animation frame and applies vertical movement.
	 */
	public void update() {
		// --- Animation cycling ---
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % frames.length;
		}
		
		// --- Vertical movement ---
		if (movingUp) {
			y -= MOVE_SPEED;
		}
		if (movingDown) {
			y += MOVE_SPEED;
		}
		
		// Clamp Y position so player stays on screen
		if (y < 0) {
			y = 0;
		}
		if (y > panelHeight - DISPLAY_HEIGHT) {
			y = panelHeight - DISPLAY_HEIGHT;
		}
	}
	
	/**
	 * Draws the current animation frame at the player's position.
	 */
	public void draw(Graphics2D g2d) {
		BufferedImage currentImage = frames[currentFrame];
		if (currentImage != null) {
			g2d.drawImage(currentImage, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
		}
	}
	
	// --- Movement control methods (called by KeyListener in MainWindow) ---
	
	public void setMovingUp(boolean moving) {
		this.movingUp = moving;
	}
	
	public void setMovingDown(boolean moving) {
		this.movingDown = moving;
	}
	
	/**
	 * Resets player position to spawn point (left quadrant, 100px below Player 1 centre).
	 */
	public void resetPosition() {
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2 + 100;
		currentFrame = 0;
		animationTick = 0;
	}
}
