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
 * FirstBoss - The Level 1 boss with 2 phases, fought after the Tutorial Enemy.
 * 
 * ==================== HOW THE FIRST BOSS WORKS ====================
 * 
 * After the Tutorial Enemy (the block) is defeated in Level 1, the First Boss
 * descends from the top of the screen. It has 2 phases sharing a single
 * health bar.
 * 
 * PHASES:
 *   Phase 1: 30,000 HP  (sprites: level1phase1-{1,2,3}.png, 3-frame loop)
 *   Phase 2: 30,000 HP  (sprites: level1phase2-{1,2,3}.png, 3-frame loop)
 *   Total:   60,000 HP  (doubled to 120,000 in multiplayer)
 * 
 * STATE MACHINE (same pattern as FinalBoss):
 *   ENTERING  → descends from top of screen into centre position
 *   ACTIVE    → on screen, can be damaged, animating
 *   SLIDING   → current phase defeated, sliding off right edge
 *   WAITING   → off screen, brief pause before next phase enters
 *   DEFEATED  → all 2 phases beaten, boss is gone (triggers score screen)
 * 
 * ENTRY ANIMATION:
 * Descends from y = -DISPLAY_HEIGHT down to the centre of the screen at
 * 3 px/frame. Cannot be damaged during descent.
 * 
 * PHASE TRANSITIONS:
 * When a phase's HP reaches 0:
 *   1. Health bar locks (no damage during transition)
 *   2. Current phase slides off the right edge (5 px/frame)
 *   3. Brief 60-frame pause (~0.6 s)
 *   4. Next phase descends from top
 * 
 * DAMAGE FLASH:
 * Same red-tint SRC_IN compositing as Tutorial Enemy and Final Boss.
 * 
 * HEALTH BAR:
 * Displayed above the boss. Shows total HP across both phases.
 * Colour: green (>50%) → yellow (20-50%) → red (<20%).
 * ===================================================================
 */
public class FirstBoss {
	
	// ========== SPRITE FRAMES ==========
	// 3 frames per phase, 2 phases = 6 frames total
	private BufferedImage[][] phaseFrames = new BufferedImage[2][3];
	
	// Current animation frame index (0-2, looping)
	private int currentFrame = 0;
	private int animationTick = 0;
	private static final int ANIMATION_DELAY = 10;  // ticks between frame changes
	
	// ========== DISPLAY ==========
	private int x;
	private int y;
	private static final int DISPLAY_WIDTH = 189;
	private static final int DISPLAY_HEIGHT = 249;
	
	// Collision insets (shrink hitbox to visible sprite area)
	private static final int COLLISION_INSET_X = 30;
	private static final int COLLISION_INSET_Y = 30;
	
	// Panel dimensions
	private static final int PANEL_WIDTH = 1000;
	private static final int PANEL_HEIGHT = 1000;
	
	// ========== HEALTH ==========
	private static final int BASE_HP_PER_PHASE = 30000;
	private static final int TOTAL_PHASES = 2;
	
	// Actual HP values (doubled in multiplayer)
	private int hpPerPhase;
	private int totalMaxHp;
	
	// Current phase (0 = phase 1, 1 = phase 2)
	private int currentPhase = 0;
	
	// HP remaining in the CURRENT phase
	private int phaseHp;
	
	// Total HP remaining across all phases (for health bar display)
	private int totalHp;
	
	// ========== STATE MACHINE ==========
	private enum State { ENTERING, ACTIVE, SLIDING, WAITING, DEFEATED }
	private State state = State.ENTERING;
	
	// ========== MOVEMENT ==========
	private static final int ENTRY_SPEED = 3;
	private int targetY;
	private static final int SLIDE_SPEED = 5;
	private int homeX;
	
	// Delay between phases
	private int waitTimer = 0;
	private static final int WAIT_BETWEEN_PHASES = 60;  // ~0.6 seconds at 100 FPS
	
	// ========== DAMAGE FLASH ==========
	private int damageFlashTimer = 0;
	private static final int DAMAGE_FLASH_DURATION = 10;
	private static final float DAMAGE_FLASH_ALPHA = 0.45f;
	
	// ========== HEALTH BAR ==========
	private static final int HEALTH_BAR_WIDTH = 300;
	private static final int HEALTH_BAR_HEIGHT = 24;
	private static final int HEALTH_BAR_Y_OFFSET = -40;  // pixels above top of boss sprite
	
	private boolean multiplayer;
	
	/**
	 * Creates the First Boss.
	 * @param multiplayer true if 2-player mode (HP is doubled)
	 */
	public FirstBoss(boolean multiplayer) {
		this.multiplayer = multiplayer;
		loadAllFrames();
		
		// Double HP in multiplayer
		hpPerPhase = multiplayer ? BASE_HP_PER_PHASE * 2 : BASE_HP_PER_PHASE;
		totalMaxHp = hpPerPhase * TOTAL_PHASES;
		
		// Initial HP
		phaseHp = hpPerPhase;
		totalHp = totalMaxHp;
		
		// Position: right side of screen, start above the top edge
		homeX = PANEL_WIDTH - DISPLAY_WIDTH - 100;
		targetY = (PANEL_HEIGHT - DISPLAY_HEIGHT) / 2;
		
		x = homeX;
		y = -DISPLAY_HEIGHT;  // start above screen for entry descent
		
		state = State.ENTERING;
	}
	
	/**
	 * Loads all 6 sprite frames (3 per phase) from the first boss subfolders.
	 */
	private void loadAllFrames() {
		String basePath = "res/New Graphics/first boss/";
		String[] phaseFolders = {"phase 1", "phase 2"};
		String[] prefixes = {"level1phase1", "level1phase2"};
		
		try {
			for (int p = 0; p < 2; p++) {
				for (int f = 0; f < 3; f++) {
					String path = basePath + phaseFolders[p] + "/" + prefixes[p] + "-" + (f + 1) + ".png";
					phaseFrames[p][f] = ImageIO.read(new File(path));
				}
			}
		} catch (IOException e) {
			System.err.println("Error loading First Boss sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Called once per frame. Handles state transitions:
	 *   ENTERING → descend from top to centre
	 *   ACTIVE   → animate sprites (boss sits in position for now)
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
				y += ENTRY_SPEED;
				if (y >= targetY) {
					y = targetY;
					state = State.ACTIVE;
				}
				advanceAnimation();
				break;
				
			case ACTIVE:
				advanceAnimation();
				break;
				
			case SLIDING:
				x += SLIDE_SPEED;
				advanceAnimation();
				if (x > PANEL_WIDTH) {
					if (currentPhase < TOTAL_PHASES - 1) {
						state = State.WAITING;
						waitTimer = WAIT_BETWEEN_PHASES;
					} else {
						state = State.DEFEATED;
					}
				}
				break;
				
			case WAITING:
				waitTimer--;
				if (waitTimer <= 0) {
					currentPhase++;
					phaseHp = hpPerPhase;
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
		
		BufferedImage sprite = phaseFrames[currentPhase][currentFrame];
		
		if (sprite != null) {
			g2d.drawImage(sprite, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
			
			// --- Damage flash (SRC_IN red-tint technique) ---
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
		
		// Health bar and phase indicator
		drawHealthBar(g2d);
		drawPhaseIndicator(g2d);
	}
	
	/**
	 * Draws the shared health bar above the boss sprite.
	 * Shows total HP across both phases.
	 */
	private void drawHealthBar(Graphics2D g2d) {
		int barX = x + (DISPLAY_WIDTH - HEALTH_BAR_WIDTH) / 2;
		int barY = y + HEALTH_BAR_Y_OFFSET;
		
		// Background (dark grey)
		g2d.setColor(new Color(50, 50, 50));
		g2d.fillRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		
		// Fill based on total HP ratio
		double hpRatio = (double) totalHp / totalMaxHp;
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
		String hpText = totalHp + " / " + totalMaxHp;
		int textWidth = g2d.getFontMetrics().stringWidth(hpText);
		g2d.setColor(Color.WHITE);
		g2d.drawString(hpText, barX + (HEALTH_BAR_WIDTH - textWidth) / 2, barY + 17);
	}
	
	/**
	 * Draws a "Phase X / 2" label above the health bar.
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
	 * Deals damage to the boss. Only works when ACTIVE.
	 * When current phase HP reaches 0, boss begins sliding off screen.
	 * 
	 * @param amount Damage to deal
	 */
	public void takeDamage(int amount) {
		if (state != State.ACTIVE) return;
		
		phaseHp -= amount;
		damageFlashTimer = DAMAGE_FLASH_DURATION;
		
		if (phaseHp <= 0) {
			phaseHp = 0;
			totalHp = getRemainingPhasesHp() + phaseHp;
			state = State.SLIDING;
		} else {
			totalHp = getRemainingPhasesHp() + phaseHp;
		}
	}
	
	/**
	 * Calculates HP from phases that haven't started yet.
	 */
	private int getRemainingPhasesHp() {
		int remainingPhases = (TOTAL_PHASES - 1) - currentPhase;
		return remainingPhases * hpPerPhase;
	}
	
	/**
	 * AABB collision check with insets. Only registers hits when ACTIVE.
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
	
	/** Returns true if all 2 phases have been defeated. */
	public boolean isDefeated() {
		return state == State.DEFEATED;
	}
	
	/** Current phase (0-indexed: 0 = phase 1, 1 = phase 2). */
	public int getCurrentPhase() {
		return currentPhase;
	}
	
	/** Total HP remaining across all phases. */
	public int getTotalHp() {
		return totalHp;
	}
	
	/** Maximum total HP (60,000 single / 120,000 multiplayer). */
	public int getTotalMaxHp() {
		return totalMaxHp;
	}
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
}
