import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Player1 - Animated sprite for Player 1 with 4-frame flight animation.
 * 
 * ==================== HOW THE SPRITE ANIMATION WORKS ====================
 * 
 * The flight animation creates the illusion of a character flying through the level
 * by cycling through 4 sprite frames in a continuous loop.
 * 
 * SETUP:
 * 1. Four PNG sprite frames are loaded from res/New Graphics/Player1Sprites/
 *    (sprite-1-1.png through sprite-1-4.png, each ~120x50 pixels).
 * 2. The sprites are drawn scaled up (2x) so the character is visible on the 1000x1000 panel.
 * 3. The player spawns in the left quadrant of the screen, vertically centred.
 * 
 * PER-FRAME UPDATE (called from the game loop via the level panel):
 * 1. updateAnimation() increments a tick counter each frame.
 * 2. Every ANIMATION_DELAY ticks (8 frames at 100 FPS = ~12.5 animation FPS),
 *    the current frame index advances: 0 -> 1 -> 2 -> 3 -> 0 -> 1 -> ...
 * 3. If W is held (movingUp = true), the player's Y position decreases (moves up).
 *    If S is held (movingDown = true), the player's Y position increases (moves down).
 * 4. Y position is clamped so the player can't go off-screen.
 * 
 * RENDERING (draw method, called from the level panel's paintComponent):
 * 1. The current frame image (frames[currentFrame]) is drawn at (x, y).
 * 2. The image is scaled to DISPLAY_WIDTH x DISPLAY_HEIGHT (2x original size).
 * 3. The player is drawn AFTER all parallax layers, so it appears in front.
 * 
 * The result: a smoothly animated flying character that the player can move
 * up and down on the left side of the screen using W and S keys.
 * =========================================================================
 */
public class Player1 {
	
	// Sprite frames (4 frames of flight animation)
	private BufferedImage[] frames = new BufferedImage[4];
	
	// Current animation frame index (0-3)
	private int currentFrame = 0;
	
	// Tick counter for controlling animation speed
	private int animationTick = 0;
	
	// How many game ticks between frame changes
	// At 100 FPS, 8 ticks = frame change every 80ms = ~12.5 animation FPS
	private static final int ANIMATION_DELAY = 8;
	
	// Display size (3/5 of the 2x scale — small enough to look right on 1000x1000 panel)
	private static final int DISPLAY_WIDTH = 150;
	private static final int DISPLAY_HEIGHT = 60;
	
	// Position on screen
	private int x;
	private int y;
	
	// Movement speed in pixels per frame
	private static final int MOVE_SPEED = 10;
	
	// Movement state - set by keyboard input from MainWindow
	private boolean movingUp = false;
	private boolean movingDown = false;
	
	// Panel dimensions for clamping
	private int panelWidth = 1000;
	private int panelHeight = 1000;
	
	// ========== COMBAT STATS ==========
	// Health Points — player starts with 5 HP per level
	private static final int MAX_HP = 5;
	private int hp = MAX_HP;
	
	// Damage dealt — total damage this player has dealt during the current level
	// Resets when entering/replaying a level. Used for score calculation at level end.
	private int damageDealt = 0;
	
	public Player1() {
		loadFrames();
		// Spawn in the left quadrant (x = 100), vertically centred
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2;
	}
	
	/**
	 * Loads the 4 sprite frames from the Player1Sprites folder.
	 */
	private void loadFrames() {
		String basePath = "res/New Graphics/Player1Sprites/";
		try {
			frames[0] = ImageIO.read(new File(basePath + "sprite-1-1.png"));
			frames[1] = ImageIO.read(new File(basePath + "sprite-1-2.png"));
			frames[2] = ImageIO.read(new File(basePath + "sprite-1-3.png"));
			frames[3] = ImageIO.read(new File(basePath + "sprite-1-4.png"));
		} catch (IOException e) {
			System.err.println("Error loading Player 1 sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Called once per frame from the game loop.
	 * Advances the animation frame and applies vertical movement.
	 */
	public void update() {
		// --- Animation cycling ---
		// Increment tick counter each frame
		animationTick++;
		// When enough ticks have passed, advance to the next frame
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			// Cycle through frames 0 -> 1 -> 2 -> 3 -> 0 -> ...
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
	 * Called from the level panel's paintComponent, AFTER all parallax layers.
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
	 * Resets player position to spawn point (left quadrant, vertically centred).
	 * Called when entering a level to ensure consistent starting position.
	 */
	public void resetPosition() {
		x = 100;
		y = (panelHeight - DISPLAY_HEIGHT) / 2;
		currentFrame = 0;
		animationTick = 0;
		hp = MAX_HP;
		damageDealt = 0;
	}
	
	// ========== COMBAT STAT ACCESSORS ==========
	
	public int getHp() { return hp; }
	public int getMaxHp() { return MAX_HP; }
	public int getDamageDealt() { return damageDealt; }
	
	/** Called when this player takes damage. Reduces HP (minimum 0). */
	public void takeDamage(int amount) {
		hp -= amount;
		if (hp < 0) hp = 0;
	}
	
	/** Called when this player deals damage to an enemy. Accumulates total. */
	public void addDamageDealt(int amount) {
		damageDealt += amount;
	}
	
	/** Returns true if the player is still alive (HP > 0). */
	public boolean isAlive() {
		return hp > 0;
	}
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
}
