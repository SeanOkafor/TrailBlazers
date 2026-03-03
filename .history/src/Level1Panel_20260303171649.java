import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

// Level 1 panel: renders a 5-layer parallax mountain background, handles
// player/enemy updates, projectile collisions, health packs, and HUD drawing.
public class Level1Panel extends JPanel {
	
	private Player1 player1;
	private Player2 player2; // null unless multiplayer enabled

	// Active projectiles (removed when off-screen or on hit)
	private List<Projectile> projectiles = new ArrayList<>();

	// Tutorial enemy: bobs vertically on right side; null until spawned
	private TutorialEnemy tutorialEnemy;

	// First boss: spawns after tutorial enemy defeated
	private FirstBoss firstBoss;
	private boolean firstBossSpawned = false;

	private ScoreScreen scoreScreen = new ScoreScreen();
	private boolean scoreScreenTriggered = false;

	private LoserScreen loserScreen = new LoserScreen();
	private boolean loserScreenTriggered = false;

	// Health packs: SP spawns every 1500 frames (15s), MP every 750 (7.5s)
	private java.awt.image.BufferedImage healthPackImage = HealthPack.loadSharedImage();
	private List<HealthPack> healthPacks = new ArrayList<>();
	private int healthPackSpawnTimer = 0;
	private static final int HP_SPAWN_INTERVAL_SP = 1500;
	private static final int HP_SPAWN_INTERVAL_MP = 750;
	private Random healthPackRng = new Random();

	// Level timer and scoring
	private long levelStartTime = 0;
	private double elapsedSeconds = 0;
	
	// Score = (damage * 100) - (seconds * 5), minimum 0
	private static final double DAMAGE_MULTIPLIER = 100.0;
	private static final double TIME_PENALTY = 5.0;

	// 5-layer parallax images (back to front)
	private BufferedImage bgLayer;
	private BufferedImage mountainFarLayer;
	private BufferedImage mountainsLayer;
	private BufferedImage treesLayer;
	private BufferedImage foregroundLayer;

	// Scroll offsets (doubles for sub-pixel precision)
	private double bgOffset = 0;
	private double mountainFarOffset = 0;
	private double mountainsOffset = 0;
	private double treesOffset = 0;
	private double foregroundOffset = 0;

	// Parallax speeds (px/frame): bg=0.2, far=0.5, mid=1.0, trees=1.5, front=2.5
	private static final double BG_SPEED = 0.2;
	private static final double MOUNTAIN_FAR_SPEED = 0.5;
	private static final double MOUNTAINS_SPEED = 1.0;
	private static final double TREES_SPEED = 1.5;
	private static final double FOREGROUND_SPEED = 2.5;
	
	public Level1Panel() {
		setDoubleBuffered(true);
		loadLayers();
		player1 = new Player1();
	}

	public Player1 getPlayer1() { return player1; }
	public Player2 getPlayer2() { return player2; }

	// Toggles multiplayer by creating or removing Player 2
	public void setMultiplayer(boolean enabled) {
		if (enabled) {
			if (player2 == null) player2 = new Player2();
		} else {
			player2 = null;
		}
	}

	// Resets all level state for a fresh play
	public void resetTimer() {
		levelStartTime = System.currentTimeMillis();
		elapsedSeconds = 0;
		projectiles.clear();
		tutorialEnemy = null;
		firstBoss = null;
		firstBossSpawned = false;
		scoreScreenTriggered = false;
		scoreScreen.deactivate();
		loserScreenTriggered = false;
		loserScreen.deactivate();
		healthPacks.clear();
		healthPackSpawnTimer = 0;
	}

	// Spawns tutorial enemy (double HP in multiplayer)
	public void spawnTutorialEnemy(boolean multiplayer) {
		tutorialEnemy = new TutorialEnemy(multiplayer);
	}

	public TutorialEnemy getTutorialEnemy() { return tutorialEnemy; }
	public FirstBoss getFirstBoss() { return firstBoss; }
	public ScoreScreen getScoreScreen() { return scoreScreen; }
	public LoserScreen getLoserScreen() { return loserScreen; }
	public double getElapsedSeconds() { return elapsedSeconds; }
	
	// Score = (damage * 100) - (seconds * 5), clamped to minimum 0
	public int calculateScore(int damageDealt) {
		double raw = (damageDealt * DAMAGE_MULTIPLIER) - (elapsedSeconds * TIME_PENALTY);
		return Math.max(0, (int) raw);
	}

	public void addProjectile(Projectile p) { projectiles.add(p); }
	public List<Projectile> getProjectiles() { return projectiles; }

	// Credits damage and special charge to the player who fired the projectile
	private void trackProjectileHit(Projectile p) {
		if (p.getOwnerPlayer() == 1 && player1 != null) {
			player1.addDamageDealt(p.getDamage());
			if (!p.isSpecial()) {
				player1.addSpecialCharge(p.getDamage());
			}
		} else if (p.getOwnerPlayer() == 2 && player2 != null) {
			player2.addDamageDealt(p.getDamage());
			if (!p.isSpecial()) {
				player2.addSpecialCharge(p.getDamage());
			}
		}
	}
	
	// Loads the 5 parallax layer images from disk
	private void loadLayers() {
		String basePath = "res/New Graphics/parallax_mountain_pack/parallax_mountain_pack/layers/";
		try {
			bgLayer = ImageIO.read(new File(basePath + "parallax-mountain-bg.png"));
			mountainFarLayer = ImageIO.read(new File(basePath + "parallax-mountain-montain-far.png"));
			mountainsLayer = ImageIO.read(new File(basePath + "parallax-mountain-mountains.png"));
			treesLayer = ImageIO.read(new File(basePath + "parallax-mountain-trees.png"));
			foregroundLayer = ImageIO.read(new File(basePath + "parallax-mountain-foreground-trees.png"));
		} catch (IOException e) {
			System.err.println("Error loading parallax layers: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Per-frame update: advances parallax offsets, updates entities, and checks collisions
	public void updateParallax() {
		bgOffset += BG_SPEED;
		mountainFarOffset += MOUNTAIN_FAR_SPEED;
		mountainsOffset += MOUNTAINS_SPEED;
		treesOffset += TREES_SPEED;
		foregroundOffset += FOREGROUND_SPEED;

		if (levelStartTime > 0) {
			elapsedSeconds = (System.currentTimeMillis() - levelStartTime) / 1000.0;
		}

		// Update player animations and movement
		if (player1 != null) {
			player1.update();
		}
		if (player2 != null) {
			player2.update();
		}

		if (tutorialEnemy != null && !tutorialEnemy.isDefeated()) {
			tutorialEnemy.update();
		}

		// Update first boss and check its projectiles against players
		if (firstBoss != null && !firstBoss.isDefeated()) {
			firstBoss.update();

			// Enemy projectile → player AABB collision
			for (EnemyProjectile ep : firstBoss.getEnemyProjectiles()) {
				if (ep.isConsumed()) continue;

				if (player1 != null && !player1.isInvincible()) {
					if (ep.collidesWithPlayer(player1.getX(), player1.getY(),
					    player1.getDisplayWidth(), player1.getDisplayHeight())) {
						player1.hit();
						ep.consume();
						continue;
					}
				}

				if (player2 != null && !player2.isInvincible()) {
					if (ep.collidesWithPlayer(player2.getX(), player2.getY(),
					    player2.getDisplayWidth(), player2.getDisplayHeight())) {
						player2.hit();
						ep.consume();
					}
				}
			}
		}

		// Update projectiles; check collisions with tutorial enemy then first boss
		Iterator<Projectile> it = projectiles.iterator();
		while (it.hasNext()) {
			Projectile p = it.next();
			p.update();

			if (tutorialEnemy != null && tutorialEnemy.isAlive() && tutorialEnemy.collidesWith(p)) {
				tutorialEnemy.takeDamage(p.getDamage());
				trackProjectileHit(p);
				it.remove();
				continue;
			}

			if (firstBoss != null && firstBoss.isAlive() && firstBoss.collidesWith(p)) {
				firstBoss.takeDamage(p.getDamage());
				trackProjectileHit(p);
				it.remove();
				continue;
			}
			
			if (p.isOffScreen()) {
				it.remove();
			}
		}

		// Spawn first boss once tutorial enemy is defeated
		if (tutorialEnemy != null && tutorialEnemy.isDefeated() && !firstBossSpawned) {
			firstBossSpawned = true;
			firstBoss = new FirstBoss(player2 != null, player1, player2);
		}

		// First boss defeated → trigger score screen
		if (firstBoss != null && firstBoss.isDefeated() && !scoreScreenTriggered) {
			scoreScreenTriggered = true;
			int p1Dmg = (player1 != null) ? player1.getDamageDealt() : 0;
			int p2Dmg = (player2 != null) ? player2.getDamageDealt() : 0;
			boolean mp = (player2 != null);
			scoreScreen.activate(p1Dmg, p2Dmg, elapsedSeconds, mp);
		}

		// Health pack spawning (SP=1500 frames / MP=750 frames interval)
		healthPackSpawnTimer++;
		int spawnInterval = (player2 != null) ? HP_SPAWN_INTERVAL_MP : HP_SPAWN_INTERVAL_SP;
		if (healthPackSpawnTimer >= spawnInterval) {
			healthPackSpawnTimer = 0;
			double randomY = 50 + healthPackRng.nextInt(850);
			healthPacks.add(new HealthPack(healthPackImage, randomY));
		}

		// Update health packs; AABB collision with players (only heals if below max HP)
		Iterator<HealthPack> hpIt = healthPacks.iterator();
		while (hpIt.hasNext()) {
			HealthPack hp = hpIt.next();
			hp.update();

			if (player1 != null && player1.isAlive() && !hp.isConsumed()) {
				if (hp.collidesWithPlayer(player1.getX(), player1.getY(),
				    player1.getDisplayWidth(), player1.getDisplayHeight())) {
					if (player1.heal()) {
						hp.consume();
					}
				}
			}

			if (player2 != null && player2.isAlive() && !hp.isConsumed()) {
				if (hp.collidesWithPlayer(player2.getX(), player2.getY(),
				    player2.getDisplayWidth(), player2.getDisplayHeight())) {
					if (player2.heal()) {
						hp.consume();
					}
				}
			}

			if (hp.isConsumed() || hp.isOffScreen()) {
				hpIt.remove();
			}
		}

		if (scoreScreen.isActive()) {
			scoreScreen.update();
		}

		// All players fallen off screen → trigger loser screen
		if (!loserScreenTriggered && !scoreScreenTriggered) {
			boolean allDead = false;
			if (player2 != null) {
				allDead = player1.hasFallenOffScreen() && player2.hasFallenOffScreen();
			} else {
				allDead = player1.hasFallenOffScreen();
			}
			if (allDead) {
				loserScreenTriggered = true;
				loserScreen.activate();
			}
		}

		if (loserScreen.isActive()) {
			loserScreen.update();
		}
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		
		int panelWidth = getWidth();
		int panelHeight = getHeight();
		
		// Draw each layer, tiled horizontally and scaled to fill the panel height
		drawLayer(g2d, bgLayer, bgOffset, panelWidth, panelHeight);
		drawLayer(g2d, mountainFarLayer, mountainFarOffset, panelWidth, panelHeight);
		drawLayer(g2d, mountainsLayer, mountainsOffset, panelWidth, panelHeight);
		drawLayer(g2d, treesLayer, treesOffset, panelWidth, panelHeight);
		drawLayer(g2d, foregroundLayer, foregroundOffset, panelWidth, panelHeight);
		
		// Draw Player 1 on top of all parallax layers
		if (player1 != null) {
			player1.draw(g2d);
		}
		// Draw Player 2 (only in multiplayer mode)
		if (player2 != null) {
			player2.draw(g2d);
		}
		
		// Draw all active projectiles on top of players
		for (Projectile p : projectiles) {
			p.draw(g2d);
		}
		
		// Draw health packs
		for (HealthPack hp : healthPacks) {
			hp.draw(g2d);
		}
		
		// Draw the tutorial enemy (on top of parallax, alongside players)
		if (tutorialEnemy != null && !tutorialEnemy.isDefeated()) {
			tutorialEnemy.draw(g2d);
		}
		
		// Draw the first boss (after tutorial enemy is defeated)
		if (firstBoss != null && !firstBoss.isDefeated()) {
			firstBoss.draw(g2d);
		}
		
		// ========== DRAW HUD (timer + HP) ==========
		drawHUD(g2d);
		
		// ========== DRAW SCORE SCREEN (on top of everything) ==========
		if (scoreScreen.isActive()) {
			scoreScreen.draw(g2d);
		}
		
		// ========== DRAW LOSER SCREEN (on top of everything) ==========
		if (loserScreen.isActive()) {
			loserScreen.draw(g2d);
		}
	}
	
	/**
	 * Draws the heads-up display: level timer (top-centre) and player HP bars.
	 * The timer is always visible so players can track their time.
	 * HP is shown as "P1 HP: ♥♥♥♥♥" near each player's area.
	 */
	private void drawHUD(Graphics2D g2d) {
		// --- Timer (top-centre) ---
		int minutes = (int) (elapsedSeconds / 60);
		int seconds = (int) (elapsedSeconds % 60);
		String timeText = String.format("Time: %02d:%02d", minutes, seconds);
		
		g2d.setFont(new Font("Arial", Font.BOLD, 35));
		g2d.setColor(Color.WHITE);
		int timeWidth = g2d.getFontMetrics().stringWidth(timeText);
		g2d.drawString(timeText, (getWidth() - timeWidth) / 2, 40);
		
		// --- Player 1 HP (top-left) ---
		if (player1 != null) {
			String p1Text = "P1 HP: " + buildHPString(player1.getHp(), player1.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.RED);
			g2d.drawString(p1Text, 15, 80);
		}
		
		// --- Player 2 HP (below P1 HP, only in multiplayer) ---
		if (player2 != null) {
			String p2Text = "P2 HP: " + buildHPString(player2.getHp(), player2.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.BLUE);
			g2d.drawString(p2Text, 15, 115);
		}
	}
	
	/**
	 * Builds a heart-based HP string: filled hearts for remaining HP, empty for lost.
	 * e.g. 3/5 HP → "♥♥♥♡♡"
	 */
	private String buildHPString(int current, int max) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < max; i++) {
			sb.append(i < current ? "\u2665" : "\u2661");  // ♥ or ♡
		}
		return sb.toString();
	}
	
	/**
	 * Draws a single parallax layer, tiled horizontally and scrolling left.
	 * 
	 * How it works:
	 * - The small layer image (e.g. 272x160) is scaled to fill the full panel height (1000px)
	 * - The scaled width is calculated proportionally (maintains aspect ratio)
	 * - The image is tiled (repeated) horizontally across the panel
	 * - The offset shifts all tiles left, creating the scrolling effect
	 * - Modulo wrapping ensures seamless infinite looping
	 */
	private void drawLayer(Graphics2D g2d, BufferedImage layer, double offset, int panelWidth, int panelHeight) {
		if (layer == null) return;
		
		// Step 1: Scale the tiny layer image to fill the panel height
		// e.g. if layer is 272x160 and panel is 1000px tall:
		//   scale = 1000 / 160 = 6.25
		//   scaledWidth = 272 * 6.25 = 1700px
		double scale = (double) panelHeight / layer.getHeight();
		int scaledWidth = (int) (layer.getWidth() * scale);
		
		// Step 2: Wrap offset using modulo so it loops seamlessly
		// When offset reaches scaledWidth, it resets to 0
		double wrappedOffset = offset % scaledWidth;
		
		// Step 3: Tile the image from left to right, shifted by the offset
		// Starting at -wrappedOffset ensures smooth leftward scrolling
		int startX = (int) -wrappedOffset;
		for (int x = startX; x < panelWidth; x += scaledWidth) {
			g2d.drawImage(layer, x, 0, scaledWidth, panelHeight, null);
		}
		
		// Step 4: Fill any gap on the left edge with one extra tile
		if (startX > 0) {
			g2d.drawImage(layer, startX - scaledWidth, 0, scaledWidth, panelHeight, null);
		}
	}
}
