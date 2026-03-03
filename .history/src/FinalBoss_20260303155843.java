import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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
	
	// Phase 1 Attack 1 (Fireball) sprites — 3 frames looping
	private BufferedImage[] p1a1Frames = new BufferedImage[3];
	
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
	//   IDLE      → at home position, can be damaged, picks next attack
	//   ATTACKING → currently performing an attack pattern
	//   RETURNING → moving back to home position after an attack
	//   SLIDING   → current phase defeated, sliding off right edge
	//   WAITING   → off screen, about to start next phase entry
	//   DEFEATED  → all 3 phases beaten, boss is gone
	private enum State { ENTERING, IDLE, ATTACKING, RETURNING, SLIDING, WAITING, DEFEATED }
	private State state = State.ENTERING;
	
	// ========== MOVEMENT ==========
	private static final int ENTRY_SPEED = 3;
	private static final int RETURN_SPEED = 4;
	private static final int SLIDE_SPEED = 5;
	private int homeX;
	private int homeY;
	
	private int waitTimer = 0;
	private static final int WAIT_BETWEEN_PHASES = 60;
	
	// Brief pause at home position before picking next attack
	private int idleTimer = 0;
	private static final int IDLE_PAUSE = 60;  // 0.6 seconds
	
	// ========== DAMAGE FLASH ==========
	private int damageFlashTimer = 0;
	private static final int DAMAGE_FLASH_DURATION = 10;
	private static final float DAMAGE_FLASH_ALPHA = 0.45f;
	
	// ========== HEALTH BAR ==========
	private static final int HEALTH_BAR_WIDTH = 350;
	private static final int HEALTH_BAR_HEIGHT = 24;
	private static final int HEALTH_BAR_Y_OFFSET = -40;
	
	// ========== ATTACK SYSTEM ==========
	private Random rng = new Random();
	private int currentAttack = -1;  // 0 = Attack1, 1 = Attack2
	
	// --- Phase 1 Attack 1: Fireball Barrage ---
	// Boss bobs up/down for 2 seconds, firing fireballs from its centre.
	// Fire rate is 20% faster than First Boss Phase 2 Attack 2 (90 frames → 72 frames).
	private int p1a1Timer = 0;
	private static final int P1A1_DURATION = 720;           // 7.2 seconds at 100 FPS (10 fireballs)
	private static final int P1A1_SHOOT_INTERVAL = 72;      // 20% faster than 90
	private int p1a1ShootCooldown = 0;
	private boolean p1a1BobUp = false;
	private static final int BOB_SPEED = 3;
	private static final int BOB_TOP = 100;
	private static final int BOB_BOTTOM = 700;
	private static final int P1A1_PROJ_WIDTH = 150;
	private static final int P1A1_PROJ_HEIGHT = 58;
	private static final double P1A1_PROJ_SPEED = -9;
	
	// --- Phase 1 Attack 2: Charge/Ram ---
	// Boss slides off right, teleports to random Y on right edge, then flies
	// horizontally left at high speed. If the boss body touches a player it
	// deals 2 hearts of damage. Repeats 3 times before returning.
	private enum P1Atk2SubState { SLIDE_OFF, CHARGE_LEFT }
	private P1Atk2SubState p1a2SubState;
	private int p1a2Reps = 0;
	private static final int P1A2_MAX_REPS = 3;
	private static final int P1A2_SLIDE_OFF_SPEED = 10;
	private static final int P1A2_CHARGE_SPEED = 12;
	private static final int P1A2_DAMAGE = 2;
	
	// ========== ENEMY PROJECTILES ==========
	private List<EnemyProjectile> enemyProjectiles = new ArrayList<>();
	
	private boolean multiplayer;
	private Player1 player1;
	private Player2 player2;
	
	/**
	 * Creates the Final Boss.
	 * @param multiplayer true if 2-player mode
	 */
	public FinalBoss(boolean multiplayer, Player1 player1, Player2 player2) {
		this.multiplayer = multiplayer;
		this.player1 = player1;
		this.player2 = player2;
		loadAllFrames();
		
		hpPerPhase = multiplayer ? 2000 : 1000;  // TESTING: was BASE_HP_PER_PHASE * 2 / BASE_HP_PER_PHASE
		totalMaxHp = hpPerPhase * TOTAL_PHASES;
		
		phaseHp = hpPerPhase;
		totalHp = totalMaxHp;
		
		homeX = PANEL_WIDTH - DISPLAY_WIDTH - 50;
		homeY = (PANEL_HEIGHT - DISPLAY_HEIGHT) / 2;
		
		x = homeX;
		y = -DISPLAY_HEIGHT;
		
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
			
			// Load Phase 1 Attack 1 fireball sprites
			String[] fireballFiles = {"sprite-1-1.png", "sprite-1-8.png", "sprite-1-12.png"};
			for (int f = 0; f < 3; f++) {
				p1a1Frames[f] = ImageIO.read(new File(basePath + "phase1/Attack1/" + fireballFiles[f]));
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
		
		if (damageFlashTimer > 0) damageFlashTimer--;
		
		switch (state) {
			case ENTERING:
				y += ENTRY_SPEED;
				if (y >= homeY) {
					y = homeY;
					state = State.IDLE;
					idleTimer = IDLE_PAUSE;
				}
				advanceAnimation();
				break;
				
			case IDLE:
				advanceAnimation();
				idleTimer--;
				if (idleTimer <= 0) {
					pickAttack();
				}
				break;
				
			case ATTACKING:
				advanceAnimation();
				updateCurrentAttack();
				break;
				
			case RETURNING:
				advanceAnimation();
				updateReturning();
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
					currentAttack = -1;
					state = State.ENTERING;
				}
				break;
				
			default:
				break;
		}
		
		// Update all enemy projectiles
		Iterator<EnemyProjectile> it = enemyProjectiles.iterator();
		while (it.hasNext()) {
			EnemyProjectile ep = it.next();
			ep.update();
			if (ep.isOffScreen() || ep.isConsumed()) {
				it.remove();
			}
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
	
	// ========== ATTACK SELECTION ==========
	
	private void pickAttack() {
		if (currentPhase == 0) {
			// Phase 1: 2 attacks (Fireball Barrage + Charge Ram)
			currentAttack = rng.nextInt(2);
			state = State.ATTACKING;
			if (currentAttack == 0) initP1Attack1();
			else initP1Attack2();
		} else {
			// Phase 2/3 attacks TBD — stay idle
			idleTimer = IDLE_PAUSE;
		}
	}
	
	// ========== PHASE 1 ATTACK 1: FIREBALL BARRAGE ==========
	// Boss bobs up/down for 2 seconds, firing fireballs every 72 frames.
	
	private void initP1Attack1() {
		p1a1Timer = P1A1_DURATION;
		p1a1ShootCooldown = P1A1_SHOOT_INTERVAL;
		p1a1BobUp = (y > (BOB_TOP + BOB_BOTTOM) / 2);
	}
	
	private void updateP1Attack1() {
		p1a1Timer--;
		
		// Bob up and down
		if (p1a1BobUp) {
			y -= BOB_SPEED;
			if (y <= BOB_TOP) { y = BOB_TOP; p1a1BobUp = false; }
		} else {
			y += BOB_SPEED;
			if (y >= BOB_BOTTOM) { y = BOB_BOTTOM; p1a1BobUp = true; }
		}
		
		// Shoot cooldown
		p1a1ShootCooldown--;
		if (p1a1ShootCooldown <= 0) {
			fireP1Fireball();
			p1a1ShootCooldown = P1A1_SHOOT_INTERVAL;
		}
		
		if (p1a1Timer <= 0) {
			state = State.RETURNING;
		}
	}
	
	/** Fires a single fireball from the boss's left edge, vertically centred. */
	private void fireP1Fireball() {
		double spawnX = x;
		double spawnY = y + (DISPLAY_HEIGHT / 2.0) - (P1A1_PROJ_HEIGHT / 2.0);
		
		EnemyProjectile ep = new EnemyProjectile(
			p1a1Frames,
			spawnX, spawnY,
			P1A1_PROJ_SPEED, 0,
			P1A1_PROJ_WIDTH, P1A1_PROJ_HEIGHT
		);
		enemyProjectiles.add(ep);
	}
	
	// ========== PHASE 1 ATTACK 2: CHARGE/RAM ==========
	// Boss slides off right, teleports to random Y, flies left 3 times.
	// Body contact deals 2 hearts of damage.
	
	private void initP1Attack2() {
		p1a2SubState = P1Atk2SubState.SLIDE_OFF;
		p1a2Reps = 0;
	}
	
	private void updateP1Attack2() {
		switch (p1a2SubState) {
			case SLIDE_OFF:
				x += P1A2_SLIDE_OFF_SPEED;
				if (x > PANEL_WIDTH) {
					// Off screen right — teleport to random Y on right edge
					x = PANEL_WIDTH + 10;
					y = rng.nextInt(PANEL_HEIGHT - DISPLAY_HEIGHT - 100) + 50;
					p1a2SubState = P1Atk2SubState.CHARGE_LEFT;
				}
				break;
				
			case CHARGE_LEFT:
				x -= P1A2_CHARGE_SPEED;
				
				// Check body collision with players during charge
				checkChargeCollision();
				
				if (x + DISPLAY_WIDTH < 0) {
					// Off screen left — increment reps
					p1a2Reps++;
					if (p1a2Reps < P1A2_MAX_REPS) {
						// Teleport back to right edge at new random Y
						x = PANEL_WIDTH + 10;
						y = rng.nextInt(PANEL_HEIGHT - DISPLAY_HEIGHT - 100) + 50;
						// Stay in CHARGE_LEFT
					} else {
						// All charges done — reappear from right for smooth return
						x = PANEL_WIDTH + 10;
						state = State.RETURNING;
					}
				}
				break;
				
			default:
				break;
		}
	}
	
	/** Checks if the boss body overlaps any player during the charge attack. */
	private void checkChargeCollision() {
		if (player1 != null && player1.isAlive() && !player1.isInvincible()) {
			if (bodyCollidesWithPlayer(player1.getX(), player1.getY(),
			    player1.getDisplayWidth(), player1.getDisplayHeight())) {
				player1.hit(P1A2_DAMAGE);
			}
		}
		if (player2 != null && player2.isAlive() && !player2.isInvincible()) {
			if (bodyCollidesWithPlayer(player2.getX(), player2.getY(),
			    player2.getDisplayWidth(), player2.getDisplayHeight())) {
				player2.hit(P1A2_DAMAGE);
			}
		}
	}
	
	/** AABB body collision check (uses collision insets). */
	private boolean bodyCollidesWithPlayer(int px, int py, int pw, int ph) {
		int hitX = x + COLLISION_INSET_X;
		int hitY = y + COLLISION_INSET_Y;
		int hitW = DISPLAY_WIDTH  - 2 * COLLISION_INSET_X;
		int hitH = DISPLAY_HEIGHT - 2 * COLLISION_INSET_Y;
		
		return px < hitX + hitW && px + pw > hitX &&
		       py < hitY + hitH && py + ph > hitY;
	}
	
	// ========== ATTACK DISPATCHER ==========
	
	private void updateCurrentAttack() {
		if (currentPhase == 0) {
			if (currentAttack == 0) updateP1Attack1();
			else if (currentAttack == 1) updateP1Attack2();
		}
		// Phase 2/3 attacks TBD
	}
	
	// ========== RETURNING TO HOME POSITION ==========
	
	private void updateReturning() {
		boolean atHome = true;
		
		if (x < homeX) {
			x += RETURN_SPEED;
			if (x > homeX) x = homeX;
			else atHome = false;
		} else if (x > homeX) {
			x -= RETURN_SPEED;
			if (x < homeX) x = homeX;
			else atHome = false;
		}
		
		if (y < homeY) {
			y += RETURN_SPEED;
			if (y > homeY) y = homeY;
			else atHome = false;
		} else if (y > homeY) {
			y -= RETURN_SPEED;
			if (y < homeY) y = homeY;
			else atHome = false;
		}
		
		if (atHome) {
			state = State.IDLE;
			idleTimer = IDLE_PAUSE;
			currentAttack = -1;
		}
	}
	
	/**
	 * Draws the current phase's animated sprite and the shared health bar.
	 */
	public void draw(Graphics2D g2d) {
		if (state == State.DEFEATED || state == State.WAITING) return;
		
		// Determine whether to draw the boss body this frame
		boolean drawBoss = true;
		// Hide during P1 Attack 2 slide-off when off screen right
		if (state == State.ATTACKING && currentPhase == 0 && currentAttack == 1
		    && p1a2SubState == P1Atk2SubState.SLIDE_OFF && x > PANEL_WIDTH) {
			drawBoss = false;
		}
		
		if (drawBoss) {
			BufferedImage sprite = phaseFrames[currentPhase][currentFrame];
			
			if (sprite != null) {
				g2d.drawImage(sprite, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
				
				// Damage flash
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
			
			drawHealthBar(g2d);
			drawPhaseIndicator(g2d);
		}
		
		// Always draw enemy projectiles
		for (EnemyProjectile ep : enemyProjectiles) {
			ep.draw(g2d);
		}
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
		if (state != State.IDLE && state != State.ATTACKING && state != State.RETURNING) return;
		
		phaseHp -= amount;
		damageFlashTimer = DAMAGE_FLASH_DURATION;
		
		if (phaseHp <= 0) {
			phaseHp = 0;
			totalHp = getRemainingPhasesHp();
			currentAttack = -1;
			state = State.SLIDING;
		} else {
			totalHp = getRemainingPhasesHp() + phaseHp;
		}
	}
	
	/**
	 * Calculates HP from phases that haven't started yet.
	 * e.g. if currentPhase=0, phases 1 and 2 still have full HP = 50,000
	 */
	private int getRemainingPhasesHp() {
		int remainingPhases = (TOTAL_PHASES - 1) - currentPhase;
		return remainingPhases * hpPerPhase;
	}
	
	/**
	 * AABB collision check with collision insets (same as Tutorial Enemy).
	 * Only registers hits when the boss is in ACTIVE state.
	 */
	public boolean collidesWith(Projectile p) {
		if (state != State.IDLE && state != State.ATTACKING && state != State.RETURNING) return false;
		
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
		return state == State.IDLE || state == State.ATTACKING || state == State.RETURNING;
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
	
	/** Maximum total HP (75,000 single / 150,000 multiplayer). */
	public int getTotalMaxHp() {
		return totalMaxHp;
	}
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
	
	/** Returns the list of active enemy projectiles for collision checking. */
	public List<EnemyProjectile> getEnemyProjectiles() {
		return enemyProjectiles;
	}
}
