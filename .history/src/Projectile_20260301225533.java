import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Projectile - An animated attack that flies horizontally across the screen.
 * 
 * ==================== HOW THE PROJECTILE WORKS ====================
 * 
 * When a player presses their shoot key (G for P1, L for P2), a Projectile
 * is spawned at the player's face (right edge, vertically centred). It then
 * travels to the right at a constant speed until it either hits an enemy
 * or flies off the right side of the screen.
 * 
 * ANIMATION:
 * The projectile has 3 sprite frames that animate in a ping-pong pattern:
 *   Frame 1 → Frame 2 → Frame 3 → Frame 2 → Frame 1 → Frame 2 → ...
 * This is achieved using a pre-defined sequence array: {0, 1, 2, 1}
 * that repeats. The animation ticks at a slightly faster rate than the
 * player sprite (every 6 game ticks at 100 FPS = ~16.7 animation FPS).
 * 
 * TYPES:
 * - Regular Attack: 100 damage, smaller sprite (60x30 display size)
 * - Special Attack: 1000 damage, larger sprite (100x50 display size)
 *   Unlocked after dealing 1500 cumulative regular-attack damage to enemies.
 *   Automatically fires on the next shot, then the charge resets to 0.
 * 
 * LIFECYCLE:
 * 1. Created by a Player's shoot() method
 * 2. Added to the active level panel's projectile list
 * 3. Updated every frame (move right + animate)
 * 4. Removed when off-screen (x > 1000) or when it hits an enemy
 * ===================================================================
 */
public class Projectile {
	
	// The 3 sprite frames for this projectile's animation
	private BufferedImage[] frames;
	
	// Ping-pong animation sequence: 1 → 2 → 3 → 2 → 1 → 2 → 3 → ...
	// Stored as 0-indexed: {0, 1, 2, 1} repeating
	private static final int[] ANIM_SEQUENCE = {0, 1, 2, 1};
	
	// Current position in the ANIM_SEQUENCE array
	private int currentSequenceIndex = 0;
	
	// Tick counter for animation timing
	private int animationTick = 0;
	
	// How many game ticks between frame changes
	// At 100 FPS, 6 ticks = frame change every 60ms = ~16.7 animation FPS
	private static final int ANIMATION_DELAY = 6;
	
	// Position on screen
	private int x;
	private int y;
	
	// Horizontal speed (pixels per frame)
	// At 100 FPS, 8 px/frame = 800 px/sec → crosses ~750px in ~0.94 seconds
	private static final int SPEED = 8;
	
	// Display dimensions — regular attacks are smaller, specials are larger
	private int displayWidth;
	private int displayHeight;
	
	// Damage this projectile deals on hit
	private int damage;
	
	// Whether this is a special attack (visual/damage distinction)
	private boolean special;
	
	// Which player fired this (1 or 2) — used for damage tracking
	private int ownerPlayer;
	
	/**
	 * Creates a new projectile.
	 * 
	 * @param frames       The 3 sprite frames (loaded by the player class)
	 * @param x            Starting X position (player's right edge)
	 * @param y            Starting Y position (vertically centred on player)
	 * @param damage       Damage dealt on hit (100 for regular, 1000 for special)
	 * @param special      True if this is a special attack
	 * @param ownerPlayer  1 for Player 1, 2 for Player 2
	 */
	public Projectile(BufferedImage[] frames, int x, int y, int damage, boolean special, int ownerPlayer) {
		this.frames = frames;
		this.x = x;
		this.y = y;
		this.damage = damage;
		this.special = special;
		this.ownerPlayer = ownerPlayer;
		
		// Special attacks are visually larger to indicate their power
		if (special) {
			displayWidth = 100;
			displayHeight = 50;
		} else {
			displayWidth = 60;
			displayHeight = 30;
		}
	}
	
	/**
	 * Called once per frame. Moves the projectile right and advances the animation.
	 */
	public void update() {
		// Move horizontally to the right
		x += SPEED;
		
		// Advance ping-pong animation
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			// Cycle through the sequence: 0 → 1 → 2 → 1 → 0 → 1 → 2 → ...
			currentSequenceIndex = (currentSequenceIndex + 1) % ANIM_SEQUENCE.length;
		}
	}
	
	/**
	 * Draws the current animation frame at the projectile's position.
	 */
	public void draw(Graphics2D g2d) {
		int frameIndex = ANIM_SEQUENCE[currentSequenceIndex];
		if (frames != null && frames[frameIndex] != null) {
			g2d.drawImage(frames[frameIndex], x, y, displayWidth, displayHeight, null);
		}
	}
	
	/**
	 * Returns true if the projectile has flown past the right edge of the screen.
	 * Used by the level panel to remove dead projectiles from the list.
	 */
	public boolean isOffScreen() {
		return x > 1000;
	}
	
	// ========== GETTERS for collision detection (used when enemies are added) ==========
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return displayWidth; }
	public int getDisplayHeight() { return displayHeight; }
	public int getDamage() { return damage; }
	public boolean isSpecial() { return special; }
	public int getOwnerPlayer() { return ownerPlayer; }
}
