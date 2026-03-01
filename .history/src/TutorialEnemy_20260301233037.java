import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * TutorialEnemy - The first boss/enemy in the game, appearing only in Level 1.
 * 
 * ==================== HOW THE TUTORIAL ENEMY WORKS ====================
 * 
 * The Tutorial Enemy is a large block that sits on the right side of the screen
 * and slowly bobs up and down. It serves as the first combat encounter, teaching
 * the player how the attack system works.
 * 
 * SPRITES (damage-based, NOT animation-based):
 * The enemy has 3 sprite frames that represent its damage state, NOT a looping
 * animation. Which frame is shown depends on how much HP the enemy has left:
 *   - Tutorialblock-1.png: Healthy state (HP above 60% of max)
 *   - Tutorialblock-2.png: Damaged state (HP between 20%-60% of max)
 *   - Tutorialblock-3.png: Critical state (HP below 20% of max)
 * 
 * For single player (5000 max HP):
 *   Frame 1: 5000-3001 HP  |  Frame 2: 3000-1001 HP  |  Frame 3: 1000-1 HP
 * For multiplayer (10000 max HP):
 *   Frame 1: 10000-6001 HP |  Frame 2: 6000-2001 HP  |  Frame 3: 2000-1 HP
 * 
 * HEALTH:
 * - Single player: 5000 HP
 * - Multiplayer: 10000 HP (double, as per the rule for all bosses)
 * 
 * MOVEMENT:
 * The enemy slowly oscillates up and down on the right side of the screen.
 * It uses a sine-wave-style bounce: each frame, a direction flag moves it
 * up or down at a constant speed, reversing at the top/bottom bounds.
 * 
 * DEATH:
 * When HP reaches 0, the enemy slides off the right edge of the screen
 * and is then marked as defeated. Nothing further happens after defeat.
 * 
 * COLLISION:
 * Projectiles that overlap the enemy's bounding box deal their damage
 * to the enemy and are removed. The firing player's damage stats are updated.
 * ========================================================================
 */
public class TutorialEnemy {
	
	// The 3 damage-state sprite frames
	private BufferedImage[] frames = new BufferedImage[3];
	
	// Position on screen
	private int x;
	private int y;
	
	// Display size (75% larger than original 150x150)
	private static final int DISPLAY_WIDTH = 263;
	private static final int DISPLAY_HEIGHT = 263;
	
	// ========== VERTICAL OSCILLATION ==========
	// The enemy bobs up and down at this speed (pixels per frame)
	private static final int MOVE_SPEED = 2;
	// true = moving down, false = moving up
	private boolean movingDown = true;
	// Vertical bounds for oscillation (stay within the panel)
	private static final int MIN_Y = 50;
	private static final int MAX_Y = 687;  // 1000 - DISPLAY_HEIGHT - 50
	
	// ========== HEALTH ==========
	private static final int SINGLE_PLAYER_HP = 5000;
	private static final int MULTIPLAYER_HP = 10000;  // double for all bosses in 2-player
	private int maxHp;
	private int hp;
	
	// ========== STATE ==========
	// Whether the enemy is still alive and on screen
	private boolean alive = true;
	// Whether the enemy has fully slid off screen after dying
	private boolean defeated = false;
	// Speed at which the enemy slides off screen after death
	private static final int DEATH_SLIDE_SPEED = 5;
	
	// Panel dimensions
	private int panelWidth = 1000;
	private int panelHeight = 1000;
	
	/**
	 * Creates the tutorial enemy.
	 * @param multiplayer true if 2-player mode (enemy gets double HP)
	 */
	public TutorialEnemy(boolean multiplayer) {
		loadFrames();
		
		// Set HP based on mode
		if (multiplayer) {
			maxHp = MULTIPLAYER_HP;
		} else {
			maxHp = SINGLE_PLAYER_HP;
		}
		hp = maxHp;
		
		// Spawn on the right side of the screen, vertically centred
		x = panelWidth - DISPLAY_WIDTH - 50;  // 50px from right edge
		y = (panelHeight - DISPLAY_HEIGHT) / 2;
	}
	
	/**
	 * Loads the 3 damage-state sprite frames from the Tutorial Enemy folder.
	 */
	private void loadFrames() {
		String basePath = "res/New Graphics/Tutorial Enemy/";
		try {
			frames[0] = ImageIO.read(new File(basePath + "Tutorialblock-1.png"));
			frames[1] = ImageIO.read(new File(basePath + "Tutorialblock-2.png"));
			frames[2] = ImageIO.read(new File(basePath + "Tutorialblock-3.png"));
		} catch (IOException e) {
			System.err.println("Error loading Tutorial Enemy sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Called once per frame. Handles vertical oscillation while alive,
	 * or sliding off screen after death.
	 */
	public void update() {
		if (defeated) return;  // fully gone, nothing to do
		
		if (alive) {
			// --- Vertical oscillation (bob up and down) ---
			if (movingDown) {
				y += MOVE_SPEED;
				if (y >= MAX_Y) {
					y = MAX_Y;
					movingDown = false;
				}
			} else {
				y -= MOVE_SPEED;
				if (y <= MIN_Y) {
					y = MIN_Y;
					movingDown = true;
				}
			}
		} else {
			// --- Death slide: move off the right edge ---
			x += DEATH_SLIDE_SPEED;
			if (x > panelWidth) {
				defeated = true;  // fully off screen
			}
		}
	}
	
	/**
	 * Draws the appropriate damage-state frame at the enemy's position.
	 * The frame shown depends on remaining HP as a percentage of max HP:
	 *   > 60% → Frame 1 (healthy)
	 *   20%-60% → Frame 2 (damaged)
	 *   < 20% → Frame 3 (critical)
	 */
	public void draw(Graphics2D g2d) {
		if (defeated) return;  // fully gone
		
		// Determine which damage-state frame to show
		int frameIndex;
		double hpPercent = (double) hp / maxHp;
		
		if (hpPercent > 0.6) {
			frameIndex = 0;  // healthy: above 60%
		} else if (hpPercent > 0.2) {
			frameIndex = 1;  // damaged: 20%-60%
		} else {
			frameIndex = 2;  // critical: below 20%
		}
		
		if (frames[frameIndex] != null) {
			g2d.drawImage(frames[frameIndex], x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
		}
	}
	
	/**
	 * Deals damage to the enemy. If HP drops to 0, the enemy dies and
	 * begins sliding off screen.
	 * 
	 * @param amount The amount of damage to deal
	 */
	public void takeDamage(int amount) {
		if (!alive) return;
		
		hp -= amount;
		if (hp <= 0) {
			hp = 0;
			alive = false;  // triggers death slide on next update
		}
	}
	
	/**
	 * Checks if a projectile's bounding box overlaps this enemy's bounding box.
	 * Simple axis-aligned bounding box (AABB) collision detection.
	 * 
	 * @return true if the projectile is hitting this enemy
	 */
	public boolean collidesWith(Projectile p) {
		if (!alive) return false;  // can't hit a dead enemy
		
		// AABB overlap check
		return p.getX() < x + DISPLAY_WIDTH &&
		       p.getX() + p.getDisplayWidth() > x &&
		       p.getY() < y + DISPLAY_HEIGHT &&
		       p.getY() + p.getDisplayHeight() > y;
	}
	
	// ========== GETTERS ==========
	
	public boolean isAlive() { return alive; }
	public boolean isDefeated() { return defeated; }
	public int getHp() { return hp; }
	public int getMaxHp() { return maxHp; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
}
