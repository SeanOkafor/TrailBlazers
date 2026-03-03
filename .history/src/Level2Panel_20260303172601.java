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

// Level2Panel — 4-layer parallax industrial background for Level 2.
// Layers scroll at different speeds (bg=0.2, far=0.6, mid=1.2, fg=2.5 px/frame)
// to create depth. Each layer is scaled, tiled and looped via modulo wrapping.
public class Level2Panel extends JPanel {
	
	private Player1 player1;
	private Player2 player2;  // null when multiplayer is off
	
	// Active projectiles — removed when off-screen or on hit
	private List<Projectile> projectiles = new ArrayList<>();
	
	private FinalBoss finalBoss;
	
	// Score/loser screen overlays
	private ScoreScreen scoreScreen = new ScoreScreen();
	private boolean scoreScreenTriggered = false;
	private LoserScreen loserScreen = new LoserScreen();
	private boolean loserScreenTriggered = false;
	
	// Health pack spawning
	private java.awt.image.BufferedImage healthPackImage = HealthPack.loadSharedImage();
	private List<HealthPack> healthPacks = new ArrayList<>();
	private int healthPackSpawnTimer = 0;
	private static final int HP_SPAWN_INTERVAL_SP = 1500;  // 15s at 100 FPS
	private static final int HP_SPAWN_INTERVAL_MP = 750;   // 7.5s at 100 FPS
	private Random healthPackRng = new Random();
	
	// Level timer and scoring
	private long levelStartTime = 0;
	private double elapsedSeconds = 0;
	private static final double DAMAGE_MULTIPLIER = 100.0;
	private static final double TIME_PENALTY = 5.0;
	
	// Parallax layers (back to front): sky, distant factories, buildings, foreground
	private BufferedImage bgLayer;
	private BufferedImage farBuildingsLayer;
	private BufferedImage buildingsLayer;
	private BufferedImage foregroundLayer;
	
	// Scroll offsets — doubles for sub-pixel precision at fractional speeds
	private double bgOffset = 0;
	private double farBuildingsOffset = 0;
	private double buildingsOffset = 0;
	private double foregroundOffset = 0;
	
	// Scroll speeds (px/frame): slower = further away, faster = closer
	private static final double BG_SPEED = 0.2;
	private static final double FAR_BUILDINGS_SPEED = 0.6;
	private static final double BUILDINGS_SPEED = 1.2;
	private static final double FOREGROUND_SPEED = 2.5;
	
	public Level2Panel() {
		setDoubleBuffered(true);
		loadLayers();
		player1 = new Player1();
	}
	
	// Returns Player1 for MainWindow movement commands
	public Player1 getPlayer1() {
		return player1;
	}
	
	// Returns Player2 (null if multiplayer is off)
	public Player2 getPlayer2() {
		return player2;
	}
	
	// Creates or removes Player 2 based on multiplayer toggle
	public void setMultiplayer(boolean enabled) {
		if (enabled) {
			if (player2 == null) player2 = new Player2();
		} else {
			player2 = null;
		}
	}
	
	// Resets timer, clears projectiles, boss state and overlays for a fresh play
	public void resetTimer() {
		levelStartTime = System.currentTimeMillis();
		elapsedSeconds = 0;
		projectiles.clear();
		finalBoss = null;
		scoreScreenTriggered = false;
		scoreScreen.deactivate();
		loserScreenTriggered = false;
		loserScreen.deactivate();
		healthPacks.clear();
		healthPackSpawnTimer = 0;
	}
	
	public double getElapsedSeconds() {
		return elapsedSeconds;
	}
	
	// Score = (damage * 100) - (time * 5), floored at 0
	public int calculateScore(int damageDealt) {
		double raw = (damageDealt * DAMAGE_MULTIPLIER) - (elapsedSeconds * TIME_PENALTY);
		return Math.max(0, (int) raw);
	}
	
	// Adds a projectile when a player shoots
	public void addProjectile(Projectile p) {
		projectiles.add(p);
	}
	
	public List<Projectile> getProjectiles() {
		return projectiles;
	}
	
	// Spawns the Final Boss; called from MainWindow on level entry
	public void spawnFinalBoss(boolean multiplayer) {
		finalBoss = new FinalBoss(multiplayer, player1, player2);
	}
	
	public FinalBoss getFinalBoss() {
		return finalBoss;
	}
	
	public ScoreScreen getScoreScreen() {
		return scoreScreen;
	}
	
	public LoserScreen getLoserScreen() {
		return loserScreen;
	}
	
	// Loads the 4 industrial parallax layer PNGs
	private void loadLayers() {
		String basePath = "res/New Graphics/parallax-industrial-pack/parallax-industrial-pack/layers/";
		try {
			bgLayer = ImageIO.read(new File(basePath + "skill-desc_0003_bg.png"));
			farBuildingsLayer = ImageIO.read(new File(basePath + "skill-desc_0002_far-buildings.png"));
			buildingsLayer = ImageIO.read(new File(basePath + "skill-desc_0001_buildings.png"));
			foregroundLayer = ImageIO.read(new File(basePath + "skill-desc_0000_foreground.png"));
		} catch (IOException e) {
			System.err.println("Error loading parallax layers: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	// Per-frame update: advances scroll offsets, updates entities, handles collisions
	public void updateParallax() {
		bgOffset += BG_SPEED;
		farBuildingsOffset += FAR_BUILDINGS_SPEED;
		buildingsOffset += BUILDINGS_SPEED;
		foregroundOffset += FOREGROUND_SPEED;
		
		if (levelStartTime > 0) {
			elapsedSeconds = (System.currentTimeMillis() - levelStartTime) / 1000.0;
		}
		
		if (player1 != null) {
			player1.update();
		}
		if (player2 != null) {
			player2.update();
		}
		
		// Iterate projectiles: move, check collisions, cull off-screen
		Iterator<Projectile> it = projectiles.iterator();
		while (it.hasNext()) {
			Projectile p = it.next();
			p.update();
			
			// Collision with the final boss
			if (finalBoss != null && finalBoss.isAlive() && finalBoss.collidesWith(p)) {
				finalBoss.takeDamage(p.getDamage());
				
				// Credit damage and special charge to the firing player
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
				
				it.remove();
				continue;
			}
			
			// Collision with mini boss (Phase 3, Attack 4 — separate hitbox)
			if (finalBoss != null && finalBoss.isMiniBossAlive() && finalBoss.miniBossCollidesWith(p)) {
				finalBoss.damageMiniBoss(p.getDamage());
				
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
				
				it.remove();
				continue;
			}
			
			if (p.isOffScreen()) {
				it.remove();
			}
		}
		
		// Update boss behaviour: entry animation, phase transitions, attacks
		if (finalBoss != null && !finalBoss.isDefeated()) {
			finalBoss.update();
			
			// Enemy projectile → player collision
			for (EnemyProjectile ep : finalBoss.getEnemyProjectiles()) {
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
		
		// Boss defeated — trigger score screen
		if (finalBoss != null && finalBoss.isDefeated() && !scoreScreenTriggered) {
			scoreScreenTriggered = true;
			int p1Dmg = (player1 != null) ? player1.getDamageDealt() : 0;
			int p2Dmg = (player2 != null) ? player2.getDamageDealt() : 0;
			boolean mp = (player2 != null);
			scoreScreen.activate(p1Dmg, p2Dmg, elapsedSeconds, mp);
		}
		
		// Health pack spawning (faster interval in multiplayer)
		healthPackSpawnTimer++;
		int spawnInterval = (player2 != null) ? HP_SPAWN_INTERVAL_MP : HP_SPAWN_INTERVAL_SP;
		if (healthPackSpawnTimer >= spawnInterval) {
			healthPackSpawnTimer = 0;
			double randomY = 50 + healthPackRng.nextInt(850);
			healthPacks.add(new HealthPack(healthPackImage, randomY));
		}
		
		// Update health packs and check player collisions
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
		
		// All players dead and off-screen — trigger loser screen
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
		
		// Draw parallax layers back-to-front
		drawLayer(g2d, bgLayer, bgOffset, panelWidth, panelHeight);
		drawLayer(g2d, farBuildingsLayer, farBuildingsOffset, panelWidth, panelHeight);
		drawLayer(g2d, buildingsLayer, buildingsOffset, panelWidth, panelHeight);
		drawLayer(g2d, foregroundLayer, foregroundOffset, panelWidth, panelHeight);
		
		if (player1 != null) {
			player1.draw(g2d);
		}
		if (player2 != null) {
			player2.draw(g2d);
		}
		
		for (Projectile p : projectiles) {
			p.draw(g2d);
		}
		
		for (HealthPack hp : healthPacks) {
			hp.draw(g2d);
		}
		
		if (finalBoss != null && !finalBoss.isDefeated()) {
			finalBoss.draw(g2d);
		}
		
		// HUD, score and loser overlays drawn last (on top of everything)
		drawHUD(g2d);
		
		if (scoreScreen.isActive()) {
			scoreScreen.draw(g2d);
		}
		
		if (loserScreen.isActive()) {
			loserScreen.draw(g2d);
		}
	}
	
	// Draws the HUD: centred timer and heart-based HP display
	private void drawHUD(Graphics2D g2d) {
		int minutes = (int) (elapsedSeconds / 60);
		int seconds = (int) (elapsedSeconds % 60);
		String timeText = String.format("Time: %02d:%02d", minutes, seconds);
		
		g2d.setFont(new Font("Arial", Font.BOLD, 35));
		g2d.setColor(Color.WHITE);
		int timeWidth = g2d.getFontMetrics().stringWidth(timeText);
		g2d.drawString(timeText, (getWidth() - timeWidth) / 2, 40);
		
		if (player1 != null) {
			String p1Text = "P1 HP: " + buildHPString(player1.getHp(), player1.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.RED);
			g2d.drawString(p1Text, 15, 80);
		}
		
		if (player2 != null) {
			String p2Text = "P2 HP: " + buildHPString(player2.getHp(), player2.getMaxHp());
			g2d.setFont(new Font("Arial", Font.BOLD, 28));
			g2d.setColor(Color.BLUE);
			g2d.drawString(p2Text, 15, 115);
		}
	}
	
	// Builds a hearts string: filled ♥ for remaining HP, empty ♡ for lost
	private String buildHPString(int current, int max) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < max; i++) {
			sb.append(i < current ? "\u2665" : "\u2661");
		}
		return sb.toString();
	}
	
	// Draws a single parallax layer, scaled to panel height and tiled horizontally.
	// Modulo wrapping on the offset ensures seamless infinite scrolling.
	private void drawLayer(Graphics2D g2d, BufferedImage layer, double offset, int panelWidth, int panelHeight) {
		if (layer == null) return;
		
		// Scale layer to fill panel height, preserving aspect ratio
		double scale = (double) panelHeight / layer.getHeight();
		int scaledWidth = (int) (layer.getWidth() * scale);
		
		// Wrap offset with modulo for seamless looping
		double wrappedOffset = offset % scaledWidth;
		
		// Tile from -wrappedOffset rightward across the panel
		int startX = (int) -wrappedOffset;
		for (int x = startX; x < panelWidth; x += scaledWidth) {
			g2d.drawImage(layer, x, 0, scaledWidth, panelHeight, null);
		}
		
		// Fill any gap on the left edge
		if (startX > 0) {
			g2d.drawImage(layer, startX - scaledWidth, 0, scaledWidth, panelHeight, null);
		}
	}
}
