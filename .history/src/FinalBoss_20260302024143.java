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
 * FinalBoss - The Level 2 boss with 3 phases that get progressively harder.
 * 
 * ==================== HOW THE FINAL BOSS WORKS ====================
 * 
 * The Final Boss is the main enemy of Level 2. It has 3 distinct phases,
 * each represented by a separate set of animated sprites. The three phases
 * share a single health bar that tracks total HP out of 75,000.
 * 
 * PHASES:
 *   Phase 1: 25,000 HP  (sprites: FBphase1-1/2/3.png, 3-frame loop)
 *   Phase 2: 25,000 HP  (sprites: FBphase2-1/2/3.png, 3-frame loop)
 *   Phase 3: 25,000 HP  (sprites: FBphase3-1/2/3.png, 3-frame loop)
 *   Total:   75,000 HP
 * 
 * Each phase is essentially a separate character that shares the health bar.
 * When a phase's 25,000 HP is depleted:
 *   1. The health bar locks (no more damage until next phase arrives)
 *   2. The current phase sprite slides off the right edge of the screen
 *   3. The next phase sprite descends from the top into the centre of the screen
 * 
 * ENTRY ANIMATION:
 * When the level starts (and at each new phase), the boss descends from above
 * the screen (y = -DISPLAY_HEIGHT) down to the centre of the screen. During
 * this descent the boss cannot be damaged.
 * 
 * SPRITES:
 * Each phase has 3 frames that loop continuously (unlike the Tutorial Enemy
 * which used damage-state sprites). Animation cycles: 0 → 1 → 2 → 0 → ...
 * 
 * DAMAGE FLASH:
 * Same red-tint SRC_IN compositing technique as the Tutorial Enemy.
 * 
 * HEALTH BAR:
 * Displayed above the boss, shows current total HP out of 75,000.
 * Colour transitions: green (>50%) → yellow (20%-50%) → red (<20%).
 * ===================================================================
 */
public class FinalBoss {
	
	// ========== SPRITE FRAMES ==========
	// 3 frames per phase, 3 phases = 9 frames total
	private BufferedImage[][] phaseFrames = new BufferedImage[3][3];
	
	// Current animation frame index (0-2, looping)
	private int currentFrame = 0;
	private int animationTick = 0;
	private static final int ANIMATION_DELAY = 10;  // ticks between frame changes
	
	// ========== DISPLAY ==========
	private int x;
	private int y;
	private static final int DISPLAY_WIDTH = 296;
	private static final int DISPLAY_HEIGHT = 296;
	
	// Collision insets (same approach as Tutorial Enemy — shrink hitbox to visible sprite)
	private static final int COLLISION_INSET_X = 75;
	private static final int COLLISION_INSET_Y = 75;
	
	// Panel dimensions
	private static final int PANEL_WIDTH = 1000;
	private static final int PANEL_HEIGHT = 1000;
	
	// ========== HEALTH ==========
	private static final int BASE_HP_PER_PHASE = 25000;
	private static final int TOTAL_PHASES = 3;
	
	// Actual HP values (doubled in multiplayer)
	private int hpPerPhase;
	private int totalMaxHp;
	
	// Current phase (0 = phase 1, 1 = phase 2, 2 = phase 3)
	private int currentPhase = 0;
	
	// HP remaining in the CURRENT phase
	private int phaseHp;
	
	// Total HP remaining across all phases (for health bar display)
	private int totalHp;
	
	// ========== STATE MACHINE ==========
	// The boss cycles through these states:
	//   ENTERING  → descending from top of screen into position
	//   ACTIVE    → on screen, can be damaged, animating
	//   SLIDING   → current phase defeated, sliding off right edge
	//   WAITING   → off screen, about to start next phase entry
	//   DEFEATED  → all 3 phases beaten, boss is gone
	private enum State { ENTERING, ACTIVE, SLIDING, WAITING, DEFEATED }
	private State state = State.ENTERING;
	
	// ========== MOVEMENT ==========
	// Speed for the entry descent (pixels per frame)
	private static final int ENTRY_SPEED = 3;
	// Target Y position (centre of screen)
	private int targetY;
	// Speed for sliding off screen after a phase is beaten
	private static final int SLIDE_SPEED = 5;
	// X position on right side of screen (same as tutorial enemy)
	private int homeX;
	
	// Small delay between phases (frames to wait before next phase enters)
	private int waitTimer = 0;
	private static final int WAIT_BETWEEN_PHASES = 60;  // ~0.6 seconds at 100 FPS
	
	// ========== DAMAGE FLASH ==========
	private int damageFlashTimer = 0;
	private static final int DAMAGE_FLASH_DURATION = 10;
	private static final float DAMAGE_FLASH_ALPHA = 0.45f;
	
	// ========== HEALTH BAR ==========
	private static final int HEALTH_BAR_WIDTH = 350;
	private static final int HEALTH_BAR_HEIGHT = 24;
	private static final int HEALTH_BAR_Y_OFFSET = -40;  // pixels above top of boss sprite
	
	// Whether this is multiplayer (for potential future HP scaling)
	private boolean multiplayer;
	
	/**
	 * Creates the Final Boss.
	 * @param multiplayer true if 2-player mode
	 */
	public FinalBoss(boolean multiplayer) {
		this.multiplayer = multiplayer;
		loadAllFrames();
		
		// Double HP in multiplayer (each phase lasts twice as long)
		hpPerPhase = multiplayer ? BASE_HP_PER_PHASE * 2 : BASE_HP_PER_PHASE;
		totalMaxHp = hpPerPhase * TOTAL_PHASES;
		
		// Initial HP
		phaseHp = hpPerPhase;
		totalHp = totalMaxHp;
		
		// Position: right side of screen, start above the top edge
		homeX = PANEL_WIDTH - DISPLAY_WIDTH - 50;
		targetY = (PANEL_HEIGHT - DISPLAY_HEIGHT) / 2;
		
		x = homeX;
		y = -DISPLAY_HEIGHT;  // start above screen for entry descent
		
		state = State.ENTERING;
	}
	
	/**
	 * Loads all 9 sprite frames (3 per phase) from the Final Boss subfolders.
	 */
	private void loadAllFrames() {
		String basePath = "res/New Graphics/Final Boss/";
		String[] phaseFolders = {"phase1", "phase2", "phase3"};
		String[] prefixes = {"FBphase1", "FBphase2", "FBphase3"};
		
		try {
			for (int p = 0; p < 3; p++) {
				for (int f = 0; f < 3; f++) {
					String path = basePath + phaseFolders[p] + "/" + prefixes[p] + "-" + (f + 1) + ".png";
					phaseFrames[p][f] = ImageIO.read(new File(path));
				}
			}
		} catch (IOException e) {
			System.err.println("Error loading Final Boss sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Called once per frame. Handles state transitions:
	 *   ENTERING → descend from top to centre
	 *   ACTIVE   → animate sprites (boss just sits there for now)
	 *   SLIDING  → slide off right edge, then transition to WAITING or DEFEATED
	 *   WAITING  → count down timer, then start next phase entry
	 *   DEFEATED → do nothing
	 */
	public void update() {
		if (state == State.DEFEATED) return;
		
		// Tick down damage flash
		if (damageFlashTimer > 0) {
			damageFlashTimer--;
		}
		
		switch (state) {
			case ENTERING:
				// Descend from top towards target Y (centre of screen)
				y += ENTRY_SPEED;
				if (y >= targetY) {
					y = targetY;
					state = State.ACTIVE;
				}
				// Animate during entry
				advanceAnimation();
				break;
				
			case ACTIVE:
				// Just animate for now (AI/attacks will be added later)
				advanceAnimation();
				break;
				
			case SLIDING:
				// Slide off the right edge of the screen
				x += SLIDE_SPEED;
				advanceAnimation();
				if (x > PANEL_WIDTH) {
					// Off screen — check if there are more phases
					if (currentPhase < TOTAL_PHASES - 1) {
						// More phases remain — wait briefly then bring in next phase
						state = State.WAITING;
						waitTimer = WAIT_BETWEEN_PHASES;
					} else {
						// All phases beaten
						state = State.DEFEATED;
					}
				}
				break;
				
			case WAITING:
				// Brief pause between phases
				waitTimer--;
				if (waitTimer <= 0) {
					// Advance to next phase
					currentPhase++;
					phaseHp = HP_PER_PHASE;
					// Reset position for entry descent
					x = homeX;
					y = -DISPLAY_HEIGHT;
					currentFrame = 0;
					animationTick = 0;
					state = State.ENTERING;
				}
				break;
				
			default:
				break;
		}
	}
	
	/**
	 * Advances the 3-frame looping animation for the current phase.
	 */
	private void advanceAnimation() {
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % 3;
		}
	}
	
	/**
	 * Draws the current phase's animated sprite and the shared health bar.
	 */
	public void draw(Graphics2D g2d) {
		if (state == State.DEFEATED || state == State.WAITING) return;
		
		// Get current sprite frame
		BufferedImage sprite = phaseFrames[currentPhase][currentFrame];
		
		if (sprite != null) {
			// Draw the boss sprite
			g2d.drawImage(sprite, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
			
			// --- Damage flash (same SRC_IN technique as Tutorial Enemy) ---
			if (damageFlashTimer > 0) {
				BufferedImage flashImage = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
				Graphics2D flashG = flashImage.createGraphics();
				flashG.drawImage(sprite, 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
				flashG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f));
				flashG.setColor(Color.RED);
				flashG.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);
				flashG.dispose();
				
				Composite originalComposite = g2d.getComposite();
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DAMAGE_FLASH_ALPHA));
				g2d.drawImage(flashImage, x, y, null);
				g2d.setComposite(originalComposite);
			}
		}
		
		// --- Health bar (shared across all phases, shows total HP out of 75,000) ---
		// Only show when the boss is on screen (not during WAITING)
		drawHealthBar(g2d);
		
		// --- Phase indicator text ---
		drawPhaseIndicator(g2d);
	}
	
	/**
	 * Draws the shared health bar above the boss sprite.
	 * The bar represents total HP across all 3 phases (out of 75,000).
	 */
	private void drawHealthBar(Graphics2D g2d) {
		int barX = x + (DISPLAY_WIDTH - HEALTH_BAR_WIDTH) / 2;
		int barY = y + HEALTH_BAR_Y_OFFSET;
		
		// Background (dark grey)
		g2d.setColor(new Color(50, 50, 50));
		g2d.fillRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		
		// Fill based on total HP ratio
		double hpRatio = (double) totalHp / TOTAL_MAX_HP;
		int fillWidth = (int) (HEALTH_BAR_WIDTH * hpRatio);
		
		if (hpRatio > 0.5) {
			g2d.setColor(new Color(50, 205, 50));   // green
		} else if (hpRatio > 0.2) {
			g2d.setColor(new Color(255, 200, 0));   // yellow
		} else {
			g2d.setColor(new Color(220, 30, 30));   // red
		}
		g2d.fillRect(barX, barY, fillWidth, HEALTH_BAR_HEIGHT);
		
		// Border (white)
		g2d.setColor(Color.WHITE);
		g2d.drawRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		
		// HP text
		g2d.setFont(new Font("Arial", Font.BOLD, 14));
		String hpText = totalHp + " / " + TOTAL_MAX_HP;
		int textWidth = g2d.getFontMetrics().stringWidth(hpText);
		g2d.setColor(Color.WHITE);
		g2d.drawString(hpText, barX + (HEALTH_BAR_WIDTH - textWidth) / 2, barY + 17);
	}
	
	/**
	 * Draws a small "Phase X" label below the health bar.
	 */
	private void drawPhaseIndicator(Graphics2D g2d) {
		int textX = x + (DISPLAY_WIDTH / 2);
		int textY = y + HEALTH_BAR_Y_OFFSET - 8;
		
		g2d.setFont(new Font("Arial", Font.BOLD, 16));
		g2d.setColor(Color.WHITE);
		String phaseText = "Phase " + (currentPhase + 1) + " / " + TOTAL_PHASES;
		int textWidth = g2d.getFontMetrics().stringWidth(phaseText);
		g2d.drawString(phaseText, textX - textWidth / 2, textY);
	}
	
	/**
	 * Deals damage to the boss. Only works when the boss is in ACTIVE state.
	 * When the current phase's HP reaches 0, the boss begins sliding off screen.
	 * The health bar locks during transitions.
	 * 
	 * @param amount Damage to deal
	 */
	public void takeDamage(int amount) {
		// Can only take damage while ACTIVE
		if (state != State.ACTIVE) return;
		
		phaseHp -= amount;
		damageFlashTimer = DAMAGE_FLASH_DURATION;
		
		if (phaseHp <= 0) {
			phaseHp = 0;
			// Recalculate total HP
			totalHp = getRemainingPhasesHp() + phaseHp;
			// This phase is done — start sliding off
			state = State.SLIDING;
		} else {
			// Update total HP display
			totalHp = getRemainingPhasesHp() + phaseHp;
		}
	}
	
	/**
	 * Calculates HP from phases that haven't started yet.
	 * e.g. if currentPhase=0, phases 1 and 2 still have full HP = 50,000
	 */
	private int getRemainingPhasesHp() {
		int remainingPhases = (TOTAL_PHASES - 1) - currentPhase;
		return remainingPhases * HP_PER_PHASE;
	}
	
	/**
	 * AABB collision check with collision insets (same as Tutorial Enemy).
	 * Only registers hits when the boss is in ACTIVE state.
	 */
	public boolean collidesWith(Projectile p) {
		if (state != State.ACTIVE) return false;
		
		int hitX = x + COLLISION_INSET_X;
		int hitY = y + COLLISION_INSET_Y;
		int hitW = DISPLAY_WIDTH  - 2 * COLLISION_INSET_X;
		int hitH = DISPLAY_HEIGHT - 2 * COLLISION_INSET_Y;
		
		return p.getX() < hitX + hitW &&
		       p.getX() + p.getDisplayWidth() > hitX &&
		       p.getY() < hitY + hitH &&
		       p.getY() + p.getDisplayHeight() > hitY;
	}
	
	// ========== GETTERS ==========
	
	/** Returns true if the boss is in ACTIVE state and can be hit. */
	public boolean isAlive() {
		return state == State.ACTIVE;
	}
	
	/** Returns true if all 3 phases have been defeated. */
	public boolean isDefeated() {
		return state == State.DEFEATED;
	}
	
	/** Current phase (0-indexed: 0 = phase 1, 1 = phase 2, 2 = phase 3). */
	public int getCurrentPhase() {
		return currentPhase;
	}
	
	/** Total HP remaining across all phases. */
	public int getTotalHp() {
		return totalHp;
	}
	
	/** Maximum total HP (75,000). */
	public int getTotalMaxHp() {
		return TOTAL_MAX_HP;
	}
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
}
