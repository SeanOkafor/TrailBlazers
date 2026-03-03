import java.awt.AlphaComposite;
import java.awt.Composite;
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
	
	// ========== COMBAT STATS ==========
	// Health Points — player starts with 5 HP per level
	private static final int MAX_HP = 5;
	private int hp = MAX_HP;
	
	// Damage dealt — total damage this player has dealt during the current level
	// Resets when entering/replaying a level. Used for score calculation at level end.
	private int damageDealt = 0;
	
	// ========== ATTACK SYSTEM ==========
	// Regular attack sprites (3 frames: P2attack-1, P2attack-2, P2attack-3)
	private BufferedImage[] attackFrames = new BufferedImage[3];
	// Special attack sprites (3 frames: P2special-1, P2special-2, P2special-3)
	private BufferedImage[] specialFrames = new BufferedImage[3];
	
	// Regular attack damage
	private static final int REGULAR_DAMAGE = 100;
	// Special attack damage
	private static final int SPECIAL_DAMAGE = 1000;
	// Cumulative regular-attack damage needed to unlock a special shot
	private static final int SPECIAL_CHARGE_THRESHOLD = 1500;
	// Tracks cumulative regular-attack damage dealt — resets to 0 after firing a special
	private int specialCharge = 0;
	
	// ========== INVINCIBILITY FRAMES ==========
	private int iFrameTimer = 0;
	private static final int IFRAME_DURATION = 100;  // 1 second at 100 FPS
	private static final int FLICKER_INTERVAL = 5;
	
	// ========== DEATH FALL ==========
	// When HP hits 0, the player sprite falls straight down off screen.
	private boolean falling = false;
	private boolean fallenOffScreen = false;
	private static final int FALL_SPEED = 5;  // pixels per frame
	
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
			// Flight animation frames
			frames[0] = ImageIO.read(new File(basePath + "sprite-2-1.png"));
			frames[1] = ImageIO.read(new File(basePath + "sprite-2-2.png"));
			frames[2] = ImageIO.read(new File(basePath + "sprite-2-3.png"));
			frames[3] = ImageIO.read(new File(basePath + "sprite-2-4.png"));
			
			// Regular attack projectile frames (3 frames)
			attackFrames[0] = ImageIO.read(new File(basePath + "P2attack-1.png"));
			attackFrames[1] = ImageIO.read(new File(basePath + "P2attack-2.png"));
			attackFrames[2] = ImageIO.read(new File(basePath + "P2attack-3.png"));
			
			// Special attack projectile frames (3 frames)
			specialFrames[0] = ImageIO.read(new File(basePath + "P2special-1.png"));
			specialFrames[1] = ImageIO.read(new File(basePath + "P2special-2.png"));
			specialFrames[2] = ImageIO.read(new File(basePath + "P2special-3.png"));
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
		// --- Death fall: override normal movement ---
		if (falling) {
			y += FALL_SPEED;
			if (y > panelHeight + 50) {
				fallenOffScreen = true;
			}
			return;  // skip normal movement and animation
		}
		
		// --- Start falling when HP reaches 0 ---
		if (hp <= 0 && !falling) {
			falling = true;
			return;
		}
		
		// --- Animation cycling ---
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % frames.length;
		}
		
		// --- Invincibility frame countdown ---
		if (iFrameTimer > 0) {
			iFrameTimer--;
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
			if (iFrameTimer > 0 && (iFrameTimer / FLICKER_INTERVAL) % 2 == 0) {
				Composite original = g2d.getComposite();
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g2d.drawImage(currentImage, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
				g2d.setComposite(original);
			} else {
				g2d.drawImage(currentImage, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
			}
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
		hp = MAX_HP;
		damageDealt = 0;
		specialCharge = 0;
		iFrameTimer = 0;
		falling = false;
		fallenOffScreen = false;
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
	
	/** Heals 1 HP if below max. Returns true if healed, false if already full. */
	public boolean heal() {
		if (hp >= MAX_HP) return false;
		hp++;
		return true;
	}
	
	/**
	 * Called when hit by an enemy projectile. Deals 1 heart of damage and
	 * activates invincibility frames. Does nothing if already invincible.
	 * @return true if damage was applied, false if player was invincible
	 */
	public boolean hit() {
		if (iFrameTimer > 0 || hp <= 0) return false;
		takeDamage(1);
		iFrameTimer = IFRAME_DURATION;
		return true;
	}
	
	/**
	 * Called when hit by an attack that deals a custom amount of damage.
	 * Activates invincibility frames. Does nothing if already invincible.
	 * @param amount hearts of damage to deal
	 * @return true if damage was applied, false if player was invincible
	 */
	public boolean hit(int amount) {
		if (iFrameTimer > 0 || hp <= 0) return false;
		takeDamage(amount);
		iFrameTimer = IFRAME_DURATION;
		return true;
	}
	
	/** Returns true if the player currently has invincibility frames active. */
	public boolean isInvincible() {
		return iFrameTimer > 0;
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
	
	/** Returns true once the player has died and fallen below the screen. */
	public boolean hasFallenOffScreen() { return fallenOffScreen; }
	
	/** Returns true if the player is currently in the death-fall animation. */
	public boolean isFalling() { return falling; }
	
	// ========== ATTACK METHODS ==========
	
	/**
	 * Creates and returns a new Projectile fired from this player's face.
	 * 
	 * If specialCharge >= 1500, fires a special attack (1000 damage) and resets charge.
	 * Otherwise, fires a regular attack (100 damage).
	 * 
	 * The projectile spawns at the right edge of the player sprite,
	 * vertically centred on the player.
	 * 
	 * @return A new Projectile instance to be added to the level's projectile list
	 */
	public Projectile shoot() {
		boolean fireSpecial = (specialCharge >= SPECIAL_CHARGE_THRESHOLD);
		
		BufferedImage[] projectileFrames;
		int damage;
		
		if (fireSpecial) {
			// Fire special attack and reset charge
			projectileFrames = specialFrames;
			damage = SPECIAL_DAMAGE;
			specialCharge = 0;
		} else {
			// Fire regular attack
			projectileFrames = attackFrames;
			damage = REGULAR_DAMAGE;
		}
		
		// Spawn at the right edge of the player (the "face"), vertically centred
		int spawnX = x + DISPLAY_WIDTH;
		int projectileHeight = fireSpecial ? 75 : 30;
		int spawnY = y + (DISPLAY_HEIGHT / 2) - (projectileHeight / 2);
		
		return new Projectile(projectileFrames, spawnX, spawnY, damage, fireSpecial, 2);
	}
	
	/**
	 * Adds regular-attack damage to the special charge meter.
	 * Called when a regular projectile from this player hits an enemy.
	 */
	public void addSpecialCharge(int amount) {
		specialCharge += amount;
	}
	
	/** Returns current special charge (for HUD display if desired). */
	public int getSpecialCharge() { return specialCharge; }
	
	/** Returns the threshold needed to unlock a special attack. */
	public static int getSpecialChargeThreshold() { return SPECIAL_CHARGE_THRESHOLD; }
}
