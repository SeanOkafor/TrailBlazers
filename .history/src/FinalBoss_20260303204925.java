import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

// FinalBoss — Level 2 boss with 3 phases (25k HP each, 75k total).
// State machine: ENTERING → IDLE → ATTACKING → RETURNING → SLIDING → WAITING → DEFEATED.
// Phases inherit attacks: P1 has 2, P2 has 3 (P1's + Zip&Shoot), P3 has 4 (all + MiniBoss).
public class FinalBoss {
	
	// 3 frames per phase, 3 phases = 9 sprite frames total
	private BufferedImage[][] phaseFrames = new BufferedImage[3][3];
	
	// Phase 1 Attack 1 (Fireball) sprites — 3-frame loop
	private BufferedImage[] p1a1Frames = new BufferedImage[3];
	
	// Animation state (0-2, looping)
	private int currentFrame = 0;
	private int animationTick = 0;
	private static final int ANIMATION_DELAY = 10;
	
	// Display dimensions
	private int x;
	private int y;
	private static final int DISPLAY_WIDTH = 296;
	private static final int DISPLAY_HEIGHT = 296;
	
	// Collision insets — shrink hitbox to visible sprite area
	private static final int COLLISION_INSET_X = 75;
	private static final int COLLISION_INSET_Y = 75;
	
	private static final int PANEL_WIDTH = 1000;
	private static final int PANEL_HEIGHT = 1000;
	
	// Health — each phase has BASE_HP_PER_PHASE, doubled in multiplayer
	private static final int BASE_HP_PER_PHASE = 4688;
	private static final int TOTAL_PHASES = 3;
	
	private int hpPerPhase;
	private int totalMaxHp;
	
	// Current phase (0 = phase 1, 1 = phase 2, 2 = phase 3)
	private int currentPhase = 0;
	private int phaseHp;       // HP remaining in current phase
	private int totalHp;       // total HP across all phases (for health bar)
	
	// State machine: ENTERING → IDLE → ATTACKING → RETURNING → SLIDING → WAITING → DEFEATED
	private enum State { ENTERING, IDLE, ATTACKING, RETURNING, SLIDING, WAITING, DEFEATED }
	private State state = State.ENTERING;
	
	// Movement speeds
	private static final int ENTRY_SPEED = 3;
	private static final int RETURN_SPEED = 4;
	private static final int SLIDE_SPEED = 5;
	private int homeX;
	private int homeY;
	
	private int waitTimer = 0;
	private static final int WAIT_BETWEEN_PHASES = 60;
	
	// Brief pause at home before picking next attack
	private int idleTimer = 0;
	private static final int IDLE_PAUSE = 60;  // 0.6s
	
	// Damage flash — red-tint via SRC_IN compositing
	private int damageFlashTimer = 0;
	private static final int DAMAGE_FLASH_DURATION = 10;
	private static final float DAMAGE_FLASH_ALPHA = 0.45f;
	
	// Health bar — colour transitions: green (>50%) → yellow (20-50%) → red (<20%)
	private static final int HEALTH_BAR_WIDTH = 350;
	private static final int HEALTH_BAR_HEIGHT = 24;
	private static final int HEALTH_BAR_Y_OFFSET = -40;
	
	// Attack system
	private Random rng = new Random();
	private int currentAttack = -1;
	
	// Attack 0 — Fireball Barrage: bobs up/down, fires every 72 frames, 720-frame duration, speed -9
	private int p1a1Timer = 0;
	private static final int P1A1_DURATION = 720;
	private static final int P1A1_SHOOT_INTERVAL = 72;      // 20% faster than FirstBoss P2A2 (90→72)
	private int p1a1ShootCooldown = 0;
	private boolean p1a1BobUp = false;
	private static final int BOB_SPEED = 3;
	private static final int BOB_TOP = 100;
	private static final int BOB_BOTTOM = 700;
	private static final int P1A1_PROJ_WIDTH = 150;
	private static final int P1A1_PROJ_HEIGHT = 58;
	private static final double P1A1_PROJ_SPEED = -9;
	
	// Attack 1 — Charge/Ram: slides off right, teleports random Y, charges left at speed 12
	// Body collision deals 2 hearts damage. Repeats 3 times.
	private enum P1Atk2SubState { SLIDE_OFF, CHARGE_LEFT }
	private P1Atk2SubState p1a2SubState;
	private int p1a2Reps = 0;
	private static final int P1A2_MAX_REPS = 3;
	private static final int P1A2_SLIDE_OFF_SPEED = 10;
	private static final int P1A2_CHARGE_SPEED = 12;
	private static final int P1A2_DAMAGE = 2;
	
	// Attack 2 — Zip & Shoot: zips at speed 25, 0.3s pause, fires 3 big fireballs
	// stacked vertically (200×77, speed -14). Repeats 4 times.
	private enum P2Atk3SubState { ZIP_TO_POS, PAUSE, SHOOT }
	private P2Atk3SubState p2a3SubState;
	private int p2a3Reps = 0;
	private static final int P2A3_MAX_REPS = 4;
	private static final int P2A3_ZIP_SPEED = 25;
	private static final int P2A3_PAUSE_DURATION = 30;      // 0.3s pause before firing
	private int p2a3PauseTimer = 0;
	private int p2a3TargetX;
	private int p2a3TargetY;
	private static final int P2A3_PROJ_WIDTH = 200;
	private static final int P2A3_PROJ_HEIGHT = 77;
	private static final double P2A3_PROJ_SPEED = -14;
	private static final int P2A3_FIREBALL_GAP = 80;        // vertical spacing between 3 fireballs
	
	// Attack 3 — Mini Boss: spawns at x≈550, 3000 HP, fires homing bolts continuously
	// 5 bolts per wave, 33-frame spawn interval, 50-frame launch delay, speed 6, normalised direction
	private BufferedImage[] p3MiniBossFrames = new BufferedImage[3];
	
	// Mini boss state
	private boolean miniBossAlive = false;
	private int miniBossHp = 0;
	private int miniBossX;
	private int miniBossY;
	private int miniBossFrame = 0;
	private int miniBossAnimTick = 0;
	private int miniBossDmgFlash = 0;
	private static final int MINI_BOSS_MAX_HP = 3000;
	private static final int MINI_BOSS_DISPLAY_W = 175;
	private static final int MINI_BOSS_DISPLAY_H = 196;
	private static final int MINI_BOSS_COLLISION_INSET_X = 20;  // AABB collision with 20px insets
	private static final int MINI_BOSS_COLLISION_INSET_Y = 20;
	
	// Mini boss homing bolt parameters
	private static final int MB_NUM_BOLTS = 5;
	private static final int MB_SPAWN_INTERVAL = 33;      // 0.33s between spawns
	private static final int MB_LAUNCH_DELAY = 50;        // 0.5s before launching
	private static final double MB_BOLT_SPEED = 6;        // speed magnitude
	private static final int MB_BOLT_WIDTH = 84;
	private static final int MB_BOLT_HEIGHT = 42;
	private int mbBoltsSpawned = 0;
	private int mbSpawnCooldown = 0;
	private List<EnemyProjectile> mbPendingBolts = new ArrayList<>();
	private List<Integer> mbPendingTimers = new ArrayList<>();
	private boolean mbWaveDone = false;
	
	// Post-defeat delay (200 frames = 2s at 100 FPS)
	private static final int MINI_BOSS_DEFEAT_DELAY = 200;
	private int miniBossDefeatTimer = 0;
	
	private List<EnemyProjectile> enemyProjectiles = new ArrayList<>();
	
	private boolean multiplayer;
	private Player1 player1;
	private Player2 player2;
	
	// Initialises boss, loads sprites, sets HP (doubled in multiplayer)
	public FinalBoss(boolean multiplayer, Player1 player1, Player2 player2) {
		this.multiplayer = multiplayer;
		this.player1 = player1;
		this.player2 = player2;
		loadAllFrames();
		
		hpPerPhase = multiplayer ? BASE_HP_PER_PHASE * 2 : BASE_HP_PER_PHASE;
		totalMaxHp = hpPerPhase * TOTAL_PHASES;
		
		phaseHp = hpPerPhase;
		totalHp = totalMaxHp;
		
		homeX = PANEL_WIDTH - DISPLAY_WIDTH - 50;
		homeY = (PANEL_HEIGHT - DISPLAY_HEIGHT) / 2;
		
		x = homeX;
		y = -DISPLAY_HEIGHT;
		
		state = State.ENTERING;
	}
	
	// Loads all 9 boss sprite frames (3 per phase) plus fireball and mini boss sprites
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
			
			// Fireball sprites (reused across multiple attacks)
			String[] fireballFiles = {"sprite-1-1.png", "sprite-1-8.png", "sprite-1-12.png"};
			for (int f = 0; f < 3; f++) {
				p1a1Frames[f] = ImageIO.read(new File(basePath + "phase1/Attack1/" + fireballFiles[f]));
			}
			
			// Mini boss sprites (3-frame loop)
			String[] miniBossFiles = {"sprite-1-1.png", "sprite-1-4.png", "sprite-1-6.png"};
			for (int f = 0; f < 3; f++) {
				p3MiniBossFrames[f] = ImageIO.read(new File(basePath + "phase3/Attack1/" + miniBossFiles[f]));
			}
		} catch (IOException e) {
			System.err.println("Error loading Final Boss sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Main update — handles state transitions and projectile cleanup each frame
	public void update() {
		if (state == State.DEFEATED) return;
		
		if (damageFlashTimer > 0) damageFlashTimer--;
		
		switch (state) {
			case ENTERING:
				// Descend from top of screen to home position
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
				// Phase defeated — slide off right edge
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
				// Off screen, about to start next phase entry
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
		
		// Remove off-screen or consumed projectiles
		Iterator<EnemyProjectile> it = enemyProjectiles.iterator();
		while (it.hasNext()) {
			EnemyProjectile ep = it.next();
			ep.update();
			if (ep.isOffScreen() || ep.isConsumed()) {
				it.remove();
			}
		}
	}
	
	// Advances the 3-frame looping animation
	private void advanceAnimation() {
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % 3;
		}
	}
	
	// Attack inheritance: P1 gets 2 attacks, P2 gets P1's + Zip&Shoot, P3 gets all + MiniBoss
	private void pickAttack() {
		int numAttacks;
		if (currentPhase == 0) {
			numAttacks = 2;  // Fireball Barrage (0), Charge/Ram (1)
		} else if (currentPhase == 1) {
			numAttacks = 3;  // + Zip & Shoot (2)
		} else {
			numAttacks = 4;  // + Mini Boss (3)
		}
		currentAttack = rng.nextInt(numAttacks);
		state = State.ATTACKING;
		
		switch (currentAttack) {
			case 0: initP1Attack1(); break;
			case 1: initP1Attack2(); break;
			case 2: initP2Attack3(); break;
			case 3: initP3Attack4(); break;
		}
	}
	
	// Attack 0 — Fireball Barrage: initialise bob direction and timers
	private void initP1Attack1() {
		p1a1Timer = P1A1_DURATION;
		p1a1ShootCooldown = P1A1_SHOOT_INTERVAL;
		p1a1BobUp = (y > (BOB_TOP + BOB_BOTTOM) / 2);
	}
	
	// Bobs up/down between BOB_TOP and BOB_BOTTOM, fires a fireball every 72 frames
	private void updateP1Attack1() {
		p1a1Timer--;
		
		// Reverse direction at bob boundaries
		if (p1a1BobUp) {
			y -= BOB_SPEED;
			if (y <= BOB_TOP) { y = BOB_TOP; p1a1BobUp = false; }
		} else {
			y += BOB_SPEED;
			if (y >= BOB_BOTTOM) { y = BOB_BOTTOM; p1a1BobUp = true; }
		}
		
		// Fire on cooldown
		p1a1ShootCooldown--;
		if (p1a1ShootCooldown <= 0) {
			fireP1Fireball();
			p1a1ShootCooldown = P1A1_SHOOT_INTERVAL;
		}
		
		if (p1a1Timer <= 0) {
			state = State.RETURNING;
		}
	}
	
	// Fires a single fireball from the boss's left edge, vertically centred
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
	
	// Attack 1 — Charge/Ram: initialise sub-state and rep counter
	private void initP1Attack2() {
		p1a2SubState = P1Atk2SubState.SLIDE_OFF;
		p1a2Reps = 0;
	}
	
	// Slides off right, teleports to random Y, charges left. 3 reps.
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
				checkChargeCollision();  // body contact deals 2 hearts
				
				if (x + DISPLAY_WIDTH < 0) {
					// Off screen left — count reps
					p1a2Reps++;
					if (p1a2Reps < P1A2_MAX_REPS) {
						// Teleport back to right edge at new random Y
						x = PANEL_WIDTH + 10;
						y = rng.nextInt(PANEL_HEIGHT - DISPLAY_HEIGHT - 100) + 50;
						// Stay in CHARGE_LEFT
					} else {
						// All charges done — reappear from right for return
						x = PANEL_WIDTH + 10;
						state = State.RETURNING;
					}
				}
				break;
				
			default:
				break;
		}
	}
	
	// Checks if boss body overlaps any player during charge (AABB with insets)
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
	
	// AABB body collision check using collision insets
	private boolean bodyCollidesWithPlayer(int px, int py, int pw, int ph) {
		int hitX = x + COLLISION_INSET_X;
		int hitY = y + COLLISION_INSET_Y;
		int hitW = DISPLAY_WIDTH  - 2 * COLLISION_INSET_X;
		int hitH = DISPLAY_HEIGHT - 2 * COLLISION_INSET_Y;
		
		return px < hitX + hitW && px + pw > hitX &&
		       py < hitY + hitH && py + ph > hitY;
	}
	
	// Attack 2 — Zip & Shoot: initialise rep counter and first zip target
	private void initP2Attack3() {
		p2a3Reps = 0;
		pickZipTarget();
		p2a3SubState = P2Atk3SubState.ZIP_TO_POS;
	}
	
	// Picks a random position on the right half of the screen
	private void pickZipTarget() {
		p2a3TargetX = PANEL_WIDTH / 2 + rng.nextInt(PANEL_WIDTH / 2 - DISPLAY_WIDTH - 20);
		p2a3TargetY = 50 + rng.nextInt(PANEL_HEIGHT - DISPLAY_HEIGHT - 100);
	}
	
	// Zips to target at speed 25, pauses 0.3s, fires 3 stacked fireballs. 4 reps.
	private void updateP2Attack3() {
		switch (p2a3SubState) {
			case ZIP_TO_POS:
				// Move toward target at high speed on each axis independently
				boolean arrived = true;
				
				if (Math.abs(x - p2a3TargetX) <= P2A3_ZIP_SPEED) {
					x = p2a3TargetX;
				} else {
					x += (p2a3TargetX > x) ? P2A3_ZIP_SPEED : -P2A3_ZIP_SPEED;
					arrived = false;
				}
				
				if (Math.abs(y - p2a3TargetY) <= P2A3_ZIP_SPEED) {
					y = p2a3TargetY;
				} else {
					y += (p2a3TargetY > y) ? P2A3_ZIP_SPEED : -P2A3_ZIP_SPEED;
					arrived = false;
				}
				
				if (arrived) {
					p2a3PauseTimer = P2A3_PAUSE_DURATION;
					p2a3SubState = P2Atk3SubState.PAUSE;
				}
				break;
				
			case PAUSE:
				p2a3PauseTimer--;
				if (p2a3PauseTimer <= 0) {
					p2a3SubState = P2Atk3SubState.SHOOT;
				}
				break;
				
			case SHOOT:
				fireP2ZipFireballs();
				p2a3Reps++;
				if (p2a3Reps < P2A3_MAX_REPS) {
					pickZipTarget();
					p2a3SubState = P2Atk3SubState.ZIP_TO_POS;
				} else {
					state = State.RETURNING;
				}
				break;
				
			default:
				break;
		}
	}
	
	// Fires 3 big fireballs stacked vertically (centre, above, below) from left edge
	private void fireP2ZipFireballs() {
		double spawnX = x;
		double centreY = y + (DISPLAY_HEIGHT / 2.0);
		
		for (int i = -1; i <= 1; i++) {
			double spawnY = centreY - (P2A3_PROJ_HEIGHT / 2.0) + (i * P2A3_FIREBALL_GAP);
			EnemyProjectile ep = new EnemyProjectile(
				p1a1Frames,   // reuse fireball sprites (drawn bigger)
				spawnX, spawnY,
				P2A3_PROJ_SPEED, 0,
				P2A3_PROJ_WIDTH, P2A3_PROJ_HEIGHT
			);
			enemyProjectiles.add(ep);
		}
	}
	
	// Dispatches update to the currently active attack
	private void updateCurrentAttack() {
		switch (currentAttack) {
			case 0: updateP1Attack1(); break;
			case 1: updateP1Attack2(); break;
			case 2: updateP2Attack3(); break;
			case 3: updateP3Attack4(); break;
		}
	}
	
	// Attack 3 — Mini Boss: spawns at centre-right, fires homing bolts until defeated
	private void initP3Attack4() {
		miniBossAlive = true;
		miniBossHp = MINI_BOSS_MAX_HP;
		miniBossX = (PANEL_WIDTH / 2) + 50;  // slightly right of centre
		miniBossY = (PANEL_HEIGHT - MINI_BOSS_DISPLAY_H) / 2;
		miniBossFrame = 0;
		miniBossAnimTick = 0;
		miniBossDmgFlash = 0;
		miniBossDefeatTimer = 0;
		
		// Reset bolt wave
		mbBoltsSpawned = 0;
		mbSpawnCooldown = 0;
		mbPendingBolts.clear();
		mbPendingTimers.clear();
		mbWaveDone = false;
	}
	
	// Updates mini boss animation and homing bolt waves; handles post-defeat delay
	private void updateP3Attack4() {
		if (miniBossAlive) {
			// Animate mini boss sprite
			miniBossAnimTick++;
			if (miniBossAnimTick >= ANIMATION_DELAY) {
				miniBossAnimTick = 0;
				miniBossFrame = (miniBossFrame + 1) % 3;
			}
			if (miniBossDmgFlash > 0) miniBossDmgFlash--;
			
			// Continuous homing bolt waves: spawn bolts at interval, launch after delay
			if (!mbWaveDone) {
				mbSpawnCooldown--;
				if (mbSpawnCooldown <= 0 && mbBoltsSpawned < MB_NUM_BOLTS) {
					spawnMiniBossHomingBolt();
					mbBoltsSpawned++;
					mbSpawnCooldown = MB_SPAWN_INTERVAL;
				}
				
				tickMiniBossHomingTimers();
				// All bolts spawned and launched → start next wave
				if (mbBoltsSpawned >= MB_NUM_BOLTS && mbPendingBolts.isEmpty()) {
					mbBoltsSpawned = 0;
					mbSpawnCooldown = MB_SPAWN_INTERVAL;  // brief gap between waves
				}
			}
		} else {
			// Mini boss defeated — 200-frame delay before returning
			miniBossDefeatTimer--;
			if (miniBossDefeatTimer <= 0) {
				state = State.RETURNING;
			}
		}
	}
	
	// Spawns one stationary homing bolt at the mini boss's centre
	private void spawnMiniBossHomingBolt() {
		double boltX = miniBossX + (MINI_BOSS_DISPLAY_W / 2.0) - (MB_BOLT_WIDTH / 2.0);
		double boltY = miniBossY + (MINI_BOSS_DISPLAY_H / 2.0) - (MB_BOLT_HEIGHT / 2.0);
		
		EnemyProjectile bolt = new EnemyProjectile(
			p1a1Frames,    // reuse fireball sprites at bolt size
			boltX, boltY,
			0, 0,          // stationary until launched
			MB_BOLT_WIDTH, MB_BOLT_HEIGHT
		);
		enemyProjectiles.add(bolt);
		mbPendingBolts.add(bolt);
		mbPendingTimers.add(MB_LAUNCH_DELAY);
	}
	
	// Ticks each pending bolt's timer; aims and launches when timer reaches 0
	private void tickMiniBossHomingTimers() {
		for (int i = mbPendingBolts.size() - 1; i >= 0; i--) {
			int timer = mbPendingTimers.get(i) - 1;
			
			if (timer <= 0) {
				launchMiniBossHomingBolt(mbPendingBolts.get(i));
				mbPendingBolts.remove(i);
				mbPendingTimers.remove(i);
			} else {
				mbPendingTimers.set(i, timer);
			}
		}
	}
	
	// Aims a bolt at a random alive player using normalised direction vector, speed 6
	private void launchMiniBossHomingBolt(EnemyProjectile bolt) {
		int targetX, targetY;
		if (multiplayer && player2 != null && player2.isAlive() && rng.nextBoolean()) {
			targetX = player2.getX() + player2.getDisplayWidth() / 2;
			targetY = player2.getY() + player2.getDisplayHeight() / 2;
		} else if (player1 != null && player1.isAlive()) {
			targetX = player1.getX() + player1.getDisplayWidth() / 2;
			targetY = player1.getY() + player1.getDisplayHeight() / 2;
		} else {
			bolt.setSpeedX(-MB_BOLT_SPEED);  // fallback: aim left
			return;
		}
		
		// Normalised direction vector × bolt speed
		double boltCX = bolt.getX() + MB_BOLT_WIDTH / 2.0;
		double boltCY = bolt.getY() + MB_BOLT_HEIGHT / 2.0;
		double dx = targetX - boltCX;
		double dy = targetY - boltCY;
		double dist = Math.sqrt(dx * dx + dy * dy);
		
		if (dist > 0) {
			bolt.setSpeedX(MB_BOLT_SPEED * dx / dist);
			bolt.setSpeedY(MB_BOLT_SPEED * dy / dist);
		} else {
			bolt.setSpeedX(-MB_BOLT_SPEED);
		}
	}
	
	// Deals damage to mini boss; triggers defeat timer when HP reaches 0
	public void damageMiniBoss(int amount) {
		if (!miniBossAlive) return;
		miniBossHp -= amount;
		miniBossDmgFlash = DAMAGE_FLASH_DURATION;
		
		if (miniBossHp <= 0) {
			miniBossHp = 0;
			miniBossAlive = false;
			miniBossDefeatTimer = MINI_BOSS_DEFEAT_DELAY;
			// Consume pending bolts so stationary ones don't linger on screen
			for (EnemyProjectile bolt : mbPendingBolts) {
				bolt.consume();
			}
			mbPendingBolts.clear();
			mbPendingTimers.clear();
		}
	}
	
	// AABB collision for mini boss hitbox (20px insets)
	public boolean miniBossCollidesWith(Projectile p) {
		if (!miniBossAlive) return false;
		
		int hitX = miniBossX + MINI_BOSS_COLLISION_INSET_X;
		int hitY = miniBossY + MINI_BOSS_COLLISION_INSET_Y;
		int hitW = MINI_BOSS_DISPLAY_W - 2 * MINI_BOSS_COLLISION_INSET_X;
		int hitH = MINI_BOSS_DISPLAY_H - 2 * MINI_BOSS_COLLISION_INSET_Y;
		
		return p.getX() < hitX + hitW &&
		       p.getX() + p.getDisplayWidth() > hitX &&
		       p.getY() < hitY + hitH &&
		       p.getY() + p.getDisplayHeight() > hitY;
	}
	
	public boolean isMiniBossAlive() { return miniBossAlive; }
	
	// Moves boss back toward home position; transitions to IDLE on arrival
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
	
	// Draws boss sprite, health bar, phase indicator, projectiles, and mini boss
	public void draw(Graphics2D g2d) {
		if (state == State.DEFEATED || state == State.WAITING) return;
		
		boolean drawBoss = true;
		// Hide during Charge/Ram slide-off when off screen right
		if (state == State.ATTACKING && currentAttack == 1
		    && p1a2SubState == P1Atk2SubState.SLIDE_OFF && x > PANEL_WIDTH) {
			drawBoss = false;
		}
		
		if (drawBoss) {
			BufferedImage sprite = phaseFrames[currentPhase][currentFrame];
			
			if (sprite != null) {
				g2d.drawImage(sprite, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
				
				// Damage flash: draw red-tinted copy via SRC_IN compositing
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
		
		// Always draw projectiles
		for (EnemyProjectile ep : enemyProjectiles) {
			ep.draw(g2d);
		}
		
		// Draw mini boss if alive (Phase 3 Attack 4)
		if (miniBossAlive) {
			BufferedImage mbSprite = p3MiniBossFrames[miniBossFrame];
			if (mbSprite != null) {
				g2d.drawImage(mbSprite, miniBossX, miniBossY,
				              MINI_BOSS_DISPLAY_W, MINI_BOSS_DISPLAY_H, null);
				
				// Mini boss damage flash (same SRC_IN technique)
				if (miniBossDmgFlash > 0) {
					BufferedImage flash = new BufferedImage(MINI_BOSS_DISPLAY_W, MINI_BOSS_DISPLAY_H,
					                                        BufferedImage.TYPE_INT_ARGB);
					Graphics2D fg = flash.createGraphics();
					fg.drawImage(mbSprite, 0, 0, MINI_BOSS_DISPLAY_W, MINI_BOSS_DISPLAY_H, null);
					fg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f));
					fg.setColor(Color.RED);
					fg.fillRect(0, 0, MINI_BOSS_DISPLAY_W, MINI_BOSS_DISPLAY_H);
					fg.dispose();
					
					Composite oc = g2d.getComposite();
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DAMAGE_FLASH_ALPHA));
					g2d.drawImage(flash, miniBossX, miniBossY, null);
					g2d.setComposite(oc);
				}
				// Mini boss health bar
				drawMiniBossHealthBar(g2d);
			}
		}
	}
	
	// Draws mini boss health bar with colour transitions
	private void drawMiniBossHealthBar(Graphics2D g2d) {
		int barW = 120;
		int barH = 10;
		int barX = miniBossX + (MINI_BOSS_DISPLAY_W - barW) / 2;
		int barY = miniBossY - 16;
		
		g2d.setColor(new Color(50, 50, 50));
		g2d.fillRect(barX, barY, barW, barH);
		
		// Fill colour: green >50%, yellow 20-50%, red <20%
		double ratio = (double) miniBossHp / MINI_BOSS_MAX_HP;
		int fillW = (int) (barW * ratio);
		if (ratio > 0.5) {
			g2d.setColor(new Color(50, 205, 50));
		} else if (ratio > 0.2) {
			g2d.setColor(new Color(255, 200, 0));
		} else {
			g2d.setColor(new Color(220, 30, 30));
		}
		g2d.fillRect(barX, barY, fillW, barH);
		
		g2d.setColor(Color.WHITE);
		g2d.drawRect(barX, barY, barW, barH);
	}
	
	// Draws shared health bar above the boss — total HP across all 3 phases
	private void drawHealthBar(Graphics2D g2d) {
		int barX = x + (DISPLAY_WIDTH - HEALTH_BAR_WIDTH) / 2;
		int barY = y + HEALTH_BAR_Y_OFFSET;
		
		g2d.setColor(new Color(50, 50, 50));
		g2d.fillRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		
		// Fill colour: green >50%, yellow 20-50%, red <20%
		double hpRatio = (double) totalHp / totalMaxHp;
		int fillWidth = (int) (HEALTH_BAR_WIDTH * hpRatio);
		
		if (hpRatio > 0.5) {
			g2d.setColor(new Color(50, 205, 50));
		} else if (hpRatio > 0.2) {
			g2d.setColor(new Color(255, 200, 0));
		} else {
			g2d.setColor(new Color(220, 30, 30));
		}
		g2d.fillRect(barX, barY, fillWidth, HEALTH_BAR_HEIGHT);
		
		g2d.setColor(Color.WHITE);
		g2d.drawRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		
		// HP text centred on bar
		g2d.setFont(new Font("Arial", Font.BOLD, 14));
		String hpText = totalHp + " / " + totalMaxHp;
		int textWidth = g2d.getFontMetrics().stringWidth(hpText);
		g2d.setColor(Color.WHITE);
		g2d.drawString(hpText, barX + (HEALTH_BAR_WIDTH - textWidth) / 2, barY + 17);
	}
	
	// Draws "Phase X / 3" label above the health bar
	private void drawPhaseIndicator(Graphics2D g2d) {
		int textX = x + (DISPLAY_WIDTH / 2);
		int textY = y + HEALTH_BAR_Y_OFFSET - 8;
		
		g2d.setFont(new Font("Arial", Font.BOLD, 16));
		g2d.setColor(Color.WHITE);
		String phaseText = "Phase " + (currentPhase + 1) + " / " + TOTAL_PHASES;
		int textWidth = g2d.getFontMetrics().stringWidth(phaseText);
		g2d.drawString(phaseText, textX - textWidth / 2, textY);
	}
	
	// Deals damage; only in IDLE/ATTACKING/RETURNING states. Triggers phase slide-off at 0 HP.
	public void takeDamage(int amount) {
		if (state != State.IDLE && state != State.ATTACKING && state != State.RETURNING) return;
		
		phaseHp -= amount;
		damageFlashTimer = DAMAGE_FLASH_DURATION;
		
		if (phaseHp <= 0) {
			phaseHp = 0;
			totalHp = getRemainingPhasesHp();
			currentAttack = -1;
			miniBossAlive = false;  // clean up mini boss if active
			mbPendingBolts.clear();
			mbPendingTimers.clear();
			state = State.SLIDING;
		} else {
			totalHp = getRemainingPhasesHp() + phaseHp;
		}
	}
	
	// HP from phases that haven't started yet (e.g. phase 0 → phases 1+2 still full)
	private int getRemainingPhasesHp() {
		int remainingPhases = (TOTAL_PHASES - 1) - currentPhase;
		return remainingPhases * hpPerPhase;
	}
	
	// AABB collision with insets; only registers in IDLE/ATTACKING/RETURNING states
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
	
	// Getters
	public boolean isAlive() {
		return state == State.IDLE || state == State.ATTACKING || state == State.RETURNING;
	}
	
	public boolean isDefeated() {
		return state == State.DEFEATED;
	}
	
	public int getCurrentPhase() {
		return currentPhase;
	}
	
	public int getTotalHp() {
		return totalHp;
	}
	
	public int getTotalMaxHp() {
		return totalMaxHp;
	}
	
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
	public List<EnemyProjectile> getEnemyProjectiles() { return enemyProjectiles; }
}
