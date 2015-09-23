package com.theaigames.game.warlight2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import com.theaigames.engine.Engine;
import com.theaigames.engine.Logic;
import com.theaigames.engine.io.IOPlayer;
import com.theaigames.game.warlight2.map.Map;
import com.theaigames.game.warlight2.move.AttackTransferMove;
import com.theaigames.game.warlight2.move.MoveResult;
import com.theaigames.game.warlight2.move.PlaceArmiesMove;

/**
 * Warlight2 class
 * 
 * Main class for Warlight2
 * 
 * @author Jim van Eeden <jim@starapple.nl>
 */

public class Warlight2 implements Logic {
	
	
	public static Random randomGenerator1 = new Random(10);
	public static Random randomGenerator2 = new Random(20);
	public static Random randomGenerator3 = new Random(30);
	public static Random randomGenerator4 = new Random(40);
	public static Random randomGenerator5 = new Random(50);
	public static Random randomGenerator6 = new Random(60);
	public static Random randomGenerator7 = new Random(70);
	
	
	private static List<String> mapFiles = new ArrayList<String>();
	private static String bot1Cmd = "java -jar C:/Users/Norman/Desktop/WarLightBot/TestBots/WarLight2Bot-2015-05-10_2.jar";
	// private static String bot1Cmd =
	// "java -jar C:/Users/Norman/Desktop/WarLightBot/TestBots/WarLight2Bot-2015-04-01.jar";
	private static String bot2Cmd = "java -jar C:/Users/Norman/Desktop/WarLightBot/TestBots/WarLight2Bot-2015-05-17.jar";
	private static int mapFileIndex = 0;

	private String playerName1, playerName2;

	private Processor processor;
	private Player player1, player2;
	private int maxRounds;

	private final int STARTING_ARMIES = 5;
	private final long TIMEBANK_MAX = 10000l;
	private final long TIME_PER_MOVE = 500l;
	private final int SIZE_WASTELANDS = 6; // size of wastelands, <= 0 for no wastelands
	private static List<WinnerInformation> winnerInformations = new ArrayList<Warlight2.WinnerInformation>();

	public Warlight2(String mapFile) {
		// Warlight2.mapFile = mapFile;
		this.playerName1 = "player1";
		this.playerName2 = "player2";
	}

	/**
	 * sets up everything that's needed before a round can be played
	 * 
	 * @param players
	 *            : list of bots that have already been initialized
	 */
	@Override
	public void setupGame(ArrayList<IOPlayer> players) throws IncorrectPlayerCountException, IOException {

		Map initMap, map;

		// Determine array size is two players
		if (players.size() != 2) {
			throw new IncorrectPlayerCountException("Should be two players");
		}

		this.player1 = new Player(playerName1, players.get(0), STARTING_ARMIES, TIMEBANK_MAX, TIME_PER_MOVE);
		this.player2 = new Player(playerName2, players.get(1), STARTING_ARMIES, TIMEBANK_MAX, TIME_PER_MOVE);

		// get map string from database and setup the map
		initMap = MapCreator.createMap(getMapString());

		map = MapCreator.setupMap(initMap, SIZE_WASTELANDS);
		this.maxRounds = MapCreator.determineMaxRounds(map);

		// start the processor
		this.processor = new Processor(map, player1, player2);

		sendSettings(player1);
		sendSettings(player2);
		MapCreator.sendSetupMapInfo(player1, map);
		MapCreator.sendSetupMapInfo(player2, map);

		player1.setTimeBank(TIMEBANK_MAX);
		player2.setTimeBank(TIMEBANK_MAX);

		this.processor.distributeStartingRegions(); // decide the player's starting regions
		this.processor.recalculateStartingArmies(); // calculate how much armies the players get at the start of the
													// round (depending on owned SuperRegions)
		this.processor.sendAllInfo();
	}

	/**
	 * play one round of the game
	 * 
	 * @param roundNumber
	 *            : round number
	 */
	@Override
	public void playRound(int roundNumber) {
		player1.getBot().addToDump(String.format("Round %d\n", roundNumber));
		player2.getBot().addToDump(String.format("Round %d\n", roundNumber));

		this.processor.playRound(roundNumber);
	}

	/**
	 * @return : True when the game is over
	 */
	@Override
	public boolean isGameWon() {
		if (this.processor.getWinner() != null || this.processor.getRoundNr() > this.maxRounds) {
			return true;
		}
		return false;
	}

	/**
	 * Sends all game settings to given player
	 * 
	 * @param player
	 *            : player to send settings to
	 */
	private void sendSettings(Player player) {
		player.sendInfo("settings timebank " + TIMEBANK_MAX);
		player.sendInfo("settings time_per_move " + TIME_PER_MOVE);
		player.sendInfo("settings max_rounds " + this.maxRounds);
		player.sendInfo("settings your_bot " + player.getName());

		if (player.getName().equals(player1.getName()))
			player.sendInfo("settings opponent_bot " + player2.getName());
		else
			player.sendInfo("settings opponent_bot " + player1.getName());
	}

	/**
	 * Reads the string from the map file
	 * 
	 * @return : string representation of the map
	 * @throws IOException
	 */
	private String getMapString() throws IOException {
		File file = new File(mapFiles.get(mapFileIndex));
		StringBuilder fileContents = new StringBuilder((int) file.length());
		Scanner scanner = new Scanner(file);
		String lineSeparator = System.getProperty("line.separator");

		try {
			while (scanner.hasNextLine()) {
				fileContents.append(scanner.nextLine() + lineSeparator);
			}
			return fileContents.toString();
		} finally {
			scanner.close();
		}
	}

	/**
	 * close the bot processes, save, exit program
	 */
	@Override
	public void finish() throws Exception {
		this.player1.getBot().finish();
		this.player2.getBot().finish();
		this.saveGame();
		this.processor = null;
		this.player1 = null;
		this.player2 = null;
		// Thread.sleep(100);
	}

	/**
	 * Does everything that is needed to store the output of a game
	 */
	private void saveGame() {
		Player winner = this.processor.getWinner();
		WinnerInformation winnerInformation = new WinnerInformation();
		if (winner != null) {
			if (winner.getName().equals("player1")) {
				winnerInformation.player1Win = true;
			} else {
				winnerInformation.player2Win = true;
			}
		}

		winnerInformation.roundNumer = this.processor.getRoundNr() - 1;
		winnerInformations.add(winnerInformation);

	}

	private static void printWinnerInformation() {
		int winsPlayer1 = 0;
		int winsPlayer2 = 0;
		int draws = 0;
		for (WinnerInformation winnerInformation : winnerInformations) {
			if (winnerInformation.player1Win) {
				winsPlayer1++;
			} else if (winnerInformation.player2Win) {
				winsPlayer2++;
			} else {
				draws++;
			}
		}
		System.out.println("Player1 wins: " + winsPlayer1);
		System.out.println("Player2 wins: " + winsPlayer2);
		System.out.println("Draws: " + draws);
	}

	/**
	 * Turns the game that is stored in the processor to a nice string for the visualization
	 * 
	 * @param winner
	 *            : winner
	 * @param gameView
	 *            : type of view
	 * @return : string that the visualizer can read
	 */
	private String getPlayedGame(Player winner, String gameView) {
		StringBuilder out = new StringBuilder();

		LinkedList<MoveResult> playedGame;
		if (gameView.equals("player1"))
			playedGame = this.processor.getPlayer1PlayedGame();
		else if (gameView.equals("player2"))
			playedGame = this.processor.getPlayer2PlayedGame();
		else
			playedGame = this.processor.getFullPlayedGame();

		playedGame.removeLast();
		int roundNr = 0;
		for (MoveResult moveResult : playedGame) {
			if (moveResult != null) {
				if (moveResult.getMove() != null) {
					try {
						PlaceArmiesMove plm = (PlaceArmiesMove) moveResult.getMove();
						out.append(plm.getString() + "\n");
					} catch (Exception e) {
						AttackTransferMove atm = (AttackTransferMove) moveResult.getMove();
						out.append(atm.getString() + "\n");
					}

				}
				out.append("map " + moveResult.getMap().getMapString() + "\n");
			} else {
				out.append("round " + roundNr + "\n");
				roundNr++;
			}
		}

		if (winner != null)
			out.append(winner.getName() + " won\n");
		else
			out.append("Nobody won\n");

		return out.toString();
	}

	public static void main(String args[]) throws Exception {
		// mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map10.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map1.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map2.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map3.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map4.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map5.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map6.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map7.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map8.txt");
		mapFiles.add("C:/Users/Norman/Desktop/WarLightBot/TestBots/map9.txt");

		for (int i = 1; i <= 200; i++) {
			mapFileIndex = i % 9;
			Engine engine = new Engine();
			engine.setLogic(new Warlight2(mapFiles.get(mapFileIndex)));
			engine.addPlayer(bot1Cmd);
			engine.addPlayer(bot2Cmd);
			engine.start();
			System.out.println(engine.players.get(1).getStderr());
			printWinnerInformation();
			randomGenerator1 = new Random(randomGenerator1.nextInt());
			randomGenerator2 = new Random(randomGenerator2.nextInt());
			randomGenerator3 = new Random(randomGenerator3.nextInt());
			randomGenerator4 = new Random(randomGenerator4.nextInt());
			randomGenerator5 = new Random(randomGenerator5.nextInt());
			randomGenerator6 = new Random(randomGenerator6.nextInt());
			randomGenerator7 = new Random(randomGenerator7.nextInt());
		}
		printWinnerInformation();
	}

	private class WinnerInformation {
		boolean player1Win = false;
		boolean player2Win = false;
		int roundNumer = 0;
	}

}
