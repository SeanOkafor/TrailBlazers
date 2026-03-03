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

// FirstBoss — Level 1 boss with 2 phases (30k HP each), 4 attacks total.
// States: ENTERING → IDLE → ATTACKING → RETURNING → SLIDING → WAITING → DEFEATED.
// Health bar colour: green (>50%) → yellow (20-50%) → red (<20%).
public class FirstBoss {
	
	// Sprite frames
	private BufferedImage[][] phaseFrames = new BufferedImage[2][3];
	
	// Attack sprites: attackSprites[phase][attack][frame] (3 frames each)
	private BufferedImage[][][] attackSprites = new BufferedImage[2][2][3];
	
	private int currentFrame = 0;
	private int animationTick = 0;
	private static final int ANIMATION_DELAY = 10;
	
	// Display
	private int x;
	private int y;
	private static final int DISPLAY_WIDTH = 189;
	private static final int DISPLAY_HEIGHT = 249;
	
	// Collision insets
	private static final int COLLISION_INSET_X = 30;
	private static final int COLLISION_INSET_Y = 30;
	
	private static final int PANEL_WIDTH = 1000;
	private static final int PANEL_HEIGHT = 1000;
	
	// Health
	private static final int BASE_HP_PER_PHASE = 5625;
	private static final int TOTAL_PHASES = 2;
	
	private int hpPerPhase;
	private int totalMaxHp;
	private int currentPhase = 0;
	private int phaseHp;
	private int totalHp;
	
	// State machine: ENTERING → IDLE → ATTACKING → RETURNING → SLIDING → WAITING → DEFEATED
	private enum State { ENTERING, IDLE, ATTACKING, RETURNING, SLIDING, WAITING, DEFEATED }
	private State state = State.ENTERING;
	
	// Movement
	private static final int ENTRY_SPEED = 3;
	private static final int RETURN_SPEED = 4;
	private static final int SLIDE_SPEED = 5;
	private int homeX;
	private int homeY;
	
	private int waitTimer = 0;
	private static final int WAIT_BETWEEN_PHASES = 60;
	
	// Brief pause before next attack
	private int idleTimer = 0;
	private static final int IDLE_PAUSE = 60;  // 0.6 seconds
	
	// Damage flash (red-tint SRC_IN compositing)
	private int damageFlashTimer = 0;
	private static final int DAMAGE_FLASH_DURATION = 10;
	private static final float DAMAGE_FLASH_ALPHA = 0.45f;
	
	// Health bar
	private static final int HEALTH_BAR_WIDTH = 300;
	private static final int HEALTH_BAR_HEIGHT = 24;
	private static final int HEALTH_BAR_Y_OFFSET = -40;
	
	// Attack system
	private Random rng = new Random();
	private int currentAttack = -1;  // 0 = Attack1, 1 = Attack2
	
	// Attack 1: Bob & Shoot — bobs up/down, fires every 75 frames for 1000 frames
	private int attack1Timer = 0;
	private static final int ATTACK1_DURATION = 1000;       // 10 seconds at 100 FPS
	private static final int ATTACK1_SHOOT_INTERVAL = 75;    // 0.75 seconds at 100 FPS
	private int attack1ShootCooldown = 0;
	private boolean attack1BobUp = false;
	private static final int BOB_SPEED = 3;
	private static final int BOB_TOP = 100;
	private static final int BOB_BOTTOM = 700;
	
	// Attack 1 projectile size (75% reduction)
	private static final int ATK1_PROJ_WIDTH = 95;
	private static final int ATK1_PROJ_HEIGHT = 65;
	private static final double ATK1_PROJ_SPEED = -7;  // moves left
	
	// Attack 2: Dive Bomb — slides off, repositions, spawns 8 bolts, waits 1s, fires. 3 reps
	private enum Attack2SubState { SLIDE_OFF, SLIDE_TO_POS, SHOW_BOLTS, WAIT_BOLTS, FIRE_BOLTS }
	private Attack2SubState atk2SubState;
	private int atk2Timer = 0;
	private int atk2Reps = 0;
	private static final int ATK2_MAX_REPS = 3;
	private static final int ATK2_NUM_BOLTS = 8;
	private boolean atk2Top = false;
	private int atk2TargetY = 0;  // Y position to slide to
	private static final int ATK2_SLIDE_SPEED = 8;
	private static final int ATK2_BOLT_WAIT = 100;        // 1 second before bolts launch
	private static final int ATK2_BOLT_SPACING = 100;     // 100px between each bolt vertically
	
	// Attack 2 bolt size (75% reduction)
	private static final int ATK2_BOLT_WIDTH = 110;
	private static final int ATK2_BOLT_HEIGHT = 34;
	private static final double ATK2_BOLT_SPEED = -8;     // flies left
	
	// Current rep's bolts (launched after wait timer)
	private List<EnemyProjectile> atk2CurrentBolts = new ArrayList<>();
	
	// Phase 2 Attack 1: Homing Bolts — spawns bolts individually, each aims at player centre then launches at speed 6
	private enum P2Atk1SubState { SLIDE_OFF, SLIDE_TO_POS, SPAWNING }
	private P2Atk1SubState p2a1SubState;
	private int p2a1Reps = 0;
	private static final int P2A1_MAX_REPS = 3;
	private int p2a1BoltsSpawned = 0;
	private static final int P2A1_NUM_BOLTS = 5;
	private static final int P2A1_SPAWN_INTERVAL = 30;    // 0.3 seconds
	private static final int P2A1_LAUNCH_DELAY = 50;      // 0.5 seconds after spawn
	private int p2a1SpawnCooldown = 0;
	private boolean p2a1Top = false;
	private static final int P2A1_SLIDE_SPEED = 8;
	private static final double P2A1_BOLT_SPEED = 6;      // total speed magnitude
	private static final int P2A1_BOLT_WIDTH = 84;
	private static final int P2A1_BOLT_HEIGHT = 42;
	// Pending bolts awaiting aim and launch
	private List<EnemyProjectile> p2a1PendingBolts = new ArrayList<>();
	private List<Integer> p2a1PendingTimers = new ArrayList<>();
	
	// Phase 2 Attack 2: Forking Bolts — bobs and shoots; bolts fork into 3 at x=500 using ±20° trig
	private int p2a2Timer = 0;
	private static final int P2A2_DURATION = 1000;         // 10 seconds
	private static final int P2A2_SHOOT_INTERVAL = 90;     // 0.9 seconds
	private int p2a2ShootCooldown = 0;
	private boolean p2a2BobUp = false;
	private static final int P2A2_BOLT_WIDTH = 97;
	private static final int P2A2_BOLT_HEIGHT = 36;
	private static final double P2A2_BOLT_SPEED = -7;
	private static final int P2A2_FORK_X = 500;            // middle of screen
	private static final double P2A2_FORK_ANGLE = 20;      // degrees
	// Bolts that haven't forked yet
	private List<EnemyProjectile> p2a2ForkableBolts = new ArrayList<>();
	
	// Enemy projectiles
	private List<EnemyProjectile> enemyProjectiles = new ArrayList<>();
	
	private boolean multiplayer;
	private Player1 player1;
	private Player2 player2;
	
	public FirstBoss(boolean multiplayer, Player1 player1, Player2 player2) {
		this.multiplayer = multiplayer;
		this.player1 = player1;
		this.player2 = player2;
		loadAllFrames();
		
		hpPerPhase = multiplayer ? BASE_HP_PER_PHASE * 2 : BASE_HP_PER_PHASE;
		totalMaxHp = hpPerPhase * TOTAL_PHASES;
		phaseHp = hpPerPhase;
		totalHp = totalMaxHp;
		
		homeX = PANEL_WIDTH - DISPLAY_WIDTH - 100;
		homeY = (PANEL_HEIGHT - DISPLAY_HEIGHT) / 2;
		
		x = homeX;
		y = -DISPLAY_HEIGHT;
		
		state = State.ENTERING;
	}
	
	// Loads all boss body and attack sprites from disk
	private void loadAllFrames() {
		String basePath = "res/New Graphics/First Boss/";
		String[] phaseFolders = {"phase 1", "phase 2"};
		String[] prefixes = {"level1phase1", "level1phase2"};
		
		try {
			// Load boss body sprites (3 per phase)
			for (int p = 0; p < 2; p++) {
				for (int f = 0; f < 3; f++) {
					String path = basePath + phaseFolders[p] + "/" + prefixes[p] + "-" + (f + 1) + ".png";
					phaseFrames[p][f] = ImageIO.read(new File(path));
				}
			}
			
			// Load attack sprites (3 frames per attack, 2 attacks per phase)
			String[][] atkPrefixes = {
				{"L1P1A1", "L1P1A2"},  // Phase 1
				{"L1P2A1", "L1P2A2"}   // Phase 2
			};
			for (int p = 0; p < 2; p++) {
				for (int a = 0; a < 2; a++) {
					for (int f = 0; f < 3; f++) {
						String path = basePath + phaseFolders[p] + "/Attack" + (a + 1) + "/"
						              + atkPrefixes[p][a] + "-" + (f + 1) + ".png";
						attackSprites[p][a][f] = ImageIO.read(new File(path));
					}
				}
			}
		} catch (IOException e) {
			System.err.println("Error loading First Boss sprites: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Main update — advances state machine each frame
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
				// Slides off right; transitions to WAITING or DEFEATED
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
				// Reset for next phase and re-enter from top
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
	
	// Randomly picks attack 0 or 1, dispatched per current phase
	private void pickAttack() {
		currentAttack = rng.nextInt(2);  // 0 or 1
		state = State.ATTACKING;
		
		if (currentPhase == 0) {
			if (currentAttack == 0) initAttack1();
			else initAttack2();
		} else {
			if (currentAttack == 0) initP2Attack1();
			else initP2Attack2();
		}
	}
	
	// Initialises Attack 1: Bob & Shoot
	private void initAttack1() {
		attack1Timer = ATTACK1_DURATION;
		attack1ShootCooldown = ATTACK1_SHOOT_INTERVAL;
		attack1BobUp = (y > (BOB_TOP + BOB_BOTTOM) / 2);
	}
	
	private void updateAttack1() {
		attack1Timer--;
		
		// Bob up and down
		if (attack1BobUp) {
			y -= BOB_SPEED;
			if (y <= BOB_TOP) {
				y = BOB_TOP;
				attack1BobUp = false;
			}
		} else {
			y += BOB_SPEED;
			if (y >= BOB_BOTTOM) {
				y = BOB_BOTTOM;
				attack1BobUp = true;
			}
		}
		
		// Shoot cooldown
		attack1ShootCooldown--;
		if (attack1ShootCooldown <= 0) {
			fireAttack1Projectile();
			attack1ShootCooldown = ATTACK1_SHOOT_INTERVAL;
		}
		
		// Attack finished
		if (attack1Timer <= 0) {
			state = State.RETURNING;
		}
	}
	
	// Fires a horizontal projectile from boss's left edge, centred vertically
	private void fireAttack1Projectile() {
		double spawnX = x;
		double spawnY = y + (DISPLAY_HEIGHT / 2.0) - (ATK1_PROJ_HEIGHT / 2.0);
		
		EnemyProjectile ep = new EnemyProjectile(
			attackSprites[currentPhase][0],
			spawnX, spawnY,
			ATK1_PROJ_SPEED, 0,
			ATK1_PROJ_WIDTH, ATK1_PROJ_HEIGHT
		);
		enemyProjectiles.add(ep);
	}
	
	// Initialises Attack 2: Dive Bomb
	private void initAttack2() {
		atk2SubState = Attack2SubState.SLIDE_OFF;
		atk2Reps = 0;
		atk2Timer = 0;
	}
	
	private void updateAttack2() {
		switch (atk2SubState) {
			case SLIDE_OFF:
				x += ATK2_SLIDE_SPEED;
				if (x > PANEL_WIDTH) {
					atk2Top = rng.nextBoolean();
					if (atk2Top) {
						y = 20;
					} else {
						y = PANEL_HEIGHT - DISPLAY_HEIGHT - 20;
					}
					// Y set while off-screen; slide back on
					x = PANEL_WIDTH + 10;
					atk2SubState = Attack2SubState.SLIDE_TO_POS;
				}
				break;
				
			case SLIDE_TO_POS:
				// Slide back to homeX
				x -= ATK2_SLIDE_SPEED;
				if (x <= homeX) {
					x = homeX;
					atk2SubState = Attack2SubState.SHOW_BOLTS;
				}
				break;
				
			case SHOW_BOLTS:
				// Spawn stationary bolts beside boss, start wait timer
				spawnAttack2Bolts();
				atk2SubState = Attack2SubState.WAIT_BOLTS;
				atk2Timer = ATK2_BOLT_WAIT;
				break;
				
			case WAIT_BOLTS:
				// Bolts are visible but stationary — pause 1 second
				atk2Timer--;
				if (atk2Timer <= 0) {
					launchAttack2Bolts();
					atk2SubState = Attack2SubState.FIRE_BOLTS;
					atk2Timer = 50;  // brief pause while bolts fly
				}
				break;
				
			case FIRE_BOLTS:
				atk2Timer--;
				if (atk2Timer <= 0) {
					atk2Reps++;
					if (atk2Reps < ATK2_MAX_REPS) {
						atk2SubState = Attack2SubState.SLIDE_OFF;
					} else {
						state = State.RETURNING;
					}
				}
				break;
				
			default:
				break;
		}
	}
	
	// Spawns 8 stationary bolts stacked vertically (100px apart), above or below boss
	private void spawnAttack2Bolts() {
		atk2CurrentBolts.clear();
		
		// Bolts align with the left edge of the boss sprite
		double boltX = x;
		
		for (int i = 0; i < ATK2_NUM_BOLTS; i++) {
			double boltY;
			if (atk2Top) {
				// Boss at top → bolts below
				boltY = y + DISPLAY_HEIGHT + 10 + (i * ATK2_BOLT_SPACING);
			} else {
				// Boss at bottom → bolts above
				boltY = y - ATK2_BOLT_HEIGHT - 10 - (i * ATK2_BOLT_SPACING);
			}
			
			EnemyProjectile bolt = new EnemyProjectile(
				attackSprites[currentPhase][1],
				boltX, boltY,
				0, 0,  // stationary until launched
				ATK2_BOLT_WIDTH, ATK2_BOLT_HEIGHT
			);
			atk2CurrentBolts.add(bolt);
			enemyProjectiles.add(bolt);
		}
	}
	
	// Launches all stationary bolts leftward
	private void launchAttack2Bolts() {
		for (EnemyProjectile bolt : atk2CurrentBolts) {
			bolt.setSpeedX(ATK2_BOLT_SPEED);
		}
		atk2CurrentBolts.clear();
	}
	
	// Initialises Phase 2 Attack 1: Homing Bolts
	private void initP2Attack1() {
		p2a1SubState = P2Atk1SubState.SLIDE_OFF;
		p2a1Reps = 0;
		p2a1PendingBolts.clear();
		p2a1PendingTimers.clear();
	}
	
	private void updateP2Attack1() {
		switch (p2a1SubState) {
			case SLIDE_OFF:
				x += P2A1_SLIDE_SPEED;
				if (x > PANEL_WIDTH) {
					p2a1Top = rng.nextBoolean();
					if (p2a1Top) {
						y = 20;
					} else {
						y = PANEL_HEIGHT - DISPLAY_HEIGHT - 20;
					}
					x = PANEL_WIDTH + 10;
					p2a1SubState = P2Atk1SubState.SLIDE_TO_POS;
				}
				break;
				
			case SLIDE_TO_POS:
				x -= P2A1_SLIDE_SPEED;
				if (x <= homeX) {
					x = homeX;
					p2a1SubState = P2Atk1SubState.SPAWNING;
					p2a1BoltsSpawned = 0;
					p2a1SpawnCooldown = 0;
				}
				break;
				
			case SPAWNING:
				// Spawn bolts one at a time
				p2a1SpawnCooldown--;
				if (p2a1SpawnCooldown <= 0 && p2a1BoltsSpawned < P2A1_NUM_BOLTS) {
					spawnP2HomingBolt();
					p2a1BoltsSpawned++;
					p2a1SpawnCooldown = P2A1_SPAWN_INTERVAL;
				}
				
				// Tick pending bolt timers and launch when ready
				tickP2HomingTimers();
				
				// All bolts spawned AND all launched → next rep or finish
				if (p2a1BoltsSpawned >= P2A1_NUM_BOLTS && p2a1PendingBolts.isEmpty()) {
					p2a1Reps++;
					if (p2a1Reps < P2A1_MAX_REPS) {
						p2a1SubState = P2Atk1SubState.SLIDE_OFF;
					} else {
						state = State.RETURNING;
					}
				}
				break;
				
			default:
				break;
		}
	}
	
	// Spawns one stationary homing bolt at boss's left edge, centred vertically
	private void spawnP2HomingBolt() {
		double boltX = x;
		double boltY = y + (DISPLAY_HEIGHT / 2.0) - (P2A1_BOLT_HEIGHT / 2.0);
		
		EnemyProjectile bolt = new EnemyProjectile(
			attackSprites[currentPhase][0],
			boltX, boltY,
			0, 0,  // stationary until launched
			P2A1_BOLT_WIDTH, P2A1_BOLT_HEIGHT
		);
		enemyProjectiles.add(bolt);
		p2a1PendingBolts.add(bolt);
		p2a1PendingTimers.add(P2A1_LAUNCH_DELAY);
	}
	
	// Ticks pending bolt timers; aims and launches each when expired
	private void tickP2HomingTimers() {
		for (int i = p2a1PendingBolts.size() - 1; i >= 0; i--) {
			int timer = p2a1PendingTimers.get(i) - 1;
			
			if (timer <= 0) {
				launchP2HomingBolt(p2a1PendingBolts.get(i));
				p2a1PendingBolts.remove(i);
				p2a1PendingTimers.remove(i);
			} else {
				p2a1PendingTimers.set(i, timer);
			}
		}
	}
	
	// Aims bolt at player centre, normalises direction vector, launches at fixed speed
	private void launchP2HomingBolt(EnemyProjectile bolt) {
		int targetX, targetY;
		if (multiplayer && player2 != null && rng.nextBoolean()) {
			targetX = player2.getX() + player2.getDisplayWidth() / 2;
			targetY = player2.getY() + player2.getDisplayHeight() / 2;
		} else {
			targetX = player1.getX() + player1.getDisplayWidth() / 2;
			targetY = player1.getY() + player1.getDisplayHeight() / 2;
		}
		
		double boltCX = bolt.getX() + P2A1_BOLT_WIDTH / 2.0;
		double boltCY = bolt.getY() + P2A1_BOLT_HEIGHT / 2.0;
		// Normalise direction vector to player and scale to fixed speed
		double dx = targetX - boltCX;
		double dy = targetY - boltCY;
		double dist = Math.sqrt(dx * dx + dy * dy);
		
		if (dist > 0) {
			bolt.setSpeedX(P2A1_BOLT_SPEED * dx / dist);
			bolt.setSpeedY(P2A1_BOLT_SPEED * dy / dist);
		} else {
			bolt.setSpeedX(-P2A1_BOLT_SPEED);
		}
	}
	
	// Initialises Phase 2 Attack 2: Forking Bolts
	private void initP2Attack2() {
		p2a2Timer = P2A2_DURATION;
		p2a2ShootCooldown = P2A2_SHOOT_INTERVAL;
		p2a2BobUp = (y > (BOB_TOP + BOB_BOTTOM) / 2);
		p2a2ForkableBolts.clear();
	}
	
	private void updateP2Attack2() {
		p2a2Timer--;
		
		// Bob up and down (same bounds as Phase 1 Attack 1)
		if (p2a2BobUp) {
			y -= BOB_SPEED;
			if (y <= BOB_TOP) { y = BOB_TOP; p2a2BobUp = false; }
		} else {
			y += BOB_SPEED;
			if (y >= BOB_BOTTOM) { y = BOB_BOTTOM; p2a2BobUp = true; }
		}
		
		// Shoot cooldown
		p2a2ShootCooldown--;
		if (p2a2ShootCooldown <= 0) {
			fireP2ForkingBolt();
			p2a2ShootCooldown = P2A2_SHOOT_INTERVAL;
		}
		
		// Check for forking at mid-screen
		checkP2A2Forks();
		
		if (p2a2Timer <= 0) {
			state = State.RETURNING;
		}
	}
	
	// Fires a forkable bolt from boss's left edge
	private void fireP2ForkingBolt() {
		double spawnX = x;
		double spawnY = y + (DISPLAY_HEIGHT / 2.0) - (P2A2_BOLT_HEIGHT / 2.0);
		
		EnemyProjectile bolt = new EnemyProjectile(
			attackSprites[currentPhase][1],
			spawnX, spawnY,
			P2A2_BOLT_SPEED, 0,
			P2A2_BOLT_WIDTH, P2A2_BOLT_HEIGHT
		);
		enemyProjectiles.add(bolt);
		p2a2ForkableBolts.add(bolt);
	}
	
	// Forks bolts into 3 at x=500: original continues + two at ±20° (cos/sin splitting)
	private void checkP2A2Forks() {
		List<EnemyProjectile> newBolts = new ArrayList<>();
		Iterator<EnemyProjectile> it = p2a2ForkableBolts.iterator();
		
		while (it.hasNext()) {
			EnemyProjectile bolt = it.next();
			if (bolt.isConsumed() || bolt.isOffScreen()) {
				it.remove();
				continue;
			}
			if (bolt.getX() + P2A2_BOLT_WIDTH / 2.0 <= P2A2_FORK_X) {
				double forkX = bolt.getX();
				double forkY = bolt.getY();
				double speed = Math.abs(P2A2_BOLT_SPEED);
				double rad = Math.toRadians(P2A2_FORK_ANGLE);
				
				// Up fork
				EnemyProjectile upBolt = new EnemyProjectile(
					attackSprites[currentPhase][1],
					forkX, forkY,
					-speed * Math.cos(rad), -speed * Math.sin(rad),
					P2A2_BOLT_WIDTH, P2A2_BOLT_HEIGHT
				);
				newBolts.add(upBolt);
				
				// Down fork
				EnemyProjectile downBolt = new EnemyProjectile(
					attackSprites[currentPhase][1],
					forkX, forkY,
					-speed * Math.cos(rad), speed * Math.sin(rad),
					P2A2_BOLT_WIDTH, P2A2_BOLT_HEIGHT
				);
				newBolts.add(downBolt);
				
				it.remove();  // stop tracking; original continues straight
			}
		}
		
		enemyProjectiles.addAll(newBolts);
	}
	
	// Dispatches to the correct attack update based on phase and attack index
	private void updateCurrentAttack() {
		if (currentPhase == 0) {
			if (currentAttack == 0) updateAttack1();
			else if (currentAttack == 1) updateAttack2();
		} else {
			if (currentAttack == 0) updateP2Attack1();
			else if (currentAttack == 1) updateP2Attack2();
		}
	}
	
	// Moves boss back towards home position after an attack
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
	
	// Advances sprite animation frame
	private void advanceAnimation() {
		animationTick++;
		if (animationTick >= ANIMATION_DELAY) {
			animationTick = 0;
			currentFrame = (currentFrame + 1) % 3;
		}
	}
	
	// Draws boss, damage flash, health bar, and projectiles
	public void draw(Graphics2D g2d) {
		if (state == State.DEFEATED || state == State.WAITING) return;
		
		// Hide boss sprite when off-screen during slide-off attacks
		boolean drawBoss = true;
		if (state == State.ATTACKING && currentPhase == 0 && currentAttack == 1
		    && atk2SubState == Attack2SubState.SLIDE_OFF && x > PANEL_WIDTH) {
			drawBoss = false;
		}
		if (state == State.ATTACKING && currentPhase == 1 && currentAttack == 0
		    && p2a1SubState == P2Atk1SubState.SLIDE_OFF && x > PANEL_WIDTH) {
			drawBoss = false;
		}
		
		if (drawBoss) {
			BufferedImage sprite = phaseFrames[currentPhase][currentFrame];
			
			if (sprite != null) {
				g2d.drawImage(sprite, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT, null);
				
				// Damage flash — SRC_IN compositing tints sprite red, overlaid with alpha
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
	
	// Draws health bar with colour transition: green → yellow → red
	private void drawHealthBar(Graphics2D g2d) {
		int barX = x + (DISPLAY_WIDTH - HEALTH_BAR_WIDTH) / 2;
		int barY = y + HEALTH_BAR_Y_OFFSET;
		
		g2d.setColor(new Color(50, 50, 50));
		g2d.fillRect(barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		
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
		
		g2d.setFont(new Font("Arial", Font.BOLD, 14));
		String hpText = totalHp + " / " + totalMaxHp;
		int textWidth = g2d.getFontMetrics().stringWidth(hpText);
		g2d.setColor(Color.WHITE);
		g2d.drawString(hpText, barX + (HEALTH_BAR_WIDTH - textWidth) / 2, barY + 17);
	}
	
	// Draws phase indicator text above health bar
	private void drawPhaseIndicator(Graphics2D g2d) {
		int textX = x + (DISPLAY_WIDTH / 2);
		int textY = y + HEALTH_BAR_Y_OFFSET - 8;
		
		g2d.setFont(new Font("Arial", Font.BOLD, 16));
		g2d.setColor(Color.WHITE);
		String phaseText = "Phase " + (currentPhase + 1) + " / " + TOTAL_PHASES;
		int textWidth = g2d.getFontMetrics().stringWidth(phaseText);
		g2d.drawString(phaseText, textX - textWidth / 2, textY);
	}
	
	// Applies damage; triggers phase transition (SLIDING) when phase HP is depleted
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
	
	// Calculates remaining HP across future phases
	private int getRemainingPhasesHp() {
		int remainingPhases = (TOTAL_PHASES - 1) - currentPhase;
		return remainingPhases * hpPerPhase;
	}
	
	// AABB collision with inset hitbox; only active during IDLE/ATTACKING/RETURNING
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
	
	public int getCurrentPhase() { return currentPhase; }
	public int getTotalHp() { return totalHp; }
	public int getTotalMaxHp() { return totalMaxHp; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDisplayWidth() { return DISPLAY_WIDTH; }
	public int getDisplayHeight() { return DISPLAY_HEIGHT; }
	
	// Active enemy projectiles for collision checking
	public List<EnemyProjectile> getEnemyProjectiles() {
		return enemyProjectiles;
	}
}
