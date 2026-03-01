import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
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
	
	// Display size (50% larger than 263x263 = ~395x395)
	private static final int DISPLAY_WIDTH = 395;
	private static final int DISPLAY_HEIGHT = 395;
	
	// ========== VERTICAL OSCILLATION ==========
	// The enemy bobs up and down at this speed (pixels per frame)
	private static final int MOVE_SPEED = 2;
	// true = moving down, false = moving up
	private boolean movingDown = true;
	// Vertical bounds for oscillation (stay within the panel)
	private static final int MIN_Y = 50;
	private static final int MAX_Y = 555;  // 1000 - DISPLAY_HEIGHT - 50
	
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
	
	// ========== DAMAGE FLASH ==========
	// When hit, the enemy flashes with a transparent red overlay for a few frames.
	// damageFlashTimer counts down from DAMAGE_FLASH_DURATION to 0 each frame.
	private int damageFlashTimer = 0;
	private static final int DAMAGE_FLASH_DURATION = 10;  // frames (~100ms at 100 FPS)
	private static final float DAMAGE_FLASH_ALPHA = 0.45f; // 45% opacity red overlay
	
	// ========== HEALTH BAR ==========
	private static final int HEALTH_BAR_WIDTH = 300;
	private static final int HEALTH_BAR_HEIGHT = 22;
	private static final int HEALTH_BAR_Y_OFFSET = -35;  // pixels above the enemy sprite
	
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
		
		// Tick down the damage flash timer
		if (damageFlashTimer > 0) {
			damageFlashTimer--;
		}
		
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
			
			// --- Damage flash: draw a transparent red rectangle over the sprite ---
			if (damageFlashTimer > 0) {
				Composite originalComposite = g2d.getComposite();
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DAMAGE_FLASH_ALPHA));
				g2d.setColor(Color.RED);
				g2d.fillRect(x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT);
				g2d.setComposite(originalComposite);  // restore original transparency
			}
		}
		
		// --- Health bar (drawn above the enemy sprite) ---
		if (alive) {
			int barX = x + (DISPLAY_WIDTH - HEALTH_BAR_WIDTH) / 2;  // centred over enemy
			int barY = y + HEALTH_BAR_Y_OFFSET;
			
			// Background (dark grey)
			g2d.setColor(new Color(50, 50, 50));
			g2d.fillRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
			
			// Fill (green → yellow → red based on HP%)
			double hpRatio = (double) hp / maxHp;
			int fillWidth = (int) (HEALTH_BAR_WIDTH * hpRatio);
			if (hpRatio > 0.5) {
				g2d.setColor(new Color(50, 205, 50));   // green
			} else if (hpRatio > 0.2) {
				g2d.setColor(new Color(255, 200, 0));   // yellow/orange
			} else {
				g2d.setColor(new Color(220, 30, 30));   // red
			}
			g2d.fillRect(barX, barY, fillWidth, HEALTH_BAR_HEIGHT);
			
			// Border (white)
			g2d.setColor(Color.WHITE);
			g2d.drawRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
			
			// HP text (centred in the bar)
			g2d.setFont(new Font("Arial", Font.BOLD, 14));
			String hpText = hp + " / " + maxHp;
			int textWidth = g2d.getFontMetrics().stringWidth(hpText);
			g2d.setColor(Color.WHITE);
			g2d.drawString(hpText, barX + (HEALTH_BAR_WIDTH - textWidth) / 2, barY + 16);
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
		damageFlashTimer = DAMAGE_FLASH_DURATION;  // trigger red flash
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
